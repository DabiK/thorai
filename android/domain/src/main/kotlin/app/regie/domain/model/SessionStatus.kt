package app.regie.domain.model

/** Où en est une session, du point de vue de l'utilisateur. */
enum class SessionStatus {
    RUNNING,
    AWAITING_APPROVAL,
    DONE,
    PAUSED,
}
