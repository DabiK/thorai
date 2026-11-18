package app.regie.domain.usecase

import app.regie.domain.model.Agent
import app.regie.domain.model.NewSessionDraft
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId

/** Ce que fait le raccourci de la session « console » (ticket 25). */
sealed interface ConsoleLaunch {
    /** Une session console existe : on l'ouvre, sans rien lui envoyer. */
    data class Resume(val id: SessionId) : ConsoleLaunch

    /** Aucune : la fiche de nouvelle session s'ouvre pré-remplie, A la lance. */
    data class Create(val draft: NewSessionDraft) : ConsoleLaunch
}

/**
 * La session « console » pilote la console plutôt qu'un projet : son dossier de travail est neutre,
 * `~/projects/console`, un dossier sans code créé à la première fois (dépôt vide, comme tout nouveau
 * projet, ticket 15). Les consignes globales des agents leur apprennent déjà à piloter Android.
 */
object ConsoleSession {
    const val PROJECT = "console"

    /**
     * Mode proposé : « Je valide chaque action ». « Plan d'abord », le mode par défaut des projets,
     * ferait préparer un plan à l'agent au lieu d'agir sur la console.
     */
    const val APPROVAL_MODE = "ask"

    /**
     * Reprendre la session console la plus récente (une session chargée d'abord), sinon préparer sa
     * création. L'agent proposé est celui de la dernière session utilisée, Codex à défaut ; la fiche
     * permet d'en changer avant A.
     */
    fun choose(sessions: List<Session>, projects: List<String>): ConsoleLaunch {
        sessions.filter { it.project == PROJECT }
            .maxWithOrNull(compareBy<Session> { it.active }.thenBy { it.lastActivityAt })
            ?.let { return ConsoleLaunch.Resume(it.id) }
        val agent = sessions.maxByOrNull { it.lastActivityAt }?.agent ?: Agent.CODEX
        return ConsoleLaunch.Create(NewSessionDraft(agent, PROJECT, approvalMode = APPROVAL_MODE, newProject = PROJECT !in projects))
    }
}
