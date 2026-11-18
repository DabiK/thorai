package app.regie

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Le jeu est devenu ThorAI (fusion dans main, 18 novembre) : il s'installe par-dessus l'app classique,
 * sous son identifiant et son nom. Le ticket 50 l'installait à côté, sous « ThorAI jeu ».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppIdentityTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the game is ThorAI, under the app's own id`() {
        assertEquals("app.thorai", app.packageName)
        assertEquals(BuildConfig.APPLICATION_ID, app.packageName)
    }

    @Test
    fun `its name and its console mode are ThorAI's`() {
        assertEquals("ThorAI", app.getString(R.string.app_name))
        assertEquals("ThorAI, mode console", app.getString(R.string.console_service_label))
    }

    @Test
    fun `the bottom screen keeps a task of its own`() {
        // Robolectric ne connaît que l'ancienne signature.
        @Suppress("DEPRECATION")
        val info = app.packageManager.getActivityInfo(ComponentName(app, BottomActivity::class.java), 0)
        assertEquals("${app.packageName}.bottom", info.taskAffinity)
    }
}
