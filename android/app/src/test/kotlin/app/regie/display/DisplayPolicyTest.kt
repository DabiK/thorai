package app.regie.display

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
