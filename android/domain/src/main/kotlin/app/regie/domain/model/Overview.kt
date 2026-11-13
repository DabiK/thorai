package app.regie.domain.model

/** Une validation en attente et la session qui la demande. */
data class PendingApproval(val session: Session, val approval: Approval)

/**
 * La vue d'ensemble. Les sessions sont rangées par [order] : actives d'abord, puis la plus
 * récemment changée d'abord.
 */
data class Overview(
    val sessions: List<Session>,
    val agents: List<AgentVersion>,
    val bridge: BridgeStatus = BridgeStatus.Connecting,
    /** Régie ne peut pas rallumer le débogage sans fil : l'accès des agents à Android ne survit pas à un redémarrage. */
    val androidAccessMissing: Boolean = false,
) {
    /** La première validation en attente, dans l'ordre des sessions. */
    val firstPendingApproval: PendingApproval? =
        sessions.firstNotNullOfOrNull { session -> session.pendingApproval?.let { PendingApproval(session, it) } }

    /** Sessions chargées dans un agent : le compteur de l'en-tête. */
    val activeCount: Int = sessions.count { it.active }

    /** Sessions qui travaillent encore : « Quitter » demande confirmation s'il y en a. */
    val busyCount: Int = sessions.count { it.status.isBusy }

    companion object {
        val EMPTY = Overview(sessions = emptyList(), agents = emptyList())

        /** Actives d'abord, puis par dernier changement, le plus récent d'abord. */
        val order: Comparator<Session> = compareByDescending<Session> { it.active }.thenByDescending { it.updatedAt }

        fun of(
            sessions: Collection<Session>,
            agents: List<AgentVersion>,
            bridge: BridgeStatus = BridgeStatus.Connecting,
            androidAccessMissing: Boolean = false,
        ) = Overview(sessions.sortedWith(order), agents, bridge, androidAccessMissing)
    }
}
