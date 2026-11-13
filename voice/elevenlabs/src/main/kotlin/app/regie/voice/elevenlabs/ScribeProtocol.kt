package app.regie.voice.elevenlabs

import app.regie.voice.api.AudioFrame
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.VoiceError
import app.regie.voice.api.Vocabulary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Base64

/**
 * Wire protocol of `wss://api.elevenlabs.io/v1/speech-to-text/realtime`, checked on 13 November
 * 2026 against the API reference and the official JS SDK (`src/wrapper/realtime/connection.ts`
 * and `scribe.ts` of elevenlabs-js).
 */
internal object ScribeProtocol {
    const val AUDIO_FORMAT = "pcm_16000"

    fun url(config: ElevenLabsConfig, request: DictationRequest): HttpUrl =
        config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("v1/speech-to-text/realtime")
            .addQueryParameter("model_id", config.sttModel.id)
            .addQueryParameter("audio_format", AUDIO_FORMAT)
            .addQueryParameter("language_code", request.language)
            // We commit ourselves when the push-to-talk button is released.
            .addQueryParameter("commit_strategy", "manual")
            .apply {
                // The SDK repeats the parameter: params.append("keyterms", term).
                ScribeKeyterms.from(request.vocabulary).forEach { addQueryParameter("keyterms", it) }
            }
            .build()

    fun audioChunk(frame: AudioFrame): String = chunk(
        Base64.getEncoder().encodeToString(frame.toLittleEndianBytes()),
        commit = false,
        sampleRate = frame.format.sampleRateHz,
    )

    /** Same message as the SDK's `commit()`: an empty chunk with `commit: true`. */
    fun commit(sampleRate: Int): String = chunk("", commit = true, sampleRate = sampleRate)

    private fun chunk(audioBase64: String, commit: Boolean, sampleRate: Int) = buildJsonObject {
        put("message_type", "input_audio_chunk")
        put("audio_base_64", audioBase64)
        put("commit", commit)
        put("sample_rate", sampleRate)
    }.toString()

    sealed interface Message {
        data object SessionStarted : Message

        data class Partial(val text: String) : Message

        data class Committed(val text: String) : Message

        data class Error(val type: String, val message: String?) : Message

        /** Messages we do not use: warnings, final_transcript, timestamps, entities… */
        data class Other(val type: String?) : Message
    }

    private val ERROR_TYPES = setOf(
        "error", "auth_error", "quota_exceeded", "commit_throttled", "transcriber_error", "unaccepted_terms",
        "rate_limited", "input_error", "invalid_request", "queue_overflow", "resource_exhausted",
        "session_time_limit_exceeded", "chunk_size_exceeded", "insufficient_audio_activity",
    )

    fun parse(text: String): Message {
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return Message.Other(null)
        val type = json.string("message_type")
        return when (type) {
            "session_started" -> Message.SessionStarted
            "partial_transcript" -> Message.Partial(json.string("text").orEmpty())
            "committed_transcript" -> Message.Committed(json.string("text").orEmpty())
            in ERROR_TYPES -> Message.Error(type!!, json.string("error"))
            else -> Message.Other(type)
        }
    }

    fun error(type: String, message: String?): VoiceError = when (type) {
        "auth_error" -> VoiceError.KeyRejected
        "quota_exceeded" -> VoiceError.QuotaExceeded
        "rate_limited", "commit_throttled" -> VoiceError.RateLimited
        else -> VoiceError.Provider(type, message)
    }

    private fun JsonObject.string(key: String): String? = runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/** ElevenLabs keyterm limits: 50 terms at most, 20 characters each. */
internal object ScribeKeyterms {
    const val MAX_TERMS = 50
    const val MAX_LENGTH = 20

    /**
     * Keeps the first [MAX_TERMS] usable terms in priority order. A term longer than [MAX_LENGTH]
     * is dropped rather than cut: a cut term would bias towards a word that does not exist.
     */
    fun from(vocabulary: Vocabulary): List<String> = vocabulary.terms.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.length <= MAX_LENGTH }
        .distinctBy { it.lowercase() }
        .take(MAX_TERMS)
        .toList()
}
