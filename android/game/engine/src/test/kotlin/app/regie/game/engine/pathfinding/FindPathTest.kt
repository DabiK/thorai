package app.regie.game.engine.pathfinding

import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindPathTest {
    @Test
    fun `sur une carte libre, le chemin est le plus court en 4 directions`() {
        val map = TileMap(10, 8)
        val path = assertNotNull(findPath(map, TilePos(1, 1), TilePos(7, 5)))
        assertEquals(TilePos(1, 1), path.first())
        assertEquals(TilePos(7, 5), path.last())
        assertEquals(6 + 4 + 1, path.size)
        assertSteps(path)
    }

    @Test
    fun `le chemin contourne un mur`() {
        val map = TileMap(9, 7)
        // Un mur vertical en x = 4, ouvert seulement en bas (y = 6).
        for (y in 0..5) map.setBlocked(TilePos(4, y))
        val path = assertNotNull(findPath(map, TilePos(1, 1), TilePos(7, 1)))
        assertSteps(path)
        assertTrue(path.none { map.isBlocked(it) })
        assertTrue(TilePos(4, 6) in path)
        // 3 vers la droite, 5 vers le bas, 3 à droite, 5 vers le haut : 16 pas, 17 tuiles.
        assertEquals(17, path.size)
    }

    @Test
    fun `une arrivee enfermee ou bloquee ne donne aucun chemin`() {
        val map = TileMap(7, 7)
        listOf(TilePos(3, 2), TilePos(2, 3), TilePos(4, 3), TilePos(3, 4)).forEach { map.setBlocked(it) }
        assertNull(findPath(map, TilePos(0, 0), TilePos(3, 3)))
        assertNull(findPath(map, TilePos(0, 0), TilePos(3, 2)))
        assertNull(findPath(map, TilePos(0, 0), TilePos(9, 9)))
    }

    @Test
    fun `partir de l'arrivee donne un chemin d'une seule tuile`() {
        val map = TileMap(3, 3)
        assertEquals(listOf(TilePos(1, 1)), findPath(map, TilePos(1, 1), TilePos(1, 1)))
    }

    @Test
    fun `le depart peut etre bloque, pas les tuiles suivantes`() {
        val map = TileMap(5, 1)
        map.setBlocked(TilePos(0, 0))
        val path = assertNotNull(findPath(map, TilePos(0, 0), TilePos(4, 0)))
        assertEquals(5, path.size)
    }

    @Test
    fun `le meme appel donne toujours le meme chemin`() {
        val map = TileMap(20, 20)
        for (x in 3..16) map.setBlocked(TilePos(x, 10))
        val first = findPath(map, TilePos(10, 2), TilePos(10, 18))
        repeat(5) { assertEquals(first, findPath(map, TilePos(10, 2), TilePos(10, 18))) }
    }

    private fun assertSteps(path: List<TilePos>) {
        path.zipWithNext().forEach { (a, b) -> assertEquals(1, abs(a.x - b.x) + abs(a.y - b.y), "$a -> $b") }
    }
}
