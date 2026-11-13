package app.regie.domain

import app.regie.domain.fake.FakeAgentGateway
import app.regie.domain.model.Agent
import app.regie.domain.model.SessionStatus
import app.regie.domain.usecase.ObserveOverview
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeAgentGatewayTest {

    @Test
    fun `the fake gateway exposes the four sessions of the mockup`() = runTest {
        val overview = ObserveOverview(FakeAgentGateway())().last()

        assertEquals(
            listOf(
                Triple(Agent.CLAUDE, "atelier", SessionStatus.RUNNING),
                Triple(Agent.CODEX, "atelier-meubles", SessionStatus.AWAITING_APPROVAL),
                Triple(Agent.CLAUDE, "montage", SessionStatus.DONE),
                Triple(Agent.CODEX, "thor-dev", SessionStatus.PAUSED),
            ),
            overview.sessions.map { Triple(it.agent, it.project, it.status) },
        )
        assertEquals(mapOf(Agent.CLAUDE to "2.1.322", Agent.CODEX to "0.167.2"), overview.agentVersions)
    }

    @Test
    fun `the waiting Codex session carries the pending command`() = runTest {
        val overview = ObserveOverview(FakeAgentGateway())().last()

        val pending = overview.firstPendingApproval
        assertEquals("atelier-meubles", pending?.session?.project)
        assertEquals("pip install open_clip_torch", pending?.approval?.subject)
    }
}
