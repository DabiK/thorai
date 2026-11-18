package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway

/** Mutations require confirmation; navigation runs on R2 release. Rechecks the frozen target. */
class ExecuteVoiceCommand(
    private val gateway: AgentGateway,
    private val answer: AnswerApproval,
    private val open: (SessionId) -> Unit,
    private val openOverview: () -> Unit = {},
) {
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
        VoiceCommand.ShowOverview -> { openOverview(); "Vue d’ensemble ouverte." }
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
