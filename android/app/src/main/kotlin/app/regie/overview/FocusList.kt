package app.regie.overview

/** La vue d'ensemble : une liste défilable, une ligne par session (ticket 22). */
object OverviewList {
    /**
     * Sessions dont le journal est lu dès la connexion, dans l'ordre de la liste : leurs lignes
     * montrent l'étape et la dernière action sans attendre. Les autres les montrent une fois ouvertes.
     */
    const val PRELOADED = 12
}

/** Le focus dans la liste : la croix monte ou descend d'une ligne, sans boucler, arrêté aux bords. */
class FocusList {

    fun move(index: Int, delta: Int, count: Int): Int = if (count == 0) 0 else (index + delta).coerceIn(0, count - 1)

    fun next(index: Int, count: Int): Int = move(index, 1, count)

    fun previous(index: Int, count: Int): Int = move(index, -1, count)
}
