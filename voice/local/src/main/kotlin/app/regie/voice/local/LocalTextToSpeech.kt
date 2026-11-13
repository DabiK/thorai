package app.regie.voice.local

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.SpeechRequest
import app.regie.voice.api.SpeechVoice
import app.regie.voice.api.TextToSpeech
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Offline synthesis with Supertonic 3, in the language of the request. The text is generated
 * sentence by sentence (Supertonic would otherwise generate it whole before the first sound), each
 * one emitted as soon as it is ready; cancelling the collection stops after the sentence in
 * progress.
 *
 * The model must have been installed by [LocalVoiceProvider.prepare]: otherwise the flow fails
 * with [app.regie.voice.api.VoiceError.ModelMissing].
 */
class LocalTextToSpeech internal constructor(
    private val engines: EngineSource,
    private val config: LocalConfig,
) : TextToSpeech {

    override fun synthesize(text: String, request: SpeechRequest): Flow<AudioFrame> = callbackFlow {
        val synthesizer = engines.synthesizer()
        val format = AudioFormat(synthesizer.sampleRateHz)
        val speaker = config.speaker()
        // A native call cannot be interrupted: the generation runs apart from the collector, so
        // that cancelling cuts the voice at once; the engine stops at its next callback.
        val generation = CoroutineScope(engines.engineDispatcher).launch {
            try {
                for (sentence in sentences(text)) {
                    if (!isActive) break
                    synthesizer.generate(sentence, request.language, speaker, config.speechSpeed) { samples ->
                        isActive && trySendBlocking(AudioFrame(toPcm(samples), format)).isSuccess
                    }
                }
                channel.close()
            } catch (e: CancellationException) {
                channel.close()
            } catch (e: Exception) {
                channel.close(VoiceException(VoiceError.Provider("local_tts", e.message), e))
            }
        }
        awaitClose { generation.cancel() }
    }

    /** The voices of Supertonic 3; the id is the speaker number to put in [LocalConfig.speaker]. */
    override suspend fun voices(): List<SpeechVoice> = (0 until VOICES).map { SpeechVoice("$it", "Supertonic ${it + 1}") }

    companion object {
        private const val VOICES = 10

        private val sentenceEnd = Regex("""(?<=[.!?…])\s+""")

        /** Sentences of [text], split after `.`, `!`, `?` and `…` followed by a space. */
        internal fun sentences(text: String): List<String> = text.trim().split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }

        internal fun toPcm(samples: FloatArray): ShortArray =
            ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }
}
