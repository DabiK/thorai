package app.regie.game.ui.village

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.semantics.SemanticsProperties
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.houseCatalog
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.GroundCache
import app.regie.game.ui.render.GroundKey
import app.regie.game.ui.scene.VillageSceneState
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Le hameau et son carnet (ticket 45), avec les vrais atlas, aux tailles des écrans de la Thor. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VillageScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val frozen = mutableLongStateOf(0L)
    private val assets: GameAssets = GameAssetLoader.load(RuntimeEnvironment.getApplication().assets)

    private val keys = mutableListOf<CarnetKey>()
    private val triggers = mutableListOf<Pair<CarnetTrigger, Boolean>>()
    private val goTo = mutableListOf<SessionId>()
    private val callbacks = object : CarnetCallbacks {
        override fun onKey(key: CarnetKey) { keys += key }
        override fun onTrigger(trigger: CarnetTrigger, held: Boolean) { triggers += trigger to held }
        override fun onGoTo(session: SessionId) { goTo += session }
        override fun onSetup() { keys += CarnetKey.START }
        override fun onVoiceSettings() {}
        override fun onQuit() {}
        override fun onConfirmQuit() {}
        override fun onCancelQuit() {}
    }

    private val sessions = DemoScene.sessions(5)

    /** Un carnet d'exemple, tel que la racine de composition le rédige. */
    private fun carnet(quit: CarnetQuit? = null) = Carnet(
        header = CarnetHeader(
            count = "4 sessions actives",
            versions = listOf(CarnetVersion("claude 2.1.322", true), CarnetVersion("codex 0.167.2", true)),
        ),
        mic = CarnetMic("Maintiens L2 et parle"),
        approval = CarnetApproval(SessionId("demo-1"), InhabitantKind.BRICOLEUR, "Codex attend ta validation", "api-meteo · migration", "npm run db:migrate"),
        rows = sessions.map { session ->
            CarnetRow(
                session = session.id,
                agent = session.agent,
                inhabitant = InhabitantKind.of(session.agent),
                project = session.project,
                title = session.title,
                branch = if (session.id.value == "demo-0") "ticket/22-tri" else null,
                state = TagState.of(session.status),
                stopped = false,
                step = session.progress?.let { "étape ${it.step} sur ${it.total}" },
                detail = when (session.id.value) {
                    "demo-1" -> "Commande à autoriser : npm run db:migrate"
                    "demo-0" -> "› Modification · Overview.kt · terminée"
                    else -> null
                },
                detailTone = if (session.id.value == "demo-1") CarnetTone.WAIT else CarnetTone.MUTED,
                age = "6 min",
            )
        },
        quit = quit,
    )

    private fun village(count: Int): Village =
        VillageGenerator(InMemoryVillageLayoutStore(), catalog = assets.houseCatalog()!!)
            .generate(Overview.of(DemoScene.sessions(count), emptyList(), BridgeStatus.Connected))

    private lateinit var activity: ComponentActivity

    private fun show(village: Village? = null, content: @Composable () -> Unit) {
        // Le sol se peint hors du fil principal ; le test le prépare pour le premier dessin.
        village?.let { GroundCache.prepare(GroundKey.of(it.world.map), assets.palette) }
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { GameTheme(clock = frozen) { content() } }
        compose.waitForIdle()
    }

    /** L'écran tel que dessiné, en vrais pixels (graphismes natifs de Robolectric). */
    private fun snapshot(): Bitmap {
        compose.waitForIdle()
        val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
    fun `the carnet puts the pending approval first, then one line per session, then the actions`() {
        show { CarnetBottomScreen(carnet(), focused = SessionId("demo-2"), atDoor = false, assets, callbacks) }

        val approval = compose.onNodeWithTag("carnet-approval").assertIsDisplayed().getUnclippedBoundsInRoot()
        val firstRow = compose.onNodeWithTag("carnet-row-demo-0").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(approval.bottom <= firstRow.top, "la validation d'abord")
        compose.onNodeWithText("! Codex attend ta validation").assertIsDisplayed()
        compose.onNodeWithText("npm run db:migrate").assertIsDisplayed()
        // Ce que montrait la liste classique : projet, branche, session et agent, état, étape, action, âge.
        compose.onNodeWithText("ticket/22-tri").assertIsDisplayed()
        compose.onNodeWithText("tri des sessions · Claude").assertIsDisplayed()
        compose.onNodeWithText("› Modification · Overview.kt · terminée").assertIsDisplayed()
        compose.onNodeWithText("étape 3 sur 5 · 6 min").assertIsDisplayed()
        compose.onNodeWithTag("carnet-row-demo-2").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithTag("carnet-row-demo-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        listOf("Y aller", "Agents", "Bâtir", "Console", "Tout mettre en pause", "Dicter", "Donner un ordre", "Changer de maison", "Configurer", "Régler la voix", "Quitter")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        save(snapshot(), "45-carnet.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
    fun `the carnet is tactile, a touched line sends the courier there`() {
        show { CarnetBottomScreen(carnet(), focused = null, atDoor = true, assets, callbacks) }

        compose.onNodeWithTag("carnet-row-demo-2").performClick()
        compose.onNodeWithTag("carnet-approval").performClick()
        listOf("Entrer", "Agents", "Bâtir", "Console", "Tout mettre en pause").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Dicter").performTouchInput { down(center); up() }

        assertEquals(listOf(SessionId("demo-2")), goTo)
        assertEquals(listOf(CarnetKey.R1, CarnetKey.A, CarnetKey.B, CarnetKey.X, CarnetKey.Y, CarnetKey.START), keys)
        assertEquals(listOf(CarnetTrigger.L2 to true, CarnetTrigger.L2 to false), triggers)
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
    fun `an empty village says how to build, quitting asks first`() {
        show {
            CarnetBottomScreen(
                carnet(CarnetQuit.Confirm("Une session travaille encore. Quitter quand même ?", "Quitter", "Annuler"))
                    .copy(rows = emptyList(), approval = null, empty = "Aucune session. Bâtis-en une avec X."),
                focused = null, atDoor = false, assets, callbacks,
            )
        }
        compose.onNodeWithTag("carnet-empty").assertIsDisplayed()
        compose.onNodeWithText("Annuler").assertIsDisplayed()
        save(snapshot(), "45-quitter.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `the village shows each house state and the panel of the nearest house`() {
        val village = village(5)
        val scene = VillageSceneState(village)
        val nearest = assertNotNull(nearestHouse(village, scene.courierTile))
        show(village) { VillageTopScreen(scene, assets, onEnterHouse = {}) }

        val house = village.houseOf(nearest)!!.session
        compose.onNodeWithTag("house-panel").assertIsDisplayed()
        compose.onNodeWithText(house.project).assertIsDisplayed()
        village.houses.filter { it.session.id != nearest }.forEach {
            compose.onNodeWithTag("house-state-${it.session.id.value}").assertExists()
        }
        compose.onNodeWithTag("enter-hint").assertDoesNotExist()
        save(snapshot(), "45-hameau.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `in front of a door, A Entrer rises above the courier`() {
        val village = village(5)
        val scene = VillageSceneState(village)
        val target = village.houses.first { it.session.status == app.regie.domain.model.SessionStatus.AWAITING_APPROVAL }.session.id
        compose.mainClock.autoAdvance = false
        show(village) { VillageTopScreen(scene, assets, onEnterHouse = {}) }
        scene.send(CourierCommand.GoTo(target, enter = false))
        var guard = 0
        while (scene.facingDoor == null && guard++ < 600) compose.mainClock.advanceTimeBy(16)
        compose.waitForIdle()

        assertEquals(target, scene.facingDoor)
        compose.onNodeWithTag("enter-hint").assertIsDisplayed()
        compose.onNodeWithText("Attend ta validation", substring = true).assertIsDisplayed()
        save(snapshot(), "45-porte.png")
    }

    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
    fun `on a single screen, the compact carnet sits under the village`() {
        val village = village(5)
        val scene = VillageSceneState(village)
        show(village) {
            VillageTopScreen(scene, assets, onEnterHouse = {}, carnet = {
                CompactCarnet(carnet(), focused = nearestHouse(village, scene.courierTile), atDoor = false, assets, callbacks)
            })
        }
        val approval = compose.onNodeWithTag("carnet-approval").assertIsDisplayed().getUnclippedBoundsInRoot()
        val world = compose.onNodeWithTag("house-panel").getUnclippedBoundsInRoot()
        assertTrue(world.bottom <= approval.top, "le hameau au-dessus du carnet")
        compose.onNodeWithText("Bâtir").assertIsDisplayed()
        compose.onNodeWithText("Dicter").assertIsDisplayed()
        compose.onNodeWithText("4 sessions actives").assertIsDisplayed()
        compose.onNodeWithTag("carnet-quit").assertIsDisplayed()
        save(snapshot(), "45-un-ecran.png")
    }

    /** Les rendus restent dans `build/` ; ceux de `docs/images/jeu/45-*.png` en sont des copies. */
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/game-demo").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
