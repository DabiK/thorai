package app.regie.overview

import kotlin.test.Test
import kotlin.test.assertEquals

class FocusGridTest {

    private val grid = FocusGrid(columns = 2)

    @Test
    fun `the d-pad moves inside a two by two grid`() {
        assertEquals(1, grid.move(0, Direction.RIGHT, count = 4))
        assertEquals(2, grid.move(0, Direction.DOWN, count = 4))
        assertEquals(3, grid.move(1, Direction.DOWN, count = 4))
        assertEquals(2, grid.move(3, Direction.LEFT, count = 4))
        assertEquals(1, grid.move(3, Direction.UP, count = 4))
    }

    @Test
    fun `the focus stays put at the edges`() {
        assertEquals(0, grid.move(0, Direction.UP, count = 4))
        assertEquals(0, grid.move(0, Direction.LEFT, count = 4))
        assertEquals(1, grid.move(1, Direction.RIGHT, count = 4))
        assertEquals(3, grid.move(3, Direction.DOWN, count = 4))
    }

    @Test
    fun `the focus never lands on an empty cell`() {
        assertEquals(1, grid.move(1, Direction.DOWN, count = 3))
        assertEquals(2, grid.move(2, Direction.RIGHT, count = 3))
    }

    @Test
    fun `previous and next walk the sessions in order`() {
        assertEquals(1, grid.next(0, count = 4))
        assertEquals(3, grid.next(3, count = 4))
        assertEquals(2, grid.previous(3, count = 4))
        assertEquals(0, grid.previous(0, count = 4))
    }

    @Test
    fun `an empty grid keeps the focus at zero`() {
        assertEquals(0, grid.move(0, Direction.RIGHT, count = 0))
        assertEquals(0, grid.next(0, count = 0))
    }
}
