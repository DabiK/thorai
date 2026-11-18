package app.regie.game.ui.house

import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.ThreadLine
import app.regie.game.engine.village.InhabitantKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Les touches que les écrans d'une maison relaient, au doigt comme à la manette. */
enum class PadKey { A, B, X, Y, L1, R1, START, UP, DOWN, LEFT, RIGHT }

/**
 * Ce que les écrans d'une maison (la session) peuvent demander. La racine de composition le relie
 * aux contrôleurs existants : la vue jeu ne décide d'aucune règle.
 */
interface HouseCallbacks {
    fun onKey(key: PadKey)

    /** L2 tenu au doigt : la dictée dure tant que le doigt reste posé. */
    fun onSpeak(held: Boolean)

    /** Droite, « → Modifier » ou un toucher sur un réglage : la table passe en modification sur [row]. */
    fun onOpenSettings(row: String?)

    /** Un toucher sur un réglage pendant la modification. */
    fun onSelectSetting(index: Int)

    fun onDraftChange(text: String)

    /** Envoi tactile explicite du message écrit. */
    fun onSend()
}

/** Ce que l'écran de la dictée peut demander. */
interface DictationCallbacks {
    fun onKey(key: PadKey)
    fun onSpeak(held: Boolean)
    fun onVoiceSettings()
}

/** Défilement demandé à la manette : pas de la croix, vitesse du stick droit. */
class ScrollInput(val steps: Flow<Int>, val velocity: StateFlow<Float>)

enum class NoticeTone { NORMAL, MUTED, WARN, ERROR }

/**
 * Un avis bref sous le fil (envoi, interruption…), rédigé par la racine de composition.
 * [lasting] : il attend une réponse du pont et ne s'efface pas seul.
 */
data class HouseNotice(val text: String, val tone: NoticeTone, val lasting: Boolean = false)

/** Un morceau du texte d'un agent et son style. */
data class AgentSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

/** Un bloc du texte d'un agent, prêt à dessiner (markdown rendu lisible, ADR 07). */
sealed interface AgentBlock {
    data class Text(
        val spans: List<AgentSpan>,
        val heading: Boolean = false,
        val marker: String? = null,
        val depth: Int = 0,
        val quote: Boolean = false,
    ) : AgentBlock

    data class Code(val code: String) : AgentBlock
}

/** Découpe le texte d'un agent en blocs. La racine de composition fournit l'analyse du markdown. */
fun interface AgentTextFormat {
    fun blocks(text: String): List<AgentBlock>

    companion object {
        /** Le texte tel quel, en un bloc. */
        val Plain = AgentTextFormat { text -> if (text.isEmpty()) emptyList() else listOf(AgentBlock.Text(listOf(AgentSpan(text)))) }
    }
}

/** L'attitude de l'habitant, lue sur l'état de la session. */
enum class Pose { WORKING, WAITING, IDLE, SLEEPING }

/** Ce que la maison montre d'une session, sans Android : intérieur, animations, bulle, réplique. */
object HouseLook {
    fun prefix(kind: InhabitantKind): String = when (kind) {
        InhabitantKind.SCRIBE -> "scribe"
        InhabitantKind.BRICOLEUR -> "bricoleur"
        InhabitantKind.CARTOGRAPHE -> "cartographe"
    }

    /** Le fond vide de l'intérieur (atlas `fonds`). */
    fun interior(kind: InhabitantKind): String = "fond.interieur.${prefix(kind)}"

    /** Une session arrêtée ou hors d'un processus d'agent dort ; un échec la laisse au repos. */
    fun pose(session: Session): Pose = when {
        !session.active || session.status == SessionStatus.STOPPED -> Pose.SLEEPING
        session.status == SessionStatus.WORKING -> Pose.WORKING
        session.status == SessionStatus.AWAITING_APPROVAL -> Pose.WAITING
        else -> Pose.IDLE
    }

    /** L'animation de l'habitant (atlas `personnages`). */
    fun inhabitant(kind: InhabitantKind, pose: Pose): String = prefix(kind) + when (pose) {
        Pose.WORKING -> ".travail"
        Pose.WAITING -> ".attente"
        Pose.IDLE -> ".repos"
        Pose.SLEEPING -> ".sommeil"
    }

    /** Le coursier, à l'entrée : de dos face à l'habitant, ou qui parle pendant la dictée. */
    fun courier(speaking: Boolean): String = if (speaking) "coursier.parle" else "coursier.debout.haut"

    /** La dernière action de l'agent, pour la bulle d'activité ; seulement pendant qu'il travaille. */
    fun activity(session: Session, lines: List<ThreadLine>): ThreadLine? =
        if (session.status != SessionStatus.WORKING) null else lines.lastOrNull { it.isAction }

    /** La dernière réplique de l'agent, telle quelle, pour la boîte de dialogue. */
    fun lastReply(lines: List<ThreadLine>): ThreadLine.AgentText? =
        lines.lastOrNull { it is ThreadLine.AgentText && it.text.isNotBlank() } as ThreadLine.AgentText?
}

/** Une action d'une ligne : lu, modifié, lancé, autre. */
val ThreadLine.isAction: Boolean
    get() = this is ThreadLine.Read || this is ThreadLine.Edit || this is ThreadLine.Run || this is ThreadLine.Other

/** L'écart avant une ligne du fil, en pixels de l'écran : serré entre deux actions, aéré sinon. */
fun threadGapBefore(previous: ThreadLine?, line: ThreadLine): Int = when {
    previous == null -> 0
    previous.isAction && line.isAction -> 6
    else -> 26
}
