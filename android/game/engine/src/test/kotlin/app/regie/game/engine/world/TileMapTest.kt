package app.regie.game.engine.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileMapTest {
    @Test
    fun `hors de la carte, tout est bloque`() {
        val map = TileMap(4, 3)
        assertTrue(map.isBlocked(TilePos(-1, 0)))
        assertTrue(map.isBlocked(TilePos(4, 0)))
        assertFalse(map.isBlocked(TilePos(3, 2)))
    }

    @Test
    fun `le sol et les collisions se posent tuile par tuile`() {
        val map = TileMap(4, 3)
        map.setGround(TilePos(1, 1), Ground.PATH)
        map.setBlocked(TilePos(2, 1))
        assertEquals(Ground.PATH, map.ground(TilePos(1, 1)))
        assertEquals(Ground.GRASS, map.ground(TilePos(0, 0)))
        assertTrue(map.isBlocked(TilePos(2, 1)))
    }
}
