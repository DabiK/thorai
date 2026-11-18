package app.regie.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/**
 * Une fenêtre posée par-dessus les applis de l'écran du haut, sans focus ni toucher (l'écran du haut
 * n'est pas tactile, et la manette passe par le service). Elle n'existe que tant qu'il y a quelque
 * chose à montrer.
 *
 * Le service d'accessibilité la pose en `TYPE_ACCESSIBILITY_OVERLAY` : pas besoin de la permission
 * de superposition, et Android ne la compte pas comme une fenêtre qui masque les touchers (ADR 19).
 */
class OverlayWindow(
    private val context: Context,
    private val render: @Composable (OverlayContent) -> Unit,
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val shown = mutableStateOf<OverlayContent?>(null)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var placedAsPanel = false

    fun show(content: OverlayContent?) {
        shown.value = content
        if (content == null) return remove()
        val panel = content is OverlayContent.Dictation || content is OverlayContent.Command
        val current = view
        if (current != null) {
            // The dictation level changes many times a second: move the window only when its place changes.
            if (panel != placedAsPanel) windows.updateViewLayout(current, params(panel))
            placedAsPanel = panel
            return
        }
        val params = params(panel)
        placedAsPanel = panel
        val newOwner = OverlayOwner()
        val newView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent { shown.value?.let { render(it) } }
        }
        windows.addView(newView, params)
        view = newView
        owner = newOwner
    }

    fun remove() {
        view?.let { runCatching { windows.removeViewImmediate(it) } }
        owner?.destroy()
        view = null
        owner = null
    }

    /** La dictée et l'ordre en bas, au centre ; la carte et la bulle en haut à droite. */
    private fun params(panel: Boolean): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (panel) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.END
            x = if (panel) 0 else dp(20)
            y = dp(20)
            title = "Régie"
        }
    }
}

/** Le cycle de vie que Compose demande à une vue posée hors d'une activité. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
