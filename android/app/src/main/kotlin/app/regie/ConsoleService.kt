package app.regie

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.overlay.OverlayWindow
import app.regie.ui.screens.ConsoleOverlayScreen
import app.regie.ui.screens.ThreadScroll
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mode console (ticket 25, ADR 18) : le service d'accessibilité de Régie. Activé une fois par
 * l'utilisateur, il lit la manette avant l'appli qui a le focus, sur l'un ou l'autre écran, et ne
 * prend que L2, R2 et les boutons qu'un panneau de Régie attend ([AppContainer.consoleKeys]). Il pose
 * aussi la carte volante, la dictée ou la bulle par-dessus l'écran du haut.
 *
 * Il ne lit ni le contenu des fenêtres ni les événements d'accessibilité.
 */
class ConsoleService : AccessibilityService() {

    private val container: AppContainer get() = (application as RegieApplication).container
    private val scope = MainScope()
    private var overlay: OverlayWindow? = null
    private var following: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "mode console : service actif")
        container.consoleService.value = true
        val press: (Button) -> Unit = { container.handleInput(GamepadInput.Pressed(it)) }
        val window = OverlayWindow(this) { content ->
            RegieTheme {
                ConsoleOverlayScreen(content, press,
                    ThreadScroll(container.dictationScroll, container.dictationVelocity),
                    ThreadScroll(container.commandScroll, container.commandVelocity))
            }
        }
        overlay = window
        following = scope.launch { container.consoleOverlay.collect { window.show(it) } }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isDown = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        return container.consoleKeys.onKey(event.keyCode, isDown, event.repeatCount)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Log.i(TAG, "mode console : service arrêté")
        following?.cancel()
        overlay?.remove()
        container.consoleKeys.reset()
        container.consoleService.value = false
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Regie"
    }
}
