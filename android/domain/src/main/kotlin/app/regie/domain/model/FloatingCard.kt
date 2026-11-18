package app.regie.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Ce qu'une carte volante montre (ticket 25) : un message terminé de l'agent, sa question, ou une
 * validation qu'il demande. Jamais un fragment en cours de flux.
 */
enum class FloatingKind(
    /** Une carte n'en remplace pas une autre plus importante encore affichée. */
    val priority: Int,
    /** La carte s'efface seule au bout de ce temps, si personne n'y répond. */
    val shownFor: Duration,
) {
    MESSAGE(0, 8.seconds),
    QUESTION(1, 15.seconds),
    APPROVAL(2, 15.seconds),
}

/**
 * Une carte volante. [key] identifie l'événement qu'elle montre (message ou validation) : le même
 * événement n'en produit jamais deux. [text] est le message de l'agent, raccourci par le début s'il
 * est long ([excerpt]) ; pour une validation, la commande ou le fichier, s'il est exposé.
 */
data class FloatingCard(
    val key: String,
    val sessionId: SessionId,
    val agent: Agent,
    val project: String,
    val kind: FloatingKind,
    val text: String,
    val approval: Approval? = null,
) {
    companion object {
        const val MAX_LENGTH = 280

        /**
         * Le message tel quel s'il est court. Sinon sa fin, coupée entre deux phrases quand c'est
         * possible : la conclusion ou la question de l'agent est en fin de message.
         */
        fun excerpt(text: String): String {
            val flat = text.replace(Regex("\\s+"), " ").trim()
            if (flat.length <= MAX_LENGTH) return flat
            val tail = flat.takeLast(MAX_LENGTH - 2)
            val sentence = Regex("(?<=[.!?…]) ").find(tail)?.range?.last?.plus(1)
            val start = sentence?.takeIf { it < tail.length - 1 } ?: tail.indexOf(' ').let { if (it in 0 until tail.length - 1) it + 1 else 0 }
            return "… " + tail.substring(start)
        }
    }
}
