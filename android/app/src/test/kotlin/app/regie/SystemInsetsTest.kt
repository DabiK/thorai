package app.regie

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = RegieApplication::class, qualifiers = "w832dp-h468dp-land-mdpi")
class SystemInsetsTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `opening session options preserves the session content in the shared activity`() {
        Robolectric.buildActivity(InsetActivity::class.java).use { host ->
            val activity = host.setup().get()
            compose.runOnIdle { activity.openOptionsForTest() }
            compose.onNodeWithTag("session-content").assertIsDisplayed()
        }
    }

    // Ticket 51 : plein écran comme un jeu, sur les deux écrans ; un glissement montre les barres un instant.
    @Test fun `both screens hide the system bars, a swipe shows them for a moment`() {
        Robolectric.buildActivity(InsetActivity::class.java).use { host ->
            val activity = host.setup().resume().get()
            val controller = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, controller.systemBarsBehavior)
            // Ce que la fenêtre demande au système. Robolectric ne simule pas les barres : on lit la demande
            // d'Android 13 (`InsetsController.isRequestedVisible`, types internes 0 = état, 1 = navigation).
            val insets = activity.window.decorView.windowInsetsController!!
            val requested = insets.javaClass.getMethod("isRequestedVisible", Int::class.javaPrimitiveType)
            fun shown(type: Int) = requested.invoke(insets, type) as Boolean
            assertEquals(listOf(false, false), listOf(shown(0), shown(1)), "barres cachées à l'ouverture")
            // Un clavier, une autre appli ou une boîte système les a rendues ; au retour du focus, elles repartent.
            controller.show(WindowInsetsCompat.Type.systemBars())
            assertEquals(listOf(true, true), listOf(shown(0), shown(1)))
            activity.onWindowFocusChanged(true)
            assertEquals(listOf(false, false), listOf(shown(0), shown(1)), "barres recachées au retour")
        }
    }

    @Test fun `both activities share the immersive base`() {
        assertTrue(GamepadActivity::class.java.isAssignableFrom(TopActivity::class.java))
        assertTrue(GamepadActivity::class.java.isAssignableFrom(BottomActivity::class.java))
    }

    @Test fun `shared activity content stays below status bar and above navigation bar`() {
        Robolectric.buildActivity(InsetActivity::class.java).use { host ->
            val activity = host.setup().get()
            kotlin.test.assertFalse(activity.window.isNavigationBarContrastEnforced)
            kotlin.test.assertFalse(androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightNavigationBars)
            fun applyInsets(top: Int, bottom: Int) = compose.runOnIdle {
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, top, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom)).build()
                ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
            }
            applyInsets(0, 0)
            val before = compose.onNodeWithTag("safe-content").getUnclippedBoundsInRoot()
            applyInsets(24, 20)
            val after = compose.onNodeWithTag("safe-content").getUnclippedBoundsInRoot()
            assertEquals(24f, after.top.value - before.top.value)
            assertEquals(20f, before.bottom.value - after.bottom.value)
        }
    }
}

class InsetActivity : GamepadActivity() {
    fun openOptionsForTest() {
        container.openSession(app.regie.domain.model.SessionId("test"))
        container.sessionSettings.value = app.regie.domain.model.SessionSettingsReview(visible = true)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Does not start AppContainer: no service, bridge or agent.
        setScreen(overview = { _, _ -> Box(Modifier.fillMaxSize().testTag("safe-content")) }, session = { _, _ -> Box(Modifier.fillMaxSize().testTag("session-content")) }, approval = {})
    }
}
