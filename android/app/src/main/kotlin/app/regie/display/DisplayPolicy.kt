package app.regie.display

import android.view.Display

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

    /**
     * Un écran ne compte comme allumé qu'en pleine marche. OFF, DOZE, DOZE_SUSPEND et
     * ON_SUSPEND (écran figé, processeur endormi) basculent en mode un seul écran.
     */
    fun isOn(state: Int): Boolean = state == Display.STATE_ON || state == Display.STATE_VR

    /**
     * L'écran du haut doit vivre sur le display par défaut. Ouvert ailleurs (lanceur de
     * l'écran du bas, `am start --display 4`), il doit s'y relancer : retourne ce display,
     * ou `null` s'il est déjà au bon endroit ou si le display par défaut est inconnu.
     */
    fun topScreenTarget(currentDisplayId: Int, displays: List<DisplaySnapshot>): Int? {
        val default = displays.firstOrNull { it.isDefault } ?: return null
        return default.id.takeIf { it != currentDisplayId }
    }

    fun choose(displays: List<DisplaySnapshot>, forceSingle: Boolean): ScreenMode {
        if (forceSingle) return ScreenMode.Single
        val candidates = displays.filter { !it.isDefault && it.isOn && !it.isPrivate }
        val bottom = candidates.firstOrNull { it.id == THOR_BOTTOM_DISPLAY }
            ?: candidates.firstOrNull { it.isPresentation }
        return bottom?.let { ScreenMode.Dual(it.id) } ?: ScreenMode.Single
    }
}
