package app.regie.input

import android.view.KeyEvent
import kotlin.math.abs

/**
 * Routeur d'entrées unique de l'app : les deux activités lui passent les touches
 * et les axes de la manette, il publie des [GamepadInput].
 *
 * L2 et R2 arrivent à la fois en touche et en axe : une gâchette est maintenue tant
 * que l'une des deux sources l'est, si bien qu'un seul début et une seule fin sont publiés.
 *
 * N'utilise d'Android que les constantes de [KeyEvent], ce qui le garde testable en JVM.
 */
class InputRouter(private val sink: (GamepadInput) -> Unit) {

    private class TriggerState {
        var keyHeld = false
        var axisHeld = false
        val held get() = keyHeld || axisHeld
    }

    private val triggers = Trigger.entries.associateWith { TriggerState() }
    private var lastScroll = 0f

    /** Retourne `true` si la touche est consommée : elle n'ira ni au système de focus ni aux raccourcis système. */
    fun onKey(keyCode: Int, isDown: Boolean, repeatCount: Int): Boolean {
        TRIGGER_KEYS[keyCode]?.let { trigger ->
            if (repeatCount == 0) update(trigger) { keyHeld = isDown }
            return true
        }
        BUTTON_KEYS[keyCode]?.let { button ->
            val repeats = button in DIRECTIONS
            if (isDown && (repeatCount == 0 || repeats)) sink(GamepadInput.Pressed(button))
            return true
        }
        // Raccourci système de A (DPAD_CENTER) et touche Entrée : avalés pour éviter un clic parasite.
        return keyCode in SWALLOWED_KEYS
    }

    fun onAxes(axes: TriggerAxes) {
        updateAxis(Trigger.L2, axes.leftTrigger)
        updateAxis(Trigger.R2, axes.rightTrigger)

        val scroll = if (abs(axes.rightStickY) < STICK_DEAD_ZONE) 0f else axes.rightStickY
        if (scroll != lastScroll) {
            lastScroll = scroll
            sink(GamepadInput.Scroll(scroll))
        }
    }

    /** Termine les appuis en cours : la manette ne nous parle plus, par exemple quand une autre appli prend l'écran. */
    fun reset() {
        Trigger.entries.forEach { trigger ->
            update(trigger) {
                keyHeld = false
                axisHeld = false
            }
        }
        if (lastScroll != 0f) {
            lastScroll = 0f
            sink(GamepadInput.Scroll(0f))
        }
    }

    private fun updateAxis(trigger: Trigger, value: Float) {
        update(trigger) {
            axisHeld = if (axisHeld) value >= AXIS_RELEASE else value >= AXIS_PRESS
        }
    }

    private inline fun update(trigger: Trigger, change: TriggerState.() -> Unit) {
        val state = triggers.getValue(trigger)
        val before = state.held
        state.change()
        if (state.held != before) {
            sink(if (state.held) GamepadInput.TriggerDown(trigger) else GamepadInput.TriggerUp(trigger))
        }
    }

    companion object {
        /** Seuils de l'axe des gâchettes, avec hystérésis pour ne pas battre autour d'une valeur. */
        const val AXIS_PRESS = 0.5f
        const val AXIS_RELEASE = 0.25f
        const val STICK_DEAD_ZONE = 0.2f

        private val TRIGGER_KEYS = mapOf(
            KeyEvent.KEYCODE_BUTTON_L2 to Trigger.L2,
            KeyEvent.KEYCODE_BUTTON_R2 to Trigger.R2,
        )

        private val BUTTON_KEYS = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to Button.A,
            KeyEvent.KEYCODE_BUTTON_B to Button.B,
            KeyEvent.KEYCODE_BUTTON_X to Button.X,
            KeyEvent.KEYCODE_BUTTON_Y to Button.Y,
            KeyEvent.KEYCODE_BUTTON_L1 to Button.L1,
            KeyEvent.KEYCODE_BUTTON_R1 to Button.R1,
            KeyEvent.KEYCODE_BUTTON_START to Button.START,
            KeyEvent.KEYCODE_DPAD_UP to Button.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to Button.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to Button.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to Button.RIGHT,
        )

        private val DIRECTIONS = setOf(Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT)

        private val SWALLOWED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
        )
    }
}
