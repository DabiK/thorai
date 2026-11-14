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
    @Test fun `interrupt stays visible and clickable while applying then disappears when idle`() {
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.WORKING, true,
            Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask")
        val state = mutableStateOf(SessionSettingsReview(visible = true, session = session))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { SessionSettingsScreen(state.value, { buttons += it }, {}) } }
            compose.onNodeWithText("Start · Interrompre").assertIsDisplayed().assertIsEnabled().performClick()
            compose.runOnIdle { state.value = state.value.copy(busy = true) }
            compose.onNodeWithText("Start · Interrompre").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText("Application…").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("B · Fermer").assertIsDisplayed().assertIsNotEnabled()
            assertEquals(listOf(Button.START, Button.START), buttons)
            compose.runOnIdle { state.value = state.value.copy(session = session.copy(status = SessionStatus.IDLE)) }
            compose.onNodeWithText("Start · Interrompre").assertDoesNotExist()
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
            compose.onNodeWithText("2 choix préparés · A confirme l’ensemble pour reprendre.").assertIsDisplayed()
            compose.onNodeWithText("A · Appliquer").assertIsEnabled().performClick()
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
            compose.onNodeWithText("En cours : Je valide chaque action").assertIsDisplayed()
            compose.onNodeWithText("Remote two").assertIsDisplayed()
            compose.onNodeWithText("A · Appliquer").assertIsDisplayed().performClick()
            assertEquals(listOf(Button.A), buttons)
            compose.runOnIdle { state.value = state.value.copy(busy = true) }
            compose.onNodeWithText("Application…").assertIsNotEnabled()
            compose.onNodeWithText("B · Fermer").assertIsNotEnabled()
        }
    }
}
