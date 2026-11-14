package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.domain.usecase.ApprovalReview
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.session.SessionControlState
import app.regie.session.SessionNotice
import app.regie.ui.screens.*
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class InlineSessionScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.WORKING, true,
        Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask")
    private val callbacks = object : SessionCallbacks {
        override fun onButton(button: Button) {}
        override fun onTrigger(trigger: Trigger, held: Boolean) {}
        override fun onDraftChange(text: String) {}
        override fun onSend() {}
        override fun onNoticeSeen(notice: SessionNotice) {}
    }
    private val scroll = ThreadScroll(MutableSharedFlow(extraBufferCapacity = 16), MutableStateFlow(0f))

    @Test fun `approval command why and actions are in the thread and disable after answer`() {
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "touch proof", reason = "Create the proof",
            options = listOf(ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
        val review = mutableStateOf(ApprovalReview(request, listOf(request), pending = true))
        val decisions = mutableListOf<ApprovalDecision>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                SessionTopScreen(LiveSession.of(session, SessionThread(loaded = true)), false, SessionControlState(), "", false, scroll, callbacks,
                    approval = review.value, onDecision = { _, decision -> decisions += decision })
            } }
            compose.onNodeWithText("touch proof").assertIsDisplayed()
            compose.onNodeWithText("Create the proof").assertIsDisplayed()
            compose.onNodeWithText("Autoriser une fois").assertIsDisplayed().performClick()
            assertEquals(listOf(ApprovalDecision.ONCE), decisions)
            compose.runOnIdle { review.value = review.value.copy(pending = false, answer = ApprovalAnswer(ApprovalReply.ACCEPTED)) }
            compose.onNodeWithText("Autoriser une fois").assertHasNoClickAction()
            compose.onNodeWithText("demo").assertIsDisplayed()
        }
    }

    @Test fun `resolved approval keeps its close action on the bottom instead of advertising an intercepted X`() {
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "touch proof")
        val review = ApprovalReview(request = request, answer = ApprovalAnswer(ApprovalReply.ACCEPTED))
        var closed = false
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                SessionBottomScreen(LiveSession.of(session, SessionThread(loaded = true)), null,
                    SessionControlState(), "", callbacks, approval = review, onDismissApproval = { closed = true })
            } }
            compose.onNodeWithText("Voir le diff").assertDoesNotExist()
            compose.onNodeWithText("Fermer la validation").assertIsDisplayed().performClick()
            kotlin.test.assertTrue(closed)
            compose.onNodeWithText("Autoriser une fois").assertHasNoClickAction()
        }
    }

    @Test fun `tool details expand in place and collapse without losing output`() {
        val thread = SessionThread(entries = listOf(ThreadEntry.ToolCall(1, ToolKind.EXECUTE, "check", command = "npm test", result = "last", output = "first\nlast")), loaded = true)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionTopScreen(LiveSession.of(session, thread), false, SessionControlState(), "", false, scroll, callbacks) } }
            compose.onNodeWithText("first\nlast").assertDoesNotExist()
            compose.onNodeWithText("npm test", substring = true).performClick()
            compose.onNodeWithText("first\nlast").assertIsDisplayed()
            compose.onNodeWithText("npm test", substring = true).performClick()
            compose.onNodeWithText("first\nlast").assertDoesNotExist()
        }
    }

    @Test fun `scrolling back pauses follow during streaming and return to bottom resumes it`() {
        val thread = mutableStateOf(SessionThread(entries = (0L..25L).map { ThreadEntry.AgentText(it, "Message $it\n" + "content ".repeat(30)) }, loaded = true))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionTopScreen(LiveSession.of(session, thread.value), false, SessionControlState(), "", false, scroll, callbacks) } }
            compose.onNodeWithText("Message 25", substring = true).assertIsDisplayed()
            compose.onNodeWithTag("session-thread").performScrollToIndex(10)
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { (scroll.velocity as MutableStateFlow).value = -1f }
            compose.mainClock.advanceTimeBy(150)
            compose.runOnUiThread { (scroll.velocity as MutableStateFlow).value = 0f }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("↓ Revenir en bas").assertIsDisplayed()
            val before = compose.onNodeWithText("Message 10", substring = true).getUnclippedBoundsInRoot()
            compose.runOnIdle { thread.value = thread.value.copy(entries = thread.value.entries + ThreadEntry.AgentText(26, "NEWEST")) }
            assertEquals(before, compose.onNodeWithText("Message 10", substring = true).getUnclippedBoundsInRoot())
            compose.onNodeWithText("↓ Revenir en bas").performClick()
            compose.onNodeWithText("NEWEST", substring = true).assertIsDisplayed()
            compose.runOnIdle { thread.value = thread.value.copy(entries = thread.value.entries.dropLast(1) + ThreadEntry.AgentText(26, "NEWEST\n\n" + "stream\n\n".repeat(40) + "END")) }
            compose.onNodeWithText("END", substring = true).assertIsDisplayed()
            compose.onNodeWithText("↓ Revenir en bas").assertDoesNotExist()
        }
    }
    @Test fun `a paused streaming item keeps the same paragraph at the same position while it grows`() {
        val text = (1..40).joinToString("\n\n") { "Paragraph $it" }
        val thread = mutableStateOf(SessionThread(entries = listOf(ThreadEntry.AgentText(1, text)), loaded = true))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionTopScreen(LiveSession.of(session, thread.value), false, SessionControlState(), "", false, scroll, callbacks) } }
            compose.onNodeWithText("Paragraph 40", substring = true).assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { (scroll.velocity as MutableStateFlow).value = -1f }
            compose.mainClock.advanceTimeBy(150)
            compose.runOnUiThread { (scroll.velocity as MutableStateFlow).value = 0f }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("↓ Revenir en bas").assertIsDisplayed()
            compose.onNodeWithText("Paragraph 30").assertIsDisplayed()
            val before = compose.onNodeWithText("Paragraph 30").getUnclippedBoundsInRoot()
            compose.runOnIdle { thread.value = thread.value.copy(entries = listOf(ThreadEntry.AgentText(1, text + "\n\n" + "new paragraph\n\n".repeat(20)))) }
            compose.onNodeWithText("Paragraph 30").assertIsDisplayed()
            assertEquals(before, compose.onNodeWithText("Paragraph 30").getUnclippedBoundsInRoot())
        }
    }

    @Test fun `following the end survives opening a compact panel that reduces the viewport`() {
        val single = mutableStateOf(false)
        val thread = SessionThread(entries = listOf(ThreadEntry.AgentText(1, "paragraph\n\n".repeat(40) + "LAST_VISIBLE")), loaded = true)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                SessionTopScreen(LiveSession.of(session, thread), false, SessionControlState(), "", single.value, scroll, callbacks,
                    options = { androidx.compose.foundation.text.BasicText("Options") })
            } }
            compose.onNodeWithText("LAST_VISIBLE", substring = true).assertIsDisplayed()
            compose.runOnIdle { single.value = true }
            compose.onNodeWithText("Options").assertIsDisplayed()
            compose.onNodeWithText("LAST_VISIBLE", substring = true).assertIsDisplayed()
        }
    }

}
