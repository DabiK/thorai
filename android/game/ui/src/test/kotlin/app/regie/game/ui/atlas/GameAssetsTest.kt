package app.regie.game.ui.atlas

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import app.regie.game.engine.world.RoofColors
import app.regie.game.ui.fixtureAssets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class GameAssetsTest {
    private val roof = RoofColors(light = 0xFFE5A669.toInt(), base = 0xFFDB6633.toInt(), shadow = 0xFF9E5A37.toInt())

    @Test
    fun `present atlases load, missing or broken ones are reported and skipped`() {
        val assets = fixtureAssets()
        assertEquals(listOf("maisons"), assets.atlases.map { it.name })
        assertEquals(0xFF63A53A.toInt(), assets.palette.grass.base)
        assertTrue(assets.problems.any { it.startsWith("decor invalide") }, assets.problems.toString())
        assertTrue("personnages absent : formes de remplacement" in assets.problems)
    }

    @Test
    fun `an id resolves to an object, an animation or a frame`() {
        val assets = fixtureAssets()
        val house = assertNotNull(assets.resolve("maison.test"))
        assertEquals("maison.test", house.frameId)
        assertEquals(Footprint(2, 1), house.footprint)
        assertEquals(3, house.roofKeys.size)
        assertEquals("arbre.0", assets.resolve("arbre", 0)?.frameId)
        assertEquals("arbre.1", assets.resolve("arbre", 600)?.frameId)
        assertEquals("arbre.1", assets.resolve("arbre.1")?.frameId)
        assertNull(assets.resolve("chantier.1"))
        assertEquals(Footprint(1, 1), assets.footprint("chantier.1"))
    }

    @Test
    fun `a roof is repainted once per session colours and keeps the walls`() {
        val atlas = fixtureAssets().atlases.single()
        val keys = atlas.spec.objects.getValue("maison.test").roofKeys
        val image = atlas.recoloured("maison.test", keys, roof)
        assertSame(image, atlas.recoloured("maison.test", keys, roof))
        assertEquals(1, atlas.cacheSize)
        val bitmap = image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        assertEquals(32, bitmap.width)
        assertEquals(roof.light, bitmap.getPixel(3, 1))
        assertEquals(roof.base, bitmap.getPixel(3, 5))
        assertEquals(roof.shadow, bitmap.getPixel(3, 9))
        assertEquals(0xFFF1D8A6.toInt(), bitmap.getPixel(3, 20))
        atlas.recoloured("maison.test", keys, roof.copy(base = 0xFF5A718C.toInt()))
        assertEquals(2, atlas.cacheSize)
    }

    @Test
    fun `without any atlas the assets are placeholders with the default palette`() {
        val none = fixtureAssets(names = emptyList())
        assertTrue(none.atlases.isEmpty())
        assertNull(GameAssets.Placeholder.resolve("maison.test"))
    }
}
