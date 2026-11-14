package app.regie.domain.model

@JvmInline
value class ApprovalId(val value: String)

/** Ce que l'agent demande l'autorisation de faire. */
enum class ApprovalKind { COMMAND, FILE_CHANGE, OTHER }

/**
 * Une demande de validation en attente. [subject] est la commande ou le fichier concerné,
 * absent si l'agent ne l'a pas exposé.
 */
data class Approval(
    val id: ApprovalId,
    val sessionId: SessionId,
    val kind: ApprovalKind,
    val subject: String?,
    val reason: String? = null,
    val workingDirectory: String? = null,
    val paths: List<String> = emptyList(),
    val plan: String? = null,
    val changes: List<ApprovalChange> = emptyList(),
    val options: List<ApprovalOption> = emptyList(),
) {
    /** Une permission inconnue ou durable ne devient jamais une autorisation de session. */
    fun optionFor(decision: ApprovalDecision): ApprovalOption? = when (decision) {
        ApprovalDecision.ONCE -> options.firstOrNull { it.kind == ApprovalOptionKind.ALLOW_ONCE }
        ApprovalDecision.SESSION -> options.firstOrNull {
            it.kind == ApprovalOptionKind.ALLOW_ALWAYS && it.id == "allow_for_session"
        }
        ApprovalDecision.REJECT -> options.firstOrNull { it.kind == ApprovalOptionKind.REJECT_ONCE }
    }
}

data class ApprovalChange(val path: String, val before: String?, val after: String)
data class ApprovalOption(val id: String, val kind: ApprovalOptionKind, val label: String)
enum class ApprovalOptionKind { ALLOW_ONCE, ALLOW_ALWAYS, REJECT_ONCE, REJECT_ALWAYS, OTHER }

enum class ApprovalDecision { ONCE, SESSION, REJECT }
enum class ApprovalReply { ACCEPTED, NOT_SENT, UNKNOWN, UNAVAILABLE }
data class ApprovalAnswer(val reply: ApprovalReply, val instructionDelivery: Delivery? = null)
