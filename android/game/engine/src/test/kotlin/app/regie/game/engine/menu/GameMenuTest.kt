package app.regie.game.engine.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameMenuTest {
    private fun GameMenu.pressAll(vararg keys: MenuKey, letters: Int = 0): MenuStep =
        keys.fold(MenuStep(this)) { step, key -> step.menu.press(key, letters) }

    @Test
    fun `the title menu offers the launch choices in order`() {
        assertEquals(
            listOf(MenuEntry.CONTINUE, MenuEntry.NEW_SESSION, MenuEntry.AGENTS, MenuEntry.SETTINGS, MenuEntry.QUIT),
            GameMenu.title().entries,
        )
    }

    @Test
    fun `the pause menu keeps pausing everything, the courier, the agents, the setup and quitting`() {
        assertEquals(
            listOf(MenuEntry.COURIER, MenuEntry.AGENTS, MenuEntry.SETTINGS, MenuEntry.SETUP, MenuEntry.PAUSE_ALL, MenuEntry.QUIT),
            GameMenu.pause().entries,
        )
    }

    @Test
    fun `the cross moves the cursor without wrapping`() {
        val menu = GameMenu.title()
        assertEquals(0, menu.press(MenuKey.UP).menu.selected)
        assertEquals(2, menu.pressAll(MenuKey.DOWN, MenuKey.DOWN).menu.selected)
        assertEquals(4, menu.pressAll(*Array(9) { MenuKey.DOWN }).menu.selected)
    }

    @Test
    fun `A on Continuer closes the title, Start too`() {
        val chosen = GameMenu.title().press(MenuKey.A)
        assertFalse(chosen.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.CONTINUE), chosen.outcome)
        val started = GameMenu.title().pressAll(MenuKey.DOWN, MenuKey.START)
        assertFalse(started.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.CONTINUE), started.outcome)
    }

    @Test
    fun `B does nothing on the title, the first screen`() {
        val step = GameMenu.title().press(MenuKey.B)
        assertTrue(step.menu.open)
        assertNull(step.outcome)
    }

    @Test
    fun `a new session leaves the title, agents and settings open over it, quit stays for its confirmation`() {
        val newSession = GameMenu.title().pressAll(MenuKey.DOWN, MenuKey.A)
        assertFalse(newSession.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.NEW_SESSION), newSession.outcome)
        val agents = GameMenu.title().pressAll(MenuKey.DOWN, MenuKey.DOWN, MenuKey.A)
        assertTrue(agents.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.AGENTS), agents.outcome)
        val quit = GameMenu.title().pressAll(*Array(4) { MenuKey.DOWN }, MenuKey.A)
        assertTrue(quit.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.QUIT), quit.outcome)
    }

    @Test
    fun `B and Start close the pause menu`() {
        assertFalse(GameMenu.pause().press(MenuKey.B).menu.open)
        assertFalse(GameMenu.pause().press(MenuKey.START).menu.open)
        assertNull(GameMenu.pause().press(MenuKey.START).outcome)
    }

    @Test
    fun `pausing everything is chosen with A and closes the menu`() {
        val step = GameMenu.pause().pressAll(*Array(4) { MenuKey.DOWN }, MenuKey.A)
        assertFalse(step.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.PAUSE_ALL), step.outcome)
    }

    @Test
    fun `Préparer la console in the pause menu reopens the setup and closes the menu`() {
        val step = GameMenu.pause().pressAll(*Array(3) { MenuKey.DOWN }, MenuKey.A)
        assertFalse(step.menu.open)
        assertEquals(MenuOutcome.Choose(MenuEntry.SETUP), step.outcome)
        assertFalse(MenuEntry.SETUP in GameMenu.title().entries, "le titre reste court ; le parcours s'ouvre seul au premier lancement")
    }

    @Test
    fun `the courier page moves among letters, opens one, marks all read, and B returns to the list`() {
        val opened = GameMenu.pause().press(MenuKey.A)
        assertTrue(opened.menu.courierOpen)
        assertEquals(MenuOutcome.Choose(MenuEntry.COURIER), opened.outcome)
        val moved = opened.menu.pressAll(MenuKey.DOWN, MenuKey.DOWN, MenuKey.DOWN, letters = 2)
        assertEquals(1, moved.menu.letter)
        val read = moved.menu.press(MenuKey.X, 2)
        assertEquals(MenuOutcome.MarkAllRead, read.outcome)
        val open = moved.menu.press(MenuKey.A, 2)
        assertEquals(MenuOutcome.OpenLetter(1), open.outcome)
        val back = moved.menu.press(MenuKey.B, 2)
        assertTrue(back.menu.open)
        assertFalse(back.menu.courierOpen)
        assertEquals(MenuEntry.COURIER, back.menu.entries[back.menu.selected])
    }

    @Test
    fun `an empty courier opens nothing`() {
        val courier = GameMenu.pause().press(MenuKey.A).menu
        assertNull(courier.press(MenuKey.A, 0).outcome)
        assertEquals(0, courier.press(MenuKey.DOWN, 0).menu.letter)
    }

    @Test
    fun `a closed menu reads no key`() {
        val step = GameMenu.CLOSED.press(MenuKey.A)
        assertFalse(step.menu.open)
        assertNull(step.outcome)
    }
}
