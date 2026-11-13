package app.regie.voice.fake

import app.regie.voice.api.AudioFrame
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.SpeechToText
import app.regie.voice.api.VoiceError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take

/**
 * What the fake transcription "hears" during one dictation.
 *
 * [partials] are shown one per [FakeSpeechToText.framesPerPartial] audio frames, then [final] when
 * the audio ends. With [error], the dictation fails after [errorAfterFrames] frames (0: at once,
 * as if the connection was refused).
 */
data class FakeDictationScript(
    val partials: List<String>,
    val final: String,
    val error: VoiceError? = null,
    val errorAfterFrames: Int = 0,
) {
    companion object {
        /** Partials growing word by word up to [text], like a real provider. */
        fun saying(text: String): FakeDictationScript {
            val words = text.split(" ").filter { it.isNotEmpty() }
            val partials = words.indices.map { words.subList(0, it + 1).joinToString(" ") }
            return FakeDictationScript(partials.dropLast(1), text)
        }

        fun failing(error: VoiceError, afterFrames: Int = 0) =
            FakeDictationScript(emptyList(), "", error, afterFrames)
    }
}

/**
 * Transcription that replays scripted texts, one script per dictation, in a loop. It reads the
 * audio for real (so the end of speech still drives the final text) but ignores its content.
 */
class FakeSpeechToText(
    private val scripts: List<FakeDictationScript>,
    val framesPerPartial: Int = 1,
) : SpeechToText {
    init {
        require(scripts.isNotEmpty()) { "at least one script" }
        require(framesPerPartial > 0) { "framesPerPartial must be positive" }
    }

    constructor(vararg texts: String) : this(texts.map { FakeDictationScript.saying(it) })

    private var next = 0
    private val received = mutableListOf<DictationRequest>()

    /** Requests received so far, oldest first. */
    val requests: List<DictationRequest> get() = synchronized(received) { received.toList() }

    override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest): Flow<DictationState> = flow {
        val script = synchronized(received) {
            received += request
            scripts[next++ % scripts.size]
        }
        emit(DictationState.Connecting)
        if (script.error != null && script.errorAfterFrames <= 0) {
            emit(DictationState.Failed(script.error))
            return@flow
        }
        emit(DictationState.Listening(""))
        var frames = 0
        var shown = 0
        val heard = if (script.error != null) audio.take(script.errorAfterFrames) else audio
        heard.collect {
            frames++
            if (frames % framesPerPartial == 0 && shown < script.partials.size) {
                emit(DictationState.Listening(script.partials[shown++]))
            }
        }
        if (script.error != null) {
            emit(DictationState.Failed(script.error))
            return@flow
        }
        emit(DictationState.Finalizing(script.partials.getOrElse(shown - 1) { "" }))
        emit(DictationState.Final(script.final))
    }
}
