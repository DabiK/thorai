package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.DictationCapture
import app.regie.domain.port.CaptureState
import app.regie.input.*
import app.regie.session.SessionNotice
import app.regie.ui.screens.*
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class SessionActionsIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `send shortcut follows A routing through pending resolved and dismissed approval`() = runTest {
        val gateway = ActionsGateway()
        gateway.approvals.value = listOf(gateway.request)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        container.sessionController.onDraftChange("draft to send")
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { Bottom(container) }
            compose.onNode(hasText("Envoyer") and hasText("A")).assertDoesNotExist()
            compose.onNodeWithText("Autoriser une fois").assertIsDisplayed()
            container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
            assertEquals(listOf(gateway.request.id to "once"), gateway.answers)
            assertTrue(gateway.sent.isEmpty())
            gateway.approvals.value = emptyList(); runCurrent()
            compose.onNode(hasText("Envoyer") and hasText("A")).assertDoesNotExist()
            container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
            assertEquals(1, gateway.answers.size)
            assertEquals("draft to send", container.sessionController.draft.value)
            container.handleInput(GamepadInput.Pressed(Button.LEFT)); runCurrent()
            compose.onNode(hasText("Envoyer") and hasText("A")).assertIsDisplayed()
            container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
            assertEquals(listOf(gateway.target to "draft to send"), gateway.sent)
            assertEquals("", container.sessionController.draft.value)
        }
    }

    @Test fun `options capture A without advertising send while touch can still send the draft`() = runTest {
        val gateway = ActionsGateway()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        container.sessionController.onDraftChange("touch draft")
        container.handleInput(GamepadInput.Pressed(Button.RIGHT)); runCurrent()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { Bottom(container) }
            compose.onNode(hasText("Envoyer") and hasText("A")).assertDoesNotExist()
            compose.onNodeWithText("A · Appliquer").assertIsDisplayed()
            container.handleInput(GamepadInput.Pressed(Button.RIGHT))
            container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
            assertEquals(listOf(gateway.target to SessionSetting(approvalMode = "allow-all")), gateway.configured)
            assertTrue(gateway.answers.isEmpty())
            assertTrue(gateway.sent.isEmpty())
            compose.onNodeWithText("Envoyer").assertIsDisplayed().performClick(); runCurrent()
            assertEquals(listOf(gateway.target to "touch draft"), gateway.sent)
            assertTrue(container.sessionSettings.value.visible)
            container.sessionController.onDraftChange("after options")
            container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
            compose.onNode(hasText("Envoyer") and hasText("A")).assertIsDisplayed()
        }
    }

    @Test fun `R2 approves the visible inline request only after release and A and never the next request`() = voiceReply("Approuve", ApprovalDecision.ONCE, "once")
    @Test fun `R2 refuses the visible inline request only after release and A and never the next request`() = voiceReply("Refuse", ApprovalDecision.REJECT, "no")

    @Test fun `a replaced inline request cannot be approved by confirming an earlier voice review`() =
        voiceReply("Approuve", ApprovalDecision.ONCE, "once", replaceBeforeConfirmation = true)

    private fun voiceReply(text: String, decision: ApprovalDecision, option: String, replaceBeforeConfirmation: Boolean = false) = runTest {
        val gateway = ActionsGateway()
        val other = gateway.request.copy(id = ApprovalId("other"), sessionId = SessionId("other"))
        val next = gateway.request.copy(id = ApprovalId("next"))
        gateway.approvals.value = listOf(other, gateway.request, next)
        val capture = ActionsCapture()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, capture) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        assertEquals(gateway.request, container.inlineApproval.value.request)
        container.handleInput(GamepadInput.TriggerDown(Trigger.R2)); runCurrent()
        capture.state.value = CaptureState(text = text, complete = true); runCurrent()
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertTrue(gateway.answers.isEmpty())
        container.handleInput(GamepadInput.TriggerUp(Trigger.R2)); runCurrent()
        assertEquals(VoiceCommand.Reply(gateway.request, decision), container.voiceCommand.state.value.command)
        assertTrue(gateway.answers.isEmpty())
        container.handleInput(GamepadInput.Pressed(Button.R1)); runCurrent()
        assertEquals(gateway.request, container.inlineApproval.value.request)
        if (replaceBeforeConfirmation) { gateway.approvals.value = listOf(other, next); runCurrent() }
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        gateway.approvals.value = listOf(other, next); runCurrent()
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(if (replaceBeforeConfirmation) emptyList() else listOf(gateway.request.id to option), gateway.answers)
        assertTrue(gateway.sent.isEmpty())
    }

    @Composable private fun Bottom(container: AppContainer) {
        val live by container.live.collectAsState()
        val approval by container.inlineApproval.collectAsState()
        val settings by container.sessionSettings.collectAsState()
        val control by container.sessionController.state.collectAsState()
        val draft by container.sessionController.draft
        val callbacks = object : SessionCallbacks {
            override fun onButton(button: Button) = container.handleInput(GamepadInput.Pressed(button))
            override fun onTrigger(trigger: Trigger, held: Boolean) {}
            override fun onDraftChange(text: String) = container.sessionController.onDraftChange(text)
            override fun onSend() = container.sessionController.send()
            override fun onNoticeSeen(notice: SessionNotice) {}
        }
        val options: (@Composable () -> Unit)? = if (settings.visible) ({
            SessionSettingsScreen(settings, callbacks::onButton, container::selectSessionSetting, compact = true)
        }) else null
        RegieTheme { SessionBottomScreen(live, null, control, draft, callbacks, approval = approval, options = options) }
    }
}

private class ActionsGateway : AgentGateway {
    val target = SessionId("displayed")
    val request = Approval(ApprovalId("visible"), target, ApprovalKind.COMMAND, "touch proof", options = listOf(
        ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Once"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
    private val time = Instant.fromEpochMilliseconds(0)
    override val sessions = MutableStateFlow(listOf(Session(target, Agent.CODEX, "demo", null, null, SessionStatus.WORKING, true, time, time, approvalMode = "ask")))
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(emptyList<AgentVersion>())
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connected)
    val answers = mutableListOf<Pair<ApprovalId, String>>()
    val sent = mutableListOf<Pair<SessionId, String>>()
    val configured = mutableListOf<Pair<SessionId, SessionSetting>>()
    override suspend fun configureSession(id: SessionId, setting: SessionSetting): SettingsResult {
        configured += id to setting
        return SettingsResult.APPLIED
    }
    override fun thread(id: SessionId) = MutableStateFlow(SessionThread(loaded = true))
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun interrupt(id: SessionId) = error("No interruption expected")
    override suspend fun shutdownBridge() = error("No shutdown expected")
    override suspend fun sendMessage(id: SessionId, text: String): Delivery { sent += id to text; return Delivery.PROMPT }
    override suspend fun answerApproval(id: ApprovalId, optionId: String): ApprovalReply { answers += id to optionId; return ApprovalReply.ACCEPTED }
}

private class ActionsCapture : DictationCapture {
    override val state = MutableStateFlow(CaptureState())
    override fun start(vocabulary: List<String>) { state.value = CaptureState(recording = true) }
    override fun finish() = Unit
    override fun cancel() = Unit
}
