package app.regie.game.ui.approval

import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalReply
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.house.PadKey

/** Où en est une validation, dit au-dessus du menu de choix. */
enum class ApprovalStatus { WAITING, SENDING, ACCEPTED, ACCEPTED_NEXT, UNKNOWN, NOT_SENT, UNAVAILABLE }

/** Une option du menu : la décision, refusée avec une consigne dictée quand [dictate], sa touche, et si elle est possible. */
data class ApprovalChoice(val decision: ApprovalDecision, val key: String, val enabled: Boolean, val dictate: Boolean = false)

/** Ce que le parchemin et le menu de choix montrent d'une validation, sans Android. */
object ApprovalMenuModel {
    /** Le même constat que l'écran classique : la réponse en cours, reçue, incertaine, non envoyée. */
    fun status(review: ApprovalReview): ApprovalStatus = when {
        review.sending -> ApprovalStatus.SENDING
        review.answer?.reply == ApprovalReply.ACCEPTED ->
            if (review.queue.any { it.id != review.request?.id }) ApprovalStatus.ACCEPTED_NEXT else ApprovalStatus.ACCEPTED
        review.answer?.reply == ApprovalReply.UNKNOWN -> ApprovalStatus.UNKNOWN
        review.answer?.reply == ApprovalReply.NOT_SENT -> ApprovalStatus.NOT_SENT
        review.answer?.reply == ApprovalReply.UNAVAILABLE || !review.pending -> ApprovalStatus.UNAVAILABLE
        else -> ApprovalStatus.WAITING
    }

    /**
     * Les choix, dans l'ordre des touches (A, B, X, L2) : autoriser une fois, refuser, autoriser pour
     * la session si l'agent le propose (ADR 08), et refuser en dictant une consigne là où L2 le fait
     * ([withInstruction] : l'écran des validations ; dans une maison, L2 parle à l'agent).
     */
    fun choices(review: ApprovalReview, withInstruction: Boolean): List<ApprovalChoice> = listOfNotNull(
        ApprovalChoice(ApprovalDecision.ONCE, "A", review.canAnswer(ApprovalDecision.ONCE)),
        ApprovalChoice(ApprovalDecision.REJECT, "B", review.canAnswer(ApprovalDecision.REJECT)),
        ApprovalChoice(ApprovalDecision.SESSION, "X", review.canAnswer(ApprovalDecision.SESSION))
            .takeIf { review.request?.optionFor(ApprovalDecision.SESSION) != null },
        ApprovalChoice(ApprovalDecision.REJECT, "L2", review.canAnswer(ApprovalDecision.REJECT), dictate = true).takeIf { withInstruction },
    )

    /** Le curseur reste sur A, l'action par défaut ; aucun curseur une fois la décision prise. */
    fun cursor(choices: List<ApprovalChoice>): Int = choices.indexOfFirst { it.key == "A" && it.enabled }

    /** La touche d'une décision, pour la relayer comme la manette. */
    fun key(decision: ApprovalDecision): PadKey = when (decision) {
        ApprovalDecision.ONCE -> PadKey.A
        ApprovalDecision.SESSION -> PadKey.X
        ApprovalDecision.REJECT -> PadKey.B
    }
}

/** Ce que l'écran des validations peut demander. La racine de composition le relie à `ApprovalController`. */
interface ApprovalCallbacks {
    /** A, B, X, L1, R1 : au doigt comme à la manette. */
    fun onKey(key: PadKey)

    /** L2 tenu au doigt : refuser en dictant une consigne. */
    fun onSpeak(held: Boolean)

    /** Un toucher sur une autre validation de la file. */
    fun onSelect(id: ApprovalId)
}
