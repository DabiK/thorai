package app.regie.voice.api

import kotlinx.coroutines.flow.Flow

/** What a dictation needs: its language and the terms to favour. */
data class DictationRequest(
    /** ISO 639-1 code. */
    val language: String = "fr",
    val vocabulary: Vocabulary = Vocabulary.EMPTY,
)

/** Where a dictation stands. Terminal states: [Final], [Failed], [Cancelled]. */
sealed interface DictationState {
    /** A local model is being downloaded or loaded; [progress] in 0..1 when known. */
    data class PreparingModel(val progress: Float?) : DictationState

    /** Opening the connection or the engine. Audio is already being captured. */
    data object Connecting : DictationState

    /** Ready and listening. [partial] is the provisional text so far (empty at first). */
    data class Listening(val partial: String) : DictationState

    /** Speech has ended; waiting for the final text. */
    data class Finalizing(val partial: String) : DictationState

    data class Final(val text: String) : DictationState

    data class Failed(val error: VoiceError) : DictationState

    data object Cancelled : DictationState

    val isTerminal: Boolean get() = this is Final || this is Failed || this is Cancelled
}

/** Streaming transcription port. */
interface SpeechToText {
    /**
     * Transcribes [audio] (PCM 16 kHz mono, see [AudioFormat.SPEECH]).
     *
     * Contract for every adapter:
     * - the returned flow is cold; collecting it starts the dictation and starts collecting [audio]
     *   right away, so nothing said while connecting is lost;
     * - the completion of [audio] means the end of speech: the adapter then emits [DictationState.Finalizing]
     *   and the [DictationState.Final] text;
     * - the flow completes after a terminal state ([DictationState.Final] or [DictationState.Failed]);
     *   errors are reported as [DictationState.Failed], not thrown;
     * - cancelling the collection cancels the dictation and releases its resources.
     *
     * Use [Dictation] to drive it from a button (start, finish, cancel).
     */
    fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest): Flow<DictationState>
}
