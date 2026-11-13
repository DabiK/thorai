package app.regie.display

import android.view.Display
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayPolicyTest {

    private val top = DisplaySnapshot(id = 0, isDefault = true, isOn = true, isPresentation = false, isPrivate = false)
    private val bottom = DisplaySnapshot(id = 4, isDefault = false, isOn = true, isPresentation = true, isPrivate = false)

    @Test
    fun `both screens on gives the dual mode on display 4`() {
        assertEquals(ScreenMode.Dual(bottomDisplayId = 4), DisplayPolicy.choose(listOf(top, bottom), forceSingle = false))
    }

    @Test
    fun `a switched off bottom screen gives the single mode`() {
        assertEquals(ScreenMode.Single, DisplayPolicy.choose(listOf(top, bottom.copy(isOn = false)), forceSingle = false))
    }

    @Test
    fun `a missing bottom screen gives the single mode`() {
        assertEquals(ScreenMode.Single, DisplayPolicy.choose(listOf(top), forceSingle = false))
    }

    @Test
    fun `the single mode can be forced`() {
        assertEquals(ScreenMode.Single, DisplayPolicy.choose(listOf(top, bottom), forceSingle = true))
    }

    @Test
    fun `a private virtual display is never used as the bottom screen`() {
        val recorder = DisplaySnapshot(id = 7, isDefault = false, isOn = true, isPresentation = false, isPrivate = true)

        assertEquals(ScreenMode.Single, DisplayPolicy.choose(listOf(top, recorder), forceSingle = false))
    }

    @Test
    fun `another presentation display is used when display 4 is gone`() {
        val other = bottom.copy(id = 5)

        assertEquals(ScreenMode.Dual(bottomDisplayId = 5), DisplayPolicy.choose(listOf(top, other), forceSingle = false))
    }

    @Test
    fun `display 4 is preferred over another presentation display`() {
        val other = bottom.copy(id = 2)

        assertEquals(ScreenMode.Dual(bottomDisplayId = 4), DisplayPolicy.choose(listOf(top, other, bottom), forceSingle = false))
    }

    @Test
    fun `only a fully lit screen counts as on`() {
        assertTrue(DisplayPolicy.isOn(Display.STATE_ON))
        listOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON_SUSPEND, Display.STATE_UNKNOWN)
            .forEach { assertFalse(DisplayPolicy.isOn(it), "état $it") }
    }

    @Test
    fun `a bottom screen in a low power state gives the single mode`() {
        listOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON_SUSPEND).forEach { state ->
            val dimmed = bottom.copy(isOn = DisplayPolicy.isOn(state))
            assertEquals(ScreenMode.Single, DisplayPolicy.choose(listOf(top, dimmed), forceSingle = false), "état $state")
        }
    }

    @Test
    fun `the top screen opened on display 4 moves back to display 0`() {
        assertEquals(0, DisplayPolicy.topScreenTarget(currentDisplayId = 4, displays = listOf(top, bottom)))
    }

    @Test
    fun `the top screen already on display 0 stays put`() {
        assertNull(DisplayPolicy.topScreenTarget(currentDisplayId = 0, displays = listOf(top, bottom)))
    }

    @Test
    fun `the top screen stays where it is when display 0 is unknown`() {
        assertNull(DisplayPolicy.topScreenTarget(currentDisplayId = 4, displays = listOf(bottom)))
    }
}
