package app.regie.display

/** Ce que l'app retient d'un écran Android. */
data class DisplaySnapshot(
    val id: Int,
    val isDefault: Boolean,
    val isOn: Boolean,
    val isPresentation: Boolean,
    val isPrivate: Boolean,
)

sealed interface ScreenMode {
    /** Écran haut sur le display par défaut, écran bas sur [bottomDisplayId]. */
    data class Dual(val bottomDisplayId: Int) : ScreenMode

    /** Écran bas éteint ou absent : la zone du bas devient un panneau en bas de l'écran haut. */
    data object Single : ScreenMode
}

object DisplayPolicy {
    /** L'écran bas de la Thor (« Screen-2 »), voir docs/thor.md. */
    const val THOR_BOTTOM_DISPLAY = 4

    fun choose(displays: List<DisplaySnapshot>, forceSingle: Boolean): ScreenMode {
        if (forceSingle) return ScreenMode.Single
        val candidates = displays.filter { !it.isDefault && it.isOn && !it.isPrivate }
        val bottom = candidates.firstOrNull { it.id == THOR_BOTTOM_DISPLAY }
            ?: candidates.firstOrNull { it.isPresentation }
        return bottom?.let { ScreenMode.Dual(it.id) } ?: ScreenMode.Single
    }
}
