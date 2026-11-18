package app.regie.game.ui.village

import android.app.Application
import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.village.PixelPoint
import app.regie.game.engine.village.PixelRect
import app.regie.game.ui.atlas.AssetJson
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.Recolor
import app.regie.game.ui.atlas.WindowLight
import app.regie.game.ui.atlas.houseCatalog
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.HouseEffects
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** La règle des panneaux et des bulles, la vie des maisons et ce que l'atlas en donne (ticket 52). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VillageOverlayTest {
    private val assets by lazy { GameAssetLoader.load(RuntimeEnvironment.getApplication().assets) }

    private fun place(anchorX: Int, below: Int, above: Int, view: Int = 1000) = SignPlacement.place(
        anchorX = anchorX, below = below, above = above,
        panelWidth = 200, panelHeight = 100, arrowWidth = 16, arrowHeight = 12,
        viewWidth = view, viewHeight = 600, margin = 8,
    )

    @Test
    fun `the house panel hangs under the door, inside the view, its pointer facing the house`() {
        val middle = place(anchorX = 500, below = 300, above = 100)
        assertTrue(middle.pointsUp)
        assertEquals(300, middle.arrowY)
        assertEquals(312, middle.panelY)
        assertEquals(400, middle.panelX)
        assertEquals(492, middle.arrowX)

        // Contre le bord gauche : le panneau est retenu, la pointe reste en face de la porte.
        val left = place(anchorX = 20, below = 300, above = 100)
        assertEquals(8, left.panelX)
        assertTrue(left.arrowX >= left.panelX && left.arrowX + 16 <= left.panelX + 200)

        // Contre le bord droit.
        val right = place(anchorX = 995, below = 300, above = 100)
        assertEquals(1000 - 8 - 200, right.panelX)
        assertTrue(right.arrowX + 16 <= right.panelX + 200)
    }

    @Test
    fun `without room under the door, the panel goes above the roof, never off screen`() {
        val above = place(anchorX = 500, below = 560, above = 300)
        assertFalse(above.pointsUp)
        assertEquals(300 - 12, above.arrowY)
        assertEquals(300 - 12 - 100, above.panelY)

        // Ni dessous ni dessus : collé au bas de la vue, entier.
        val squeezed = place(anchorX = 500, below = 590, above = 40)
        assertTrue(squeezed.panelY + 100 <= 600 - 8)
        assertTrue(squeezed.arrowY >= 8)
    }

    @Test
    fun `each house of the village has its mark on the roof line and its doorstep`() {
        val village = DemoScene.village(8, assets)
        HouseMarks.of(village, assets).forEach { mark ->
            val house = mark.house
            assertTrue(mark.top < mark.bubble.y && mark.bubble.y < mark.bottom, "bulle de ${house.modelId}")
            assertEquals(house.entry.x * 16 + 8, mark.doorstep.x)
            assertTrue(mark.doorstep.y in house.entry.y * 16..(house.entry.y + 1) * 16)
            // La bulle n'est pas du côté de la cheminée.
            val chimney = assertNotNull(assets.objectSpec(house.modelId)?.chimney)
            val frame = assertNotNull(assets.resolve(house.modelId)).frame
            val left = house.footprint.left * 16 + house.footprint.width * 8 - frame.ax
            assertTrue((chimney.x < frame.w / 2) == (mark.bubble.x - left > frame.w / 2), house.modelId)
        }
        assertEquals(village.houses.size, village.houseStates().size)
    }

    @Test
    fun `working houses smoke and flicker, waiting ones light their door, resting ones go dark`() {
        assertEquals(WindowLight.LIT, HouseEffects.light(TagState.WORKING))
        assertEquals(WindowLight.LIT, HouseEffects.light(TagState.WAITING))
        assertEquals(WindowLight.DARK, HouseEffects.light(TagState.IDLE))
        assertEquals(WindowLight.DARK, HouseEffects.light(TagState.FAILED))
        assertTrue(HouseEffects.smokes(TagState.WORKING) && HouseEffects.flickers(TagState.WORKING))
        assertFalse(HouseEffects.smokes(TagState.IDLE) || HouseEffects.smokes(TagState.WAITING))
        assertTrue(HouseEffects.doorGlows(TagState.WAITING) && !HouseEffects.doorGlows(TagState.WORKING))
        // La fumée monte, grossit et pâlit ; les panaches se suivent.
        val puffs = (0 until HouseEffects.SMOKE_PUFFS).map { HouseEffects.puff(0, 0, it) }
        assertEquals(puffs.size, puffs.toSet().size)
        assertTrue(HouseEffects.puffDy(900) < HouseEffects.puffDy(100))
        assertTrue(HouseEffects.puffFrame(900) > HouseEffects.puffFrame(100))
        assertTrue(HouseEffects.puffAlpha(900) < HouseEffects.puffAlpha(100))
        assertTrue(HouseEffects.puffDx(900, -1) < 0 && HouseEffects.puffDx(900, 1) > 0)
    }

    @Test
    fun `windows switch only their glass, inside their rectangles`() {
        val yellow = AssetJson.color("#DDDC2C")
        val pale = AssetJson.color("#DCDA5F")
        val wall = AssetJson.color("#F1D8A6")
        val pixels = intArrayOf(yellow, pale, wall, yellow)
        Recolor.windows(pixels, 2, listOf(PixelRect(0, 0, 2, 1)), WindowLight.DARK)
        assertEquals(listOf(AssetJson.color("#5A718C"), AssetJson.color("#718EA0"), wall, yellow), pixels.toList())
        val lit = intArrayOf(yellow, pale)
        Recolor.windows(lit, 2, listOf(PixelRect(0, 0, 2, 1)), WindowLight.LIT)
        assertEquals(listOf(yellow, pale), lit.toList())
    }

    @Test
    fun `the house atlas gives chimneys and windows, moved to the engine's footprint frame`() {
        val spec = assertNotNull(assets.objectSpec("maison.chaumiere"))
        assertEquals(PixelPoint(22, 2), spec.chimney)
        assertEquals(3, spec.windows.size)
        val model = assertNotNull(assets.houseCatalog()?.get("maison.chaumiere"))
        val frame = assertNotNull(assets.resolve("maison.chaumiere")).frame
        // Image de 88 × 85, ancre (44, 84), empreinte 6 × 4 : le coin de l'image est en (4, -21).
        assertEquals(PixelPoint(22 + 48 - frame.ax, 2 + 63 - frame.ay), model.chimney)
        assertEquals(spec.windows.size, model.windows.size)
        // Toutes les maisons de l'atlas ont une cheminée et des fenêtres.
        assets.houseCatalog()!!.models.forEach { assertTrue(it.chimney != null && it.windows.isNotEmpty(), it.id) }
    }

    @Test
    fun `every decor key of the village is in the atlas, with the same footprint`() {
        val decor = DecorCatalog.DEFAULT
        decor.all.forEach { id ->
            assertNotNull(assets.resolve(id), id)
            val atlas = assets.objectSpec(id)?.footprint
            if (atlas != null) {
                assertEquals(atlas.w to atlas.h, decor.sizeOf(id).width to decor.sizeOf(id).height, id)
            }
        }
        // Les bulles d'état et la fumée sont dans l'atlas des icônes.
        listOf("bulle.travail", "bulle.attente", "sommeil", "fumee").forEach { assertNotNull(assets.animation(it), it) }
    }
}
