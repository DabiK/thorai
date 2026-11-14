package app.regie.console

import android.app.Application
import android.content.pm.PackageInfo
import app.regie.domain.model.LaunchResult
import app.regie.domain.model.WirelessDebugging
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ExternalBridgeTest {
    @Test
    fun `without Termux the external bridge sends no service command and requires no wireless debugging`() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(TermuxBridgeLauncher.isInstalled(context))
        val bridge = ExternalBridge()
        assertEquals(LaunchResult.EXTERNAL, bridge.start())
        assertEquals(LaunchResult.EXTERNAL, bridge.stop())
        assertEquals(WirelessDebugging.NOT_REQUIRED, bridge.restoreWirelessDebugging())
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test
    fun `an installed Termux keeps the console launch path even without permission`() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.termux" })
        assertTrue(TermuxBridgeLauncher.isInstalled(context))
        assertEquals(LaunchResult.PERMISSION_MISSING, TermuxBridgeLauncher(context).start())
        assertNull(shadowOf(context).nextStartedService)
    }
}
