package app.regie.overlay

import app.regie.domain.model.DictationReview
import app.regie.domain.model.FloatingCard
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Session
import app.regie.domain.model.VoiceCommandReview

/**
 * Ce que Régie pose par-dessus l'écran du haut quand une autre appli l'occupe (ticket 25). Une seule
 * chose à la fois, la plus utile d'abord.
 */
sealed interface OverlayContent {
    /**
     * La dictée en cours, quand l'écran du bas de Régie n'est pas là pour la montrer. [replyingTo] :
     * la phrase de la carte à laquelle on répond.
     */
    data class Dictation(val review: DictationReview, val session: Session?, val replyingTo: String? = null) : OverlayContent

    /** L'ordre vocal en cours, pour la même raison. */
    data class Command(val review: VoiceCommandReview) : OverlayContent

    /** Le message volant : la carte de l'agent. [keys] : elle prend déjà A et B. */
    data class Card(val card: FloatingCard, val keys: Boolean = true) : OverlayContent

    /** Bulle discrète : l'agent travaille, et l'écran du bas de Régie n'est pas visible. */
    data class Bubble(val session: Session, val lastSentence: String?) : OverlayContent

    companion object {
        /**
         * Rien par-dessus Régie elle-même, ni quand son écran du bas est visible : la conversation y
         * est déjà. Sinon la dictée ou l'ordre en cours, puis la carte, puis la bulle de la session
         * affichée dans Régie, tant qu'elle travaille.
         */
        fun of(
            topVisible: Boolean,
            bottomVisible: Boolean,
            dictation: DictationReview,
            command: VoiceCommandReview,
            card: FloatingCard?,
            answeringCard: Boolean,
            live: LiveSession?,
            sessions: List<Session>,
            cardKeys: Boolean = true,
        ): OverlayContent? = when {
            topVisible || bottomVisible -> null
            dictation.visible -> Dictation(dictation, sessions.firstOrNull { it.id == dictation.target?.sessionId },
                card?.takeIf { answeringCard }?.text)
            command.visible -> Command(command)
            card != null -> Card(card, cardKeys)
            live != null && live.session.status.isBusy -> Bubble(live.session, live.summary.lastSentence)
            else -> null
        }
    }
}
