package app.regie

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.InputRouter
import app.regie.input.Trigger
import app.regie.input.TriggerAxes
import app.regie.overview.ControlState
import app.regie.domain.model.Overview
import app.regie.ui.screens.ControlCallbacks
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Base des deux activités : la manette passe par le routeur d'entrées unique, avant
 * le système de focus de Compose et avant les raccourcis système (A → DPAD_CENTER…),
 * qui ne s'appliquent qu'aux touches non consommées.
 */
abstract class GamepadActivity : ComponentActivity() {

    protected val container: AppContainer get() = (application as RegieApplication).container

    protected val callbacks = object : ControlCallbacks {
        override fun onButton(button: Button) = container.controller.handle(GamepadInput.Pressed(button))

        override fun onTrigger(trigger: Trigger, held: Boolean) = container.controller.handle(
            if (held) GamepadInput.TriggerDown(trigger) else GamepadInput.TriggerUp(trigger),
        )

        override fun onOpenApproval() = container.controller.openPendingApproval()

        override fun onQuit() = container.controller.requestQuit()

        override fun onConfirmQuit() = container.controller.confirmQuit()

        override fun onCancelQuit() = container.controller.cancelQuit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.callback = GamepadWindowCallback(window.callback, container.inputRouter)
        // « Quitter » est allé au bout : les deux écrans se ferment, même en arrière-plan.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.closing.collect { if (it) finishAndRemoveTask() }
            }
        }
    }

    override fun onStop() {
        // Une autre appli a pris l'écran : un relâché de gâchette ne nous parviendrait plus.
        container.inputRouter.reset()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Plein écran, comme le lanceur de la console : les barres reviennent d'un glissement. */
    private fun hideSystemBars() {
        window.insetsController?.apply {
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsets.Type.systemBars())
        }
    }

    protected fun setScreen(content: @Composable (Overview, ControlState) -> Unit) {
        setContent {
            RegieTheme {
                val overview by container.overview.collectAsStateWithLifecycle()
                val control by container.controller.state.collectAsStateWithLifecycle()
                content(overview, control)
            }
        }
        // Sans vue focalisée, Android avale la première touche de navigation pour quitter
        // le mode tactile, avant même dispatchKeyEvent : la vue racine prend donc le focus.
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0)?.apply {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }
}

/**
 * Enveloppe le Window.Callback de l'activité : la fenêtre lui passe chaque touche et
 * chaque mouvement de manette avant la hiérarchie de vues, donc avant Compose.
 */
private class GamepadWindowCallback(
    private val wrapped: Window.Callback,
    private val router: InputRouter,
) : Window.Callback by wrapped {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return wrapped.dispatchKeyEvent(event)
        }
        return router.onKey(event.keyCode, isDown, event.repeatCount) || wrapped.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            router.onAxes(
                TriggerAxes(
                    leftTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                    rightTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
                    rightStickY = event.getAxisValue(MotionEvent.AXIS_RZ),
                ),
            )
        }
        // La croix peut aussi arriver en axe (HAT) : on laisse Android la convertir en touches.
        return wrapped.dispatchGenericMotionEvent(event)
    }
}
