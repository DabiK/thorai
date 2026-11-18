package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.session.SessionControlState
import app.regie.session.SessionNotice
import app.regie.ui.screens.SessionCallbacks
import app.regie.ui.screens.SessionTopScreen
import app.regie.ui.screens.ThreadScroll
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Ticket 22 (formerly the written-report panel test): the thread shows what the agent produced and
 * nothing Régie adds on top, even at the end of a turn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class RawThreadTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val t0 = Instant.parse("2026-11-18T14:18:05Z")
    private val session = Session(SessionId("s"), Agent.CODEX, "demo", "main", null, SessionStatus.IDLE, true, t0, t0, approvalMode = "allow-all")
    private val callbacks = object : SessionCallbacks {
        override fun onButton(button: Button) {}
        override fun onTrigger(trigger: Trigger, held: Boolean) {}
        override fun onDraftChange(text: String) {}
        override fun onSend() {}
        override fun onNoticeSeen(notice: SessionNotice) {}
    }
    private val scroll = ThreadScroll(MutableSharedFlow(extraBufferCapacity = 16), MutableStateFlow(0f))

    @Test fun `an ended turn shows the agent words only, without report, labels or empty side panel`() {
        val thread = SessionThread(listOf(
            ThreadEntry.UserMessage(1, "Corrige le seuil"),
            ThreadEntry.AgentText(2, "J’ai corrigé le seuil.\n\n[Point Régie]\nFait : seuil corrigé\nTests : non communiqués\nSuite : rien\nQuestion : aucune\n[/Point Régie]"),
        ), loaded = true, timestamps = mapOf(1L to t0, 2L to t0 + 20.seconds))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                SessionTopScreen(LiveSession.of(session, thread), false, SessionControlState(), "", false, scroll, callbacks)
            } }
            compose.onNodeWithText("Corrige le seuil").assertIsDisplayed()
            compose.onNodeWithText("J’ai corrigé le seuil.").assertIsDisplayed()
            for (added in listOf("Point de session", "Point Régie", "Fait :", "Tests :", "Suite :", "Question :", "Bilan", "Toi",
                "Plan non communiqué", "Aucune modification suivie", "→ Régler")) {
                compose.onAllNodesWithText(added, substring = true).assertCountEquals(0)
            }
            // Both items fall in the same minute: the time is written once.
            compose.onAllNodesWithText(":18", substring = true).assertCountEquals(1)
        }
    }

    @Test fun `the plan and files appear only when the agent exposes them`() {
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, "Je commence.")), loaded = true,
            plan = listOf(PlanStep("Lire le code", PlanStepStatus.IN_PROGRESS)))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                SessionTopScreen(LiveSession.of(session.copy(status = SessionStatus.WORKING), thread), false, SessionControlState(), "", false, scroll, callbacks)
            } }
            compose.onNodeWithText("Plan").assertIsDisplayed()
            compose.onNodeWithText("Lire le code").assertIsDisplayed()
            compose.onNodeWithText("Fichiers modifiés").assertDoesNotExist()
        }
    }
}
