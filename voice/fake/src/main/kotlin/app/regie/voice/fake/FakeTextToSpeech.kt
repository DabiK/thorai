package app.regie.voice.fake

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.SpeechRequest
import app.regie.voice.api.SpeechVoice
import app.regie.voice.api.TextToSpeech
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Synthesis that "speaks" silence: [msPerCharacter] of audio per character, in frames of
 * [frameMs], paced in real time when [realtime] is set. Remembers what it was asked to say.
 */
class FakeTextToSpeech(
    val format: AudioFormat = AudioFormat(22_050),
    private val msPerCharacter: Int = 60,
    private val frameMs: Int = 100,
    private val realtime: Boolean = false,
    private val error: VoiceError? = null,
) : TextToSpeech {
    private val texts = mutableListOf<String>()

    /** Texts given to [synthesize], oldest first. */
    val spoken: List<String> get() = synchronized(texts) { texts.toList() }

    override fun synthesize(text: String, request: SpeechRequest): Flow<AudioFrame> = flow {
        synchronized(texts) { texts += text }
        if (error != null) throw VoiceException(error)
        var remainingMs = text.length * msPerCharacter
        while (remainingMs > 0) {
            val ms = minOf(frameMs, remainingMs)
            emit(AudioFrame(ShortArray(format.samplesFor(ms)), format))
            remainingMs -= ms
            if (realtime) delay(ms.toLong())
        }
    }

    override suspend fun voices(): List<SpeechVoice> = listOf(SpeechVoice("fake", "Voix factice", "fr"))
}
