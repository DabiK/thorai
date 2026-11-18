package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.regie.domain.model.*
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.house.AgentBlock
import app.regie.game.ui.house.AgentSpan
import app.regie.game.ui.house.PadKey
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.session.SessionControlState
import app.regie.session.SessionNotice
import app.regie.ui.game.GameDictation
import app.regie.ui.game.GameSessionBottom
import app.regie.ui.game.GameSessionTop
import app.regie.ui.game.MarkdownFormat
import app.regie.ui.game.house
import app.regie.ui.screens.SessionCallbacks
import app.regie.ui.screens.ThreadScroll
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

/** Ticket 46 : la maison reçoit l'état existant et rend la main aux contrôleurs de l'app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-369dpi")
class HouseWiringTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val session = Session(SessionId("s"), Agent.CODEX, "demo", "main", null, SessionStatus.WORKING, true,
        Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask")
    private val scroll = ThreadScroll(MutableSharedFlow(extraBufferCapacity = 16), MutableStateFlow(0f))

    private class Recorder : SessionCallbacks {
        val events = mutableListOf<String>()
        override fun onButton(button: Button) { events += button.name }
        override fun onOptions() { events += "options" }
        override fun onSettingRow(row: String) { events += "row $row" }
        override fun onTrigger(trigger: Trigger, held: Boolean) { events += "$trigger $held" }
        override fun onDraftChange(text: String) { events += "draft $text" }
        override fun onSend() { events += "send" }
        override fun onNoticeSeen(notice: SessionNotice) { events += "seen $notice" }
    }

    @Test fun `agent markdown keeps the app's reading, without rewriting the words`() {
        assertEquals(
            listOf(AgentBlock.Text(listOf(AgentSpan("Le "), AgentSpan("tri", bold = true), AgentSpan(" sur "), AgentSpan("updatedAt", code = true), AgentSpan(".")))),
            MarkdownFormat.blocks("Le **tri** sur `updatedAt`."),
        )
        assertEquals(listOf(AgentBlock.Code("val x = 1")), MarkdownFormat.blocks("```\nval x = 1\n```"))
    }

    @Test fun `the house asks the same controllers as the classic session screens`() {
        val recorder = Recorder()
        val selected = mutableListOf<Int>()
        val house = recorder.house { selected += it }
        house.onKey(PadKey.START); house.onKey(PadKey.X); house.onSpeak(true); house.onSpeak(false)
        house.onOpenSettings(null); house.onOpenSettings("model"); house.onSelectSetting(2); house.onDraftChange("a"); house.onSend()
        assertEquals(listOf("START", "X", "L2 true", "L2 false", "options", "row model", "draft a", "send"), recorder.events)
        assertEquals(listOf(2), selected)
    }

    @Test fun `the bottom of a session is the house notebook, with the app notices and the inline approval`() {
        val recorder = Recorder()
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "touch proof",
            options = listOf(ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
        val decisions = mutableListOf<ApprovalDecision>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { app.regie.ui.theme.RegieTheme {
                GameSessionBottom(LiveSession.of(session, SessionThread(listOf(ThreadEntry.AgentText(1, "Je lance **les tests**.")), loaded = true)), false, null,
                    SessionControlState(notice = SessionNotice.Delivered(Delivery.UNKNOWN)), "", SessionSettingsReview(), scroll, recorder,
                    ApprovalReview(request, listOf(request), pending = true), { _, decision -> decisions += decision }, {}, {}, {})
            } }
            compose.onNodeWithText("Je lance les tests.▌", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Envoi non confirmé. Vérifie le fil avant de renvoyer.").assertIsDisplayed()
            compose.onNodeWithText("Réglages de Codex").assertIsDisplayed()
            compose.onNodeWithText("Autoriser une fois").performClick()
            assertEquals(listOf(ApprovalDecision.ONCE), decisions)
        }
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `on a single screen a dictation keeps the house and the thread, and takes the place of the field and keys`() {
        val recorder = Recorder()
        val live = LiveSession.of(session, SessionThread(listOf(ThreadEntry.UserMessage(1, "Lance les tests")), loaded = true))
        val dictation = DictationReview(DictationTarget.Message(session.id), DictationPhase.REVIEW, "Lance updatedAt", vocabulary = listOf("updatedAt"))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { app.regie.ui.theme.RegieTheme {
                GameSessionTop(live, false, null, SessionControlState(), "", SessionSettingsReview(), true, scroll, recorder, ApprovalReview(),
                    { _, _ -> }, {}, {}, {}, dictation) { GameDictation(dictation, session, { buttons += it }, {}, {}, scroll, compact = true) }
            } }
            compose.onNode(androidx.compose.ui.test.hasTestTag("house-scene")).assertIsDisplayed()
            compose.onNodeWithText("Lance les tests").assertIsDisplayed()
            compose.onNodeWithText("Lance updatedAt", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Écrire un message").assertDoesNotExist()
            compose.onNodeWithText("Envoyer").performClick()
            assertEquals(listOf(Button.A), buttons)
        }
    }
}
