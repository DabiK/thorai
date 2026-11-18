package app.regie.game.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.ApprovalOption
import app.regie.domain.model.ApprovalOptionKind
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.DictationReview
import app.regie.domain.model.DiffBlock
import app.regie.domain.model.DiffFile
import app.regie.domain.model.DiffKind
import app.regie.domain.model.DiffLine
import app.regie.domain.usecase.DiffReview
import app.regie.domain.model.FloatingCard
import app.regie.domain.model.FloatingKind
import app.regie.domain.model.LineChanges
import app.regie.domain.model.Neighbours
import app.regie.domain.model.NewSessionField
import app.regie.domain.model.NewSessionForm
import app.regie.domain.model.Overview
import app.regie.domain.model.ProjectChoice
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionSettingsReview
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.VoiceSettings
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.engine.menu.GameMenu
import app.regie.game.engine.menu.MenuKey
import app.regie.game.engine.village.Construction
import app.regie.game.engine.village.ConstructionStage
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.ui.approval.ApprovalCallbacks
import app.regie.game.ui.approval.ApprovalNotebook
import app.regie.game.ui.approval.ApprovalScene
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.houseCatalog
import app.regie.game.ui.chantier.BuildCallbacks
import app.regie.game.ui.chantier.BuildKey
import app.regie.game.ui.chantier.NewSessionBottomScreen
import app.regie.game.ui.chantier.constructionLayer
import app.regie.game.ui.console.ConsoleTowerHeader
import app.regie.game.ui.console.PixelFloatingCard
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.diff.CorkBoardScreen
import app.regie.game.ui.diff.DiffCallbacks
import app.regie.game.ui.diff.DiffNotebook
import app.regie.game.ui.house.HouseBottomScreen
import app.regie.game.ui.house.HouseTopScreen
import app.regie.game.ui.house.InteriorScenes
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.RecordingCallbacks
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.house.live
import app.regie.game.ui.house.scrollInput
import app.regie.game.ui.house.session
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.menu.MainMenuScreen
import app.regie.game.ui.menu.PauseMenuBottom
import app.regie.game.ui.menu.PauseMenuTop
import app.regie.game.ui.menu.Samples
import app.regie.game.ui.menu.TitleTopScreen
import app.regie.game.ui.menu.Captures
import app.regie.game.ui.render.GroundCache
import app.regie.game.ui.render.GroundKey
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.theme.GameTheme
import app.regie.game.ui.village.Carnet
import app.regie.game.ui.village.CarnetApproval
import app.regie.game.ui.village.CarnetBottomScreen
import app.regie.game.ui.village.CarnetCallbacks
import app.regie.game.ui.village.CarnetHeader
import app.regie.game.ui.village.CarnetKey
import app.regie.game.ui.village.CarnetMic
import app.regie.game.ui.village.CarnetRow
import app.regie.game.ui.village.CarnetTone
import app.regie.game.ui.village.CarnetTrigger
import app.regie.game.ui.village.CarnetVersion
import app.regie.game.ui.village.VillageTopScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * Les écrans finals de la version jeu (ticket 51), un par geste du parcours : titre, hameau, maison,
 * validation, modifications, bâtir, menu de pause, courrier et console. Chacun vérifie qu'il est
 * bien l'écran du jeu ; avec `-PgameCaptures`, il est rangé sous `docs/images/jeu/51-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class FinalScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private val assets: GameAssets by lazy { GameAssetLoader.load(app.assets) }
    private val scenes by lazy { InteriorScenes.load(app.assets) }
    private lateinit var activity: ComponentActivity

    private fun show(village: Village? = null, content: @Composable () -> Unit) {
        // Le sol se peint hors du fil principal ; le test le prépare pour le premier dessin.
        village?.let { GroundCache.prepare(GroundKey.of(it.world.map), assets.palette) }
        val clock = mutableLongStateOf(0)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Sans la barre de titre de l'activité de test : l'écran entier, comme sur la Thor en plein écran.
        activity.actionBar?.hide()
        activity.setContent { GameTheme(clock = clock) { content() } }
        compose.waitForIdle()
    }

    private fun capture(name: String) = Captures.save(compose, activity, name, ticket = "51")

    // Le titre

    @Test
    fun `title top`() {
        show { TitleTopScreen(GameMenu.title(), Samples.overview, assets, singleScreen = false) }
        compose.onNodeWithText("Commencer").assertIsDisplayed()
        capture("titre-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `title bottom`() {
        show { MainMenuScreen(GameMenu.title(), Samples.overview) }
        compose.onNodeWithText("Continuer").assertIsDisplayed()
        capture("titre-bas")
    }

    // Le hameau

    private val hamletSessions = DemoScene.sessions(6)
    private val hamlet by lazy {
        VillageGenerator(InMemoryVillageLayoutStore(), catalog = assets.houseCatalog()!!)
            .generate(Overview.of(hamletSessions, emptyList(), BridgeStatus.Connected))
    }

    private val carnetCallbacks = object : CarnetCallbacks {
        override fun onKey(key: CarnetKey) {}
        override fun onTrigger(trigger: CarnetTrigger, held: Boolean) {}
        override fun onGoTo(session: SessionId) {}
        override fun onSetup() {}
        override fun onVoiceSettings() {}
        override fun onQuit() {}
        override fun onConfirmQuit() {}
        override fun onCancelQuit() {}
    }

    /** Le carnet tel que l'app le rédige (`rememberCarnet`), avec les textes de l'app. */
    private fun carnet(): Carnet {
        val pending = hamletSessions.firstOrNull { it.status == SessionStatus.AWAITING_APPROVAL }
        return Carnet(
            header = CarnetHeader(
                count = "${hamletSessions.size} sessions actives",
                versions = listOf(CarnetVersion("claude 2.1.322", true), CarnetVersion("codex 0.167.2", true)),
            ),
            mic = CarnetMic("Maintiens L2 et parle"),
            approval = pending?.let {
                CarnetApproval(it.id, InhabitantKind.of(it.agent), "${it.agent.label} attend ta validation",
                    listOfNotNull(it.project, it.title).joinToString(" · "), "npm run db:migrate")
            },
            rows = hamletSessions.map { session ->
                CarnetRow(
                    session = session.id, agent = session.agent, inhabitant = InhabitantKind.of(session.agent),
                    project = session.project, title = session.title, branch = null,
                    state = TagState.of(session.status), stopped = session.status == SessionStatus.STOPPED,
                    step = session.progress?.let { "étape ${it.step} sur ${it.total}" },
                    detail = if (session.id == pending?.id) "Commande à autoriser : npm run db:migrate" else null,
                    detailTone = if (session.id == pending?.id) CarnetTone.WAIT else CarnetTone.MUTED,
                    age = "6 min",
                )
            },
        )
    }

    @Test
    fun `hamlet top`() {
        val scene = VillageSceneState(hamlet)
        show(hamlet) { VillageTopScreen(scene, assets, onEnterHouse = {}) }
        compose.onNodeWithTag("village-top").assertIsDisplayed()
        capture("hameau-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `hamlet bottom`() {
        // La maison choisie : celle du haut de la liste, pour que la capture montre la liste sans défilement.
        val focused = hamletSessions.first().id
        show { CarnetBottomScreen(carnet(), focused, atDoor = false, assets, carnetCallbacks) }
        compose.onNodeWithText("Préparer la console").assertIsDisplayed()
        capture("hameau-bas")
    }

    // La maison

    @Test
    fun `house top`() {
        show { HouseTopScreen(live(), false, assets, scenes, ageLabel = "4 min", dictation = DictationReview()) }
        compose.onNodeWithTag("house-scene").assertIsDisplayed()
        capture("maison-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `house bottom`() {
        val neighbours = Neighbours(DemoScene.sessions(2)[1], null, 2, 4)
        show { HouseBottomScreen(live(), false, neighbours, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks()) }
        compose.onNodeWithText("Voir les modifications").assertIsDisplayed()
        capture("maison-bas")
    }

    // La validation

    private val request = Approval(
        ApprovalId("a"), SessionId("s1"), ApprovalKind.COMMAND, "./gradlew :app:connectedCheck", "Vérifier le tri dans l'app, sur la console.",
        "/home/dev/regie", paths = listOf("android/app/build", "android/domain/Overview.kt"),
        options = listOf(
            ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"),
            ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Always"),
            ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
        ),
    )
    private val approval = ApprovalReview(request, listOf(request), pending = true)

    @Test
    fun `validation top`() {
        val scroll = ScrollInput(MutableSharedFlow(extraBufferCapacity = 16), MutableStateFlow(0f))
        show { ApprovalScene(approval, session(SessionStatus.AWAITING_APPROVAL), assets, scenes, scroll) }
        compose.onNodeWithText("Exécuter une commande").assertIsDisplayed()
        capture("validation-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `validation bottom`() {
        val callbacks = object : ApprovalCallbacks {
            override fun onKey(key: PadKey) {}
            override fun onSpeak(held: Boolean) {}
            override fun onSelect(id: ApprovalId) {}
        }
        show { ApprovalNotebook(approval, listOf(session(SessionStatus.AWAITING_APPROVAL)), callbacks) }
        compose.onNodeWithText("Autoriser une fois").assertIsDisplayed()
        capture("validation-bas")
    }

    // Les modifications

    private val diff = DiffReview(
        SessionId("s1"), "regie",
        listOf(
            DiffFile(
                "android/domain/Overview.kt",
                listOf(
                    DiffBlock(
                        "1",
                        listOf(
                            DiffLine(DiffKind.CONTEXT, "class Overview(private val store: SessionStore) {", 40, 40),
                            DiffLine(DiffKind.CONTEXT, "", 41, 41),
                            DiffLine(DiffKind.REMOVED, "    fun sessions() = store.all()", 42, null),
                            DiffLine(DiffKind.ADDED, "    fun sessions() = store.all()", null, 42),
                            DiffLine(DiffKind.ADDED, "        .sortedByDescending { it.updatedAt }", null, 43),
                            DiffLine(DiffKind.ADDED, "        .sortedBy { it.isPaused }", null, 44),
                            DiffLine(DiffKind.CONTEXT, "}", 45, 47),
                        ),
                        fileCoordinates = true,
                    ),
                ),
                LineChanges(12, 3),
            ),
            DiffFile("android/domain/OverviewTest.kt", emptyList(), LineChanges(18, 0)),
            DiffFile("android/app/SessionList.kt", emptyList(), LineChanges(1, 1)),
        ),
        lineIndex = 4,
        loading = false,
    )

    @Test
    fun `diff top`() {
        show { CorkBoardScreen(diff, session(), assets, scenes, scrollInput()) }
        compose.onNodeWithTag("cork-board").assertIsDisplayed()
        capture("modifications-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `diff bottom`() {
        val callbacks = object : DiffCallbacks {
            override fun onKey(key: PadKey) {}
            override fun onSpeak(held: Boolean) {}
            override fun onFile(index: Int) {}
        }
        show { DiffNotebook(diff, callbacks) }
        compose.onNodeWithText("Modifications").assertIsDisplayed()
        capture("modifications-bas")
    }

    // Bâtir

    @Test
    fun `build top`() {
        val sessions = DemoScene.sessions(6)
        val overview = Overview.of(sessions, emptyList(), BridgeStatus.Connected)
        val generator = VillageGenerator(InMemoryVillageLayoutStore(), catalog = assets.houseCatalog()!!)
        generator.generate(overview)
        val built = DemoScene.sessions(1).single().copy(id = SessionId("n"), project = sessions[0].project, status = SessionStatus.WORKING)
        generator.reserve(overview, sessions[0].project)
        generator.bind(SessionId("n"))
        val construction = Construction(sessions[0].project).launched(prompted = true).created(SessionId("n")).copy(stage = ConstructionStage.FOUNDATIONS)
        val village = generator.generate(Overview.of(sessions + built, emptyList(), BridgeStatus.Connected), construction)
        val scene = VillageSceneState(village, start = village.site!!.entry)
        show(village) { VillageTopScreen(scene, assets, onEnterHouse = {}, layer = constructionLayer(construction, panel = true)) }
        compose.onNodeWithText("Bâtir une maison").assertIsDisplayed()
        capture("batir-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `build bottom`() {
        val form = NewSessionForm(
            visible = true, heard = "Lance Claude sur météo, ajoute un cache de dix minutes", agent = Agent.CLAUDE,
            agents = listOf(Agent.CLAUDE, Agent.CODEX), project = "api-meteo",
            projects = listOf(ProjectChoice("api-meteo", "main", listOf("main", "cache")), ProjectChoice("regie", "main", listOf("main"))),
            approvalMode = "ask", instruction = "Ajoute un cache de dix minutes sur les prévisions.", field = NewSessionField.AGENT,
        )
        val callbacks = object : BuildCallbacks {
            override fun onKey(key: BuildKey) {}
            override fun onHold(held: Boolean) {}
            override fun onField(field: NewSessionField) {}
            override fun onInstruction(text: String) {}
            override fun onBranch(text: String) {}
            override fun onProjectName(text: String) {}
        }
        show { NewSessionBottomScreen(form, callbacks) }
        compose.onNodeWithText("Bâtir").assertIsDisplayed()
        capture("batir-bas")
    }

    // Le menu de pause, avec « Préparer la console » (ticket 53)

    @Test
    fun `pause menu top`() {
        show { PauseMenuTop(GameMenu.pause(), Samples.overview, Samples.courier, VoiceSettings(), Samples.now, assets, singleScreen = false) }
        compose.onNodeWithText("Préparer la console").assertIsDisplayed()
        capture("pause-haut")
    }

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `pause menu bottom`() {
        show { PauseMenuBottom(GameMenu.pause(), Samples.overview, Samples.courier, Samples.now, assets) }
        compose.onNodeWithText("Préparer la console").assertIsDisplayed()
        capture("pause-bas")
    }

    // Le courrier

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `courier`() {
        show { PauseMenuBottom(GameMenu.pause().press(MenuKey.A).menu, Samples.overview, Samples.courier, Samples.now, assets) }
        compose.onNodeWithText("Tout marquer lu").assertIsDisplayed()
        capture("courrier-bas")
    }

    // La console : la tour et son carnet en bas, la carte volante par-dessus une autre appli en haut

    private val t = Instant.fromEpochMilliseconds(0)

    @Test @Config(qualifiers = THOR_BOTTOM)
    fun `console bottom`() {
        val console = session(SessionStatus.WORKING).copy(id = SessionId("console"), project = "console", branch = null, title = null)
        val thread = live(SessionStatus.WORKING).copy(session = console)
        show {
            Column {
                ConsoleTowerHeader(console, assets)
                HouseBottomScreen(thread, false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks(), console = true)
            }
        }
        compose.onNodeWithTag("console-tower").assertIsDisplayed()
        capture("console-bas")
    }

    @Test
    fun `console card over another app`() {
        val card = FloatingCard("k", SessionId("console"), Agent.CLAUDE, "console", FloatingKind.MESSAGE, "J'ai ouvert l'affichage. Je baisse la luminosité à 40 % ?")
        show {
            Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F6)), contentAlignment = Alignment.TopEnd) {
                Box(Modifier.padding(20.dp)) { PixelFloatingCard(card, assets) }
            }
        }
        compose.onNodeWithText("Répondre").assertIsDisplayed()
        capture("console-carte")
    }
}
