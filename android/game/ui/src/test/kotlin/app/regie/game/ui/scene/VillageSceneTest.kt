package app.regie.game.ui.scene

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
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.pathfinding.findPath
import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.houseCatalog
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.render.GroundCache
import app.regie.game.ui.render.GroundKey
import app.regie.game.ui.render.PixelWorld
import app.regie.game.ui.render.Sprite
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.render.fallbacks
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** La scène du hameau avec les vrais atlas, sur l'écran du haut de la Thor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class VillageSceneTest {
    @get:Rule val compose = createEmptyComposeRule()

    // Horloge du thème figée : les animations du décor restent à leur première image.
    private val frozen = mutableLongStateOf(0L)

    private val assets: GameAssets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)

    private fun village(sessions: Int): Village {
        val generator = VillageGenerator(InMemoryVillageLayoutStore(), catalog = assets.houseCatalog()!!)
        return generator.generate(Overview.of(DemoScene.sessions(sessions), emptyList(), BridgeStatus.Connected))
    }

    private fun host(village: Village): ComponentActivity {
        // Le sol se peint hors du fil principal ; le test le prépare pour le premier dessin.
        GroundCache.prepare(GroundKey.of(village.world.map), assets.palette)
        return Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    }

    @Test
    fun `the atlases are all there`() {
        assertEquals(emptyList(), assets.problems)
    }

    @Test
    fun `the courier is drawn in depth order, exactly as a still sprite of the world`() {
        val village = village(3)
        // Une tuile libre juste au nord d'un buisson : le buisson doit cacher ses pieds, pas sa tête.
        val spot = village.decorations
            .filter { it.placed.objectId in DecorCatalog.DEFAULT.bushes }
            .map { it.footprint.bottomLeft.offset(0, -1) }
            .filter { findPath(village.world.map, village.spawn, it) != null }
            .minBy { abs(it.x - village.spawn.x) + abs(it.y - village.spawn.y) }
        val state = VillageSceneState(village, start = spot)
        val foot = state.foot
        val focus = state.cameraFocus

        val scene = host(village)
        scene.setContent {
            GameTheme(clock = frozen) { VillageScene(state, assets, Modifier.fillMaxSize()) }
        }
        val expected = host(village)
        expected.setContent {
            GameTheme(clock = frozen) {
                PixelWorld(
                    village.world,
                    assets,
                    { focus },
                    Modifier.fillMaxSize(),
                    sprites = listOf(Sprite(CourierLook.idOf(CourierAction.IDLE, Direction.DOWN), foot)),
                    fallbacks = village.fallbacks(),
                )
            }
        }
        val a = snapshot(scene)
        val b = snapshot(expected)
        val differing = (0 until a.height step 2).sumOf { y -> (0 until a.width step 2).count { x -> a.getPixel(x, y) != b.getPixel(x, y) } }
        assertEquals(0, differing, "pixels différents")
        save(a, "44-derriere-buisson.png")
    }

    @Test
    fun `walking moves the camera smoothly and the iris closes on the door before entering`() {
        val village = village(8)
        val state = VillageSceneState(village)
        val activity = host(village)
        var projection: WorldProjection? = null
        val entered = mutableListOf<SessionId>()
        compose.mainClock.autoAdvance = false
        activity.setContent {
            GameTheme(clock = frozen) {
                VillageScene(state, assets, Modifier.fillMaxSize(), onEnterHouse = { entered += it }) { projection = it }
            }
        }
        compose.waitForIdle()
        save(snapshot(activity), "44-depart.png")
        val p = assertNotNull(projection)
        val start = p.origin

        // Une maison à l'est : la caméra suit le coursier, pixel par pixel.
        val target = village.houses.filter { it.entry.x > village.spawn.x + 6 }.minBy { it.entry.x }
        state.send(CourierCommand.GoTo(target.session.id))
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val walking = snapshot(activity)
        assertNotEquals(start, p.origin, "la caméra a suivi ${state.foot} ${village.spawn} ${state.action} ${state.destination}")
        assertEquals(CourierAction.WALK, state.action)
        save(walking, "44-marche.png")

        var guard = 0
        while (state.entering == null && guard++ < 400) compose.mainClock.advanceTimeBy(16)
        assertEquals(target.session.id, state.entering)
        assertEquals(TilePos(target.entry.x, target.entry.y), TilePos(state.foot.x / 16, state.foot.y / 16))
        assertTrue(entered.isEmpty(), "l'app attend la fin de l'iris")
        compose.mainClock.advanceTimeBy(IRIS_MILLIS / 2L)
        save(snapshot(activity), "44-iris.png")
        compose.mainClock.advanceTimeBy(IRIS_MILLIS.toLong())
        compose.waitForIdle()
        assertEquals(listOf(target.session.id), entered)

        // Retour : le coursier ressort, face au hameau, et l'iris se rouvre.
        state.leaveHouse()
        compose.mainClock.advanceTimeBy(IRIS_MILLIS / 2L)
        save(snapshot(activity), "44-sortie-iris.png")
        assertEquals(true, state.leaving)
        compose.mainClock.advanceTimeBy(IRIS_MILLIS * 4L)
        compose.waitForIdle()
        assertEquals(false, state.leaving)
        assertEquals(Direction.DOWN, state.facing)
        save(snapshot(activity), "44-sortie.png")
    }

    private fun snapshot(activity: ComponentActivity): Bitmap {
        compose.waitForIdle()
        val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** Les rendus restent dans `build/` ; ceux de `docs/images/jeu/44-*.png` en sont des copies. */
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/game-demo").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
