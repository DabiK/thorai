package app.regie.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputRouterTest {

    private val received = mutableListOf<GamepadInput>()
    private val held = mutableListOf<Pair<Button, Boolean>>()
    private val router = InputRouter({ received += it }, { button, down -> held += button to down })

    private fun press(keyCode: Int, repeatCount: Int = 0) = router.onKey(keyCode, isDown = true, repeatCount = repeatCount)
    private fun release(keyCode: Int) = router.onKey(keyCode, isDown = false, repeatCount = 0)
    private fun axes(l2: Float = 0f, r2: Float = 0f, rightStickY: Float = 0f, leftX: Float = 0f, leftY: Float = 0f, hatX: Float = 0f, hatY: Float = 0f) =
        router.onAxes(TriggerAxes(l2, r2, rightStickY, leftStickX = leftX, leftStickY = leftY, hatX = hatX, hatY = hatY))

    /** Une touche de la croix qu'Android fabrique d'un mouvement non consommé (FLAG_FALLBACK). */
    private fun synthetic(keyCode: Int, isDown: Boolean, repeatCount: Int = 0) =
        router.onKey(keyCode, isDown, repeatCount, fallback = true)

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

    @Test
    fun `a d-pad direction is held from its first press to its release, repeats aside`() {
        press(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 1)
        press(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 2)
        release(KeyEvent.KEYCODE_DPAD_RIGHT)
        press(KeyEvent.KEYCODE_BUTTON_A)
        release(KeyEvent.KEYCODE_BUTTON_A)

        assertEquals(listOf(Button.RIGHT to true, Button.RIGHT to false), held)
    }

    @Test
    fun `a release without a press is not reported`() {
        release(KeyEvent.KEYCODE_DPAD_UP)

        assertEquals(emptyList(), held)
    }

    @Test
    fun `the left stick holds its dominant direction outside the dead zone, without pressing anything`() {
        axes(leftX = 0.2f)
        axes(leftX = 0.8f, leftY = 0.3f)
        axes(leftX = 0.7f, leftY = 0.2f) // même direction : rien de plus
        axes(leftX = 0.2f, leftY = -0.9f)
        axes(leftX = 0.1f, leftY = 0.1f)

        assertEquals(
            listOf(Button.RIGHT to true, Button.RIGHT to false, Button.UP to true, Button.UP to false),
            held,
        )
        assertEquals(emptyList(), received, "le stick gauche ne déplace pas le focus des listes")
    }

    @Test
    fun `the left stick keeps its direction between the two thresholds`() {
        axes(leftY = 0.9f)
        axes(leftY = 0.4f)
        axes(leftY = 0.2f)

        assertEquals(listOf(Button.DOWN to true, Button.DOWN to false), held)
    }

    @Test
    fun `a reset releases the held directions`() {
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        axes(leftY = 1f)
        held.clear()

        router.reset()
        router.reset()

        assertEquals(listOf(Button.LEFT to false, Button.DOWN to false), held)
    }

    @Test
    fun `the d-pad keys Android makes from the left stick are swallowed, the stick already walks`() {
        // Diagonale : Android tire une touche de chaque axe du stick ; une seule direction est tenue.
        axes(leftX = 0.9f, leftY = 0.8f)
        assertTrue(synthetic(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = true))
        assertTrue(synthetic(KeyEvent.KEYCODE_DPAD_DOWN, isDown = true))
        assertTrue(synthetic(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = true, repeatCount = 1))
        axes()
        assertTrue(synthetic(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = false))
        assertTrue(synthetic(KeyEvent.KEYCODE_DPAD_DOWN, isDown = false))

        assertEquals(emptyList(), received, "aucun appui en double sur les listes")
        assertEquals(listOf(Button.RIGHT to true, Button.RIGHT to false), held)
    }

    @Test
    fun `the d-pad keys Android makes from a hat d-pad are kept, repeats included, until released`() {
        axes(hatX = 1f)
        synthetic(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = true)
        synthetic(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = true, repeatCount = 1)
        axes(hatX = 0f)
        synthetic(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = false)

        assertEquals(List<GamepadInput>(2) { GamepadInput.Pressed(Button.RIGHT) }, received)
        assertEquals(listOf(Button.RIGHT to true, Button.RIGHT to false), held)
    }
}
