package app.regie.game.ui.render

import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.render.GroundEdges.E
import app.regie.game.ui.render.GroundEdges.N
import app.regie.game.ui.render.GroundEdges.NE
import app.regie.game.ui.render.GroundEdges.S
import app.regie.game.ui.render.GroundEdges.SE
import app.regie.game.ui.render.GroundEdges.SW
import app.regie.game.ui.render.GroundEdges.W
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroundTest {
    private fun map(rows: List<String>): TileMap {
        val map = TileMap(rows.first().length, rows.size)
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                map.setGround(
                    TilePos(x, y),
                    when (c) {
                        '.' -> Ground.GRASS
                        '#' -> Ground.PATH
                        'o' -> Ground.PLAZA
                        '~' -> Ground.WATER
                        '=' -> Ground.BRIDGE
                        else -> error(c)
                    },
                )
            }
        }
        return map
    }

    @Test
    fun `grass is the background and never draws an edge`() {
        Ground.entries.forEach { assertFalse(GroundEdges.contrasts(Ground.GRASS, it)) }
        assertTrue(GroundEdges.contrasts(Ground.PATH, Ground.GRASS))
        assertFalse(GroundEdges.contrasts(Ground.PATH, Ground.PLAZA))
        assertTrue(GroundEdges.contrasts(Ground.PLAZA, Ground.PATH))
        assertTrue(GroundEdges.contrasts(Ground.WATER, Ground.GRASS))
        assertFalse(GroundEdges.contrasts(Ground.WATER, Ground.BRIDGE))
        assertTrue(GroundEdges.contrasts(Ground.BRIDGE, Ground.WATER))
        assertFalse(GroundEdges.contrasts(Ground.BRIDGE, Ground.PATH))
    }

    @Test
    fun `a path tile edges the grass on each side where it meets it`() {
        val m = map(
            listOf(
                "...",
                ".##",
                ".#.",
            ),
        )
        // Au sud-est, l'herbe touche la tuile par la diagonale seulement : coin intérieur.
        assertEquals(N or W or SE, GroundEdges.mask(m, TilePos(1, 1)))
        // Le bord de la carte, à l'est de (2, 1) et au sud de (1, 2), ne compte pas.
        assertEquals(N or S, GroundEdges.mask(m, TilePos(2, 1)))
        assertEquals(E or W, GroundEdges.mask(m, TilePos(1, 2)))
        assertEquals(0, GroundEdges.mask(m, TilePos(0, 0)))
    }

    @Test
    fun `the map border is not an edge`() {
        val m = map(listOf("##", "##"))
        assertEquals(0, GroundEdges.mask(m, TilePos(0, 0)))
    }

    @Test
    fun `an inner corner is set only when both sides are the same ground`() {
        val m = map(
            listOf(
                "##.",
                "###",
                "###",
            ),
        )
        assertEquals(NE, GroundEdges.mask(m, TilePos(1, 1)))
        // La tuile (1, 0) borde déjà l'herbe à l'est : pas de coin en plus.
        assertEquals(E, GroundEdges.mask(m, TilePos(1, 0)))
    }

    @Test
    fun `a bridge runs east west when water borders it north or south`() {
        val m = map(
            listOf(
                "~~~",
                "#=#",
                "~~~",
            ),
        )
        assertTrue(GroundEdges.bridgeRunsEastWest(m, TilePos(1, 1)))
        assertEquals(N or S, GroundEdges.mask(m, TilePos(1, 1)))
        // L'eau ne fait pas de berge contre le pont, seulement aux coins qui touchent le chemin.
        assertEquals(SE or SW, GroundEdges.mask(m, TilePos(1, 0)))
    }

    @Test
    fun `the ground is painted deterministically, with the edge colours of its neighbours`() {
        val m = map(
            listOf(
                "...",
                ".#.",
                ".~.",
            ),
        )
        val palette = GroundPalette.Default
        val first = GroundPainter.paint(m, palette)
        val second = GroundPainter.paint(m, palette)
        assertEquals(48, first.width)
        assertEquals(48, first.height)
        assertContentEquals(first.pixels, second.pixels)

        val t = GroundPainter.TILE
        // Chemin : la ligne du haut prend le sombre de l'herbe ; le coin extérieur s'arrondit.
        assertEquals(palette.grass.dark, first[t + 5, t])
        assertEquals(palette.grass.base, first[t, t])
        // Eau : berge puis écume, le long de l'herbe à l'ouest.
        assertEquals(palette.outline, first[t, 2 * t + 8])
        assertEquals(palette.water.light, first[t + 1, 2 * t + 8])
        // Toutes les couleurs viennent de la palette.
        val allowed = with(palette) {
            listOf(grass, path, plaza, water, bridge).flatMap { listOf(it.light, it.base, it.dark) } + outline
        }.toSet()
        assertTrue(first.pixels.all { it in allowed })
    }

    @Test
    fun `palette roles override the defaults`() {
        val custom = GroundPalette.Default.withRoles(mapOf("water.base" to 1, "outline" to 2))
        assertEquals(1, custom.water.base)
        assertEquals(2, custom.outline)
        assertEquals(GroundPalette.Default.water.light, custom.water.light)
        assertEquals(16, GroundPalette.ROLES.size)
    }
}
