package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.DictationPhase
import app.regie.domain.model.DictationReview
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.screens.DictationTopScreen
import app.regie.ui.screens.DictationBottomScreen
import app.regie.ui.screens.ThreadScroll
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
// Neutral host: no application container, bridge, microphone or agent is started.
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class DictationScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `single screen can hold and release the microphone after completing`() =
        holdAfterAction("Compléter", Button.Y, DictationPhase.APPEND)

    @Test fun `single screen can hold and release the microphone after correcting`() =
        holdAfterAction("Corriger un mot", Button.X, DictationPhase.CORRECT)

    @Test fun `the bottom screen shows the live transcription with its recipient and the review keys`() {
        val session = Session(SessionId("s"), Agent.CODEX, "teste-v-deux", "main", null, SessionStatus.IDLE, true,
            kotlin.time.Instant.fromEpochMilliseconds(0), kotlin.time.Instant.fromEpochMilliseconds(0))
        val state = mutableStateOf(DictationReview(DictationTarget.Message(session.id), DictationPhase.RECORDING, text = "Ouvre PassageCheck.ts",
            vocabulary = listOf("PassageCheck.ts")))
        val scrolling = ThreadScroll(emptyFlow(), MutableStateFlow(0f))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                DictationBottomScreen(state.value, {}, {}, {}, session = session, transcript = scrolling)
            } }
            compose.onNodeWithText("Message pour").assertIsDisplayed()
            compose.onNodeWithText("teste-v-deux").assertIsDisplayed()
            compose.onNodeWithText("Ouvre PassageCheck.ts").assertIsDisplayed()
            compose.onNodeWithText("Relâche L2 pour relire").assertIsDisplayed()
            compose.runOnIdle { state.value = state.value.copy(phase = DictationPhase.REVIEW, text = "Ouvre PassageCheck.ts et lance les tests") }
            compose.onNodeWithText("Ouvre PassageCheck.ts et lance les tests").assertIsDisplayed()
            listOf("Envoyer", "Effacer", "Corriger un mot", "Compléter").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        }
    }

    private fun holdAfterAction(label: String, button: Button, phase: DictationPhase) {
        val state = mutableStateOf(DictationReview(phase = DictationPhase.REVIEW, text = "Modifie PassageCheck.ts"))
        val buttons = mutableListOf<Button>()
        val holds = mutableListOf<Boolean>()
        val scrolling = ThreadScroll(emptyFlow(), MutableStateFlow(0f))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                RegieTheme {
                    DictationTopScreen(state.value, null, single = true, onButton = {
                        buttons += it
                        state.value = state.value.copy(phase = phase)
                    }, onHeld = {
                        holds += it
                        state.value = state.value.copy(phase = if (it) DictationPhase.RECORDING else DictationPhase.REVIEW)
                    }, onSettings = {}, scrolling = scrolling)
                }
            }
            compose.onNodeWithText(label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf(button), buttons) }
            val mic = compose.onNodeWithContentDescription("Maintenir pour dicter")
            mic.assertIsDisplayed().performTouchInput { down(center) }
            compose.runOnIdle { assertEquals(listOf(true), holds) }
            compose.onNodeWithText("Relâche L2 pour relire").assertIsDisplayed()
            compose.runOnIdle { state.value = state.value.copy(level = 0.6f, durationMs = 3_000) }
            compose.onNodeWithContentDescription("Niveau du micro").assertIsDisplayed().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.6f, 0f..1f)),
            )
            mic.performTouchInput { up() }
            compose.runOnIdle {
                assertEquals(listOf(true, false), holds)
                state.value = state.value.copy(level = 0f, durationMs = 3_100)
            }
            compose.onNodeWithContentDescription("Niveau du micro").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f)),
            )
            compose.onNodeWithText("Envoyer").assertIsDisplayed()
            compose.onNodeWithText("Effacer").assertIsDisplayed()
        }
    }
}
