package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.AgentAuthenticationGateway
import app.regie.domain.usecase.ReviewAgentAuthentication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AgentAuthenticationTest {
    private class Fake : AgentAuthenticationGateway {
        var agents = listOf(AgentAuthentication(Agent.CLAUDE, connected = true), AgentAuthentication(Agent.CODEX, connected = false))
        var failStatus = false
        var failStart = false
        var starts = 0
        var logouts = 0
        var cancels = 0
        var pending: CompletableDeferred<AgentAuthentication>? = null
        override suspend fun authentication(): List<AgentAuthentication> { if (failStatus) error("raw secret"); return agents }
        override suspend fun startAuthentication(agent: Agent): AgentAuthentication {
            starts++
            if (failStart) error("acknowledgement lost")
            return pending?.await() ?: AgentAuthentication(agent, false, phase = AuthenticationPhase.WAITING, url = "https://auth.openai.com/codex/device", code = "ABCD-EFGHI")
        }
        override suspend fun logoutAgent(agent: Agent): AgentAuthentication { logouts++; return AgentAuthentication(agent, false) }
        override suspend fun cancelAuthentication(agent: Agent): AgentAuthentication { cancels++; return AgentAuthentication(agent, false) }
    }

    @Test fun `first launch offers disconnected agent once without starting login`() = runTest {
        val fake = Fake(); val review = ReviewAgentAuthentication(fake, backgroundScope)
        review.offerOnLaunch(); runCurrent()
        assertTrue(review.state.value.visible)
        assertEquals(Agent.CODEX, review.state.value.current?.agent)
        assertEquals(0, fake.starts)
        review.close(); runCurrent()
        review.offerOnLaunch(); runCurrent()
        assertFalse(review.state.value.visible)
    }

    @Test fun `double A starts only once and result is refreshed without resending login`() = runTest {
        val fake = Fake(); val review = ReviewAgentAuthentication(fake, backgroundScope)
        review.open(); runCurrent(); review.select(1)
        fake.pending = CompletableDeferred()
        review.confirm(); review.confirm(); runCurrent()
        assertEquals(1, fake.starts)
        fake.pending!!.complete(AgentAuthentication(Agent.CODEX, false, phase = AuthenticationPhase.WAITING, url = "https://auth.openai.com/codex/device", code = "ABCD-EFGHI"))
        runCurrent()
        assertEquals("ABCD-EFGHI", review.state.value.current?.code)
        fake.agents = listOf(AgentAuthentication(Agent.CLAUDE, true), AgentAuthentication(Agent.CODEX, true, phase = AuthenticationPhase.CONNECTED))
        advanceTimeBy(2_001); runCurrent()
        assertEquals(AuthenticationPhase.CONNECTED, review.state.value.current?.phase)
        assertNull(review.state.value.current?.code)
        assertEquals(1, fake.starts)
    }

    @Test fun `logout needs confirmation and B cancels it without disconnecting`() = runTest {
        val fake = Fake(); val review = ReviewAgentAuthentication(fake, backgroundScope)
        review.open(); runCurrent()
        review.requestLogout(); review.close(); runCurrent()
        assertEquals(0, fake.logouts)
        assertTrue(review.state.value.visible)
        review.requestLogout(); review.confirm(); review.confirm(); runCurrent()
        assertEquals(1, fake.logouts)
    }

    @Test fun `closing waiting login cancels it and clears the displayed code`() = runTest {
        val fake = Fake(); val review = ReviewAgentAuthentication(fake, backgroundScope)
        review.open(); runCurrent(); review.select(1); review.confirm(); runCurrent()
        review.close(); runCurrent()
        assertEquals(1, fake.cancels)
        assertFalse(review.state.value.visible)
        assertNull(review.state.value.current?.code)
    }
    @Test fun `bridge recovery clears its error notice without reissuing any login`() = runTest {
        val fake = Fake(); fake.failStatus = true
        val review = ReviewAgentAuthentication(fake, backgroundScope)
        review.open(); runCurrent()
        assertTrue(review.state.value.notice.contains("Pont injoignable"))
        fake.failStatus = false
        advanceTimeBy(2_001); runCurrent()
        assertEquals("", review.state.value.notice)
        assertEquals(0, fake.starts)
    }

    @Test fun `unknown start result is cancelled on exit rather than starting another login`() = runTest {
        val fake = Fake(); fake.failStart = true
        val review = ReviewAgentAuthentication(fake, backgroundScope)
        review.open(); runCurrent(); review.select(1)
        review.confirm(); runCurrent()
        review.close(); runCurrent()
        assertEquals(1, fake.starts)
        assertEquals(1, fake.cancels)
        assertFalse(review.state.value.visible)
    }

}
