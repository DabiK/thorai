package app.regie.session

import app.regie.domain.model.SessionId

/** L'écran affiché, le même sur les deux écrans de la console. */
sealed interface Screen {
    /** La vue d'ensemble (la régie). */
    data object Overview : Screen

    /** Une session en direct. */
    data class Session(val id: SessionId) : Screen
}
