package app.regie.gateway.bridge

import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplayAgentGatewayTest {

    @Test
    fun `the fake gateway replays the demo at its own pace, through the bridge decoding`() = runTest {
        val gateway = ReplayAgentGateway()
        val job = launch { gateway.maintainConnection() }

        // Temps virtuel : la démo dure une vingtaine de secondes.
        val sessions = gateway.sessions.first { list -> list.count { it.status == SessionStatus.IDLE } == 2 && list.size == 11 }
        assertEquals(BridgeConnection.Connected, gateway.connection.value)
        assertTrue(gateway.agents.value.isNotEmpty())
        assertTrue(sessions.any { it.lastAction?.description == "Edit todo.md" })
        assertTrue(gateway.shutdownBridge())
        job.cancel()
    }
}
