package app.regie.domain.usecase

import app.regie.domain.model.ApprovalAnswer
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalReply
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Une décision par demande. Une réponse sans accusé n’est jamais renvoyée automatiquement. */
class AnswerApproval(private val gateway: AgentGateway) {
    private val mutex = Mutex()
    private val sent = mutableSetOf<ApprovalId>()

    suspend operator fun invoke(id: ApprovalId, decision: ApprovalDecision, instruction: String? = null): ApprovalAnswer {
        val request = mutex.withLock {
            val approval = gateway.approvals.value.firstOrNull { it.id == id } ?: return ApprovalAnswer(ApprovalReply.UNAVAILABLE)
            if (id in sent) return ApprovalAnswer(ApprovalReply.UNAVAILABLE)
            val option = approval.optionFor(decision) ?: return ApprovalAnswer(ApprovalReply.UNAVAILABLE)
            sent += id
            approval to option
        }
        val reply = gateway.answerApproval(id, request.second.id)
        if (reply == ApprovalReply.NOT_SENT) mutex.withLock { sent -= id }
        // Le ticket 08 fournit la transcription confirmée. Pas de consigne après un refus incertain.
        val text = instruction?.trim().orEmpty()
        val delivery = if (reply == ApprovalReply.ACCEPTED && decision == ApprovalDecision.REJECT && text.isNotEmpty()) {
            gateway.sendMessage(request.first.sessionId, text)
        } else null
        return ApprovalAnswer(reply, delivery)
    }
}
