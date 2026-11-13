package app.regie.voice.local

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.SpeechToText
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import app.regie.voice.api.Vocabulary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline transcription: Silero cuts the audio into utterances and Parakeet decodes each of them
 * ("simulated streaming", see [SegmentedTranscriber]).
 *
 * Models are installed on first use, which shows as [DictationState.PreparingModel]; the audio is
 * buffered meanwhile, so nothing is lost.
 */
class LocalSpeechToText internal constructor(
    private val engines: EngineSource,
    private val config: LocalConfig,
) : SpeechToText {

    /** Statistics of the last finished dictation, for the measures of the trial app. */
    @Volatile
    var lastStats: TranscriptionStats? = null
        private set

    override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest): Flow<DictationState> = channelFlow {
        // Audio is read at once, whatever the state of the models.
        val frames = Channel<AudioFrame>(Channel.UNLIMITED)
        launch {
            try {
                audio.collect { frames.send(it) }
                frames.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                frames.close(e)
            }
        }

        val loaded = try {
            engines.recognition { progress -> trySend(DictationState.PreparingModel(progress)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            send(DictationState.Failed(e.error))
            frames.cancel()
            return@channelFlow
        } catch (e: Exception) {
            send(DictationState.Failed(VoiceError.Provider("local", e.message ?: e.javaClass.simpleName)))
            frames.cancel()
            return@channelFlow
        }

        val transcriber = SegmentedTranscriber(
            decoder = loaded.decoder,
            segmenter = loaded.segmenter,
            partialIntervalSamples = AudioFormat.SPEECH.samplesFor(config.partialIntervalMs),
            hotwords = if (loaded.hotwords) hotwords(request.vocabulary) else null,
        )
        val terminal = try {
            send(DictationState.Listening(""))
            for (frame in frames) {
                if (frame.format != AudioFormat.SPEECH) {
                    throw VoiceException(VoiceError.Provider("audio_format", "expected 16 kHz, got ${frame.format.sampleRateHz} Hz"))
                }
                val partial = withContext(engines.engineDispatcher) { transcriber.accept(frame.samples) }
                if (partial != null) send(DictationState.Listening(partial))
            }
            send(DictationState.Finalizing(transcriber.text))
            DictationState.Final(withContext(engines.engineDispatcher) { transcriber.finish() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            DictationState.Failed(e.error)
        } catch (e: Exception) {
            DictationState.Failed(VoiceError.Provider("local", e.message ?: e.javaClass.simpleName))
        } finally {
            // Gives the decoder back: a decoder replaced meanwhile (new setting, release) is freed now.
            withContext(NonCancellable + engines.engineDispatcher) { loaded.close() }
        }
        if (terminal is DictationState.Final) lastStats = transcriber.stats
        send(terminal)
    }

    internal companion object {
        /** sherpa-onnx hotwords: phrases separated by `/`. */
        fun hotwords(vocabulary: Vocabulary): String? =
            vocabulary.terms.map { it.trim() }.filter { it.isNotEmpty() && '/' !in it }.distinct()
                .joinToString("/").ifEmpty { null }
    }
}
