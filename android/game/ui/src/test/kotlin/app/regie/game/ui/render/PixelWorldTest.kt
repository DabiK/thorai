package app.regie.game.ui.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.World
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.demo.DemoBottomScreen
import app.regie.game.ui.demo.DemoTopScreen
import app.regie.game.ui.fixtureAssets
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class PixelWorldTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val roof = RoofColors(light = 0xFFE5A669.toInt(), base = 0xFFDB6633.toInt(), shadow = 0xFF9E5A37.toInt())

    private fun world(): World {
        val map = TileMap(40, 20)
        for (x in 0 until 40) map.setGround(TilePos(x, 9), Ground.PATH)
        for (y in 0 until 4) for (x in 30 until 40) map.setGround(TilePos(x, y), Ground.WATER)
        return World(
            map,
            listOf(
                Placed("maison.test", TilePos(5, 5), roof),
                Placed("inconnu", TilePos(20, 10)),
            ),
        )
    }

    private fun render(assets: GameAssets, time: Long, onProjection: (WorldProjection) -> Unit): Bitmap {
        val clock = mutableLongStateOf(time)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameTheme(clock = clock) {
                    PixelWorld(
                        world(),
                        assets,
                        focus = ArtPoint(240, 135),
                        modifier = Modifier.fillMaxSize(),
                        sprites = listOf(Sprite("arbre", ArtPoint(200, 150))),
                    ) { onProjection(it) }
                }
            }
            return snapshot(host.get())
        }
    }

    /** Un pixel du dessin couvre exactement 4 × 4 pixels de l'écran, tous de la même couleur. */
    private fun Bitmap.artPixel(projection: WorldProjection, x: Int, y: Int): Int {
        val left = projection.toScreenX(x)
        val top = projection.toScreenY(y)
        val colours = (0 until 4).flatMap { dy -> (0 until 4).map { dx -> getPixel(left + dx, top + dy) } }.toSet()
        assertEquals(1, colours.size, "pixel ($x, $y) flou : $colours")
        return colours.single()
    }

    @Test
    fun `the world is drawn at scale 4, crisp, with session roofs, animations and placeholders`() {
        val assets = fixtureAssets()
        var projection: WorldProjection? = null
        val image = render(assets, time = 600) { projection = it }
        val p = assertNotNull(projection)
        assertEquals(4, p.scale)
        // Centrée sur (240, 135) et bornée au monde : jamais de coin négatif dans un monde plus grand que la vue.
        assertEquals(0, p.origin.x)
        assertTrue(p.origin.y in 0..135)

        val ground = GroundPainter.paint(world().map, assets.palette)
        listOf(300 to 200, 301 to 200, 100 to 40, 250 to 150, 470 to 30).forEach { (x, y) ->
            assertEquals(ground[x, y], image.artPixel(p, x, y), "sol en ($x, $y)")
        }
        // La maison : ancrage (16, 23) au milieu du bas de son empreinte 2 × 1, en (96, 95).
        assertEquals(roof.light, image.artPixel(p, 82, 73))
        assertEquals(roof.base, image.artPixel(p, 82, 77))
        assertEquals(roof.shadow, image.artPixel(p, 82, 81))
        assertEquals(0xFFF1D8A6.toInt(), image.artPixel(p, 82, 90))
        // L'arbre animé : deuxième image à 600 ms.
        assertEquals(0xFF1B5F53.toInt(), image.artPixel(p, 195, 140))
        // L'objet inconnu : forme de remplacement, cadre de la couleur de contour.
        assertEquals(assets.palette.outline, image.artPixel(p, 320, 160))

        val first = render(assets, time = 0) {}
        assertEquals(0xFF22944C.toInt(), first.artPixel(p, 195, 140))
    }

    @Test
    fun `an atlas image outside the world is drawn at its own scale, roof included`() {
        val assets = fixtureAssets()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameTheme { SpriteImage("maison.test", assets, scale = 2, roof = roof) }
            }
            val image = snapshot(host.get())
            // 32 × 24 pixels natifs agrandis ×2 : toit clair en haut, mur en bas.
            assertEquals(roof.light, image.getPixel(6, 2))
            assertEquals(roof.light, image.getPixel(7, 3))
            assertEquals(0xFFF1D8A6.toInt(), image.getPixel(63, 47))
        }
    }

    @Test
    fun `the demo scene renders on the top screen without any atlas`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameTheme { DemoTopScreen(GameAssets.Placeholder) } }
            val top = snapshot(host.get())
            assertTrue(top.width in 1918..1921, "écran du haut : ${top.width}")
            save(top, "demo-haut.png")
        }
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `the kit demo renders on the bottom screen`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameTheme { DemoBottomScreen() } }
            val bottom = snapshot(host.get())
            assertTrue(bottom.width in 1238..1241, "écran du bas : ${bottom.width}")
            save(bottom, "demo-bas.png")
        }
    }

    /** Le contenu de la fenêtre, dessiné en logiciel : sans dépendre du rendu matériel du banc de test. */
    private fun snapshot(activity: ComponentActivity): Bitmap {
        compose.waitForIdle()
        val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** Les rendus restent dans `build/` pour les regarder ; ils ne sont pas versionnés. */
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/game-demo").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
