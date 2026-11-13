package app.regie.domain

import app.regie.domain.model.LaunchResult
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.usecase.StartUp
import app.regie.domain.usecase.StartUpReport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class StartUpTest {

    private val gateway = StubGateway()
    private val access = StubAccess()
    private val launcher = StubLauncher()
    private val time = TestTimeSource()
    private val startUp = StartUp(gateway, access, launcher, restartInterval = 30.seconds, timeSource = time)

    @Test
    fun `a bridge already up with wireless debugging on is left alone`() = runTest {
        val report = startUp()

        assertEquals(StartUpReport(WirelessDebugging.ALREADY_ON, lastLaunch = null), report)
        assertEquals(emptyList(), launcher.sent)
    }

    @Test
    fun `a silent bridge is started`() = runTest {
        gateway.bridgeUp = false

        assertEquals(StartUpReport(WirelessDebugging.ALREADY_ON, LaunchResult.SENT), startUp())
        assertEquals(listOf("start"), launcher.sent)
    }

    @Test
    fun `wireless debugging turned back on restarts the bridge script even if the bridge runs`() = runTest {
        access.result = WirelessDebugging.TURNED_ON

        startUp()

        assertEquals(listOf("start"), launcher.sent)
        assertEquals(1, access.calls)
    }

    @Test
    fun `wireless debugging is restored before the bridge is started`() = runTest {
        gateway.bridgeUp = false
        val order = mutableListOf<String>()
        val tracingAccess = object : app.regie.domain.port.AndroidAccess {
            override fun restoreWirelessDebugging(): WirelessDebugging {
                order += "wifi"
                return WirelessDebugging.TURNED_ON
            }
        }
        val tracingLauncher = object : app.regie.domain.port.BridgeLauncher {
            override fun start(): LaunchResult = LaunchResult.SENT.also { order += "start" }
            override fun stop(): LaunchResult = LaunchResult.SENT
        }

        StartUp(gateway, tracingAccess, tracingLauncher)()

        assertEquals(listOf("wifi", "start"), order)
    }

    @Test
    fun `without the permission for wireless debugging, the bridge still starts`() = runTest {
        access.result = WirelessDebugging.PERMISSION_MISSING
        gateway.bridgeUp = false

        assertEquals(StartUpReport(WirelessDebugging.PERMISSION_MISSING, LaunchResult.SENT), startUp())
        assertEquals(startUp.report.value, StartUpReport(WirelessDebugging.PERMISSION_MISSING, LaunchResult.SENT))
    }

    @Test
    fun `a missing RUN_COMMAND permission is reported, and asked again next time`() = runTest {
        gateway.bridgeUp = false
        launcher.result = LaunchResult.PERMISSION_MISSING

        assertEquals(LaunchResult.PERMISSION_MISSING, startUp().lastLaunch)
        launcher.result = LaunchResult.SENT
        assertEquals(LaunchResult.SENT, startUp().lastLaunch)
        assertEquals(listOf("start", "start"), launcher.sent)
    }

    @Test
    fun `a start is not sent again while the previous one may still be under way`() = runTest {
        gateway.bridgeUp = false
        startUp()
        time += 10.seconds
        val report = startUp()

        assertEquals(listOf("start"), launcher.sent)
        assertEquals(LaunchResult.SENT, report.lastLaunch)
        time += 25.seconds
        startUp()
        assertEquals(listOf("start", "start"), launcher.sent)
    }
}
