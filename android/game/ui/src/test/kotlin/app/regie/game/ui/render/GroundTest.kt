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
        val grass = with(palette) { setOf(grass.light, grass.base, grass.dark, grassDeep) + flowers }
        val stones = with(palette.stone) { setOf(light, base, dark) }
        // Chemin : l'herbe déborde sur le bord et dans le coin arrondi, puis vient le liseré.
        assertTrue(first[t, t] in grass, "coin du chemin")
        assertTrue(first[t + 8, t] in grass, "bord du chemin")
        assertTrue((0 until 6).any { first[t + 8, t + it] == palette.path.dark }, "liseré du chemin")
        assertTrue(first[t + 8, t + 8] in with(palette) { setOf(path.light, path.base, plaza.dark, stone.base) }, "milieu du chemin")
        // Eau : berge de pierres le long de l'herbe à l'ouest, puis l'eau.
        assertTrue((0 until 5).any { first[t + it, 2 * t + 8] in stones }, "berge")
        assertTrue(first[t + 7, 2 * t + 10] in with(palette) { setOf(water.base, water.light, water.dark, waterMid) })
        // Toutes les couleurs viennent de la palette.
        assertTrue(first.pixels.all { it in palette.all })
    }

    @Test
    fun `the grass is rich, three tones, tufts and small flowers in soft patches`() {
        val m = map(List(12) { ".".repeat(16) })
        val image = GroundPainter.paint(m, GroundPalette.Default)
        val counts = image.pixels.groupBy { it }.mapValues { it.value.size }
        with(GroundPalette.Default) {
            listOf(grass.light, grass.base, grass.dark, grassDeep).forEach { assertTrue((counts[it] ?: 0) > 20, "ton absent") }
            assertTrue(flowers.any { (counts[it] ?: 0) > 0 }, "pas de fleur")
            // Le fond domine : des plaques, pas un bruit uniforme.
            assertTrue((counts[grass.base] ?: 0) > image.pixels.size / 3)
        }
    }

    @Test
    fun `the plaza is made of rounded cobbles with grey joints`() {
        val m = map(List(3) { "ooo" })
        val image = GroundPainter.paint(m, GroundPalette.Default)
        val counts = image.pixels.groupBy { it }.mapValues { it.value.size }
        with(GroundPalette.Default) {
            assertTrue((counts[stone.base] ?: 0) > image.pixels.size / 6, "joints")
            assertTrue((counts[plaza.base] ?: 0) + (counts[plaza.light] ?: 0) > image.pixels.size / 3, "pavés clairs")
        }
    }

    @Test
    fun `edge distances round the outer corners and bite the inner ones`() {
        assertEquals(GroundEdges.FAR, GroundEdges.distance(0, 3, 3, 16, 6f))
        assertEquals(0.5f, GroundEdges.distance(GroundEdges.N, 8, 0, 16, 6f))
        // Coin extérieur : le pixel du coin est plus loin dans l'herbe qu'un pixel du bord droit.
        val corner = GroundEdges.distance(GroundEdges.N or GroundEdges.W, 0, 0, 16, 6f)
        assertTrue(corner < 0f)
        assertTrue(GroundEdges.distance(NE, 15, 0, 16, 6f) < 1f)
        assertTrue(GroundEdges.distance(NE, 0, 15, 16, 6f) > 10f)
    }

    @Test
    fun `palette roles override the defaults`() {
        val custom = GroundPalette.Default.withRoles(mapOf("water.base" to 1, "outline" to 2))
        assertEquals(1, custom.water.base)
        assertEquals(2, custom.outline)
        assertEquals(GroundPalette.Default.water.light, custom.water.light)
        assertEquals(25, GroundPalette.ROLES.size)
        assertEquals(3, GroundPalette.Default.withRoles(mapOf("flower.0" to 3)).flowers[0])
    }
}
