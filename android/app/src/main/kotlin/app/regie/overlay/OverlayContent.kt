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
    /** La dictée en cours, quand l'écran du bas de Régie n'est pas là pour la montrer. */
    data class Dictation(val review: DictationReview, val session: Session?) : OverlayContent

    /** L'ordre vocal en cours, pour la même raison. */
    data class Command(val review: VoiceCommandReview) : OverlayContent

    /** Le message volant : la carte de l'agent. [answering] : on y répond par L2. */
    data class Card(val card: FloatingCard, val answering: Boolean) : OverlayContent

    /** Bulle discrète : l'agent travaille, et l'écran du bas de Régie n'est pas visible. */
    data class Bubble(val session: Session, val lastSentence: String?) : OverlayContent

    companion object {
        /**
         * Rien par-dessus Régie elle-même. Sinon la dictée ou l'ordre en cours s'ils ne sont visibles
         * nulle part, puis la carte, puis la bulle de la session affichée dans Régie, tant qu'elle
         * travaille et que l'écran du bas est éteint ou occupé.
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
        ): OverlayContent? = when {
            topVisible -> null
            dictation.visible && !bottomVisible -> Dictation(dictation, sessions.firstOrNull { it.id == dictation.target?.sessionId })
            command.visible && !bottomVisible -> Command(command)
            card != null -> Card(card, answeringCard && dictation.visible)
            !bottomVisible && live != null && live.session.status.isBusy -> Bubble(live.session, live.summary.lastSentence)
            else -> null
        }
    }
}
