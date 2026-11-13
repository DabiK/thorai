package app.regie.domain

import app.regie.domain.model.Agent
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.LaunchResult
import app.regie.domain.model.UnreachableReason
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.usecase.ObserveOverview
import app.regie.domain.usecase.StartUpReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveOverviewTest {

    @Test
    fun `the overview gathers sessions, versions, bridge state and launch report`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("1"))
        gateway.agents.value = listOf(AgentVersion(Agent.CODEX, "0.167.2", isTested = false))
        gateway.connection.value = BridgeConnection.Unreachable(UnreachableReason.NOT_RESPONDING)
        val report = MutableStateFlow<StartUpReport?>(StartUpReport(WirelessDebugging.PERMISSION_MISSING, bridgeUp = false, launch = LaunchResult.PERMISSION_MISSING))

        val overview = ObserveOverview(gateway, report)().first()

        assertEquals(listOf("1"), overview.sessions.map { it.id.value })
        assertEquals(listOf(AgentVersion(Agent.CODEX, "0.167.2", isTested = false)), overview.agents)
        assertEquals(BridgeStatus.Unreachable(BridgeHint.GRANT_RUN_COMMAND), overview.bridge)
        assertTrue(overview.androidAccessMissing)
    }
}
