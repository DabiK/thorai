package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.ui.screens.SetupScreen
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Ticket 28 : explication en haut, état et actions en bas, au doigt comme à la manette. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class SetupScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun show(review: SetupReview, bottom: Boolean, single: Boolean = false, onAction: (SetupAction?) -> Unit = {}) {
        val state = mutableStateOf(review)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            RegieTheme { SetupScreen(state.value, bottom, single, "app.thorai", onAction) }
        }
    }

    @Test fun `the bottom shows the checks and every key of the step, a touch plays its action`() {
        val pressed = mutableListOf<SetupAction?>()
        show(SetupReview(true, SetupStep.CONSOLE_MODE, SetupChecks(consoleMode = false)), bottom = true) { pressed += it }
        compose.onNodeWithText("Étape 6 sur 9 · facultative").assertIsDisplayed()
        compose.onNodeWithText("Désactivé").assertIsDisplayed()
        compose.onNodeWithText("Ouvrir Accessibilité").performClick()
        compose.onNodeWithText("Passer").performClick()
        compose.onNodeWithText("Revenir").performClick()
        compose.onNodeWithText("Ouvrir les infos de l’appli").performClick()
        assertEquals(listOf<SetupAction?>(SetupAction.OPEN_ACCESSIBILITY, SetupAction.SKIP, SetupAction.BACK, SetupAction.OPEN_APP_INFO), pressed)
        compose.onNodeWithTag("setup-later").performClick()
        assertEquals(SetupAction.CLOSE, pressed.last())
    }

    @Test fun `the top explains the step with its commands, the pairing names this app`() {
        show(SetupReview(true, SetupStep.ANDROID_ACCESS), bottom = false)
        compose.onNodeWithText("L’accès des agents à Android").assertIsDisplayed()
        compose.onNodeWithTag("setup-commands").assertTextContains("pm grant app.thorai android.permission.WRITE_SECURE_SETTINGS", substring = true)
        // Les commandes prennent la largeur : le chemin ne garde que ses repères.
        compose.onNodeWithText("Accès à Android").assertDoesNotExist()
    }

    @Test fun `the path names every step beside a plain explanation`() {
        show(SetupReview(true, SetupStep.NOTIFICATIONS), bottom = false)
        listOf("Bienvenue", "Termux et Debian", "Pont", "Voix", "Mode console", "Accès à Android", "Comptes des agents", "Première session")
            .forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun `the voice step shows the size to download, then the progress`() {
        show(SetupReview(true, SetupStep.VOICE, SetupChecks(microphone = true, voiceModel = VoiceModel.Missing(487_650_000, 1_217_650_000))), bottom = true)
        compose.onNodeWithText("488 Mo à télécharger").assertIsDisplayed()
        compose.onNodeWithText("Télécharger le modèle").assertIsDisplayed()
    }

    @Test fun `while the bridge starts, A says what it waits for and does nothing`() {
        val pressed = mutableListOf<SetupAction?>()
        show(SetupReview(true, SetupStep.BRIDGE, SetupChecks(termux = true, runCommand = true, bridge = BridgeStatus.Connecting)), bottom = true) { pressed += it }
        compose.onNodeWithText("Connexion au pont…").assertIsDisplayed().performClick()
        assertEquals(emptyList<SetupAction?>(), pressed)
    }

    @Test fun `one screen shows the explanation, the checks and the keys together`() {
        show(SetupReview(true, SetupStep.NOTIFICATIONS), bottom = false, single = true)
        compose.onNodeWithText("Les notifications").assertIsDisplayed()
        compose.onNodeWithText("Autoriser les notifications").assertIsDisplayed()
        compose.onNodeWithText("À autoriser").assertIsDisplayed()
    }
}
