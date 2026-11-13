package app.regie.voice.api

import kotlinx.coroutines.flow.Flow

data class SpeechRequest(
    /** ISO 639-1 code. */
    val language: String = "fr",
)

/** A synthesis voice offered by a provider. */
data class SpeechVoice(val id: String, val name: String, val language: String? = null)

/** Streaming, interruptible speech synthesis port. */
interface TextToSpeech {
    /**
     * Speaks [text]. The flow is cold: collecting starts the synthesis, frames arrive as soon as
     * they are produced, and cancelling the collection interrupts it (the request or the engine
     * is stopped). Failures are thrown as [VoiceException].
     */
    fun synthesize(text: String, request: SpeechRequest = SpeechRequest()): Flow<AudioFrame>

    /** Voices the user can choose from; the choice itself is a setting of the adapter. */
    suspend fun voices(): List<SpeechVoice>
}
