package app.regie

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ticket 50 : la version jeu s'installe à côté de l'app classique, sur la même console. Rien de
 * ce qu'Android partage entre applis ne doit reprendre l'identité de l'app classique.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CohabitationTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the game build has its own application id, suffixed, whatever the base id`() {
        assertTrue(app.packageName.endsWith(".jeu"), app.packageName)
        assertEquals(BuildConfig.APPLICATION_ID, app.packageName)
    }

    @Test
    fun `its name and its console mode are told apart from the classic app`() {
        assertEquals("ThorAI jeu", app.getString(R.string.app_name))
        assertEquals("ThorAI jeu, mode console", app.getString(R.string.console_service_label))
    }

    @Test
    fun `the bottom screen never joins the task of the classic app`() {
        // Robolectric ne connaît que l'ancienne signature.
        @Suppress("DEPRECATION")
        val info = app.packageManager.getActivityInfo(ComponentName(app, BottomActivity::class.java), 0)
        assertEquals("${app.packageName}.bottom", info.taskAffinity)
    }
}
