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
    fun `an unpaired app configures a running bridge and retries until authenticated`() = runTest {
        var calls = 0
        val pairing = app.regie.domain.port.BridgePairing {
            calls++
            LaunchResult.SENT
        }
        val pairedStart = StartUp(gateway, access, launcher, timeSource = time, pairing = pairing)
        pairedStart()
        assertEquals(1, calls)
        time += 10.seconds
        pairedStart()
        assertEquals(1, calls)
        time += 21.seconds
        pairedStart()
        assertEquals(2, calls)
        gateway.connection.value = app.regie.domain.model.BridgeConnection.Connected
        time += 31.seconds
        pairedStart()
        assertEquals(2, calls)
        assertEquals(emptyList(), launcher.sent)
    }

    @Test
    fun `pairing permission refusal is retried without waiting thirty seconds`() = runTest {
        var calls = 0
        val pairedStart = StartUp(gateway, access, launcher, pairing = app.regie.domain.port.BridgePairing {
            calls++
            LaunchResult.PERMISSION_MISSING
        })
        assertEquals(LaunchResult.PERMISSION_MISSING, pairedStart().launch)
        pairedStart()
        assertEquals(2, calls)
    }

    @Test
    fun `a bridge already up with wireless debugging on is left alone`() = runTest {
        val report = startUp()

        assertEquals(StartUpReport(WirelessDebugging.ALREADY_ON, bridgeUp = true, launch = null), report)
        assertEquals(emptyList(), launcher.sent)
    }

    @Test
    fun `a silent bridge is started`() = runTest {
        gateway.bridgeUp = false

        assertEquals(StartUpReport(WirelessDebugging.ALREADY_ON, bridgeUp = false, launch = LaunchResult.SENT), startUp())
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

        val expected = StartUpReport(WirelessDebugging.PERMISSION_MISSING, bridgeUp = false, launch = LaunchResult.SENT)
        assertEquals(expected, startUp())
        assertEquals(expected, startUp.report.value)
    }

    @Test
    fun `a missing RUN_COMMAND permission is reported, and asked again next time`() = runTest {
        gateway.bridgeUp = false
        launcher.result = LaunchResult.PERMISSION_MISSING

        assertEquals(LaunchResult.PERMISSION_MISSING, startUp().launch)
        launcher.result = LaunchResult.SENT
        assertEquals(LaunchResult.SENT, startUp().launch)
        assertEquals(listOf("start", "start"), launcher.sent)
    }

    @Test
    fun `a start is not sent again while the previous one may still be under way`() = runTest {
        gateway.bridgeUp = false
        startUp()
        time += 10.seconds
        val report = startUp()

        assertEquals(listOf("start"), launcher.sent)
        assertEquals(StartUpReport(WirelessDebugging.ALREADY_ON, bridgeUp = false, launch = null, retryIn = 20.seconds), report)
        time += 25.seconds
        startUp()
        assertEquals(listOf("start", "start"), launcher.sent)
    }

    @Test
    fun `the report tells what this launch did, not an earlier one`() = runTest {
        gateway.bridgeUp = false
        launcher.result = LaunchResult.PERMISSION_MISSING
        assertEquals(LaunchResult.PERMISSION_MISSING, startUp().launch)

        // Le pont a été démarré depuis le Mac : rien à démarrer cette fois.
        gateway.bridgeUp = true
        val report = startUp()

        assertEquals(StartUpReport(WirelessDebugging.ALREADY_ON, bridgeUp = true, launch = null), report)
        assertEquals(report, startUp.report.value)
    }
}
