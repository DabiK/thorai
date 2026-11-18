package app.regie.input

import android.view.KeyEvent
import kotlin.math.abs

/**
 * Routeur d'entrées unique de l'app : les deux activités lui passent les touches
 * et les axes de la manette, il publie des [GamepadInput].
 *
 * Il publie aussi, à part ([held]), les directions tenues : de l'appui au relâché d'une flèche de la
 * croix, sans ses répétitions, et la direction dominante du stick gauche. Le coursier du hameau
 * marche tant qu'une direction est tenue ; les listes, elles, suivent les [GamepadInput.Pressed].
 *
 * L2 et R2 arrivent à la fois en touche et en axe : une gâchette est maintenue tant
 * que l'une des deux sources l'est, si bien qu'un seul début et une seule fin sont publiés.
 *
 * N'utilise d'Android que les constantes de [KeyEvent], ce qui le garde testable en JVM.
 */
class InputRouter(
    private val sink: (GamepadInput) -> Unit,
    private val held: (Button, Boolean) -> Unit,
) {
    /** Sans coursier à faire marcher : seules les [GamepadInput] comptent. */
    constructor(sink: (GamepadInput) -> Unit) : this(sink, { _, _ -> })

    private class TriggerState {
        var keyHeld = false
        var axisHeld = false
        val held get() = keyHeld || axisHeld
    }

    private val triggers = Trigger.entries.associateWith { TriggerState() }
    private var lastScroll = 0f

    /** Les flèches de la croix enfoncées, dans l'ordre d'appui. */
    private val heldKeys = LinkedHashSet<Button>()

    /** La direction tenue au stick gauche, ou null au centre. */
    private var stickDirection: Button? = null

    /** La croix en axe (HAT), lue avec les autres axes, et les touches qu'Android en a tirées. */
    private var hatX = 0f
    private var hatY = 0f
    private val hatKeys = mutableSetOf<Button>()

    /**
     * Retourne `true` si la touche est consommée : elle n'ira ni au système de focus ni aux raccourcis système.
     *
     * [fallback] : une touche de la croix fabriquée par Android à partir d'un mouvement de manette que
     * personne n'a consommé (`ViewRootImpl.SyntheticJoystickHandler`, `KeyEvent.FLAG_FALLBACK`), depuis la
     * croix en axe (HAT) comme depuis le stick gauche. Celles du stick gauche sont avalées : le stick
     * passe déjà par [onAxes], et ses deux axes donneraient deux directions en diagonale. Celles d'une
     * croix en axe sont gardées, répétitions comprises, jusqu'à leur relâché.
     */
    fun onKey(keyCode: Int, isDown: Boolean, repeatCount: Int, fallback: Boolean = false): Boolean {
        if (fallback && dropStickKey(keyCode, isDown, repeatCount)) return true
        TRIGGER_KEYS[keyCode]?.let { trigger ->
            if (repeatCount == 0) update(trigger) { keyHeld = isDown }
            return true
        }
        BUTTON_KEYS[keyCode]?.let { button ->
            val repeats = button in DIRECTIONS
            if (isDown && (repeatCount == 0 || repeats)) sink(GamepadInput.Pressed(button))
            if (repeats) holdKey(button, isDown)
            return true
        }
        // Raccourci système de A (DPAD_CENTER) et touche Entrée : avalés pour éviter un clic parasite.
        return keyCode in SWALLOWED_KEYS
    }

    /** Vrai si cette touche fabriquée vient du stick gauche, et non d'une croix en axe. */
    private fun dropStickKey(keyCode: Int, isDown: Boolean, repeatCount: Int): Boolean {
        val button = BUTTON_KEYS[keyCode]?.takeIf { it in DIRECTIONS } ?: return false
        if (!isDown) return !hatKeys.remove(button)
        if (repeatCount == 0 && hatPointsTo(button)) hatKeys.add(button)
        return button !in hatKeys
    }

    private fun hatPointsTo(button: Button): Boolean = when (button) {
        Button.LEFT -> hatX <= -HAT_PRESS
        Button.RIGHT -> hatX >= HAT_PRESS
        Button.UP -> hatY <= -HAT_PRESS
        else -> hatY >= HAT_PRESS
    }

    fun onAxes(axes: TriggerAxes) {
        hatX = axes.hatX
        hatY = axes.hatY
        updateAxis(Trigger.L2, axes.leftTrigger)
        updateAxis(Trigger.R2, axes.rightTrigger)

        val scroll = if (abs(axes.rightStickY) < STICK_DEAD_ZONE) 0f else axes.rightStickY
        if (scroll != lastScroll) {
            lastScroll = scroll
            sink(GamepadInput.Scroll(scroll))
        }
        updateStick(axes.leftStickX, axes.leftStickY)
    }

    private fun holdKey(button: Button, isDown: Boolean) {
        if (isDown) {
            if (heldKeys.add(button)) held(button, true)
        } else if (heldKeys.remove(button)) {
            held(button, false)
        }
    }

    /**
     * La direction dominante du stick gauche, avec hystérésis : elle part au-delà de [LEFT_STICK_PRESS]
     * et tient jusqu'à ce que son axe redescende sous [LEFT_STICK_RELEASE].
     */
    private fun updateStick(x: Float, y: Float) {
        val current = stickDirection
        val keep = current != null && when (current) {
            Button.LEFT -> -x
            Button.RIGHT -> x
            Button.UP -> -y
            else -> y
        } >= LEFT_STICK_RELEASE && current == dominant(x, y, LEFT_STICK_RELEASE)
        if (keep) return
        val next = dominant(x, y, LEFT_STICK_PRESS)
        if (next == current) return
        current?.let { held(it, false) }
        stickDirection = next
        next?.let { held(it, true) }
    }

    private fun dominant(x: Float, y: Float, threshold: Float): Button? = when {
        abs(x) < threshold && abs(y) < threshold -> null
        abs(x) >= abs(y) -> if (x > 0) Button.RIGHT else Button.LEFT
        else -> if (y > 0) Button.DOWN else Button.UP
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
        heldKeys.toList().forEach { holdKey(it, isDown = false) }
        hatKeys.clear()
        stickDirection?.let {
            stickDirection = null
            held(it, false)
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
        /** La gâchette d'une touche, ou null. */
        fun triggerOf(keyCode: Int): Trigger? = TRIGGER_KEYS[keyCode]

        /** Le bouton d'une touche de la manette, ou null. */
        fun buttonOf(keyCode: Int): Button? = BUTTON_KEYS[keyCode]

        /** Seuils de l'axe des gâchettes, avec hystérésis pour ne pas battre autour d'une valeur. */
        const val AXIS_PRESS = 0.5f
        const val AXIS_RELEASE = 0.25f
        const val STICK_DEAD_ZONE = 0.2f

        /** Seuils du stick gauche : il fait marcher au-delà du premier et s'arrête sous le second. */
        const val LEFT_STICK_PRESS = 0.5f
        const val LEFT_STICK_RELEASE = 0.3f

        /** Une croix en axe vaut -1, 0 ou 1 ; Android la lit enfoncée au-delà de la moitié. */
        const val HAT_PRESS = 0.5f

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
