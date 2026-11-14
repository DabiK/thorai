package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.input.TriggerAxes
import app.regie.ui.screens.NewSessionScreen
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class LongInstructionReviewTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `single screen allows reading the last instruction line with the stick before launch`() = readToEnd(single = true)
    @Test fun `two screens allow reading the last instruction line with the stick before launch`() = readToEnd(single = false)

    private fun readToEnd(single: Boolean) {
        val instruction = "Vérifie chaque fichier et explique les résultats avant de proposer une correction.\n".repeat(24)
            .take(1918) + "\nDernière ligne : ne lance rien."
        val form = NewSessionForm(visible = true, agent = Agent.CODEX, project = "demo",
            projects = listOf(ProjectChoice("demo", "main", listOf("main"))),
            instruction = instruction, field = NewSessionField.INSTRUCTION)
        assertTrue(form.canLaunch)
        assertEquals(1950, instruction.length)
        // No start(): no gateway, microphone, Android service or agent is run.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java))
        container.newSession.value = form
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                NewSessionScreen(form, bottom = false, single = single, onButton = {}, onHeld = {},
                    onField = {}, onInstruction = {}, onBranch = {}, velocity = container.newSessionVelocity)
            } }
            val text = compose.onNodeWithText(instruction, useUnmergedTree = true)
            val layout = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val lastLine = layout.single().lineCount - 1
            val viewport = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
            fun lastBottom() = text.getUnclippedBoundsInRoot().top.value + layout.single().getLineBottom(lastLine)
            assertTrue(lastBottom() > viewport.bottom.value, "Regression fixture must overflow the viewport")
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { container.inputRouter.onAxes(TriggerAxes(0f, 0f, rightStickY = 1f)) }
            compose.mainClock.advanceTimeBy(2500)
            compose.waitForIdle()
            val lastTop = text.getUnclippedBoundsInRoot().top.value + layout.single().getLineTop(lastLine)
            assertTrue(lastTop >= viewport.top.value && lastBottom() <= viewport.bottom.value,
                "Last line must be entirely visible before A: top=$lastTop bottom=${lastBottom()}, viewport=$viewport")
            if (single) compose.onNodeWithText("Lancer la session").assertIsDisplayed().assertIsEnabled()

            // Reverse direction, then release while settings own the inputs: no drift on return.
            compose.runOnIdle { container.inputRouter.onAxes(TriggerAxes(0f, 0f, rightStickY = -1f)) }
            compose.mainClock.advanceTimeBy(160)
            compose.waitForIdle()
            val reversed = text.getUnclippedBoundsInRoot().top.value
            assertTrue(lastBottom() > viewport.bottom.value, "The stick must also scroll back up")
            compose.runOnIdle {
                container.openVoiceSettings()
                container.inputRouter.reset()
                container.closeVoiceSettings()
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            assertEquals(reversed, text.getUnclippedBoundsInRoot().top.value, "Releasing the stick stops scrolling even behind settings")
        }
    }
}
