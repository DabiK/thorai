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
)
