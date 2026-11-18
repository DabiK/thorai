package app.regie.overview

import app.regie.domain.model.ApprovalId
import app.regie.domain.model.SessionId

/** Ce que la vue d'ensemble demande au domaine. Chaque action appelle un cas d'usage. */
sealed interface OverviewAction {
    data class StartDictation(val target: SessionId?) : OverviewAction
    data object StopDictation : OverviewAction
    data object StartVoiceCommand : OverviewAction
    data object StopVoiceCommand : OverviewAction
    data class OpenSession(val id: SessionId) : OverviewAction
    data class OpenApproval(val id: ApprovalId) : OverviewAction
    data object NewSession : OverviewAction
    data object PauseAll : OverviewAction

    /** Arrêter les sessions et le pont, puis fermer Régie. */
    data object Quit : OverviewAction
}
