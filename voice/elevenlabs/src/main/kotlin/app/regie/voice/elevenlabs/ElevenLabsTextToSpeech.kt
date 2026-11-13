package app.regie.voice.elevenlabs

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.Pcm16
import app.regie.voice.api.SpeechRequest
import app.regie.voice.api.SpeechVoice
import app.regie.voice.api.TextToSpeech
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.io.IOException

/** Streaming synthesis with ElevenLabs Flash, as raw PCM. */
class ElevenLabsTextToSpeech(
    private val config: ElevenLabsConfig,
    private val client: OkHttpClient = ElevenLabsVoiceProvider.defaultClient(),
) : TextToSpeech {
    private val format = AudioFormat(config.ttsSampleRateHz)

    override fun synthesize(text: String, request: SpeechRequest): Flow<AudioFrame> = callbackFlow {
        val key = config.apiKey()?.takeIf { it.isNotBlank() } ?: throw VoiceException(VoiceError.KeyMissing)
        val voiceId = config.voiceId()?.takeIf { it.isNotBlank() } ?: throw VoiceException(VoiceError.NotConfigured("voice_id"))
        val url = config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("v1/text-to-speech")
            .addPathSegment(voiceId)
            .addPathSegment("stream")
            .addQueryParameter("output_format", "pcm_${format.sampleRateHz}")
            .build()
        val body = buildJsonObject {
            put("text", text)
            put("model_id", config.ttsModel)
            put("language_code", request.language)
        }.toString().toRequestBody(JSON)
        val call = client.newCall(Request.Builder().url(url).header("xi-api-key", key).post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(if (call.isCanceled()) null else VoiceException(VoiceError.Offline, e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        close(VoiceException(ElevenLabsErrors.http(it.code, it.peekBody(4_096).string())))
                        return
                    }
                    try {
                        readFrames(it.body.source()) { frame -> trySendBlocking(frame).isSuccess }
                        close()
                    } catch (e: IOException) {
                        close(if (call.isCanceled()) null else VoiceException(VoiceError.Offline, e))
                    }
                }
            }
        })
        // Interrupting = cancelling the collection: the HTTP request is cancelled with it.
        awaitClose { call.cancel() }
    }

    /** Emits frames as soon as bytes arrive, keeping an odd trailing byte for the next read. */
    private fun readFrames(source: BufferedSource, emit: (AudioFrame) -> Boolean) {
        val maxBytes = format.samplesFor(FRAME_MS).toLong() * 2
        val buffer = Buffer()
        var carry: Byte? = null
        while (source.read(buffer, maxBytes) != -1L) {
            val fresh = buffer.readByteArray()
            val bytes = if (carry != null) byteArrayOf(carry) + fresh else fresh
            val even = bytes.size and 1.inv()
            carry = if (even < bytes.size) bytes[even] else null
            if (even > 0 && !emit(AudioFrame(Pcm16.fromLittleEndian(bytes, 0, even), format))) return
        }
    }

    override suspend fun voices(): List<SpeechVoice> = withContext(Dispatchers.IO) {
        val key = config.apiKey()?.takeIf { it.isNotBlank() } ?: throw VoiceException(VoiceError.KeyMissing)
        val url = config.baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/voices").build()
        val response = try {
            client.newCall(Request.Builder().url(url).header("xi-api-key", key).get().build()).execute()
        } catch (e: IOException) {
            throw VoiceException(VoiceError.Offline, e)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) throw VoiceException(ElevenLabsErrors.http(it.code, body))
            parseVoices(body)
        }
    }

    private fun parseVoices(body: String): List<SpeechVoice> {
        val voices = Json.parseToJsonElement(body).jsonObject["voices"] as? JsonArray ?: return emptyList()
        return voices.mapNotNull { element ->
            val voice = element as? JsonObject ?: return@mapNotNull null
            val id = voice.string("voice_id") ?: return@mapNotNull null
            val language = (voice["labels"] as? JsonObject)?.string("language")
                ?: ((voice["verified_languages"] as? JsonArray)?.firstOrNull() as? JsonObject)?.string("language")
            SpeechVoice(id, voice.string("name") ?: id, language)
        }
    }

    private fun JsonObject.string(key: String): String? = runCatching { get(key)?.jsonPrimitive?.contentOrNull }.getOrNull()

    private companion object {
        val JSON = "application/json".toMediaType()
        const val FRAME_MS = 100
    }
}
