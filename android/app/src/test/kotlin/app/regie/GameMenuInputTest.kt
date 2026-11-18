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
    override val sessions = MutableStateFlow(listOf(Session(working, Agent.CODEX, "api-meteo", "main", null, SessionStatus.WORKING, true, t, t)))
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
