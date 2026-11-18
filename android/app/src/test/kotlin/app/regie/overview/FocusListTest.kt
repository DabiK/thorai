package app.regie.overview

import kotlin.test.Test
import kotlin.test.assertEquals

class FocusListTest {

    private val list = FocusList()

    @Test
    fun `the d-pad moves one line up or down`() {
        assertEquals(1, list.move(0, 1, count = 6))
        assertEquals(5, list.move(4, 1, count = 6))
        assertEquals(3, list.move(4, -1, count = 6))
    }

    @Test
    fun `the focus stays put at the ends of the list`() {
        assertEquals(0, list.move(0, -1, count = 6))
        assertEquals(5, list.move(5, 1, count = 6))
    }

    @Test
    fun `the focus never lands past the last session`() {
        assertEquals(2, list.move(7, 1, count = 3))
    }

    @Test
    fun `previous and next walk the sessions in order`() {
        assertEquals(1, list.next(0, count = 4))
        assertEquals(3, list.next(3, count = 4))
        assertEquals(2, list.previous(3, count = 4))
        assertEquals(0, list.previous(0, count = 4))
    }

    @Test
    fun `an empty list keeps the focus at zero`() {
        assertEquals(0, list.move(0, 1, count = 0))
        assertEquals(0, list.next(0, count = 0))
    }
}
