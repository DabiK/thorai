package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.screens.SessionSettingsScreen
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class SessionSettingsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `compact options keep session neighbours and selected model with apply on the bottom screen`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.IDLE, true,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask",
            configOptions = listOf(SessionOption("model", "Model", "model", "one", listOf(OptionValue("one", "Model one"), OptionValue("two", "Model two")))))
        val settings = SessionSettingsReview(visible = true, session = session, selected = 1, value = "two")
        val buttons = mutableListOf<Button>()
        val callbacks = object : app.regie.ui.screens.SessionCallbacks {
            override fun onButton(button: Button) { buttons += button }
            override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) {}
            override fun onDraftChange(text: String) {}
            override fun onSend() {}
            override fun onNoticeSeen(notice: app.regie.session.SessionNotice) {}
        }
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                app.regie.ui.screens.SessionBottomScreen(LiveSession.of(session, SessionThread(loaded = true)),
                    Neighbours(null, null, 1, 1), app.regie.session.SessionControlState(), "draft kept", callbacks,
                    settings = settings)
            } }
            compose.onNodeWithText("1 / 1").assertIsDisplayed()
            compose.onNodeWithText("draft kept").assertIsDisplayed()
            compose.onNodeWithText("Model two").assertIsDisplayed()
            compose.onNodeWithText("Appliquer").assertIsDisplayed().performClick()
            assertEquals(listOf(Button.A), buttons)
            compose.onNodeWithText("Fermer").assertIsDisplayed()
        }
    }

    @Test fun `the bottom table lists every option the agent exposes and a touch edits that row`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.IDLE, true,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "allow-all",
            configOptions = listOf(
                SessionOption("mode", "Mode", "mode", "agent", listOf(OptionValue("agent", "Agent"))),
                SessionOption("model", "Model", "model", "luna", listOf(OptionValue("luna", "5.6 Luna"), OptionValue("sol", "5.6 Sol"))),
                SessionOption("reasoning_effort", "Reasoning effort", "thought_level", "high", listOf(OptionValue("low", "Low"), OptionValue("high", "High"))),
                SessionOption("fast-mode", "Fast mode", null, "off", listOf(OptionValue("off", "Off"), OptionValue("on", "On")))))
        val rows = mutableListOf<String>()
        val callbacks = object : app.regie.ui.screens.SessionCallbacks {
            override fun onButton(button: Button) {}
            override fun onSettingRow(row: String) { rows += row }
            override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) {}
            override fun onDraftChange(text: String) {}
            override fun onSend() {}
            override fun onNoticeSeen(notice: app.regie.session.SessionNotice) {}
        }
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                app.regie.ui.screens.SessionBottomScreen(LiveSession.of(session, SessionThread(loaded = true)),
                    Neighbours(null, null, 1, 1), app.regie.session.SessionControlState(), "", callbacks)
            } }
            compose.onNodeWithText("Réglages de Codex").assertIsDisplayed()
            listOf("Mode" to "Tout autoriser", "Modèle" to "5.6 Luna", "Réflexion" to "High", "Fast mode" to "Off").forEach { (label, value) ->
                compose.onNodeWithText(label).assertIsDisplayed()
                compose.onNodeWithText(value).assertIsDisplayed()
            }
            // The agent's own mode selector stays behind Régie's approval mode, as in ticket 17.
            compose.onNodeWithText("Agent").assertDoesNotExist()
            compose.onNodeWithText("Appliquer").assertDoesNotExist()
            compose.onNodeWithText("Réflexion").performClick()
            assertEquals(listOf("reasoning_effort"), rows)
        }
    }

    @Test fun `interrupt stays visible and clickable while applying then disappears when idle`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.WORKING, true,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask")
        val state = mutableStateOf(SessionSettingsReview(visible = true, session = session))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionSettingsScreen(state.value, { buttons += it }, {}) } }
            compose.onNodeWithText("Interrompre").assertIsDisplayed().assertIsEnabled().performClick()
            compose.runOnIdle { state.value = state.value.copy(busy = true) }
            compose.onNodeWithText("Interrompre").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Application…").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("Fermer").assertIsDisplayed().assertIsNotEnabled()
            assertEquals(listOf(Button.START, Button.START), buttons)
            compose.runOnIdle { state.value = state.value.copy(session = session.copy(status = SessionStatus.IDLE)) }
            compose.onNodeWithText("Interrompre").assertDoesNotExist()
        }
    }
    @Test fun `prepared replacements stay visible on other rows and A announces joint confirmation`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.FAILED, false,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "allow-all",
            configOptions = listOf(
                SessionOption("model", "Model", "model", "old", listOf(OptionValue("new", "New model"))),
                SessionOption("effort", "Effort", "thought_level", "high", listOf(OptionValue("low", "Low")))))
        val state = SessionSettingsReview(visible = true, session = session, selected = 2, value = "low",
            replacements = mapOf("model" to "new", "effort" to "low"))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionSettingsScreen(state, { buttons += it }, {}) } }
            compose.onNodeWithText("New model").assertIsDisplayed()
            compose.onNodeWithText("Low").assertIsDisplayed()
            compose.onNodeWithText("2 choix préparés. Applique-les pour reprendre.").assertIsDisplayed()
            compose.onNodeWithText("Appliquer").assertIsEnabled().performClick()
            assertEquals(listOf(Button.A), buttons)
        }
    }
    @Test fun `small screen keeps selected option and explicit apply visible without optimistic mode`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.IDLE, true,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask",
            configOptions = listOf(SessionOption("remote-model", "Model", "model", "one", listOf(OptionValue("one", "Remote one"), OptionValue("two", "Remote two")))))
        val state = mutableStateOf(SessionSettingsReview(visible = true, session = session, selected = 1, value = "two"))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionSettingsScreen(state.value, { buttons += it }, {}) } }
            compose.onNodeWithText("Mode : Je valide chaque action").assertIsDisplayed()
            compose.onNodeWithText("Remote two").assertIsDisplayed()
            compose.onNodeWithText("Appliquer").assertIsDisplayed().performClick()
            assertEquals(listOf(Button.A), buttons)
            compose.runOnIdle { state.value = state.value.copy(busy = true) }
            compose.onNodeWithText("Application…").assertIsNotEnabled()
            compose.onNodeWithText("Fermer").assertIsNotEnabled()
        }
    }

    @Test fun `resume allows model confirmation while saved effort has no provisional choices`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.FAILED, false,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "allow-all",
            configOptions = listOf(
                SessionOption("model", "Model", "model", "removed", listOf(OptionValue("new", "New model"))),
                SessionOption("effort", "Effort", "thought_level", "high", emptyList())))
        val state = SessionSettingsReview(visible = true, session = session, selected = 3,
            replacements = mapOf("model" to "new"))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionSettingsScreen(state, { buttons += it }, {}) } }
            compose.onNodeWithText("New model").assertIsDisplayed()
            compose.onNodeWithText("high").assertIsDisplayed()
            compose.onNodeWithText("Mode : Tout autoriser").assertIsDisplayed()
            compose.onNodeWithText("Appliquer").assertIsDisplayed().assertIsEnabled().performClick()
            assertEquals(listOf(Button.A), buttons)
        }
    }
}
