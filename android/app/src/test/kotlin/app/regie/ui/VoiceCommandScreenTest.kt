package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.screens.VoiceCommandScreen
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
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class VoiceCommandScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `redire starts on touch down and ends only on release`() {
        val holds = mutableListOf<Boolean>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                VoiceCommandScreen(VoiceCommandReview(CommandPhase.UNKNOWN, "inconnu"), bottom = true, single = false,
                    onButton = {}, onHeld = { holds += it }, onSettings = {},
                    scrolling = ThreadScroll(emptyFlow(), MutableStateFlow(0f)))
            } }
            val redo = compose.onNodeWithText("Redire")
            redo.assertIsDisplayed().performTouchInput { down(center) }
            compose.runOnIdle { assertEquals(listOf(true), holds) }
            redo.performTouchInput { up() }
            compose.runOnIdle { assertEquals(listOf(true, false), holds) }
        }
    }

    @Test fun `single screen shows interpretation and confirmation only after recognition`() {
        val state = mutableStateOf(VoiceCommandReview(CommandPhase.RECORDING, text = "approuve"))
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                VoiceCommandScreen(state.value, bottom = false, single = true, onButton = { buttons += it }, onHeld = {}, onSettings = {},
                    scrolling = ThreadScroll(emptyFlow(), MutableStateFlow(0f)))
            } }
            compose.onNodeWithText("Confirmer").assertDoesNotExist()
            compose.runOnIdle { state.value = VoiceCommandReview(CommandPhase.UNKNOWN, "N’approuve pas", notice = "Ordre non compris") }
            compose.onNodeWithText("Confirmer").assertDoesNotExist()
            compose.onNodeWithText("Ordre non compris").assertIsDisplayed()
            compose.runOnIdle { state.value = VoiceCommandReview(CommandPhase.REVIEW, "Mets tout en pause", VoiceCommand.Pause(listOf(SessionId("one"))), "Interrompre le tour de : Atelier") }
            compose.onNodeWithText("Interrompre le tour de : Atelier").assertIsDisplayed()
            compose.onNodeWithText("Confirmer").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf(Button.A), buttons) }
            compose.onNodeWithText("Annuler").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(listOf(Button.A, Button.B), buttons) }
        }
    }
}
