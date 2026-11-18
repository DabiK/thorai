package app.regie.game.ui.village

import androidx.compose.runtime.Immutable
import app.regie.domain.model.Agent
import app.regie.domain.model.SessionId
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.kit.TagState

/** Les touches que le carnet relaie, au doigt comme à la manette. */
enum class CarnetKey { A, B, X, Y, L1, R1, START }

/** Les gâchettes tenues près du micro : L2 dicte, R2 donne un ordre. */
enum class CarnetTrigger { L2, R2 }

/**
 * Ce que le hameau et son carnet peuvent demander. La racine de composition le relie aux
 * contrôleurs existants : la vue jeu ne décide d'aucune règle.
 */
interface CarnetCallbacks {
    fun onKey(key: CarnetKey)

    /** Une gâchette tenue au doigt : elle dure tant que le doigt reste posé. */
    fun onTrigger(trigger: CarnetTrigger, held: Boolean)

    /** Un toucher sur une ligne du carnet : le coursier marche jusqu'à cette maison et y entre. */
    fun onGoTo(session: SessionId)

    fun onSetup()
    fun onVoiceSettings()

    /** « Quitter », au toucher : le PRD ne lui donne pas de bouton de manette. */
    fun onQuit()
    fun onConfirmQuit()
    fun onCancelQuit()
}

/** Le ton d'un texte rédigé par la racine de composition. */
enum class CarnetTone { NORMAL, MUTED, WAIT, ERROR }

/** La version d'un agent dans l'en-tête ; [label] porte déjà l'avertissement d'une version non testée. */
@Immutable
data class CarnetVersion(val label: String, val tested: Boolean)

/**
 * L'en-tête du carnet : le nombre de sessions, l'état du pont quand il n'est pas connecté, et les
 * versions des agents, comme l'en-tête de la vue d'ensemble classique.
 */
@Immutable
data class CarnetHeader(
    val count: String,
    val bridge: String? = null,
    val bridgeTone: CarnetTone = CarnetTone.MUTED,
    val versions: List<CarnetVersion> = emptyList(),
)

/** Un avis : ce que l'utilisateur doit savoir et faire maintenant (le pont injoignable). */
@Immutable
data class CarnetNotice(val title: String, val hint: String, val tone: CarnetTone = CarnetTone.WAIT)

/** La première validation en attente, en tête du carnet (PRD, parcours 1). R1 ou un toucher y mène. */
@Immutable
data class CarnetApproval(
    val session: SessionId,
    val inhabitant: InhabitantKind,
    /** « Codex attend ta validation ». */
    val title: String,
    /** « api-meteo · migration ». */
    val where: String,
    /** La commande ou le fichier, tel que l'agent l'expose. */
    val subject: String?,
)

/**
 * Une ligne du carnet, une par session, avec ce que montrait la liste classique : projet, branche,
 * session, agent, état, étape, demande, erreur ou dernière action, ancienneté. Les textes sont
 * rédigés par la racine de composition ; ce que le pont n'expose pas reste `null`.
 */
@Immutable
data class CarnetRow(
    val session: SessionId,
    val agent: Agent,
    val inhabitant: InhabitantKind,
    val project: String,
    val title: String?,
    val branch: String?,
    val state: TagState,
    val stopped: Boolean,
    /** « étape 2 sur 5 ». */
    val step: String?,
    val detail: String?,
    val detailTone: CarnetTone = CarnetTone.MUTED,
    /** « 6 min ». */
    val age: String,
)

/** Où en est « Quitter » : la confirmation, puis l'arrêt. */
@Immutable
sealed interface CarnetQuit {
    data class Confirm(val question: String, val confirm: String, val cancel: String) : CarnetQuit
    data class Stopping(val text: String) : CarnetQuit
}

/** Le micro : la phrase de L2 (ou de la gâchette tenue) et les gâchettes tenues. */
@Immutable
data class CarnetMic(val title: String, val held: Set<CarnetTrigger> = emptySet())

/**
 * Le carnet de la vue d'ensemble : l'écran du bas sur deux écrans, une bande sous le hameau sur un
 * seul. [rows] dans l'ordre de la vue d'ensemble ; [empty] remplace la liste quand elle est vide.
 * [setupLeft] : une étape facultative du parcours d'accueil reste à faire (ticket 30).
 */
@Immutable
data class Carnet(
    val header: CarnetHeader,
    val mic: CarnetMic,
    val notices: List<CarnetNotice> = emptyList(),
    val approval: CarnetApproval? = null,
    val rows: List<CarnetRow> = emptyList(),
    val empty: String? = null,
    val setupLeft: Boolean = false,
    val quit: CarnetQuit? = null,
)
