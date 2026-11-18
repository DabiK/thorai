package app.regie

import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import app.regie.display.DisplayPolicy
import app.regie.display.DisplaySnapshot
import app.regie.domain.model.SessionId
import app.regie.session.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Ticket 54, premiers essais sur la Thor.
 *
 * 1. ThorAI rouverte depuis le lanceur de l'écran du bas : Android déplaçait l'écran du haut sur l'écran
 *    du bas, derrière lui, sans le recréer. Plus de hameau, Start et B ne ramenaient à rien.
 * 2. Un message tapé dans une maison : le champ gardait le focus, le clavier couvrait le fil, et B
 *    quittait la maison au lieu de rendre le fil.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = RegieApplication::class, qualifiers = "w538dp-h468dp-land-mdpi")
class ResumeAndKeyboardTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun KeyboardActivity.key(keyCode: Int) {
        compose.runOnIdle {
            window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    @Test fun `with the keyboard open, B gives the thread back first, the next B leaves the house`() {
        Robolectric.buildActivity(KeyboardActivity::class.java).use { host ->
            val activity = host.setup().resume().get()
            val house = SessionId("maison")
            compose.runOnIdle { activity.app.openSession(house) }
            compose.onNodeWithTag("message").requestFocus()
            compose.onNodeWithTag("message").assertIsFocused()

            activity.key(KeyEvent.KEYCODE_BUTTON_B)
            compose.onNodeWithTag("message").assertIsNotFocused()
            assertEquals(Screen.Session(house), activity.app.screen.value, "B a fermé le clavier, pas la maison")

            activity.key(KeyEvent.KEYCODE_BUTTON_B)
            assertEquals(Screen.Overview, activity.app.screen.value, "clavier fermé : B fait « retour »")
        }
    }

    @Test fun `A sends the typed message, then closes the keyboard so the thread shows again`() {
        Robolectric.buildActivity(KeyboardActivity::class.java).use { host ->
            val activity = host.setup().resume().get()
            val house = SessionId("maison")
            compose.runOnIdle {
                activity.app.openSession(house)
                activity.app.sessionController.onDraftChange("Lis notes.md")
            }
            compose.onNodeWithTag("message").requestFocus()

            activity.key(KeyEvent.KEYCODE_BUTTON_A)
            compose.onNodeWithTag("message").assertIsNotFocused()
            assertNotNull(activity.app.sessionController.state.value.notice, "le message est parti")
            assertEquals(Screen.Session(house), activity.app.screen.value)
        }
    }

    @Test fun `without a text field in use, B keeps its usual meaning`() {
        Robolectric.buildActivity(KeyboardActivity::class.java).use { host ->
            val activity = host.setup().resume().get()
            compose.runOnIdle { activity.app.openSession(SessionId("maison")) }
            activity.key(KeyEvent.KEYCODE_BUTTON_B)
            assertEquals(Screen.Overview, activity.app.screen.value)
        }
    }

    @Test fun `the top screen moved to the bottom display asks to go back there, without being recreated`() {
        // La règle : hors du display par défaut, l'écran du haut vise le display par défaut (écran du bas 4 → 0).
        val top = DisplaySnapshot(0, isDefault = true, isOn = true, isPresentation = false, isPrivate = false)
        val bottom = DisplaySnapshot(4, isDefault = false, isOn = true, isPresentation = true, isPrivate = false)
        assertEquals(Display.DEFAULT_DISPLAY, DisplayPolicy.topScreenTarget(4, listOf(top, bottom)))
        assertNull(DisplayPolicy.topScreenTarget(0, listOf(top, bottom)))

        Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).use { host ->
            val activity = host.setup().get()
            activity.returnTopScreenTo(Display.DEFAULT_DISPLAY)
            val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult, "elle demande à revenir")
            assertEquals(TopActivity::class.java.name, started.intent.component?.className)
            assertEquals(Display.DEFAULT_DISPLAY, started.options.getInt("android.activity.launchDisplayId", -1))
            assertEquals(false, activity.isFinishing, "sans être fermée : le hameau et la maison ouverte restent")
        }
    }

    @Test fun `the top activity checks its display when Android moves it, and when it comes back to the front`() {
        // Robolectric ne sait pas déplacer une activité d'un écran à l'autre : le branchement est relu.
        val source = java.io.File("src/main/kotlin/app/regie/TopActivity.kt").readText()
        val onConfiguration = source.substringAfter("override fun onConfigurationChanged").substringBefore("override fun")
        val onStart = source.substringAfter("override fun onStart()").substringBefore("override fun")
        kotlin.test.assertTrue("backToTopScreen()" in onConfiguration)
        kotlin.test.assertTrue("backToTopScreen()" in onStart)
    }
}

/** Une maison réduite à son champ de message : GamepadActivity, sans pont ni agent. */
class KeyboardActivity : GamepadActivity() {
    val app: AppContainer get() = container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen(
            overview = { _, _ -> Box(Modifier.fillMaxSize()) },
            session = { _, _ ->
                val draft by container.sessionController.draft
                BasicTextField(draft, container.sessionController::onDraftChange, Modifier.testTag("message"))
            },
            approval = {},
        )
    }
}
