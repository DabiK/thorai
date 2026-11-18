package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.CaptureState
import app.regie.domain.port.DictationCapture
import app.regie.game.engine.courier.LetterKind
import app.regie.game.engine.menu.MenuEntry
import app.regie.game.engine.menu.MenuKind
import app.regie.input.*
import app.regie.overview.QuitStep
import app.regie.session.Screen
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import kotlin.time.Instant

/**
 * Le menu du jeu et le courrier (ticket 49), de la manette jusqu'au domaine : écran titre, menu de
 * pause ouvert par Start, connexion des agents et réglages dans le menu, « Quitter » confirmé, et
 * les lettres apportées par les mêmes cas d'usage que les notifications.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GameMenuInputTest {
    private val gateway = MenuGateway()

    private fun TestScope.container(): AppContainer {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, MenuCapture()) { gateway }.also {
            it.start(replay = true); runCurrent()
        }
    }

    private fun TestScope.press(container: AppContainer, vararg buttons: Button) =
        buttons.forEach { container.handleInput(GamepadInput.Pressed(it)); runCurrent() }

    @Test fun `the title opens once per process, and Continuer leaves it for the hamlet`() = runTest {
        val container = container()
        container.showTitle()
        assertEquals(MenuKind.TITLE, container.gameMenu.value.kind)
        press(container, Button.B)
        assertTrue(container.gameMenu.value.open, "B n'a rien à annuler sur le titre")
        press(container, Button.A)
        assertFalse(container.gameMenu.value.open)
        container.showTitle()
        assertFalse(container.gameMenu.value.open)
    }

    @Test fun `at first launch the setup comes first, then the title takes over`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("setup", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val container = container()
        container.agentAuthentication.value.takeIf { it.visible }?.let { press(container, Button.B) }
        // Même ordre que TopActivity : le parcours d'accueil, puis le titre.
        container.offerSetup(); container.showTitle(); runCurrent()
        assertTrue(container.setup.state.value.visible)
        assertEquals(MenuKind.TITLE, container.gameMenu.value.kind)
        press(container, Button.A)
        assertEquals(SetupStep.TERMUX, container.setup.state.value.step, "A va au parcours, pas au titre")
        assertEquals(MenuKind.TITLE, container.gameMenu.value.kind)
        container.setup.close(); runCurrent()
        assertEquals(MenuKind.TITLE, container.gameMenu.value.kind, "le titre prend le relais")
        press(container, Button.A)
        assertFalse(container.gameMenu.value.open)
    }

    @Test fun `Nouvelle session on the title opens the existing new-session flow`() = runTest {
        val container = container()
        container.showTitle()
        press(container, Button.DOWN, Button.A)
        assertFalse(container.gameMenu.value.open)
        assertTrue(container.newSession.value.visible)
    }

    @Test fun `Start on the hamlet opens the pause menu, which still pauses everything`() = runTest {
        val container = container()
        press(container, Button.START)
        assertEquals(MenuKind.PAUSE, container.gameMenu.value.kind)
        press(container, Button.DOWN, Button.DOWN, Button.DOWN)
        assertEquals(MenuEntry.PAUSE_ALL, container.gameMenu.value.current)
        press(container, Button.A)
        assertFalse(container.gameMenu.value.open)
        assertEquals(listOf(gateway.working), gateway.interrupted)
    }

    @Test fun `Start inside a session still interrupts it`() = runTest {
        val container = container()
        container.openSession(gateway.working); runCurrent()
        press(container, Button.START)
        assertFalse(container.gameMenu.value.open)
        assertEquals(listOf(gateway.working), gateway.interrupted)
    }

    @Test fun `agents open over the menu and B returns to it, the shortcut B still closes everything`() = runTest {
        val container = container()
        press(container, Button.START, Button.DOWN, Button.A)
        assertTrue(container.agentAuthentication.value.visible)
        press(container, Button.B)
        assertFalse(container.agentAuthentication.value.visible)
        assertTrue(container.gameMenu.value.open)
        press(container, Button.B)
        assertFalse(container.gameMenu.value.open)
        press(container, Button.B)
        assertTrue(container.agentAuthentication.value.visible)
        assertFalse(container.gameMenu.value.open)
    }

    @Test fun `settings open over the menu and B returns to it`() = runTest {
        val container = container()
        press(container, Button.START, Button.DOWN, Button.DOWN, Button.A)
        assertTrue(container.settingsOpen.value)
        press(container, Button.B)
        assertFalse(container.settingsOpen.value)
        assertTrue(container.gameMenu.value.open)
    }

    @Test fun `quitting from the menu asks for confirmation while a session works, B keeps the menu`() = runTest {
        val container = container()
        press(container, Button.START, Button.UP)
        repeat(4) { press(container, Button.DOWN) }
        assertEquals(MenuEntry.QUIT, container.gameMenu.value.current)
        press(container, Button.A)
        assertEquals(QuitStep.Confirm(1), container.controller.state.value.quit)
        press(container, Button.B)
        assertNull(container.controller.state.value.quit)
        assertTrue(container.gameMenu.value.open)
    }

    @Test fun `L2 under the menu still dictates, R2 still gives an order`() = runTest {
        val container = container()
        press(container, Button.START)
        container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); runCurrent()
        assertTrue(container.dictation.state.value.visible)
        container.handleInput(GamepadInput.TriggerUp(Trigger.L2)); runCurrent()
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        assertFalse(container.dictation.state.value.visible)
        assertTrue(container.gameMenu.value.open)
        container.handleInput(GamepadInput.TriggerDown(Trigger.R2)); runCurrent()
        assertTrue(container.voiceCommand.state.value.visible, "R2 donne un ordre vocal sous le menu")
        container.handleInput(GamepadInput.TriggerUp(Trigger.R2)); runCurrent()
    }

    @Test fun `R1 under the arriving bird opens the validation of that letter, not the first pending one`() = runTest {
        val first = Approval(ApprovalId("p0"), gateway.other, ApprovalKind.COMMAND, "rm -rf build",
            options = listOf(ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Oui")))
        gateway.sessions.value = gateway.sessions.value.map {
            if (it.id == gateway.other) it.copy(pendingApproval = first) else it.copy(pendingApproval = gateway.approval)
        }
        val container = container()
        gateway.approvals.value = listOf(first, gateway.approval); runCurrent()
        assertEquals(first.id, container.overview.value.firstPendingApproval?.approval?.id)
        assertEquals(gateway.approval.id, container.courier.value.latest?.approval)
        press(container, Button.R1)
        assertEquals(Screen.Approval, container.screen.value)
        assertEquals(gateway.approval.id, container.approvalReview.value.request?.id)
    }

    // --- Avec le hameau à l'écran (ticket 45) ---

    /** Des images de 16 ms pour le coursier, jusqu'à ce que [done] soit vrai. */
    private fun AppContainer.walk(done: (app.regie.game.ui.scene.VillageSceneState) -> Boolean) {
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

    @Test fun `with the hamlet on screen, R1 under the bird walks the courier to that house, then the session opens`() = runTest {
        val first = Approval(ApprovalId("p0"), gateway.other, ApprovalKind.COMMAND, "rm -rf build",
            options = listOf(ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Oui")))
        gateway.sessions.value = gateway.sessions.value.map {
            if (it.id == gateway.other) it.copy(pendingApproval = first) else it.copy(pendingApproval = gateway.approval)
        }
        val container = container()
        container.topStarted.value = true
        gateway.approvals.value = listOf(first, gateway.approval); runCurrent()
        assertEquals(gateway.approval.id, container.courier.value.latest?.approval)

        press(container, Button.R1)

        assertEquals(Screen.Overview, container.screen.value, "le coursier y va d'abord")
        assertEquals(gateway.working, container.village.scene.value?.destination, "la maison de la lettre, pas la première validation")
        container.walk { it.entering != null }
        // L'iris fermé, l'écran appelle enterHouse : la session s'ouvre, sa validation y attend.
        container.enterHouse(gateway.working); runCurrent()
        assertEquals(Screen.Session(gateway.working), container.screen.value)
        container.openOverview(); runCurrent()
        assertNull(container.village.scene.value?.entering, "ressorti devant la porte")
    }

    @Test fun `the pause menu and the setup take the d-pad before the courier`() = runTest {
        val container = container()
        container.topStarted.value = true
        val scene = assertNotNull(container.village.scene.value)
        val start = scene.foot

        press(container, Button.START)
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN, isDown = true, repeatCount = 0); runCurrent()
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_DOWN, isDown = false, repeatCount = 0); runCurrent()
        assertEquals(1, container.gameMenu.value.selected, "la croix choisit dans le menu")
        scene.onFrame(0); scene.onFrame(1_000)
        assertEquals(start, scene.foot, "le coursier n'a pas bougé sous le menu")
        press(container, Button.B)

        container.openSetup(); runCurrent()
        assertTrue(container.setup.state.value.visible)
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT, isDown = true, repeatCount = 0); runCurrent()
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT, isDown = false, repeatCount = 0); runCurrent()
        scene.onFrame(2_000); scene.onFrame(3_000)
        assertEquals(start, scene.foot, "ni sous le parcours d'accueil")
        container.setup.close(); runCurrent()

        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT, isDown = true, repeatCount = 0); runCurrent()
        scene.onFrame(4_000); scene.onFrame(4_100)
        container.inputRouter.onKey(android.view.KeyEvent.KEYCODE_DPAD_LEFT, isDown = false, repeatCount = 0); runCurrent()
        assertNotEquals(start, scene.foot, "sur le hameau, il marche")
    }

    @Test fun `on the hamlet, L2 dictates to the house the courier stands before, R2 still orders`() = runTest {
        val container = container()
        container.topStarted.value = true
        val scene = assertNotNull(container.village.scene.value)
        scene.send(app.regie.game.engine.courier.CourierCommand.GoTo(gateway.working, enter = false))
        container.walk { it.facingDoor != null }

        container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); runCurrent()
        assertEquals(gateway.working, container.dictation.state.value.target?.sessionId)
        assertEquals(app.regie.game.engine.courier.CourierAction.TALK, scene.action, "le coursier parle")
        container.handleInput(GamepadInput.TriggerUp(Trigger.L2)); runCurrent()
        press(container, Button.B)
        container.handleInput(GamepadInput.TriggerDown(Trigger.R2)); runCurrent()
        assertTrue(container.voiceCommand.state.value.visible)
        container.handleInput(GamepadInput.TriggerUp(Trigger.R2)); runCurrent()
    }

    @Test fun `a validation becomes a letter, and opening it shows the validation`() = runTest {
        val container = container()
        gateway.approvals.value = listOf(gateway.approval); runCurrent()
        val letter = container.courier.value.latest
        assertNotNull(letter)
        assertEquals(LetterKind.APPROVAL, letter.kind)
        assertEquals("Codex attend ta validation", letter.title)
        assertEquals("api-meteo · npm run db:migrate", letter.detail)
        assertEquals(1, container.courier.value.unread)
        press(container, Button.START, Button.A)
        assertTrue(container.gameMenu.value.courierOpen)
        press(container, Button.A)
        assertEquals(Screen.Approval, container.screen.value)
        assertFalse(container.gameMenu.value.open)
        assertEquals(0, container.courier.value.unread)
    }

    @Test fun `a session that stops working becomes a letter quoting the agent`() = runTest {
        val container = container()
        gateway.thread.value = SessionThread(listOf(ThreadEntry.AgentText(1, "La migration est prête.")), loaded = true)
        gateway.sessions.value = gateway.sessions.value.map { if (it.id == gateway.working) it.copy(status = SessionStatus.IDLE) else it }
        runCurrent()
        val letter = container.courier.value.latest
        assertNotNull(letter)
        assertEquals(LetterKind.FINISHED, letter.kind)
        assertEquals("Session terminée", letter.title)
        assertEquals("api-meteo · La migration est prête.", letter.detail)
        press(container, Button.START, Button.A, Button.X)
        assertEquals(0, container.courier.value.unread)
        press(container, Button.A)
        assertEquals(Screen.Session(gateway.working), container.screen.value)
    }
}

