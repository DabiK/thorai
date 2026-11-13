package app.regie.voice.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A dictation in progress, driven by a push-to-talk button: created when the button is pressed,
 * [finish] when it is released, [cancel] to drop it. Cancelling the scope it runs on ends it as
 * [DictationState.Cancelled], like [cancel].
 */
class Dictation internal constructor(
    speechToText: SpeechToText,
    source: AudioSource,
    request: DictationRequest,
    scope: CoroutineScope,
) {
    private val endOfSpeech = MutableStateFlow(false)
    private val mutableState = MutableStateFlow<DictationState>(DictationState.Connecting)

    val state: StateFlow<DictationState> = mutableState.asStateFlow()

    private val job: Job = scope.launch {
        try {
            speechToText.transcribe(source.frames().until(endOfSpeech), request).collect { next ->
                mutableState.update { current -> if (current.isTerminal) current else next }
            }
            settle(DictationState.Failed(VoiceError.Provider("incomplete", "transcription ended without a final text")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            settle(DictationState.Failed(e.error))
        } catch (e: Exception) {
            settle(DictationState.Failed(VoiceError.Provider("unexpected", e.message)))
        }
    }

    init {
        // Whatever ends the job (cancel, the caller's scope cancelled, even before it started), the
        // state becomes terminal, so that [await] and the observers of [state] never wait forever.
        job.invokeOnCompletion { settle(DictationState.Cancelled) }
    }

    /** End of speech: stops the audio capture and asks for the final text. */
    fun finish() {
        endOfSpeech.value = true
    }

    /** Drops the dictation: no final text will come. */
    fun cancel() {
        settle(DictationState.Cancelled)
        job.cancel()
    }

    /** Waits for the terminal state. */
    suspend fun await(): DictationState = state.first { it.isTerminal }

    private fun settle(terminal: DictationState) {
        mutableState.update { current -> if (current.isTerminal) current else terminal }
    }
}

/** Starts a dictation on [scope], reading audio from [source]. */
fun SpeechToText.startDictation(
    scope: CoroutineScope,
    source: AudioSource,
    request: DictationRequest = DictationRequest(),
): Dictation = Dictation(this, source, request, scope)

/** Emits frames until [stop] becomes true, then completes and stops the upstream capture. */
internal fun Flow<AudioFrame>.until(stop: StateFlow<Boolean>): Flow<AudioFrame> = channelFlow {
    val producer = launch { this@until.collect { send(it) } }
    val watcher = launch {
        stop.first { it }
        producer.cancel()
    }
    producer.join()
    watcher.cancel()
}
