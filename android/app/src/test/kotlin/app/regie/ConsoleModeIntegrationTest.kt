package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.CaptureState
import app.regie.domain.port.DictationCapture
import app.regie.overlay.OverlayContent
import app.regie.session.Screen
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*
import kotlin.time.Instant

/**
 * Mode console (ticket 25), de la touche lue par le service d'accessibilité jusqu'au pont : Chrome a
 * la manette sur l'écran du haut, Régie montre la session console en bas.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ConsoleModeIntegrationTest {
    private val gateway = ConsoleGateway()
    private val capture = ConsoleCapture()

    private fun TestScope.container(): AppContainer {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return AppContainer(app, app.getSystemService(DisplayManager::class.java), backgroundScope, capture) { gateway }.also {
            it.start(replay = true); it.openSession(gateway.target); runCurrent()
        }
    }

    private fun AppContainer.key(keyCode: Int, down: Boolean) = consoleKeys.onKey(keyCode, down, 0)

    private fun TestScope.dictate(container: AppContainer, text: String) {
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, true)); runCurrent()
        capture.state.value = CaptureState(text = text, complete = true); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, false)); runCurrent()
    }

    @Test fun `R2 opens the named session over Chrome without A and returns gamepad control`() = runTest {
        val container = container()
        container.bottomStarted.value = true
        container.openOverview(); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_R2, true)); runCurrent()
        capture.state.value = CaptureState(text = "Où en est la session console ?", complete = true)
        runCurrent()
        assertEquals(Screen.Overview, container.screen.value)
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_R2, false)); runCurrent()
        assertEquals(Screen.Session(gateway.target), container.screen.value)
        assertFalse(container.voiceCommand.state.value.visible)
        assertNull(container.consoleOverlay.value)
        assertFalse(container.key(KeyEvent.KEYCODE_BUTTON_A, true), "A belongs to Chrome after navigation")
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun `L2 held over Chrome dictates to the session shown in Régie and A sends it`() = runTest {
        val container = container()
        dictate(container, "ouvre YouTube et cherche lofi")
        assertEquals(DictationTarget.Message(gateway.target), container.dictation.state.value.target)
        // The bottom screen shows the dictation: nothing on top of Chrome.
        container.bottomStarted.value = true; runCurrent()
        assertNull(container.consoleOverlay.value)
        assertFalse(container.key(KeyEvent.KEYCODE_BUTTON_L1, true), "L1 stays with Chrome")
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_A, true)); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_A, false))
        assertEquals(listOf(gateway.target to "ouvre YouTube et cherche lofi"), gateway.sent)
        assertFalse(container.dictation.state.value.visible)
        // Once sent, A belongs to Chrome again.
        assertFalse(container.key(KeyEvent.KEYCODE_BUTTON_A, true))
        assertEquals(Screen.Session(gateway.target), container.screen.value)
    }

    @Test fun `without the bottom screen the dictation goes on top of the other app`() = runTest {
        val container = container()
        dictate(container, "ouvre YouTube")
        assertIs<OverlayContent.Dictation>(container.consoleOverlay.value)
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_B, true)); runCurrent()
        assertFalse(container.dictation.state.value.visible)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun `nothing is taken while a Régie screen has the gamepad`() = runTest {
        val container = container()
        container.gamepadFocus("app.regie.BottomActivity", true)
        assertFalse(container.key(KeyEvent.KEYCODE_BUTTON_L2, true))
        assertFalse(container.dictation.state.value.visible)
        container.gamepadFocus("app.regie.BottomActivity", false)
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, true))
    }

    private fun TestScope.press(container: AppContainer, keyCode: Int): Boolean =
        container.key(keyCode, true).also { container.key(keyCode, false); runCurrent() }

    @Test fun `a finished message pops a card only when no Régie screen shows the conversation`() = runTest {
        val container = container()
        container.bottomStarted.value = true; runCurrent()
        gateway.say(ThreadEntry.AgentText(2, "J’ouvre YouTube."), ThreadEntry.ToolCall(3, ToolKind.OTHER, "mobile_open_url")); runCurrent()
        assertNull(container.floatingCard.value, "the bottom already shows the conversation")
        assertNull(container.consoleOverlay.value)

        container.bottomStarted.value = false; runCurrent()
        gateway.say(ThreadEntry.AgentText(4, "La recherche est lancée."), ThreadEntry.ToolCall(5, ToolKind.OTHER, "mobile_type")); runCurrent()
        val card = assertIs<OverlayContent.Card>(container.consoleOverlay.value)
        assertEquals("La recherche est lancée.", card.card.text)
        assertFalse(card.keys, "a brand new card leaves B to the app a moment")
        assertFalse(press(container, KeyEvent.KEYCODE_BUTTON_B))
        advanceTimeBy(1_100); runCurrent()
        assertTrue(press(container, KeyEvent.KEYCODE_BUTTON_B))
        assertNull(container.floatingCard.value)
        // The agent still works: the discreet bubble follows it.
        assertIs<OverlayContent.Bubble>(container.consoleOverlay.value)
        assertFalse(press(container, KeyEvent.KEYCODE_BUTTON_B), "B goes back to Chrome")
    }

    @Test fun `L2 answers the card's session and the card leaves with the answer`() = runTest {
        val container = container()
        gateway.say(ThreadEntry.AgentText(2, "Je lance la première vidéo ?"))
        gateway.sessions.value = gateway.sessions.value.map { it.copy(status = SessionStatus.IDLE) }; runCurrent()
        assertEquals(FloatingKind.QUESTION, container.floatingCard.value?.kind)
        dictate(container, "oui")
        // No Régie screen: the dictation goes on top, with the sentence it answers.
        val panel = assertIs<OverlayContent.Dictation>(container.consoleOverlay.value)
        assertEquals("Je lance la première vidéo ?", panel.replyingTo)
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_A, true)); runCurrent()
        assertEquals(listOf(gateway.target to "oui"), gateway.sent)
        assertNull(container.floatingCard.value)
    }

    @Test fun `an approval card authorizes once with A, never with a fresh or doubled A`() = runTest {
        val container = container()
        gateway.approvals.value = listOf(gateway.request); runCurrent()
        assertEquals(FloatingKind.APPROVAL, container.floatingCard.value?.kind)
        // Pressed for Chrome as the card appears: Chrome keeps it, nothing is authorized.
        assertFalse(press(container, KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(gateway.answers.isEmpty())
        advanceTimeBy(1_100); runCurrent()
        assertTrue(press(container, KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(listOf(gateway.request.id to "once"), gateway.answers)
        assertNull(container.floatingCard.value)
    }

    @Test fun `L2 on an approval card dictates a refusal with an instruction`() = runTest {
        val container = container()
        gateway.approvals.value = listOf(gateway.request); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, true)); runCurrent()
        assertEquals(DictationTarget.Rejection(gateway.target, gateway.request.id), container.dictation.state.value.target)
    }

    @Test fun `an approval asked while answering a card still shows, and stays after the answer`() = runTest {
        val container = container()
        gateway.say(ThreadEntry.AgentText(2, "J’ouvre YouTube."), ThreadEntry.ToolCall(3, ToolKind.OTHER, "mobile_open_url")); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, true)); runCurrent()
        gateway.approvals.value = listOf(gateway.request); runCurrent()
        assertEquals(FloatingKind.APPROVAL, container.floatingCard.value?.kind)
        container.key(KeyEvent.KEYCODE_BUTTON_L2, false); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_B, true)); runCurrent()
        assertFalse(container.dictation.state.value.visible)
        assertEquals(FloatingKind.APPROVAL, container.floatingCard.value?.kind, "only the answered card leaves")
    }

    @Test fun `L2 released in silence over Chrome sends nothing and the bottom says so`() = runTest {
        val container = container()
        container.bottomStarted.value = true
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, true)); runCurrent()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, false)); runCurrent()
        capture.state.value = CaptureState(text = "", complete = true); runCurrent()
        assertFalse(container.dictation.state.value.visible, "back to the settings table")
        assertEquals(app.regie.session.SessionNotice.NothingHeard, container.sessionController.state.value.notice)
        assertTrue(gateway.sent.isEmpty())
        assertFalse(container.key(KeyEvent.KEYCODE_BUTTON_A, true), "A belongs to Chrome again")
    }

    @Test fun `L2 from another app closes a panel left open in Régie and dictates`() = runTest {
        val container = container()
        container.openVoiceSettings()
        assertTrue(container.key(KeyEvent.KEYCODE_BUTTON_L2, true)); runCurrent()
        assertFalse(container.settingsOpen.value)
        assertEquals(DictationTarget.Message(gateway.target), container.dictation.state.value.target)
    }

    @Test fun `Régie coming back on top removes everything it posed over other apps`() = runTest {
        val container = container()
        gateway.say(ThreadEntry.AgentText(2, "Fait."), ThreadEntry.ToolCall(3, ToolKind.READ, "Read")); runCurrent()
        assertNotNull(container.consoleOverlay.value)
        container.topStarted.value = true; runCurrent()
        assertNull(container.consoleOverlay.value)
        assertNull(container.floatingCard.value)
    }
}

private class ConsoleGateway : AgentGateway {
    val target = SessionId("console")
    val request = Approval(ApprovalId("visible"), target, ApprovalKind.COMMAND, "am start -a VIEW", options = listOf(
        ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Once"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
    private val time = Instant.fromEpochMilliseconds(0)
    override val sessions = MutableStateFlow(listOf(Session(target, Agent.CODEX, "console", null, null, SessionStatus.WORKING, true, time, time, approvalMode = "ask")))
    override val approvals = MutableStateFlow(emptyList<Approval>())
    override val agents = MutableStateFlow(emptyList<AgentVersion>())
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connected)
    val answers = mutableListOf<Pair<ApprovalId, String>>()
    val sent = mutableListOf<Pair<SessionId, String>>()
    private val thread = MutableStateFlow(SessionThread(listOf(ThreadEntry.UserMessage(1, "ouvre YouTube")), loaded = true))
    fun say(vararg entries: ThreadEntry) { thread.value = thread.value.copy(entries = thread.value.entries + entries) }
    override fun thread(id: SessionId) = thread
    override suspend fun maintainConnection(): Nothing = awaitCancellation()
    override suspend fun isBridgeUp() = true
    override suspend fun interrupt(id: SessionId) = error("No interruption expected")
    override suspend fun shutdownBridge() = error("No shutdown expected")
    override suspend fun sendMessage(id: SessionId, text: String): Delivery { sent += id to text; return Delivery.PROMPT }
    override suspend fun answerApproval(id: ApprovalId, optionId: String): ApprovalReply { answers += id to optionId; return ApprovalReply.ACCEPTED }
}

private class ConsoleCapture : DictationCapture {
    override val state = MutableStateFlow(CaptureState())
    override fun start(vocabulary: List<String>) { state.value = CaptureState(recording = true) }
    override fun finish() = Unit
    override fun cancel() = Unit
}
