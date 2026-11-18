package app.regie.console

import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import app.regie.domain.model.LaunchResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BridgePairingTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `pairing sends only a socket name and gives the transfer the Termux UID`() {
        shadowOf(app).grantPermissions(TermuxBridgeLauncher.PERMISSION)
        val tokenFile = File(app.filesDir, "bridge-token").also { it.delete() }
        val token = BridgeTokenFile(tokenFile).readOrCreate()
        assertEquals(64, token.length)
        assertTrue(token.matches(Regex("[0-9a-f]{64}")))
        assertEquals(token, BridgeTokenFile(tokenFile).readOrCreate())
        val info = PackageInfo().apply {
            packageName = "com.termux"
            applicationInfo = ApplicationInfo().apply { packageName = "com.termux"; uid = 10123 }
        }
        shadowOf(app.packageManager).installPackage(info)
        assertEquals(LaunchResult.SENT, TermuxBridgeLauncher(app) { secret, uid ->
            assertEquals(token, secret)
            assertEquals(10123, uid)
            "regie-pair-fixture"
        }.configure())
        val intent = shadowOf(app).nextStartedService
        assertEquals("/data/data/com.termux/files/home/bin/regie-bridge-configure", intent.getStringExtra("com.termux.RUN_COMMAND_PATH"))
        assertEquals(listOf("regie-pair-fixture"), intent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")?.toList())
        assertNull(intent.getStringExtra("com.termux.RUN_COMMAND_STDIN"))
        assertTrue(intent.extras.toString().contains(token).not())
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission("irrelevant.permission"))
        tokenFile.delete()
        assertNotEquals(token, BridgeTokenFile(tokenFile).readOrCreate())
    }

    @Test
    fun `missing permissions or Termux never start a token transfer`() {
        val launcher = TermuxBridgeLauncher(app) { _, _ -> error("No transfer without Termux permission") }
        shadowOf(app).denyPermissions(TermuxBridgeLauncher.PERMISSION)
        assertEquals(LaunchResult.PERMISSION_MISSING, launcher.configure())
        shadowOf(app).grantPermissions(TermuxBridgeLauncher.PERMISSION)
        assertEquals(LaunchResult.TERMUX_MISSING, launcher.configure())
    }

    @Test
    fun `debug token already copied by the Mac is preserved`() {
        val tokenFile = File(app.filesDir, "bridge-token")
        tokenFile.writeText("existing-debug-token\n")
        assertEquals("existing-debug-token", BridgeTokenFile(tokenFile).readOrCreate())
    }
}
