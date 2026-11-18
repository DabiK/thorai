package app.regie

import android.app.Application
import androidx.activity.ComponentActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bogue du 18 novembre : après avoir tapé un message, le retour système de l'écran du bas fermait
 * BottomActivity (`wm_finish_activity … app-request` sur la Thor), et l'écran du bas restait au lanceur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BottomBackTest {
    @Test fun `system Back no longer closes the bottom screen`() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.keepOpenOnSystemBack()
            activity.onBackPressedDispatcher.onBackPressed()
            assertFalse(activity.isFinishing)
        }
        // Without it, the same Back finishes the activity: the test sees the bug.
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.onBackPressedDispatcher.onBackPressed()
            assertTrue(activity.isFinishing)
        }
    }

    @Test fun `the bottom activity installs it`() {
        assertTrue(File("src/main/kotlin/app/regie/BottomActivity.kt").readText().contains("keepOpenOnSystemBack()"))
    }
}
