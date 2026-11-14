package app.regie.domain.model

sealed interface DictationTarget {
    val sessionId: SessionId
    data class Message(override val sessionId: SessionId) : DictationTarget
    data class Rejection(override val sessionId: SessionId, val approvalId: ApprovalId) : DictationTarget
    data class DiffComment(override val sessionId: SessionId, val path: String, val line: Int, val removed: Boolean = false, val fileCoordinates: Boolean = true, val selectedText: String? = null) : DictationTarget {
        val location: String get() = "$path, ligne $line" + (if (removed) " supprimée" else "") +
            (if (fileCoordinates) "" else " de l’extrait (position dans le fichier non communiquée)")
    }
}

enum class DictationPhase { CLOSED, RECORDING, FINALIZING, REVIEW, APPEND, CORRECT, SENDING, SENT }

data class DictationReview(
    val target: DictationTarget? = null,
    val phase: DictationPhase = DictationPhase.CLOSED,
    val text: String = "",
    val vocabulary: List<String> = emptyList(),
    val selectedWord: Int = 0,
    val level: Float = 0f,
    val durationMs: Long = 0,
    val notice: String? = null,
) {
    val words: List<MatchResult> get() = Regex("\\S+").findAll(text).toList()
    val editable: Boolean get() = phase in setOf(DictationPhase.REVIEW, DictationPhase.APPEND, DictationPhase.CORRECT)
    val visible: Boolean get() = phase != DictationPhase.CLOSED
}

/** An uncertain delivery is terminal; only a definite failure can be retried. */
data class DictationDelivery(val notice: String, val retryTarget: DictationTarget? = null)
