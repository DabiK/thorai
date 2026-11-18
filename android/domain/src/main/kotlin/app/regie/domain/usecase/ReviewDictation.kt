package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.DictationCapture
import app.regie.domain.port.CaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the destination and the reviewed text until explicit confirmation. A message the agent
 * received closes the review by itself, with no screen to dismiss; [onDelivered] receives its
 * receipt. Away from its session ([shown] false), only a new turn closes: a queued or steering
 * message keeps its notice, as do uncertain results, failures and other targets, until B.
 */
class ReviewDictation(
    private val capture: DictationCapture,
    private val deliver: suspend (DictationTarget, String) -> DictationDelivery,
    private val scope: CoroutineScope,
    private val shown: (SessionId) -> Boolean = { true },
    private val onDelivered: (DictationTarget, Delivery) -> Unit = { _, _ -> },
) {
    private val mutable = MutableStateFlow(DictationReview())
    val state = mutable.asStateFlow()
    private var watching: Job? = null
    private var held = false
    private var base = ""
    private var replacement: IntRange? = null

    fun start(target: DictationTarget, vocabulary: List<String>) {
        if (state.value.visible) return
        mutable.value = DictationReview(target, DictationPhase.REVIEW, vocabulary = vocabulary)
        record()
    }

    fun record() {
        val before = state.value
        if (!before.editable) return
        held = true
        base = before.text
        replacement = if (before.phase == DictationPhase.CORRECT) before.words.getOrNull(before.selectedWord)?.range else null
        mutable.value = before.copy(phase = DictationPhase.RECORDING, notice = null, level = 0f, durationMs = 0)
        capture.start(before.vocabulary)
        watching?.cancel()
        watching = scope.launch {
            capture.state.collect(::applyCapture)
        }
    }

    private fun applyCapture(event: CaptureState) {
        val current = state.value
        if (current.phase != DictationPhase.RECORDING && current.phase != DictationPhase.FINALIZING) return
        val text = when {
            event.error != null -> base // A failed partial must not overwrite a reviewed word.
            event.text.isBlank() -> base
            replacement != null -> base.replaceRange(replacement!!, event.text.trim())
            base.isBlank() -> event.text.trim()
            else -> "$base ${event.text.trim()}"
        }
        mutable.value = current.copy(
            text = text,
            phase = if ((event.complete || event.error != null) && !held) DictationPhase.REVIEW else current.phase,
            level = event.level, durationMs = event.durationMs,
            notice = event.error ?: event.notice,
        )
    }

    fun finish() {
        held = false
        if (state.value.phase != DictationPhase.RECORDING) return
        // The producer may have published its final result before the collector resumes.
        applyCapture(capture.state.value)
        mutable.value = state.value.copy(phase = if (state.value.phase == DictationPhase.RECORDING) DictationPhase.FINALIZING else state.value.phase, level = 0f)
        capture.finish()
    }

    fun append() { if (state.value.editable) mutable.value = state.value.copy(phase = DictationPhase.APPEND, notice = "Maintiens L2 pour compléter") }
    fun correct() { if (state.value.editable && state.value.words.isNotEmpty()) mutable.value = state.value.copy(phase = DictationPhase.CORRECT, selectedWord = 0, notice = "Choisis un mot. Maintiens L2 pour le remplacer.") }
    fun moveWord(delta: Int) {
        val current = state.value
        if (current.phase == DictationPhase.CORRECT) mutable.value = current.copy(selectedWord = (current.selectedWord + delta).coerceIn(0, (current.words.size - 1).coerceAtLeast(0)))
    }

    fun send() {
        val current = state.value
        val target = current.target ?: return
        if (!current.editable || current.text.isBlank()) return
        mutable.value = current.copy(phase = DictationPhase.SENDING, notice = "Envoi…")
        scope.launch {
            val result = deliver(target, current.text)
            if (target is DictationTarget.Message && result.delivered && (result.delivery == Delivery.PROMPT || shown(target.sessionId))) {
                watching?.cancel()
                mutable.value = DictationReview()
                onDelivered(target, result.delivery!!)
                return@launch
            }
            mutable.value = current.copy(
                target = result.retryTarget ?: target,
                phase = if (result.retryTarget != null) DictationPhase.REVIEW else DictationPhase.SENT,
                notice = result.notice,
            )
        }
    }

    fun discard() {
        if (state.value.phase == DictationPhase.SENDING) return
        watching?.cancel()
        capture.cancel()
        mutable.value = DictationReview()
    }
}
