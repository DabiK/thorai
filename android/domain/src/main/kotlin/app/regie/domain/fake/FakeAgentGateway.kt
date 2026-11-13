package app.regie.domain.fake

import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.DiffStat
import app.regie.domain.model.LastAction
import app.regie.domain.model.Progress
import app.regie.domain.model.Session
import app.regie.domain.model.SessionEvent
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Passerelle factice : rejoue les quatre sessions de la maquette (écran 1).
 * Le ticket 05 la remplacera par des enregistrements du vrai pont.
 */
class FakeAgentGateway(
    private val script: List<SessionEvent> = MOCKUP,
) : AgentGateway {

    override val events: Flow<SessionEvent> get() = script.asFlow()

    companion object {
        private val codexApproval = Approval(
            id = ApprovalId("approval-atelier-meubles-1"),
            sessionId = SessionId("atelier-meubles"),
            kind = ApprovalKind.COMMAND,
            subject = "pip install open_clip_torch",
        )

        val MOCKUP: List<SessionEvent> = listOf(
            SessionEvent.AgentsDetected(mapOf(Agent.CLAUDE to "2.1.322", Agent.CODEX to "0.167.2")),
            SessionEvent.SessionUpdated(
                Session(
                    id = SessionId("atelier"),
                    agent = Agent.CLAUDE,
                    project = "atelier",
                    branch = "feat/atelier-3d",
                    task = "Vérifier les passages de 70 cm autour du canapé",
                    status = SessionStatus.RUNNING,
                    sinceLastActivity = 12.seconds,
                    progress = Progress(step = 3, total = 5, currentStep = "lance les tests"),
                    lastAction = LastAction(command = "npm test", outcome = "42 réussis · 1 échec"),
                ),
            ),
            SessionEvent.SessionUpdated(
                Session(
                    id = SessionId("atelier-meubles"),
                    agent = Agent.CODEX,
                    project = "atelier-meubles",
                    branch = null,
                    task = "Classer les meubles du scan par catégorie",
                    status = SessionStatus.AWAITING_APPROVAL,
                    sinceLastActivity = 40.seconds,
                    progress = Progress(step = 2, total = 6),
                    pendingApproval = codexApproval,
                ),
            ),
            SessionEvent.SessionUpdated(
                Session(
                    id = SessionId("montage"),
                    agent = Agent.CLAUDE,
                    project = "montage",
                    branch = "main",
                    task = "Ajouter le test de lecture muette au rendu",
                    status = SessionStatus.DONE,
                    sinceLastActivity = 6.minutes,
                    summary = "4 fichiers modifiés · tests réussis · résumé lu à voix haute",
                    diffStat = DiffStat(added = 86, removed = 12),
                ),
            ),
            SessionEvent.SessionUpdated(
                Session(
                    id = SessionId("thor-dev"),
                    agent = Agent.CODEX,
                    project = "thor-dev",
                    branch = null,
                    task = "Démarrer le pont au boot avec Termux:Boot",
                    status = SessionStatus.PAUSED,
                    sinceLastActivity = 2.hours,
                    progress = Progress(step = 1, total = 3),
                    question = "installer Termux:Boot depuis GitHub ?",
                ),
            ),
        )
    }
}
