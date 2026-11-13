package app.regie.domain.model

/** Ce que la passerelle vers les agents publie. */
sealed interface SessionEvent {
    /** Versions des agents installés, relevées par le pont. */
    data class AgentsDetected(val versions: Map<Agent, String>) : SessionEvent

    /** Une session est apparue ou a changé. */
    data class SessionUpdated(val session: Session) : SessionEvent

    /** Une session a disparu de la liste. */
    data class SessionRemoved(val id: SessionId) : SessionEvent
}
