package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.runtime.mutableStateOf
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
            host.setup().get().setContent { run {
                GameSessionBottom(LiveSession.of(session, SessionThread(listOf(ThreadEntry.AgentText(1, "Je lance **les tests**.")), loaded = true)), false, null,
                    SessionControlState(notice = SessionNotice.Delivered(Delivery.UNKNOWN)), "", SessionSettingsReview(), scroll, recorder,
                    ApprovalReview(request, listOf(request), pending = true), { _, decision -> decisions += decision }, {}, {})
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
            host.setup().get().setContent { run {
                GameSessionTop(live, false, null, SessionControlState(), "", SessionSettingsReview(), true, scroll, recorder, ApprovalReview(),
                    { _, _ -> }, {}, {}, dictation) { GameDictation(dictation, session, { buttons += it }, {}, {}, scroll, compact = true) }
            } }
            compose.onNode(androidx.compose.ui.test.hasTestTag("house-scene")).assertIsDisplayed()
            compose.onNodeWithText("Lance les tests").assertIsDisplayed()
            compose.onNodeWithText("Lance updatedAt", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Écrire un message").assertDoesNotExist()
            compose.onNodeWithText("Envoyer").performClick()
            assertEquals(listOf(Button.A), buttons)
        }
    }

    // Repris des réglages classiques (ticket 51) : chaque option exposée, sauf le sélecteur de mode de l'agent ; un toucher édite la ligne.
    @Test fun `the settings table lists every option the agent exposes and a touch edits that row`() {
        val recorder = Recorder()
        val options = session.copy(approvalMode = "allow-all", status = SessionStatus.IDLE, configOptions = listOf(
            SessionOption("mode", "Mode", "mode", "agent", listOf(OptionValue("agent", "Agent"))),
            SessionOption("model", "Model", "model", "luna", listOf(OptionValue("luna", "5.6 Luna"), OptionValue("sol", "5.6 Sol"))),
            SessionOption("reasoning_effort", "Reasoning effort", "thought_level", "high", listOf(OptionValue("low", "Low"), OptionValue("high", "High"))),
            SessionOption("fast-mode", "Fast mode", null, "off", listOf(OptionValue("off", "Off"), OptionValue("on", "On")))))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameSessionBottom(LiveSession.of(options, SessionThread(loaded = true)), false, Neighbours(null, null, 1, 1), SessionControlState(), "",
                    SessionSettingsReview(), scroll, recorder, ApprovalReview(), { _, _ -> }, {}, {})
            }
            compose.onNodeWithText("Réglages de Codex").assertIsDisplayed()
            listOf("Mode" to "Tout autoriser", "Modèle" to "5.6 Luna", "Réflexion" to "High", "Fast mode" to "Off").forEach { (label, value) ->
                compose.onNodeWithText(label).assertIsDisplayed()
                compose.onNodeWithText(value).assertIsDisplayed()
            }
            // Le sélecteur de mode de l'agent reste derrière le mode de validation de l'app (ticket 17).
            compose.onNodeWithText("Agent").assertDoesNotExist()
            compose.onNodeWithText("Appliquer").assertDoesNotExist()
            compose.onNodeWithText("Réflexion").performClick()
            assertEquals(listOf("row reasoning_effort"), recorder.events)
        }
    }

    // Repris des réglages classiques : les choix préparés sont dits ; pendant l'application, Interrompre reste, Appliquer et Fermer attendent.
    @Test fun `prepared choices are announced, and while applying Interrompre stays`() {
        val recorder = Recorder()
        val stopped = session.copy(status = SessionStatus.FAILED, active = false, configOptions = listOf(
            SessionOption("model", "Model", "model", "old", listOf(OptionValue("new", "New model"))),
            SessionOption("effort", "Effort", "thought_level", "high", listOf(OptionValue("low", "Low")))))
        val settings = mutableStateOf(SessionSettingsReview(visible = true, session = stopped, selected = 2, value = "low",
            replacements = mapOf("model" to "new", "effort" to "low")))
        val live = mutableStateOf(LiveSession.of(stopped, SessionThread(loaded = true)))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameSessionBottom(live.value, false, null, SessionControlState(), "", settings.value, scroll, recorder, ApprovalReview(), { _, _ -> }, {}, {})
            }
            compose.onNodeWithText("New model").assertIsDisplayed()
            compose.onNodeWithText("Low").assertIsDisplayed()
            compose.onNodeWithText("2 choix préparés. Applique-les pour reprendre.").assertIsDisplayed()
            compose.onNodeWithText("Appliquer").performClick()
            assertEquals(listOf("A"), recorder.events)
            val working = session.copy(status = SessionStatus.WORKING)
            compose.runOnIdle {
                settings.value = SessionSettingsReview(visible = true, session = working, busy = true)
                live.value = LiveSession.of(working, SessionThread(loaded = true))
            }
            compose.onNodeWithText("Application…").assertHasNoClickAction()
            compose.onNodeWithText("Fermer").assertHasNoClickAction()
            compose.onNodeWithText("Interrompre").performClick()
            assertEquals(listOf("A", "START"), recorder.events)
        }
    }

    // Repris de la validation classique en fin de fil : une fois répondue, elle se ferme au doigt et ses choix ne répondent plus.
    @Test fun `a resolved approval is closed by touch and its choices no longer answer`() {
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "touch proof",
            options = listOf(ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
        val decisions = mutableListOf<ApprovalDecision>()
        var closed = false
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameSessionBottom(LiveSession.of(session, SessionThread(loaded = true)), false, null, SessionControlState(), "", SessionSettingsReview(), scroll,
                    Recorder(), ApprovalReview(request = request, answer = ApprovalAnswer(ApprovalReply.ACCEPTED)), { _, d -> decisions += d }, { closed = true }, {})
            }
            compose.onNodeWithText("Voir les modifications").assertDoesNotExist()
            compose.onNodeWithText("Autoriser une fois").assertHasNoClickAction()
            compose.onNodeWithText("Fermer la validation").assertIsDisplayed().performClick()
            assertEquals(true, closed)
            assertEquals(emptyList(), decisions)
        }
    }

    // Repris de la dictée classique : sur un seul écran, après Compléter ou Corriger, L2 se tient au doigt et se relâche.
    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `on a single screen L2 is held by touch after completing or correcting`() {
        listOf("Compléter" to Button.Y, "Corriger un mot" to Button.X).forEach { (label, key) ->
            val state = mutableStateOf(DictationReview(DictationTarget.Message(session.id), DictationPhase.REVIEW, "Modifie PassageCheck.ts"))
            val buttons = mutableListOf<Button>()
            val holds = mutableListOf<Boolean>()
            Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
                host.setup().get().setContent {
                    GameDictation(state.value, session, { buttons += it }, { held ->
                        holds += held
                        state.value = state.value.copy(phase = if (held) DictationPhase.RECORDING else DictationPhase.REVIEW)
                    }, {}, scroll, compact = true)
                }
                compose.onNodeWithText(label).performClick()
                assertEquals(listOf(key), buttons)
                compose.onNodeWithText("Parler").performTouchInput { down(center) }
                compose.runOnIdle { assertEquals(listOf(true), holds) }
                compose.onNodeWithText("Parler").performTouchInput { up() }
                compose.runOnIdle { assertEquals(listOf(true, false), holds) }
                compose.onNodeWithText("Envoyer").assertIsDisplayed()
            }
        }
    }
}