private class MenuCapture : DictationCapture {
    override val state = MutableStateFlow(CaptureState())
    override fun start(vocabulary: List<String>) { state.value = CaptureState(recording = true) }
    override fun finish() { state.value = state.value.copy(recording = false, complete = true) }
    override fun cancel() { state.value = CaptureState() }
}

private class MenuGateway : AgentGateway {
    val working = SessionId("meteo")
    private val t = Instant.fromEpochMilliseconds(0)
    val other = SessionId("site")
    override val sessions = MutableStateFlow(listOf(
        Session(working, Agent.CODEX, "api-meteo", "main", null, SessionStatus.WORKING, true, t, t),
        // Plus récente : elle passe devant dans la vue d'ensemble.
        Session(other, Agent.CLAUDE, "site-perso", "main", null, SessionStatus.IDLE, true, Instant.fromEpochMilliseconds(1), t),
    ))
    val approval = Approval(ApprovalId("p1"), working, ApprovalKind.COMMAND, "npm run db:migrate",
        options = listOf(ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Oui")))
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(emptyList<AgentVersion>())
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connected)
    val thread = MutableStateFlow(SessionThread.EMPTY)
    val interrupted = mutableListOf<SessionId>()
    override suspend fun authentication() = listOf(AgentAuthentication(Agent.CLAUDE, true), AgentAuthentication(Agent.CODEX, true))
    override suspend fun startAuthentication(agent: Agent) = AgentAuthentication(agent, false, phase = AuthenticationPhase.STARTING)
    override suspend fun cancelAuthentication(agent: Agent) = AgentAuthentication(agent, false)
    override suspend fun logoutAgent(agent: Agent) = AgentAuthentication(agent, false)
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun shutdownBridge() = true
    override fun thread(id: SessionId) = thread
    override suspend fun sendMessage(id: SessionId, text: String) = Delivery.NOT_SENT
    override suspend fun interrupt(id: SessionId): Boolean { interrupted += id; return true }
    override suspend fun answerApproval(id: ApprovalId, optionId: String) = ApprovalReply.UNKNOWN
}
