package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.screens.NewSessionScreen
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class NewSessionScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val form = NewSessionForm(visible = true, agent = Agent.CODEX, project = "demo", projects = listOf(ProjectChoice("demo", "main", listOf("main"))),
        branchChoice = BranchChoice.NEW, branch = "essai", base = "main", instruction = "Corrige le scan", approvalMode = "auto-edit")

    @Test fun `project row explains loading and unreachable bridge`() {
        val state = mutableStateOf(NewSessionForm(visible = true, loading = true))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                NewSessionScreen(state.value, bottom = false, single = false, onButton = {}, onHeld = {}, onField = {}, onInstruction = {}, onBranch = {}, velocity = MutableStateFlow(0f))
            } }
            compose.onNodeWithText("Chargement des projets…").assertIsDisplayed()
            compose.runOnIdle { state.value = state.value.copy(loading = false, catalogAvailable = false) }
            compose.onNodeWithText("Pont injoignable · reconnexion…").assertIsDisplayed()
        }
    }

    @Test fun `top displays editable values and compact layout keeps launch and cancel visible`() {
        val buttons = mutableListOf<Button>()
        val state = mutableStateOf(form)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                NewSessionScreen(state.value, bottom = false, single = true, onButton = { buttons += it }, onHeld = {}, onField = {}, onInstruction = {}, onBranch = {}, velocity = MutableStateFlow(0f))
            } }
            compose.onNodeWithText("Codex").assertIsDisplayed()
            compose.onNodeWithText("demo").assertIsDisplayed()
            compose.onNodeWithText("Lancer la session").assertIsDisplayed().performClick()
            compose.onNodeWithText("Annuler").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf(Button.A, Button.B), buttons); state.value = form.copy(field = NewSessionField.INSTRUCTION) }
            compose.onNodeWithText("Corrige le scan").assertIsDisplayed()
            compose.runOnIdle { state.value = form.copy(uncertain = true) }
            compose.onNodeWithText("Lancer la session").assertIsNotEnabled()
        }
    }

    @Test fun `bottom supports held redictation and editing the new branch name`() {
        val holds = mutableListOf<Boolean>(); val names = mutableListOf<String>()
        val state = mutableStateOf(form.copy(field = NewSessionField.BRANCH))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                NewSessionScreen(state.value, bottom = true, single = false,
                    onButton = {}, onHeld = { holds += it; state.value = state.value.copy(capturing = it) }, onField = {}, onInstruction = {}, onBranch = { names += it }, velocity = MutableStateFlow(0f))
            } }
            compose.onNodeWithTag("branch-name").performTextReplacement("autre")
            compose.runOnIdle { assertEquals(listOf("autre"), names) }
            val mic = compose.onNodeWithText("Redicter la consigne")
            mic.assertIsDisplayed().performTouchInput { down(center) }
            compose.runOnIdle { assertEquals(listOf(true), holds) }
            mic.performTouchInput { up() }
            compose.runOnIdle { assertEquals(listOf(true, false), holds) }
        }
    }
}
