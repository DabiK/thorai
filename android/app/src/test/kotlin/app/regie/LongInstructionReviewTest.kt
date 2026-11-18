package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.input.TriggerAxes
import app.regie.ui.game.GameNewSessionBottom
import app.regie.ui.game.buildCallbacks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR 13 : une longue consigne se relit au stick droit jusqu'à sa dernière ligne avant A. La fiche
 * du jeu (ticket 48) remplace la fiche classique depuis le ticket 51 ; la preuve reste la même.
 * En mdpi, un pixel vaut un dp : la mise en page du texte se compare directement aux bornes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class LongInstructionReviewTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test @Config(qualifiers = "w832dp-h468dp-land-mdpi")
    fun `single screen allows reading the last instruction line with the stick before launch`() = readToEnd(single = true)

    @Test fun `two screens allow reading the last instruction line with the stick before launch`() = readToEnd(single = false)

    private fun readToEnd(single: Boolean) {
        val instruction = "Vérifie chaque fichier et explique les résultats avant de proposer une correction.\n".repeat(24)
            .take(1918) + "\nDernière ligne : ne lance rien."
        val form = NewSessionForm(visible = true, agent = Agent.CODEX, project = "demo",
            projects = listOf(ProjectChoice("demo", "main", listOf("main"))),
            instruction = instruction, field = NewSessionField.INSTRUCTION)
        assertTrue(form.canLaunch)
        assertEquals(1950, instruction.length)
        // No start(): no gateway, microphone, Android service or agent is run.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app, app.getSystemService(DisplayManager::class.java))
        container.newSession.value = form
        val callbacks = buildCallbacks({ _: Button -> }, {}, {}, {}, {}, {})
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().also { it.actionBar?.hide() }.setContent {
                GameNewSessionBottom(form, callbacks, compact = single, scrollVelocity = container.newSessionVelocity)
            }
            val text = compose.onNodeWithText(instruction, useUnmergedTree = true)
            val layout = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val lastLine = layout.single().lineCount - 1
            val viewport = compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("batir-champ-INSTRUCTION"))).getUnclippedBoundsInRoot()
            fun lastBottom() = text.getUnclippedBoundsInRoot().top.value + layout.single().getLineBottom(lastLine)
            assertTrue(lastBottom() > viewport.bottom.value, "Regression fixture must overflow the viewport")
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { container.inputRouter.onAxes(TriggerAxes(0f, 0f, rightStickY = 1f)) }
            compose.mainClock.advanceTimeBy(2500)
            compose.waitForIdle()
            val lastTop = text.getUnclippedBoundsInRoot().top.value + layout.single().getLineTop(lastLine)
            assertTrue(lastTop >= viewport.top.value && lastBottom() <= viewport.bottom.value,
                "Last line must be entirely visible before A: top=$lastTop bottom=${lastBottom()}, viewport=$viewport")
            compose.onNodeWithText("Bâtir").assertIsDisplayed()
            compose.onNodeWithTag("batir-lancer").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))

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
