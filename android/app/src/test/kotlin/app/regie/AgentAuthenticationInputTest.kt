package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.input.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AgentAuthenticationInputTest {
    @Test fun `B on home opens login, arrows choose agent, A and B stay within the login screen`() = runTest {
        val gateway = LoginGateway()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope) { gateway }
        container.start(replay = true); runCurrent()
        assertTrue(container.agentAuthentication.value.visible)
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        assertFalse(container.agentAuthentication.value.visible)
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        assertTrue(container.agentAuthentication.value.visible)
        container.handleInput(GamepadInput.Pressed(Button.RIGHT)); runCurrent()
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(listOf(Agent.CODEX), gateway.started)
        assertTrue(container.agentAuthentication.value.current!!.pending)
        container.handleInput(GamepadInput.Pressed(Button.X)); runCurrent()
        assertFalse(container.newSession.value.visible)
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        assertEquals(listOf(Agent.CODEX), gateway.cancelled)
    }
}
private class LoginGateway : AgentGateway {
    override val sessions = MutableStateFlow(emptyList<Session>())
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(emptyList<AgentVersion>())
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connected)
    val started = mutableListOf<Agent>()
    val cancelled = mutableListOf<Agent>()
    override suspend fun authentication() = listOf(AgentAuthentication(Agent.CLAUDE, false), AgentAuthentication(Agent.CODEX, false))
    override suspend fun startAuthentication(agent: Agent): AgentAuthentication { started += agent; return AgentAuthentication(agent, false, phase = AuthenticationPhase.STARTING) }
    override suspend fun cancelAuthentication(agent: Agent): AgentAuthentication { cancelled += agent; return AgentAuthentication(agent, false) }
    override suspend fun logoutAgent(agent: Agent) = AgentAuthentication(agent, false)
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun shutdownBridge() = true
    override fun thread(id: SessionId) = MutableStateFlow(SessionThread.EMPTY)
    override suspend fun sendMessage(id: SessionId, text: String) = Delivery.NOT_SENT
    override suspend fun interrupt(id: SessionId) = false
    override suspend fun answerApproval(id: ApprovalId, optionId: String) = ApprovalReply.UNKNOWN
}
