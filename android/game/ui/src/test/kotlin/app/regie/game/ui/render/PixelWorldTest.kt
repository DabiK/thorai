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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.World
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.Footprint
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.demo.DemoBottomScreen
import app.regie.game.ui.demo.DemoKitScreen
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.demo.DemoTopScreen
import app.regie.game.ui.fixtureAssets
import app.regie.game.ui.theme.GameTheme
import app.regie.game.ui.village.VillageOverlay
import app.regie.game.ui.village.houseStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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
                Placed("maison.sans.atlas", TilePos(10, 13), roof),
            ),
        )
    }

    private val houseFallback = Fallback(FallbackKind.HOUSE, Footprint(6, 4), doorColumn = 3)

    private fun render(assets: GameAssets, time: Long, onProjection: (WorldProjection) -> Unit): Bitmap {
        val world = world()
        // Le sol se peint hors du fil principal ; le test le prépare pour le premier dessin.
        GroundCache.prepare(GroundKey.of(world.map), assets.palette)
        val clock = mutableLongStateOf(time)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameTheme(clock = clock) {
                    PixelWorld(
                        world,
                        assets,
                        focus = { ArtPoint(240, 135) },
                        modifier = Modifier.fillMaxSize(),
                        sprites = listOf(Sprite("arbre", ArtPoint(200, 150))),
                        fallbacks = mapOf(world.objects[2] to houseFallback),
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
        listOf(300 to 200, 301 to 200, 100 to 40, 280 to 150, 470 to 30).forEach { (x, y) ->
            assertEquals(ground[x, y], image.artPixel(p, x, y), "sol en ($x, $y)")
        }
        // La maison : ancrage (16, 23) au milieu du bas de son empreinte 2 × 1, en (96, 95).
        assertEquals(roof.light, image.artPixel(p, 82, 73))
        assertEquals(roof.base, image.artPixel(p, 82, 77))
        assertEquals(roof.shadow, image.artPixel(p, 82, 81))
        assertEquals(0xFFF1D8A6.toInt(), image.artPixel(p, 82, 90))
        // L'arbre animé : deuxième image à 600 ms.
        assertEquals(0xFF1B5F53.toInt(), image.artPixel(p, 195, 140))
        // L'objet inconnu sans repli : un bloc d'une tuile, contour de pierre.
        assertEquals(0xFF605751.toInt(), image.artPixel(p, 320, 160))

        val first = render(assets, time = 0) {}
        assertEquals(0xFF22944C.toInt(), first.artPixel(p, 195, 140))
    }

    @Test
    fun `without an atlas, a house is a readable block of its engine footprint, roof and door included`() {
        val assets = fixtureAssets()
        var projection: WorldProjection? = null
        val image = render(assets, time = 0) { projection = it }
        val p = assertNotNull(projection)
        // Empreinte 6 × 4 tuiles ancrée en (10, 13) : x de 160 à 255, y de 160 à 223 (et le toit au-dessus).
        val sprite = PlaceholderPainter.paint(houseFallback, roof)
        assertEquals(96, sprite.image.width)
        assertEquals(64 + 10, sprite.image.height)
        val left = 160
        val top = 223 - sprite.ay
        val roofColours = setOf(roof.light, roof.base, roof.shadow)
        assertTrue((0 until 40).any { y -> image.artPixel(p, left + 48, top + y) in roofColours }, "toit")
        // La porte, dans la colonne 3 de l'empreinte.
        assertTrue((0 until 74).any { y -> image.artPixel(p, left + 3 * 16 + 5, top + y) == 0xFF9E5A37.toInt() }, "porte")
        // Le bloc couvre toute la largeur de l'empreinte.
        val ground = GroundPainter.paint(world().map, assets.palette)
        assertTrue(image.artPixel(p, left + 2, 218) != ground[left + 2, 218])
        assertTrue(image.artPixel(p, left + 93, 218) != ground[left + 93, 218])
    }

    @Test
    fun `the ground key follows the content, not the map instance, and the cache paints once`() {
        val a = world().map
        val b = world().map
        assertEquals(GroundKey.of(a), GroundKey.of(b))
        b.setGround(TilePos(0, 0), Ground.WATER)
        assertTrue(GroundKey.of(a) != GroundKey.of(b))
        GroundCache.clear()
        val palette = GroundPalette.Default
        val painted = GroundCache.prepare(GroundKey.of(a), palette)
        assertSame(painted, GroundCache.prepare(GroundKey.of(world().map), palette))
        assertNull(GroundCache.peek(GroundKey.of(b), palette))
    }

    @Test
    fun `the real atlases load and give the village its houses and decor`() {
        val assets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)
        assertEquals(GameAssetLoader.ATLASES, assets.atlases.map { it.name }, assets.problems.toString())
        assertTrue(assets.problems.isEmpty(), assets.problems.toString())
        for (count in listOf(3, 8)) {
            val village = DemoScene.village(count, assets)
            assertEquals(count, village.houses.size)
            village.houses.forEach { house ->
                val sprite = assertNotNull(assets.resolve(house.modelId), "maison ${house.modelId}")
                assertEquals(3, sprite.roofKeys.size)
                val spec = assertNotNull(assets.objectSpec(house.modelId))
                assertEquals(house.footprint.width, spec.footprint.w)
                assertEquals(house.footprint.height, spec.footprint.h)
            }
            village.decorations.forEach { assertNotNull(assets.resolve(it.placed.objectId), it.placed.objectId) }
            // Chaque toit est repeint : plus aucune couleur clé, et les couleurs de la session à la place.
            village.houses.forEach { house ->
                val sprite = assertNotNull(assets.resolve(house.modelId))
                val image = sprite.atlas.recoloured(sprite.frameId, sprite.roofKeys, house.roof)
                val bitmap = image.asAndroidBitmap()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val keys = sprite.roofKeys.map { it and 0xFFFFFF }.toSet()
                assertTrue(pixels.none { (it ushr 24) != 0 && (it and 0xFFFFFF) in keys }, "clé restante sur ${house.modelId}")
                assertTrue(pixels.any { it == house.roof.base }, "toit de ${house.session.project} absent")
            }
        }
        with(DecorCatalog.DEFAULT) {
            (trees + bushes + flowers + rocks + listOf(fence, fountain, bench, lantern, sign)).forEach {
                assertNotNull(assets.resolve(it), it)
            }
        }
    }

    @Test
    fun `the demo village renders on the top screen with the real atlases`() {
        val assets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)
        val village = DemoScene.village(3, assets)
        GroundCache.prepare(GroundKey.of(village.world.map), assets.palette)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameTheme { DemoTopScreen(assets, sessions = 3) } }
            val top = snapshot(host.get())
            assertTrue(top.width in 1918..1921, "écran du haut : ${top.width}")
            save(top, "hameau-3-haut.png")
        }
    }

    @Test
    fun `the demo village renders without any atlas`() {
        val village = DemoScene.village(3, GameAssets.Placeholder)
        GroundCache.prepare(GroundKey.of(village.world.map), GameAssets.Placeholder.palette)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameTheme { DemoTopScreen(GameAssets.Placeholder, sessions = 3) } }
            save(snapshot(host.get()), "hameau-3-repli.png")
        }
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `the demo village and the kit render on the bottom screen`() {
        val assets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)
        val village = DemoScene.village(8, assets)
        GroundCache.prepare(GroundKey.of(village.world.map), assets.palette)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameTheme { DemoBottomScreen(assets, sessions = 8) } }
            val bottom = snapshot(host.get())
            assertTrue(bottom.width in 1238..1241, "écran du bas : ${bottom.width}")
            save(bottom, "hameau-8-bas.png")
        }
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameTheme { DemoKitScreen() } }
            save(snapshot(host.get()), "kit-bas.png")
        }
    }

    @Test
    @Config(qualifiers = "w3600dp-h2400dp-land-mdpi")
    fun `the whole village of 8 and 20 sessions fits a map view at scale 2, houses alive`() {
        val assets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)
        for (count in listOf(8, 20)) {
            val village = DemoScene.village(count, assets)
            GroundCache.prepare(GroundKey.of(village.world.map), assets.palette)
            val width = village.world.map.width * GroundPainter.TILE
            val height = village.world.map.height * GroundPainter.TILE
            val states = village.houseStates()
            Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
                host.setup().get().setContent {
                    GameTheme(clock = mutableLongStateOf(900)) {
                        PixelWorld(village.world, assets, { ArtPoint(width / 2, height / 2) }, Modifier.fillMaxSize(), scale = 2, states = states) {
                            VillageOverlay(village, assets, it)
                        }
                    }
                }
                val map = snapshot(host.get())
                assertTrue(map.width >= width * 2 && map.height >= height * 2, "carte ${width}×$height dans ${map.width}×${map.height}")
                save(map, "hameau-$count-carte.png")
            }
        }
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
