package app.regie.domain

import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionEvent
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class OverviewTest {

    private fun session(id: String, status: SessionStatus = SessionStatus.RUNNING, approval: Approval? = null) =
        Session(
            id = SessionId(id),
            agent = Agent.CLAUDE,
            project = "projet-$id",
            branch = null,
            task = "tâche $id",
            status = status,
            sinceLastActivity = 1.seconds,
            pendingApproval = approval,
        )

    private fun approval(sessionId: String) =
        Approval(ApprovalId("a-$sessionId"), SessionId(sessionId), ApprovalKind.COMMAND, "rm -rf build")

    @Test
    fun `a new session is appended in arrival order`() {
        val overview = Overview.EMPTY
            .apply(SessionEvent.SessionUpdated(session("1")))
            .apply(SessionEvent.SessionUpdated(session("2")))

        assertEquals(listOf("1", "2"), overview.sessions.map { it.id.value })
    }

    @Test
    fun `an update replaces the session in place`() {
        val overview = Overview.EMPTY
            .apply(SessionEvent.SessionUpdated(session("1")))
            .apply(SessionEvent.SessionUpdated(session("2")))
            .apply(SessionEvent.SessionUpdated(session("1", SessionStatus.DONE)))

        assertEquals(listOf("1", "2"), overview.sessions.map { it.id.value })
        assertEquals(SessionStatus.DONE, overview.sessions.first().status)
    }

    @Test
    fun `a removed session disappears`() {
        val overview = Overview.EMPTY
            .apply(SessionEvent.SessionUpdated(session("1")))
            .apply(SessionEvent.SessionRemoved(SessionId("1")))

        assertEquals(emptyList(), overview.sessions)
    }

    @Test
    fun `agent versions are kept`() {
        val versions = mapOf(Agent.CLAUDE to "2.1.322", Agent.CODEX to "0.167.2")

        val overview = Overview.EMPTY.apply(SessionEvent.AgentsDetected(versions))

        assertEquals(versions, overview.agentVersions)
    }

    @Test
    fun `the first pending approval follows the session order`() {
        val overview = Overview.EMPTY
            .apply(SessionEvent.SessionUpdated(session("1")))
            .apply(SessionEvent.SessionUpdated(session("2", SessionStatus.AWAITING_APPROVAL, approval("2"))))
            .apply(SessionEvent.SessionUpdated(session("3", SessionStatus.AWAITING_APPROVAL, approval("3"))))

        assertEquals("2", overview.firstPendingApproval?.session?.id?.value)
        assertEquals(ApprovalId("a-2"), overview.firstPendingApproval?.approval?.id)
    }

    @Test
    fun `no pending approval when no session waits`() {
        val overview = Overview.EMPTY.apply(SessionEvent.SessionUpdated(session("1")))

        assertNull(overview.firstPendingApproval)
    }
}
