package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.CaptureState
import app.regie.domain.port.DictationCapture
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.village.ConstructionStage
import app.regie.game.engine.village.PlotReservation
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.village.courierTile
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.session.Screen
import app.regie.storage.PreferencesVillageLayoutStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * Bâtir (ticket 48), de la manette jusqu'au domaine : X ou l'ordre R2 ouvrent la fiche, le coursier
 * marche jusqu'au terrain, A le réserve avant la création, la maison y est bâtie, et un échec le libère.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BuildInputTest {
    private val gateway = BuildGateway()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = PreferencesVillageLayoutStore(application)

    private fun TestScope.container(top: Boolean = true): AppContainer =
        AppContainer(application, application.getSystemService(DisplayManager::class.java), backgroundScope, SilentCapture(),
            villageWorker = kotlinx.coroutines.Dispatchers.Unconfined) { gateway }.also {
            it.topStarted.value = top
            it.start(replay = true); runCurrent()
        }

    private fun TestScope.press(container: AppContainer, vararg buttons: Button) =
        buttons.forEach { container.handleInput(GamepadInput.Pressed(it)); runCurrent() }

    /** Des images de 16 ms, jusqu'à ce que [done] soit vrai. */
    private val VillageSceneState.moving: Boolean get() = action == CourierAction.WALK

    private fun AppContainer.walk(done: (VillageSceneState) -> Boolean) {
        val scene = assertNotNull(village.scene.value, "le hameau est généré")
        var t = 0L
        scene.onFrame(t)
        repeat(3_000) {
            if (done(scene)) return
            t += 16
            scene.onFrame(t)
        }
        fail("le coursier marche encore")
    }

    private val draft = NewSessionDraft(Agent.CODEX, "regie", instruction = "Lis notes.md")

    @Test fun `X opens the form, the courier walks to a free plot of the chosen project, nothing is reserved yet`() = runTest {
        val container = container()
        press(container, Button.X)
        assertTrue(container.newSession.value.visible)
        assertNull(container.village.construction.value, "sans projet, aucun terrain n'est visé")

        // Projet : la croix choisit « regie ».
        press(container, Button.DOWN, Button.RIGHT)
        assertEquals("api-meteo", container.newSession.value.project)
        press(container, Button.RIGHT)
        assertEquals("regie", container.newSession.value.project)
        assertEquals("regie", container.village.construction.value?.project)
        val site = assertNotNull(container.village.scene.value?.village?.site)
        assertNull(site.stage)
        assertEquals("regie", site.project)
        container.walk { it.courierTile == it.village.site?.entry && !it.moving }
        assertEquals(Direction.UP, container.village.scene.value?.facing, "il regarde le terrain")
        assertNull(store.load().reservation, "la fiche ne réserve rien")
    }

    @Test fun `A reserves the plot before the session is created, and the house is built exactly there`() = runTest {
        val container = container()
        container.openNewSession(draft, "lance Codex sur regie"); runCurrent()
        val target = assertNotNull(container.village.scene.value?.village?.site).plot
        container.walk { it.courierTile == it.village.site?.entry && !it.moving }
        gateway.onCreate = { store.load().reservation }

        press(container, Button.A)

        assertEquals(PlotReservation("regie", target), gateway.reservedAtCreation, "réservé avant session.create")
        assertFalse(container.newSession.value.visible)
        assertEquals(Screen.Overview, container.screen.value, "on regarde le chantier")
        val construction = assertNotNull(container.village.construction.value)
        assertEquals(gateway.created, construction.session)
        assertEquals(ConstructionStage.FRAME, construction.stage, "la session est chargée dans l'agent")
        val village = assertNotNull(container.village.scene.value).village
        assertEquals(target, village.houseOf(gateway.created)?.plot, "la maison sur le terrain réservé")
        assertTrue(village.isUnderConstruction(village.houseOf(gateway.created)!!))
        assertTrue(container.village.mapOpen.value, "la carte du hameau en bas")

        // La première réponse : la maison est finie, le coursier resté devant y entre.
        gateway.thread.value = SessionThread(listOf(ThreadEntry.UserMessage(1, "Lis notes.md"), ThreadEntry.AgentText(2, "Je lis notes.md.")), loaded = true)
        runCurrent()
        assertEquals(ConstructionStage.FINISHED, container.village.construction.value?.stage)
        container.walk { it.entering != null }
        container.enterHouse(gateway.created); runCurrent()
        assertEquals(Screen.Session(gateway.created), container.screen.value)
        assertNull(container.village.construction.value, "le chantier fini s'efface")
        assertFalse(container.village.mapOpen.value)
    }

    @Test fun `a refused creation stops at the plot, the error stays readable and the plot is freed`() = runTest {
        val container = container()
        gateway.result = SessionCreation(error = "Ce dossier existe déjà dans ~/projects.")
        container.openNewSession(draft); runCurrent()
        val target = assertNotNull(container.village.scene.value?.village?.site).plot

        press(container, Button.A)

        assertTrue(container.newSession.value.visible, "la fiche reste, pour corriger")
        val construction = assertNotNull(container.village.construction.value)
        assertEquals(ConstructionStage.PLOT, construction.stage)
        assertEquals("Ce dossier existe déjà dans ~/projects.", construction.error)
        assertNull(store.load().reservation, "le terrain est libéré")
        val village = assertNotNull(container.village.scene.value).village
        assertNull(village.site)
        assertTrue(village.freePlots.any { it.index == target && village.streets[it.street].project == "regie" })

        // B ferme la fiche ; une nouvelle fiche repart de zéro.
        press(container, Button.B)
        assertFalse(container.newSession.value.visible)
    }

    @Test fun `an uncertain creation is a failure too, B on the form reserves nothing`() = runTest {
        val container = container()
        gateway.result = SessionCreation(uncertain = true)
        container.openNewSession(draft); runCurrent()
        press(container, Button.A)
        assertTrue(container.village.construction.value?.failed == true)
        assertNull(store.load().reservation)

        gateway.result = SessionCreation(id = gateway.created)
        container.openNewSession(draft); runCurrent()
        press(container, Button.B)
        assertNull(container.village.construction.value)
        assertNull(store.load().reservation)
        assertTrue(gateway.drafts.size == 1, "B ne crée rien")
    }

    @Test fun `a new project opens a new street, where its first house is built`() = runTest {
        val container = container()
        val streets = assertNotNull(container.village.scene.value).village.streets.size
        container.openNewSession(NewSessionDraft(Agent.CODEX, "Météo du jour", newProject = true)); runCurrent()
        assertEquals("meteo-du-jour", container.village.construction.value?.project, "le dossier que le pont créera")
        val preview = assertNotNull(container.village.scene.value).village
        assertEquals(streets + 1, preview.streets.size, "la rue que le projet ouvrirait")
        assertEquals("meteo-du-jour", preview.site?.project)
        assertTrue(preview.site?.opensStreet == true)

        gateway.created = SessionId("m"); gateway.result = SessionCreation(id = gateway.created)
        press(container, Button.A)

        assertTrue(gateway.drafts.single().newProject)
        val built = assertNotNull(container.village.scene.value).village
        val house = assertNotNull(built.houseOf(SessionId("m")))
        assertEquals("meteo-du-jour", built.streets[house.street].project)
        assertEquals(0, house.plot)
    }

    @Test fun `without the hamlet on screen, the created session opens directly, as before`() = runTest {
        val container = container(top = false)
        container.openNewSession(draft); runCurrent()
        press(container, Button.A)
        assertEquals(Screen.Session(gateway.created), container.screen.value)
        assertTrue(gateway.created in store.load().houses, "la maison a quand même son terrain")
    }

    @Test fun `the map takes the cross during a construction, A walks to a street, B closes it`() = runTest {
        val container = container()
        container.openNewSession(draft); runCurrent()
        press(container, Button.A)
        assertTrue(container.village.mapOpen.value)
        val start = assertNotNull(container.village.scene.value).foot
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN, isDown = true, repeatCount = 0); runCurrent()
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN, isDown = false, repeatCount = 0); runCurrent()
        assertEquals(1, container.village.mapStreet.value, "la croix choisit la rue")
        assertEquals(start, container.village.scene.value?.foot, "le coursier ne marche pas pendant la carte")
        press(container, Button.B)
        assertFalse(container.village.mapOpen.value)
        assertFalse(container.agentAuthentication.value.visible, "B ferme la carte, sans ouvrir les agents")
    }
}

