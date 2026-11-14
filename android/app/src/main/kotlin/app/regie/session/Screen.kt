package app.regie.session

import app.regie.domain.model.SessionId

/** L'écran affiché, le même sur les deux écrans de la console. */
sealed interface Screen {
    /** La vue d'ensemble (la régie). */
    data object Overview : Screen

    data object Approval : Screen

    data class Diff(val id: SessionId) : Screen

    /** Une session en direct. */
    data class Session(val id: SessionId) : Screen
}

val Screen.sessionId: SessionId? get() = when (this) {
    is Screen.Session -> id
    is Screen.Diff -> id
    else -> null
}
