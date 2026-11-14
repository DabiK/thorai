package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.session.SessionNotice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SessionSettingsInputTest {
    @Test fun `Start interrupts the displayed session while its options are open`() = verifyInterruption()

    @Test fun `Start interrupts without waiting for the setting acknowledgement even when B cannot close`() = verifyInterruption(busy = true)

    @Test fun `Start in session options does not interrupt an idle session`() = verifyInterruption(status = SessionStatus.IDLE)

    private fun verifyInterruption(busy: Boolean = false, status: SessionStatus = SessionStatus.WORKING) = runTest {
        val gateway = SettingsInputGateway(status)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope) { gateway }
        container.topStarted.value = true
        container.start(replay = true)
        container.openSession(gateway.target)
        runCurrent()
        assertEquals(status == SessionStatus.WORKING, container.live.value!!.canInterrupt)

        container.handleInput(GamepadInput.Pressed(Button.RIGHT))
        runCurrent()
        assertTrue(container.sessionSettings.value.visible)
        assertFalse(container.sessionSettings.value.busy)
        assertTrue(gateway.interrupted.isEmpty())

        if (busy) {
            container.handleInput(GamepadInput.Pressed(Button.RIGHT))
            container.handleInput(GamepadInput.Pressed(Button.A))
            runCurrent()
            assertEquals(listOf(gateway.target to SessionSetting(approvalMode = "allow-all")), gateway.configured)
            container.handleInput(GamepadInput.Pressed(Button.B))
            assertTrue(container.sessionSettings.value.visible)
            assertTrue(container.sessionSettings.value.busy)
            assertFalse(gateway.settingResult.isCompleted)
        }

        container.handleInput(GamepadInput.Pressed(Button.START))
        runCurrent()
        assertEquals(if (status == SessionStatus.WORKING) listOf(gateway.target) else emptyList(), gateway.interrupted)
        assertEquals(if (status == SessionStatus.WORKING) SessionNotice.Interrupted(true) else null,
            container.sessionController.state.value.notice)
        assertTrue(container.sessionSettings.value.visible)
        if (busy) {
            assertTrue(container.sessionSettings.value.busy)
            gateway.settingResult.complete(SettingsResult.REFUSED)
            runCurrent()
            assertFalse(container.sessionSettings.value.busy)
            assertEquals(1, gateway.configured.size)
        } else assertTrue(gateway.configured.isEmpty())
    }
}

private class SettingsInputGateway(private val status: SessionStatus) : AgentGateway {
    val target = SessionId("displayed")
    private val time = Instant.parse("2026-11-14T12:00:00Z")
    private fun session(id: SessionId) = Session(id, Agent.CODEX, id.value, null, null,
        status, true, time, time, approvalMode = "ask")
    override val sessions = MutableStateFlow(listOf(session(SessionId("other")), session(target)))
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(emptyList<AgentVersion>())
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connected)
    val interrupted = mutableListOf<SessionId>()
    val configured = mutableListOf<Pair<SessionId, SessionSetting>>()
    val settingResult = CompletableDeferred<SettingsResult>()
    override suspend fun configureSession(id: SessionId, setting: SessionSetting): SettingsResult {
        configured += id to setting
        return settingResult.await()
    }
    override suspend fun interrupt(id: SessionId): Boolean { interrupted += id; return true }
    override fun thread(id: SessionId) = MutableStateFlow(SessionThread(loaded = true))
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun shutdownBridge() = error("No shutdown expected")
    override suspend fun sendMessage(id: SessionId, text: String): Delivery = error("No prompt expected")
    override suspend fun answerApproval(id: ApprovalId, optionId: String): ApprovalReply = error("No approval expected")
}