private class SilentCapture : DictationCapture {
    override val state = MutableStateFlow(CaptureState())
    override fun start(vocabulary: List<String>) { state.value = CaptureState(recording = true) }
    override fun finish() { state.value = state.value.copy(recording = false, complete = true) }
    override fun cancel() { state.value = CaptureState() }
}

private class BuildGateway : AgentGateway {
    private val t = Instant.fromEpochMilliseconds(0)
    var created = SessionId("n")
    var result: SessionCreation = SessionCreation(id = created)
    var onCreate: () -> PlotReservation? = { null }
    var reservedAtCreation: PlotReservation? = null
    val drafts = mutableListOf<NewSessionDraft>()
    override val sessions = MutableStateFlow(listOf(
        Session(SessionId("a"), Agent.CLAUDE, "regie", "main", "tri", SessionStatus.IDLE, true, t, t),
        Session(SessionId("b"), Agent.CODEX, "api-meteo", "main", "cache", SessionStatus.WORKING, true, t, t),
    ))
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(listOf(AgentVersion(Agent.CLAUDE, "2.1", true), AgentVersion(Agent.CODEX, "0.1", true)))
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connected)
    val thread = MutableStateFlow(SessionThread(listOf(ThreadEntry.UserMessage(1, "Lis notes.md")), loaded = true))
    override suspend fun projectCatalog() = listOf(ProjectChoice("api-meteo", "main", listOf("main")), ProjectChoice("regie", "main", listOf("main")))
    override suspend fun createSession(draft: NewSessionDraft): SessionCreation {
        drafts += draft
        reservedAtCreation = onCreate()
        val id = result.id
        if (id != null) {
            val folder = if (draft.newProject) app.regie.domain.usecase.projectFolderName(draft.project)!! else draft.project
            sessions.value = sessions.value + Session(id, draft.agent, folder, "main", null, SessionStatus.WORKING, true, t, t)
        }
        return result
    }
    override suspend fun authentication() = listOf(AgentAuthentication(Agent.CLAUDE, true), AgentAuthentication(Agent.CODEX, true))
    override suspend fun startAuthentication(agent: Agent) = AgentAuthentication(agent, false, phase = AuthenticationPhase.STARTING)
    override suspend fun cancelAuthentication(agent: Agent) = AgentAuthentication(agent, false)
    override suspend fun logoutAgent(agent: Agent) = AgentAuthentication(agent, false)
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun shutdownBridge() = true
    override fun thread(id: SessionId) = thread
    override suspend fun sendMessage(id: SessionId, text: String) = Delivery.NOT_SENT
    override suspend fun interrupt(id: SessionId) = true
    override suspend fun answerApproval(id: ApprovalId, optionId: String) = ApprovalReply.UNKNOWN
}
