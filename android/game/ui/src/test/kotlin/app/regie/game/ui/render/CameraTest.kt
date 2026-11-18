package app.regie.game.ui.render

import androidx.compose.ui.unit.IntOffset
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.atlas.Footprint
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraTest {
    // L'écran du haut : 480 × 270 pixels du dessin.
    private val view = 480 to 270

    @Test
    fun `the camera centres on the focus inside a large world`() {
        val origin = Camera.topLeft(ArtPoint(800, 400), 1600, 800, view.first, view.second)
        assertEquals(ArtPoint(560, 265), origin)
    }

    @Test
    fun `the camera never shows beyond the world edges`() {
        assertEquals(ArtPoint(0, 0), Camera.topLeft(ArtPoint(10, 10), 1600, 800, view.first, view.second))
        assertEquals(ArtPoint(1120, 530), Camera.topLeft(ArtPoint(1590, 790), 1600, 800, view.first, view.second))
    }

    @Test
    fun `a world smaller than the view is centred`() {
        assertEquals(ArtPoint(-80, -55), Camera.topLeft(ArtPoint(0, 0), 320, 160, view.first, view.second))
    }

    @Test
    fun `a tile centre and its screen position follow the scale`() {
        assertEquals(ArtPoint(40, 24), Camera.center(TilePos(2, 1)))
        val projection = WorldProjection(ArtPoint(16, 0), scale = 4)
        assertEquals(IntOffset(96, 64), projection.toScreen(ArtPoint(40, 16)))
        assertEquals(IntOffset((40 - 16) * 4, 16 * 4), projection.tileTop(TilePos(2, 1)))
    }

    @Test
    fun `objects stand on the bottom middle of their footprint and are drawn from back to front`() {
        val house = Placed("maison", TilePos(2, 5))
        assertEquals(ArtPoint(2 * 16 + 48, 6 * 16 - 1), WorldLayout.footOf(house, Footprint(6, 4)))

        val footprints = mapOf("maison" to Footprint(6, 4))
        val order = WorldLayout.drawOrder(
            listOf(house, Placed("arbre", TilePos(10, 2)), Placed("arbre", TilePos(0, 5))),
            listOf(Sprite("coursier", ArtPoint(60, 100))),
        ) { footprints[it.objectId] ?: Footprint(1, 1) }
        assertEquals(
            listOf("arbre" to ArtPoint(168, 47), "arbre" to ArtPoint(8, 95), "maison" to ArtPoint(80, 95), "coursier" to ArtPoint(60, 100)),
            order.map { it.id to it.foot },
        )
    }
}
