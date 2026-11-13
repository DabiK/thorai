package app.regie.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputRouterTest {

    private val received = mutableListOf<GamepadInput>()
    private val router = InputRouter { received += it }

    private fun press(keyCode: Int, repeatCount: Int = 0) = router.onKey(keyCode, isDown = true, repeatCount = repeatCount)
    private fun release(keyCode: Int) = router.onKey(keyCode, isDown = false, repeatCount = 0)
    private fun axes(l2: Float = 0f, r2: Float = 0f, rightStickY: Float = 0f) =
        router.onAxes(TriggerAxes(leftTrigger = l2, rightTrigger = r2, rightStickY = rightStickY))

    @Test
    fun `face buttons, shoulders, start and the d-pad are consumed and mapped`() {
        val mapping = mapOf(
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

        mapping.forEach { (keyCode, button) ->
            received.clear()
            assertTrue(press(keyCode), "appui sur $button consommé")
            assertTrue(release(keyCode), "relâché de $button consommé")
            assertEquals(listOf<GamepadInput>(GamepadInput.Pressed(button)), received)
        }
    }

    @Test
    fun `a held face button fires once`() {
        press(KeyEvent.KEYCODE_BUTTON_A)
        press(KeyEvent.KEYCODE_BUTTON_A, repeatCount = 1)
        press(KeyEvent.KEYCODE_BUTTON_A, repeatCount = 2)

        assertEquals(listOf<GamepadInput>(GamepadInput.Pressed(Button.A)), received)
    }

    @Test
    fun `a held d-pad direction repeats`() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_DPAD_DOWN, repeatCount = 1)

        assertEquals(List<GamepadInput>(2) { GamepadInput.Pressed(Button.DOWN) }, received)
    }

    @Test
    fun `the system shortcut keys of the A button are swallowed so nothing gets clicked`() {
        assertTrue(press(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(release(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(press(KeyEvent.KEYCODE_ENTER))

        assertEquals(emptyList(), received)
    }

    @Test
    fun `other keys are left to the system`() {
        assertFalse(press(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(press(KeyEvent.KEYCODE_BACK))
        assertEquals(emptyList(), received)
    }

    @Test
    fun `L2 and R2 as keys give a start and an end`() {
        assertTrue(press(KeyEvent.KEYCODE_BUTTON_L2))
        press(KeyEvent.KEYCODE_BUTTON_L2, repeatCount = 1)
        assertTrue(release(KeyEvent.KEYCODE_BUTTON_L2))
        press(KeyEvent.KEYCODE_BUTTON_R2)
        release(KeyEvent.KEYCODE_BUTTON_R2)

        assertEquals(
            listOf(
                GamepadInput.TriggerDown(Trigger.L2),
                GamepadInput.TriggerUp(Trigger.L2),
                GamepadInput.TriggerDown(Trigger.R2),
                GamepadInput.TriggerUp(Trigger.R2),
            ),
            received,
        )
    }

    @Test
    fun `a trigger axis starts above the press threshold and ends below the release threshold`() {
        axes(l2 = 0.3f)
        assertEquals(emptyList(), received)

        axes(l2 = 0.8f)
        axes(l2 = 0.4f) // entre les deux seuils : toujours maintenu
        assertEquals(listOf<GamepadInput>(GamepadInput.TriggerDown(Trigger.L2)), received)

        axes(l2 = 0.1f)
        assertEquals(listOf(GamepadInput.TriggerDown(Trigger.L2), GamepadInput.TriggerUp(Trigger.L2)), received)
    }

    @Test
    fun `key then axis give a single start and a single end`() {
        press(KeyEvent.KEYCODE_BUTTON_L2)
        axes(l2 = 1f)
        axes(l2 = 0f) // la touche est encore enfoncée
        assertEquals(listOf<GamepadInput>(GamepadInput.TriggerDown(Trigger.L2)), received)

        release(KeyEvent.KEYCODE_BUTTON_L2)
        assertEquals(listOf(GamepadInput.TriggerDown(Trigger.L2), GamepadInput.TriggerUp(Trigger.L2)), received)
    }

    @Test
    fun `axis then key give a single start and a single end`() {
        axes(r2 = 1f)
        press(KeyEvent.KEYCODE_BUTTON_R2)
        release(KeyEvent.KEYCODE_BUTTON_R2) // l'axe est encore enfoncé
        axes(r2 = 0f)

        assertEquals(listOf(GamepadInput.TriggerDown(Trigger.R2), GamepadInput.TriggerUp(Trigger.R2)), received)
    }

    @Test
    fun `both triggers are tracked independently`() {
        axes(l2 = 1f)
        axes(l2 = 1f, r2 = 1f)
        axes(l2 = 0f, r2 = 1f)
        axes(l2 = 0f, r2 = 0f)

        assertEquals(
            listOf(
                GamepadInput.TriggerDown(Trigger.L2),
                GamepadInput.TriggerDown(Trigger.R2),
                GamepadInput.TriggerUp(Trigger.L2),
                GamepadInput.TriggerUp(Trigger.R2),
            ),
            received,
        )
    }

    @Test
    fun `the right stick scrolls outside its dead zone and stops once centred`() {
        axes(rightStickY = 0.1f)
        axes(rightStickY = 0.6f)
        axes(rightStickY = 0.6f)
        axes(rightStickY = -1f)
        axes(rightStickY = 0.05f)
        axes(rightStickY = 0f)

        assertEquals(
            listOf<GamepadInput>(
                GamepadInput.Scroll(0.6f),
                GamepadInput.Scroll(-1f),
                GamepadInput.Scroll(0f),
            ),
            received,
        )
    }

    @Test
    fun `a reset ends the held triggers once, for example when another app takes the screen`() {
        press(KeyEvent.KEYCODE_BUTTON_L2)
        axes(r2 = 1f)
        received.clear()

        router.reset()
        router.reset()
        release(KeyEvent.KEYCODE_BUTTON_L2) // le relâché arrive après coup : rien de plus

        assertEquals(listOf<GamepadInput>(GamepadInput.TriggerUp(Trigger.L2), GamepadInput.TriggerUp(Trigger.R2)), received)
    }

    @Test
    fun `a reset stops a running scroll`() {
        axes(rightStickY = 0.8f)
        received.clear()

        router.reset()

        assertEquals(listOf<GamepadInput>(GamepadInput.Scroll(0f)), received)
    }
}
