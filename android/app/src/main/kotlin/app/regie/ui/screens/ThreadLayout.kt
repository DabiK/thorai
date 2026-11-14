package app.regie.ui.screens

import app.regie.domain.model.ThreadLine

/** Une action d'une ligne : lu, modifié, lancé, autre. */
internal val ThreadLine.isAction: Boolean
    get() = this is ThreadLine.Read || this is ThreadLine.Edit || this is ThreadLine.Run || this is ThreadLine.Other

/**
 * L'écart, en dp, avant une ligne du fil : 2 entre deux actions qui se suivent (un bloc serré, comme
 * la maquette), 11 sinon, rien avant la première.
 */
internal fun gapBefore(previous: ThreadLine?, line: ThreadLine): Int = when {
    previous == null -> 0
    previous.isAction && line.isAction -> 2
    else -> 11
}
