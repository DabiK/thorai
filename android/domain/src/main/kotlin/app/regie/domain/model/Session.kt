package app.regie.domain.model

import kotlin.time.Instant

@JvmInline
value class SessionId(val value: String)

/**
 * Avancement d'après le plan de l'agent : [done] étapes faites sur [total], et l'étape en cours
 * ([currentStep]) si l'agent en a une.
 */
data class Progress(val done: Int, val total: Int, val currentStep: String? = null) {
    init {
        require(total > 0 && done in 0..total) { "$done étapes faites hors de 0..$total" }
    }

    /** Le numéro affiché, « étape x sur y » : l'étape en cours, sinon la dernière faite. */
    val step: Int get() = if (currentStep != null) (done + 1).coerceAtMost(total) else done
}

/** Où en est la dernière action de l'agent. */
enum class ActionStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

/** Dernière action de l'agent : la commande, sinon le titre que l'agent lui donne. */
data class LastAction(val description: String, val status: ActionStatus?)

/**
 * Une session de Claude ou de Codex, telle que la vue d'ensemble la montre.
 * Les champs facultatifs sont absents quand le pont ou l'agent ne les expose pas.
 *
 * - [active] : chargée dans un processus d'agent (`false` pour l'historique).
 * - [updatedAt] : dernier changement de statut, de titre ou de validations, d'après le pont.
 * - [lastActivityAt] : dernier signe de l'agent, quel qu'il soit ; l'ancienneté se compte depuis là.
 */
data class Session(
    val id: SessionId,
    val agent: Agent,
    val project: String,
    val branch: String?,
    val title: String?,
    val status: SessionStatus,
    val active: Boolean,
    val updatedAt: Instant,
    val lastActivityAt: Instant,
    val progress: Progress? = null,
    val lastAction: LastAction? = null,
    val pendingApproval: Approval? = null,
    val lastError: String? = null,
)
