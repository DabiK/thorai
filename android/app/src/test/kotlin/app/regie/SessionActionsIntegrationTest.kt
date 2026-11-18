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
import app.regie.session.Screen
import app.regie.session.SessionNotice
import app.regie.ui.SessionCallbacks
import app.regie.ui.ThreadScroll
import app.regie.ui.game.GameSessionBottom
import app.regie.ui.game.GameSessionTop
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
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-369dpi")
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
            host.setup().get().setContent { SessionSurface(container) }
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
            host.setup().get().setContent { SessionSurface(container) }
            compose.onNode(hasText("Envoyer") and hasText("A")).assertDoesNotExist()
            compose.onNodeWithText("Appliquer").assertIsDisplayed()
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

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `options hide approval shortcuts and touch still allows once on two screens`() =
        approvalWithOptions(singleScreen = false, label = "Autoriser une fois", option = "once")

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `options hide approval shortcuts and touch still allows session on one screen`() =
        approvalWithOptions(singleScreen = true, label = "Autoriser pour la session", option = "allow_for_session")

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `options hide approval shortcuts and touch still refuses on two screens`() =
        approvalWithOptions(singleScreen = false, label = "Refuser", option = "no")

    private fun approvalWithOptions(singleScreen: Boolean, label: String, option: String) = runTest {
        val gateway = ActionsGateway()
        gateway.approvals.value = listOf(gateway.request)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        container.sessionController.onDraftChange("preserved draft")
        container.handleInput(GamepadInput.Pressed(Button.RIGHT)); runCurrent()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { SessionSurface(container, singleScreen = singleScreen) }
            val shortcuts = listOf("A" to "Autoriser une fois", "X" to "Autoriser pour la session", "B" to "Refuser")
            shortcuts.forEach { (key, action) ->
                compose.onNode(hasText(key) and hasText(action)).assertDoesNotExist()
                compose.onNodeWithText(action).assertIsDisplayed().assertHasClickAction()
            }
            container.handleInput(GamepadInput.Pressed(Button.RIGHT))
            container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
            assertEquals(listOf(gateway.target to SessionSetting(approvalMode = "allow-all")), gateway.configured)
            container.handleInput(GamepadInput.Pressed(Button.X)); runCurrent()
            assertTrue(gateway.answers.isEmpty())
            container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
            assertFalse(container.sessionSettings.value.visible)
            assertTrue(gateway.answers.isEmpty())
            // Options closed, the menu advertises its keys again; one menu, in the notebook, on one or two screens.
            shortcuts.forEach { (key, action) ->
                compose.onAllNodes(hasText(key) and hasText(action)).assertCountEquals(1)
            }
            container.handleInput(GamepadInput.Pressed(Button.RIGHT)); runCurrent()
            compose.onNodeWithText(label).assertIsDisplayed().performClick(); runCurrent()
            assertEquals(listOf(gateway.request.id to option), gateway.answers)
            assertTrue(container.sessionSettings.value.visible)
            assertTrue(gateway.sent.isEmpty())
            assertEquals("preserved draft", container.sessionController.draft.value)
            val next = gateway.request.copy(id = ApprovalId("next"))
            gateway.approvals.value = listOf(next); runCurrent()
            compose.onNodeWithText("Réponse reçue.", substring = true).assertIsDisplayed()
            // The options keep the R1 key, but a touch on « Voir la suivante » still shows the next request (touch as a fallback).
            container.handleInput(GamepadInput.Pressed(Button.R1)); runCurrent()
            assertEquals(gateway.request.id, container.inlineApproval.value.request?.id)
            compose.onNodeWithText("Voir la suivante").assertIsDisplayed().performClick(); runCurrent()
            assertEquals(next.id, container.inlineApproval.value.request?.id)
            assertTrue(container.sessionSettings.value.visible)
            compose.onNodeWithText("Refuser").assertIsDisplayed().performClick(); runCurrent()
            assertTrue(container.sessionSettings.value.visible)
            gateway.approvals.value = emptyList(); runCurrent()
            compose.onNode(hasText("←") and hasText("Fermer la validation")).assertDoesNotExist()
            container.handleInput(GamepadInput.Pressed(Button.LEFT)); runCurrent()
            assertEquals(next.id, container.inlineApproval.value.request?.id)
            compose.onNodeWithText("Fermer la validation").assertIsDisplayed().performClick(); runCurrent()
            assertNull(container.inlineApproval.value.request)
            assertEquals(listOf(gateway.request.id to option, next.id to "no"), gateway.answers)
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

    @Test fun `a dictated message goes on A and the bottom returns to the settings table by itself`() = runTest {
        val gateway = ActionsGateway()
        val capture = ActionsCapture()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, capture) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); runCurrent()
        assertTrue(container.dictation.state.value.visible)
        assertEquals(Screen.Session(gateway.target), container.screen.value)
        capture.state.value = CaptureState(text = "Lance les tests", complete = true); runCurrent()
        container.handleInput(GamepadInput.TriggerUp(Trigger.L2)); runCurrent()
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(listOf(gateway.target to "Lance les tests"), gateway.sent)
        assertFalse(container.dictation.state.value.visible)
        // A new turn shows in the thread: no « Message envoyé » screen or notice to dismiss.
        assertNull(container.sessionController.state.value.notice)
        assertEquals(Screen.Session(gateway.target), container.screen.value)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { SessionSurface(container) }
            compose.onNodeWithText("Réglages de Codex").assertIsDisplayed()
            compose.onNodeWithText("Je valide chaque action").assertIsDisplayed()
        }
    }

    @Test fun `a message dictated from the overview leaves L2 free for the next dictation`() = runTest {
        val gateway = ActionsGateway()
        val capture = ActionsCapture()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, capture) { gateway }
        container.start(replay = true); runCurrent()
        repeat(2) { round ->
            container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); runCurrent()
            assertTrue(container.dictation.state.value.visible, "dictée $round")
            capture.state.value = CaptureState(text = "Message $round", complete = true); runCurrent()
            container.handleInput(GamepadInput.TriggerUp(Trigger.L2))
            container.handleInput(GamepadInput.Scroll(0.7f)); runCurrent()
            container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
            assertFalse(container.dictation.state.value.visible)
            assertEquals(emptySet(), container.controller.state.value.heldTriggers)
            assertEquals(0f, container.dictationVelocity.value)
        }
        assertEquals(listOf(gateway.target to "Message 0", gateway.target to "Message 1"), gateway.sent)
        assertEquals(Screen.Overview, container.screen.value)
    }

    @Test fun `a second A right after a dictation closes itself does not answer the validation behind it`() = runTest {
        val gateway = ActionsGateway()
        val capture = ActionsCapture()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, capture) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); runCurrent()
        capture.state.value = CaptureState(text = "Continue", complete = true); runCurrent()
        container.handleInput(GamepadInput.TriggerUp(Trigger.L2)); runCurrent()
        // The agent asks for a validation while the user reviews the dictation.
        gateway.approvals.value = listOf(gateway.request); runCurrent()
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(listOf(gateway.target to "Continue"), gateway.sent)
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertTrue(gateway.answers.isEmpty())
        Thread.sleep(600)
        container.handleInput(GamepadInput.Pressed(Button.A)); runCurrent()
        assertEquals(listOf(gateway.request.id to "once"), gateway.answers)
    }

    @Test fun `settings cannot open under a dictation, and a debug replay serves one capture only`() = runTest {
        val gateway = ActionsGateway()
        val capture = ActionsCapture()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, capture) { gateway }
        container.start(replay = true); container.openSession(gateway.target); runCurrent()
        container.handleInput(GamepadInput.TriggerDown(Trigger.L2)); runCurrent()
        container.openSessionSettings("model"); runCurrent()
        assertFalse(container.sessionSettings.value.visible)
        container.handleInput(GamepadInput.Pressed(Button.B)); runCurrent()
        container.openSessionSettings(); runCurrent()
        assertTrue(container.sessionSettings.value.visible)
        val wav = java.io.File.createTempFile("replay", ".wav")
        container.debugVoiceWav = wav
        assertEquals(wav, container.takeDebugVoiceWav())
        assertNull(container.takeDebugVoiceWav())
        wav.delete()
    }

    /**
     * La maison branchée comme dans l'app (ticket 46) : sur deux écrans, le carnet du bas porte le fil,
     * la validation et les réglages ; sur un seul, il passe à droite de la pièce.
     */
    @Composable private fun SessionSurface(container: AppContainer, singleScreen: Boolean = false) {
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
            override fun onNextApproval() = container.nextInlineApproval()
        }
        val scroll = ThreadScroll(container.sessionController.scrollSteps, container.sessionController.scrollVelocity)
        if (singleScreen) GameSessionTop(live, false, null, control, draft, settings, true, scroll, callbacks, approval,
            container::answerInline, container::dismissInlineApproval, container::selectSessionSetting, DictationReview(), null)
        else GameSessionBottom(live, false, null, control, draft, settings, scroll, callbacks, approval,
            container::answerInline, container::dismissInlineApproval, container::selectSessionSetting)
    }
}

private class ActionsGateway : AgentGateway {
    val target = SessionId("displayed")
    val request = Approval(ApprovalId("visible"), target, ApprovalKind.COMMAND, "touch proof", options = listOf(
        ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Session"),
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
