package app.regie.domain.model

import kotlin.time.Duration

@JvmInline
value class SessionId(val value: String)

/** Étape [step] sur [total] du plan de l'agent (0 : aucune étape commencée), avec le libellé de l'étape en cours s'il est connu. */
data class Progress(val step: Int, val total: Int, val currentStep: String? = null) {
    init {
        require(total > 0 && step in 0..total) { "étape $step hors de 0..$total" }
    }
}

/** Dernière action de l'agent, par exemple une commande et son résultat. */
data class LastAction(val command: String, val outcome: String? = null)

/** Lignes ajoutées et retirées par la session. */
data class DiffStat(val added: Int, val removed: Int)

/**
 * Une session de Claude ou de Codex, telle que la vue d'ensemble la montre.
 * Les champs facultatifs sont absents quand l'agent ne les expose pas.
 */
data class Session(
    val id: SessionId,
    val agent: Agent,
    val project: String,
    val branch: String?,
    val task: String,
    val status: SessionStatus,
    val sinceLastActivity: Duration,
    val progress: Progress? = null,
    val lastAction: LastAction? = null,
    val pendingApproval: Approval? = null,
    val question: String? = null,
    val summary: String? = null,
    val diffStat: DiffStat? = null,
)
