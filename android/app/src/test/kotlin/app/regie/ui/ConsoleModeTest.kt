package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.overlay.OverlayContent
import app.regie.overview.ControlState
import app.regie.domain.usecase.ApprovalReview
import app.regie.session.SessionControlState
import app.regie.ui.game.ConsoleSessionBottom
import app.regie.ui.game.GameConsoleOverlay
import androidx.compose.runtime.mutableStateOf
import app.regie.ui.screens.ControlCallbacks
import app.regie.ui.screens.OverviewBottomScreen
import app.regie.ui.game.GameSessionBottom
import app.regie.ui.screens.OverviewShortcuts
import app.regie.ui.screens.SessionCallbacks
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class ConsoleModeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val session = Session(SessionId("s"), Agent.CODEX, "console", null, null, SessionStatus.WORKING, true,
        Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask")
    private val thread = SessionThread(listOf(ThreadEntry.UserMessage(1, "ouvre YouTube et cherche lofi"),
        ThreadEntry.AgentText(2, "J’ouvre YouTube."),
        ThreadEntry.ToolCall(3, ToolKind.OTHER, "mobile_open_url", status = ActionStatus.IN_PROGRESS)), loaded = true)
    private val scroll = ThreadScroll(MutableSharedFlow(), MutableStateFlow(0f))
    private val buttons = mutableListOf<Button>()
    private val sessionCallbacks = object : SessionCallbacks {
        override fun onButton(button: Button) { buttons += button }
        override fun onTrigger(trigger: Trigger, held: Boolean) {}
        override fun onDraftChange(text: String) {}
        override fun onSend() {}
        override fun onNoticeSeen(notice: app.regie.session.SessionNotice) {}
    }

    // La session console (tickets 46 et 49) : la tour en en-tête, le carnet de la session dessous.
    @Test @Config(qualifiers = "w538dp-h468dp-land-369dpi")
    fun `another app on top keeps the thread on the bottom, names the session, settings one gesture away`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                ConsoleSessionBottom(session, LiveSession.of(session, thread), false, Neighbours(null, null, 1, 1), SessionControlState(), "",
                    SessionSettingsReview(), scroll, sessionCallbacks, ApprovalReview(), { _, _ -> }, {}, {}, {})
            } }
            compose.onNodeWithTag("console-tower").assertIsDisplayed()
            compose.onNodeWithText("Tour de la console").assertIsDisplayed()
            compose.onNodeWithText("Codex pilote Android").assertIsDisplayed()
            compose.onNodeWithTag("house-thread").assertIsDisplayed()
            compose.onNodeWithText("ouvre YouTube et cherche lofi").assertIsDisplayed()
            compose.onNodeWithText("J’ouvre YouTube.", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Codex · console").assertIsDisplayed()
            compose.onNodeWithText("→ Modifier").performClick()
            assertEquals(listOf(Button.RIGHT), buttons)
            compose.onNodeWithText("Parler").assertIsDisplayed()
        }
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    // ADR 19 : la tour ne retire rien au carnet. Avis, brouillon et touches restent ceux de la session.
    @Test @Config(qualifiers = "w538dp-h468dp-land-369dpi")
    fun `the console tower keeps every notice and the draft of the session`() {
        val notices = listOf(
            app.regie.session.SessionNotice.NothingHeard to "Rien entendu. Aucun message envoyé.",
            app.regie.session.SessionNotice.Delivered(Delivery.NOT_SENT) to "Le message n'est pas parti.",
            app.regie.session.SessionNotice.Interrupted(heard = false) to "Le pont n'a pas pu l'interrompre.",
        )
        val control = mutableStateOf(SessionControlState())
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            val activity = host.setup().get().also { it.actionBar?.hide() }
            activity.setContent { RegieTheme {
                ConsoleSessionBottom(session, LiveSession.of(session, thread), false, Neighbours(null, null, 1, 1), control.value, "Brouillon tapé",
                    SessionSettingsReview(), scroll, sessionCallbacks, ApprovalReview(), { _, _ -> }, {}, {}, {})
            } }
            compose.onNodeWithTag("console-tower").assertIsDisplayed()
            compose.onNodeWithText("Brouillon tapé").assertIsDisplayed()
            notices.forEach { (notice, text) ->
                control.value = SessionControlState(notice = notice)
                compose.onNodeWithText(text).assertIsDisplayed()
            }
            GameCaptures.save(compose, activity, "tour-bas")
        }
    }

    @Test @Config(qualifiers = "w538dp-h468dp-land-369dpi")
    fun `with Régie on top the bottom keeps its thread and settings table, without the session name`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                GameSessionBottom(LiveSession.of(session, thread), false, Neighbours(null, null, 1, 1), SessionControlState(), "", SessionSettingsReview(),
                    scroll, sessionCallbacks, ApprovalReview(), { _, _ -> }, {}, {}, {})
            } }
            compose.onNodeWithTag("house-settings").assertIsDisplayed()
            compose.onNodeWithTag("house-thread").assertIsDisplayed()
            compose.onNodeWithTag("console-session").assertDoesNotExist()
        }
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `a message card shows the agent, its words and the keys that answer`() {
        val card = FloatingCard("message:s:2", session.id, Agent.CODEX, "console", FloatingKind.QUESTION, "La recherche est lancée. Je lance la vidéo ?")
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameConsoleOverlay(OverlayContent.Card(card), {}, scroll, scroll) }
            compose.onNodeWithText("Codex").assertIsDisplayed()
            compose.onNodeWithText("console").assertIsDisplayed()
            compose.onNodeWithText("! Attend ta réponse").assertIsDisplayed()
            compose.onNodeWithText("La recherche est lancée. Je lance la vidéo ?").assertIsDisplayed()
            compose.onNodeWithText("Répondre").assertIsDisplayed()
            compose.onNodeWithText("Fermer").assertIsDisplayed()
            compose.onNodeWithText("Autoriser une fois").assertDoesNotExist()
        }
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `an approval card offers A once, B to close and L2 to refuse`() {
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "am start -a VIEW",
            options = listOf(ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Oui"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "Non")))
        val card = FloatingCard("approval:a", session.id, Agent.CODEX, "console", FloatingKind.APPROVAL, "am start -a VIEW", request)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameConsoleOverlay(OverlayContent.Card(card), {}, scroll, scroll) }
            compose.onNodeWithText("! Attend ta validation").assertIsDisplayed()
            compose.onNodeWithText("Autoriser une fois").assertIsDisplayed()
            compose.onNodeWithText("Refuser en dictant").assertIsDisplayed()
        }
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `an approval card offers only the choices the agent gives`() {
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.OTHER, "outil", options = emptyList())
        val card = FloatingCard("approval:a", session.id, Agent.CODEX, "console", FloatingKind.APPROVAL, "outil", request)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameConsoleOverlay(OverlayContent.Card(card), {}, scroll, scroll) }
            compose.onNodeWithText("Fermer").assertIsDisplayed()
            compose.onNodeWithText("Autoriser une fois").assertDoesNotExist()
            compose.onNodeWithText("Refuser en dictant").assertDoesNotExist()
        }
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `a reply dictated over another app shows the game dictation and the sentence it answers`() {
        val dictation = DictationReview(DictationTarget.Message(session.id), DictationPhase.REVIEW, "oui, lance la vidéo")
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameConsoleOverlay(OverlayContent.Dictation(dictation, session, replyingTo = "Je lance la vidéo ?"), {}, scroll, scroll) }
            compose.onNodeWithTag("console-dictation").assertIsDisplayed()
            compose.onNodeWithText("En réponse à\u00A0: Je lance la vidéo ?").assertIsDisplayed()
            compose.onNodeWithText("oui, lance la vidéo", substring = true).assertExists()
        }
    }

    @Test fun `the overview offers the console session, and Configurer replaces the permanent console notice`() {
        val pressed = mutableListOf<Button>()
        val callbacks = object : ControlCallbacks {
            override fun onButton(button: Button) { pressed += button }
            override fun onTrigger(trigger: Trigger, held: Boolean) {}
            override fun onOpenApproval() {}
            override fun onQuit() {}
            override fun onConfirmQuit() {}
            override fun onCancelQuit() {}
        }
        var setup = 0
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                androidx.compose.foundation.layout.Column {
                    OverviewShortcuts(setupLeft = true, onAgents = {}, onSetup = { setup++ }, onVoice = {})
                    OverviewBottomScreen(Overview.EMPTY, ControlState(), callbacks)
                }
            } }
            compose.onNodeWithText("Piloter la console").performClick()
            assertEquals(listOf(Button.Y), pressed)
            compose.onNodeWithText("Mode console").assertDoesNotExist()
            compose.onNodeWithText("Maintiens R2", substring = true).assertDoesNotExist()
            compose.onNodeWithTag("setup-left", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("open-setup").performClick()
            assertEquals(1, setup)
        }
    }

    @Test fun `nothing covers Régie, and the dictation goes on top only when the bottom cannot show it`() {
        val live = LiveSession.of(session, thread)
        val dictation = DictationReview(DictationTarget.Message(session.id), DictationPhase.REVIEW, "ouvre YouTube")
        val card = FloatingCard("k", session.id, Agent.CODEX, "console", FloatingKind.MESSAGE, "Fait.")
        val idle = DictationReview()
        val noCommand = VoiceCommandReview()
        assertNull(OverlayContent.of(true, false, dictation, noCommand, card, false, live, listOf(session)))
        // The bottom shows Régie: the conversation is there, no card and no bubble on top.
        assertNull(OverlayContent.of(false, true, idle, noCommand, card, false, live, listOf(session)))
        assertNull(OverlayContent.of(false, true, dictation, noCommand, card, true, live, listOf(session)))
        assertEquals(OverlayContent.Dictation(dictation, session, replyingTo = "Fait."),
            OverlayContent.of(false, false, dictation, noCommand, card, true, live, listOf(session)))
        assertEquals(OverlayContent.Card(card, keys = false), OverlayContent.of(false, false, idle, noCommand, card, false, live, listOf(session), cardKeys = false))
        assertTrue(OverlayContent.of(false, false, idle, noCommand, null, false, live, listOf(session)) is OverlayContent.Bubble)
        val resting = LiveSession.of(session.copy(status = SessionStatus.IDLE), thread)
        assertNull(OverlayContent.of(false, false, idle, noCommand, null, false, resting, listOf(session)))
    }
}
