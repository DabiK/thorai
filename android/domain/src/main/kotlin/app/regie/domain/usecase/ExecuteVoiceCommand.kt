package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Called only after confirmation. Checks the frozen target against current bridge state. */
class ExecuteVoiceCommand(private val gateway: AgentGateway, private val answer: AnswerApproval, private val open: (SessionId) -> Unit) {
    suspend operator fun invoke(command: VoiceCommand): String = when (command) {
        is VoiceCommand.Reply -> {
            if (gateway.approvals.value.none { it == command.approval }) "Validation modifiée ou déjà traitée. Aucun ordre envoyé."
            else when (answer(command.approval.id, command.decision).reply) {
                ApprovalReply.ACCEPTED -> if (command.decision == ApprovalDecision.ONCE) "Validation autorisée une fois." else "Validation refusée."
                ApprovalReply.UNKNOWN -> "Réponse incertaine. Vérifie la validation ; aucun renvoi automatique."
                else -> "Validation indisponible ou réponse non envoyée."
            }
        }
        is VoiceCommand.Pause -> {
            val ids = command.ids.filter { id -> gateway.sessions.value.any { it.id == id && it.status.isBusy } }
            var heard = 0
            ids.forEach { if (gateway.interrupt(it)) heard++ }
            "Interruption reçue : $heard sur ${ids.size} sessions occupées. Les programmes déjà lancés peuvent continuer."
        }
        is VoiceCommand.Navigate -> if (gateway.sessions.value.any { it.id == command.id }) {
            open(command.id); "Session ouverte."
        } else "Session indisponible."
        is VoiceCommand.Summary -> command.ids.mapNotNull { id ->
            val session = gateway.sessions.value.firstOrNull { it.id == id } ?: return@mapNotNull null
            val thread = withTimeoutOrNull(2000) { gateway.thread(id).first { it.loaded } } ?: SessionThread.EMPTY
            val summary = WrittenSummary.of(session, thread)
            "${session.title ?: session.project} · ${session.agent}\n${if (summary.fromAgent) "Bilan déclaré par l’agent" else "État observé · bilan absent"}\n${(summary.lines + listOfNotNull(summary.question)).joinToString("\n")}"
        }.joinToString("\n\n").ifEmpty { "Aucune session disponible." }
        is VoiceCommand.Launch -> {
            if (command.draft.project !in gateway.projects()) "Projet indisponible. Aucune session créée."
            else {
                val result = gateway.createSession(command.draft)
                if (result.id != null) { open(result.id); "Session lancée." }
                else if (result.uncertain) "Création incertaine. Vérifie la régie avant de réessayer."
                else "Session non créée. Vérifie le pont et le projet."
            }
        }
    }
}
