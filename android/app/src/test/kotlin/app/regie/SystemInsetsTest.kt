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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = RegieApplication::class, qualifiers = "w832dp-h468dp-land-mdpi")
class SystemInsetsTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `shared activity content stays below status bar and above navigation bar`() {
        Robolectric.buildActivity(InsetActivity::class.java).use { host ->
            val activity = host.setup().get()
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Does not start AppContainer: no service, bridge or agent.
        setScreen(overview = { _, _ -> Box(Modifier.fillMaxSize().testTag("safe-content")) }, session = { _, _ -> }, approval = {})
    }
}
