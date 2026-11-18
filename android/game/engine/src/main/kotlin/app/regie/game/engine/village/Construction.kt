package app.regie.game.engine.village

import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.SessionThread
import app.regie.domain.model.ThreadEntry

/**
 * Les quatre étapes du chantier, qui suivent la création d'une session (`da-jeu.md`) :
 * 1. [PLOT] : la fiche est remplie et lancée, le terrain est réservé ;
 * 2. [FOUNDATIONS] : le pont a créé la session ;
 * 3. [FRAME] : l'agent est lancé, la session est chargée dans son processus ;
 * 4. [FINISHED] : la première réponse de l'agent est arrivée, la maison est finie.
 */
enum class ConstructionStage { PLOT, FOUNDATIONS, FRAME, FINISHED }

/**
 * Le chantier d'une nouvelle session, tel que le hameau le montre. Rien n'y est deviné : chaque étape
 * vient de ce que le domaine expose (la fiche, la réponse du pont, la vue d'ensemble, le fil).
 *
 * - [project] : le dossier visé ; vide pour un nouveau projet qui n'a pas encore de nom valide.
 * - [stage] : `null` tant que la fiche est en cours (le coursier va au terrain, rien n'est réservé).
 * - [session] : connue dès que le pont l'a créée.
 * - [prompted] : une consigne part avec la création ; sans elle, l'agent n'a rien à répondre et la
 *   maison est finie dès qu'il attend sa consigne.
 * - [error] : l'échec, tel que le pont ou l'agent le donne ; l'étape s'arrête là.
 */
data class Construction(
    val project: String,
    val newProject: Boolean = false,
    val stage: ConstructionStage? = null,
    val session: SessionId? = null,
    val prompted: Boolean = true,
    val error: String? = null,
) {
    val failed: Boolean get() = error != null
    val finished: Boolean get() = stage == ConstructionStage.FINISHED

    /** La fiche est lancée (A) : le terrain est réservé. */
    fun launched(prompted: Boolean): Construction =
        copy(stage = ConstructionStage.PLOT, prompted = prompted, error = null, session = null)

    /** Le pont a créé la session [id]. */
    fun created(id: SessionId): Construction =
        if (failed) this else copy(stage = maxOf(stage ?: ConstructionStage.PLOT, ConstructionStage.FOUNDATIONS), session = id)

    /** La création a échoué ou reste incertaine : l'étape s'arrête, avec le message du domaine. */
    fun failedWith(message: String): Construction = copy(error = message)

    /**
     * Ce que la vue d'ensemble ([session]) et le fil ([thread]) disent de la session créée. Une étape
     * ne recule jamais ; un échec arrête le chantier là où il en est.
     */
    fun observed(session: Session?, thread: SessionThread?): Construction {
        val id = this.session ?: return this
        if (failed || finished || session != null && session.id != id) return this
        val failure = thread?.entries?.firstNotNullOfOrNull { (it as? ThreadEntry.TurnFailed)?.let { failed -> failed.message ?: "" } }
        if (session?.status == SessionStatus.FAILED || failure != null) {
            return copy(stage = advance(session), error = session?.lastError ?: failure?.takeIf { it.isNotBlank() } ?: "")
        }
        return copy(stage = if (answered(session, thread)) ConstructionStage.FINISHED else advance(session))
    }

    private fun advance(session: Session?): ConstructionStage {
        val current = stage ?: ConstructionStage.PLOT
        return if (session?.active == true) maxOf(current, ConstructionStage.FRAME) else current
    }

    /**
     * La première réponse : un texte ou une action de l'agent dans le fil, ou une validation qu'il
     * demande ; sans consigne, l'agent qui attend la sienne. Un tour fini sans rien dire compte aussi.
     */
    private fun answered(session: Session?, thread: SessionThread?): Boolean {
        if (session == null || !session.active) return false
        val spoke = thread?.entries?.any { it is ThreadEntry.AgentText && it.text.isNotBlank() || it is ThreadEntry.ToolCall } == true
        return spoke || session.pendingApproval != null || session.status == SessionStatus.IDLE
    }
}
