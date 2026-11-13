package app.regie.domain

import app.regie.domain.model.LaunchResult
import app.regie.domain.usecase.Quit
import app.regie.domain.usecase.QuitOutcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuitTest {

    private val gateway = StubGateway()
    private val launcher = StubLauncher()

    @Test
    fun `the bridge is asked to stop through the connection`() = runTest {
        assertEquals(QuitOutcome.SHUTDOWN_SENT, Quit(gateway, launcher)())
        assertEquals(1, gateway.shutdowns)
        assertEquals(emptyList(), launcher.sent)
    }

    @Test
    fun `an unreachable bridge is stopped by the Termux command`() = runTest {
        gateway.shutdownHeard = false

        assertEquals(QuitOutcome.STOP_COMMAND_SENT, Quit(gateway, launcher)())
        assertEquals(listOf("stop"), launcher.sent)
    }

    @Test
    fun `without any way to reach the bridge, quitting still ends`() = runTest {
        gateway.shutdownHeard = false
        launcher.result = LaunchResult.PERMISSION_MISSING

        assertEquals(QuitOutcome.NOT_STOPPED, Quit(gateway, launcher)())
    }
}
