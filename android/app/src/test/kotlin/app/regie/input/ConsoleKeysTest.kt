package app.regie.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsoleKeysTest {
    private val received = mutableListOf<GamepadInput>()
    private var available = true
    private var captured = emptySet<Button>()
    private val keys = ConsoleKeys(InputRouter { received += it }, { available }, { captured })

    private fun press(keyCode: Int, repeat: Int = 0) = keys.onKey(keyCode, isDown = true, repeatCount = repeat)
    private fun release(keyCode: Int) = keys.onKey(keyCode, isDown = false, repeatCount = 0)

    @Test
    fun `L2 and R2 are taken from any app, every other key goes through`() {
        assertTrue(press(KeyEvent.KEYCODE_BUTTON_L2))
        assertTrue(release(KeyEvent.KEYCODE_BUTTON_L2))
        assertTrue(press(KeyEvent.KEYCODE_BUTTON_R2))
        assertTrue(release(KeyEvent.KEYCODE_BUTTON_R2))
        listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_VOLUME_UP).forEach {
            assertFalse(press(it), "$it")
            assertFalse(release(it), "$it")
        }
        assertEquals(listOf<GamepadInput>(GamepadInput.TriggerDown(Trigger.L2), GamepadInput.TriggerUp(Trigger.L2),
            GamepadInput.TriggerDown(Trigger.R2), GamepadInput.TriggerUp(Trigger.R2)), received)
    }

    @Test
    fun `buttons are taken only while a Régie panel waits for them`() {
        captured = setOf(Button.A, Button.B)
        assertTrue(press(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(release(KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(press(KeyEvent.KEYCODE_BUTTON_X))
        assertFalse(release(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(listOf<GamepadInput>(GamepadInput.Pressed(Button.A)), received)
    }

    @Test
    fun `a release follows its press even when the state changed in between`() {
        captured = setOf(Button.DOWN)
        assertTrue(press(KeyEvent.KEYCODE_DPAD_DOWN))
        captured = emptySet()
        available = false
        assertTrue(press(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 1), "repeat of a taken press")
        assertTrue(release(KeyEvent.KEYCODE_DPAD_DOWN))
        // Pressed while Régie had the focus: the app keeps the release too.
        assertFalse(press(KeyEvent.KEYCODE_BUTTON_L2))
        available = true
        assertFalse(release(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(listOf<GamepadInput>(GamepadInput.Pressed(Button.DOWN), GamepadInput.Pressed(Button.DOWN)), received)
    }

    @Test
    fun `nothing is taken while Régie itself has the gamepad`() {
        available = false
        captured = Button.entries.toSet()
        assertFalse(press(KeyEvent.KEYCODE_BUTTON_L2))
        assertFalse(press(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(received.isEmpty())
    }

    @Test
    fun `reset ends a held trigger once`() {
        press(KeyEvent.KEYCODE_BUTTON_L2)
        keys.reset()
        assertFalse(release(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(listOf<GamepadInput>(GamepadInput.TriggerDown(Trigger.L2), GamepadInput.TriggerUp(Trigger.L2)), received)
    }
}
