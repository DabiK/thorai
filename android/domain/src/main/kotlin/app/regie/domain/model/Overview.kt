package app.regie.domain.model

/** Une validation en attente et la session qui la demande. */
data class PendingApproval(val session: Session, val approval: Approval)

/** La vue d'ensemble : toutes les sessions, dans leur ordre d'arrivée. */
data class Overview(
    val sessions: List<Session>,
    val agentVersions: Map<Agent, String>,
) {
    /** La première validation en attente, dans l'ordre des sessions. */
    val firstPendingApproval: PendingApproval? =
        sessions.firstNotNullOfOrNull { session -> session.pendingApproval?.let { PendingApproval(session, it) } }

    fun apply(event: SessionEvent): Overview = when (event) {
        is SessionEvent.AgentsDetected -> copy(agentVersions = event.versions)
        is SessionEvent.SessionUpdated -> copy(sessions = sessions.upsert(event.session))
        is SessionEvent.SessionRemoved -> copy(sessions = sessions.filterNot { it.id == event.id })
    }

    private fun List<Session>.upsert(session: Session): List<Session> {
        val index = indexOfFirst { it.id == session.id }
        return if (index < 0) this + session else toMutableList().apply { set(index, session) }
    }

    companion object {
        val EMPTY = Overview(sessions = emptyList(), agentVersions = emptyMap())
    }
}
