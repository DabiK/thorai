package app.regie.domain

import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.UnreachableReason
import app.regie.domain.usecase.KeepBridgeUp
import app.regie.domain.usecase.StartUp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class KeepBridgeUpTest {

    private val gateway = StubGateway(bridgeUp = false)
    private val launcher = StubLauncher()
    private val silent = BridgeConnection.Unreachable(UnreachableReason.NOT_RESPONDING)

    @Test
    fun `a refused token retries provisioning without a foreground transition`() = runTest {
        gateway.bridgeUp = true
        var configured = 0
        val start = StartUp(gateway, StubAccess(), launcher, timeSource = testTimeSource,
            pairing = app.regie.domain.port.BridgePairing { configured++; app.regie.domain.model.LaunchResult.SENT })
        gateway.connection.value = BridgeConnection.Unreachable(UnreachableReason.TOKEN_REFUSED)
        val job = launch { KeepBridgeUp(gateway, start, canStart = { true }).run() }
        runCurrent()
        assertEquals(1, configured)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(2, configured)
        gateway.connection.value = BridgeConnection.Connected
        runCurrent()
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(2, configured)
        job.cancel()
    }

    @Test
    fun `a bridge falling soon after a start is restarted when the interval is over`() = runTest {
        val startUp = StartUp(gateway, StubAccess(), launcher, restartInterval = 30.seconds, timeSource = testTimeSource)
        startUp()
        assertEquals(1, launcher.sent.size)
        gateway.connection.value = BridgeConnection.Connected
        val job = launch { KeepBridgeUp(gateway, startUp, canStart = { true }).run() }
        runCurrent()

        // Le pont tombe 10 s après le démarrage : trop tôt pour le redemander…
        advanceTimeBy(10.seconds)
        gateway.connection.value = silent
        runCurrent()
        assertEquals(1, launcher.sent.size)

        // … mais la tentative part à l'échéance, sans attendre un retour au premier plan.
        advanceTimeBy(19.seconds)
        runCurrent()
        assertEquals(1, launcher.sent.size)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(2, launcher.sent.size)

        // Toujours injoignable : une tentative par intervalle.
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(3, launcher.sent.size)
        job.cancel()
    }

    @Test
    fun `nothing is retried once the bridge answers again`() = runTest {
        val startUp = StartUp(gateway, StubAccess(), launcher, timeSource = testTimeSource)
        gateway.connection.value = silent
        val job = launch { KeepBridgeUp(gateway, startUp, canStart = { true }).run() }
        runCurrent()
        assertEquals(1, launcher.sent.size)

        gateway.connection.value = BridgeConnection.Connected
        runCurrent()
        advanceTimeBy(120.seconds)
        runCurrent()
        assertEquals(1, launcher.sent.size)
        job.cancel()
    }

    @Test
    fun `no start while Régie cannot start the bridge (hidden, or quitting), then it resumes`() = runTest {
        val startUp = StartUp(gateway, StubAccess(), launcher, timeSource = testTimeSource)
        var visible = false
        gateway.connection.value = silent
        val job = launch { KeepBridgeUp(gateway, startUp, canStart = { visible }, idleCheck = 5.seconds).run() }
        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(0, launcher.sent.size)

        visible = true
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(1, launcher.sent.size)
        job.cancel()
    }
}
