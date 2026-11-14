package app.regie.domain.usecase

import app.regie.domain.model.*

/** Destination-specific routing and delivery rules, shared by all speech providers. */
class DeliverDictation(private val sendMessage: SendMessage, private val answer: AnswerApproval) {
    suspend operator fun invoke(target: DictationTarget, text: String): DictationDelivery {
        if (target is DictationTarget.Rejection) {
            // Keep the refusal receipt separate: if only the instruction fails, never repeat refusal.
            val reply = answer(target.approvalId, ApprovalDecision.REJECT).reply
            when (reply) {
                ApprovalReply.NOT_SENT -> return DictationDelivery("Refus non envoyé. A pour réessayer.", target)
                ApprovalReply.UNKNOWN -> return DictationDelivery("Refus non confirmé. Vérifie la session ; consigne non envoyée.")
                ApprovalReply.UNAVAILABLE -> return DictationDelivery("Cette demande n’est plus disponible. Consigne non envoyée.")
                ApprovalReply.ACCEPTED -> Unit
            }
        }
        val content = if (target is DictationTarget.DiffComment) "Sur ${target.location} :\n" + (target.selectedText?.let { "Ligne sélectionnée : $it\n" } ?: "") + text else text
        val delivery = sendMessage(target.sessionId, content, dictated = true) ?: Delivery.NOT_SENT
        val retry = if (target is DictationTarget.Rejection) DictationTarget.Message(target.sessionId) else target
        return when (delivery) {
            Delivery.NOT_SENT, Delivery.CANCELLED -> DictationDelivery("Message non livré. A pour réessayer.", retry)
            Delivery.UNKNOWN -> DictationDelivery("Livraison non confirmée. Vérifie le fil avant de dicter à nouveau.")
            Delivery.QUEUED -> DictationDelivery("Message mis en attente. B pour revenir.")
            Delivery.STEERING -> DictationDelivery("Consigne transmise au tour en cours. B pour revenir.")
            Delivery.PROMPT -> DictationDelivery("Message envoyé. B pour revenir.")
        }
    }
}
