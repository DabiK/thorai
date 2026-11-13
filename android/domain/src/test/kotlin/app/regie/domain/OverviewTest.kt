package app.regie.domain

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class OverviewTest {

    private fun approval(sessionId: String) =
        Approval(ApprovalId("a-$sessionId"), SessionId(sessionId), ApprovalKind.COMMAND, "rm -rf build")

    @Test
    fun `active sessions come first, then the most recently changed`() {
        val overview = Overview.of(
            sessions = listOf(
                session("old-history", SessionStatus.STOPPED, active = false, updatedAt = T0 - 60.minutes),
                session("recent-history", SessionStatus.STOPPED, active = false, updatedAt = T0 + 5.minutes),
                session("idle", SessionStatus.IDLE, updatedAt = T0 - 10.minutes),
                session("working", updatedAt = T0),
            ),
            agents = emptyList(),
        )

        assertEquals(listOf("working", "idle", "recent-history", "old-history"), overview.sessions.map { it.id.value })
    }

    @Test
    fun `the header counts the active sessions`() {
        val overview = Overview.of(
            listOf(session("1"), session("2", SessionStatus.IDLE), session("3", SessionStatus.STOPPED, active = false)),
            emptyList(),
        )

        assertEquals(2, overview.activeCount)
    }

    @Test
    fun `only working sessions and those awaiting an approval are busy`() {
        val overview = Overview.of(
            SessionStatus.entries.mapIndexed { index, status -> session("$index", status) },
            emptyList(),
        )

        assertEquals(2, overview.busyCount)
    }

    @Test
    fun `the first pending approval follows the session order`() {
        val overview = Overview.of(
            listOf(
                session("older", SessionStatus.AWAITING_APPROVAL, updatedAt = T0, approval = approval("older")),
                session("newer", SessionStatus.AWAITING_APPROVAL, updatedAt = T0 + 1.minutes, approval = approval("newer")),
                session("other", updatedAt = T0 + 2.minutes),
            ),
            emptyList(),
        )

        assertEquals("newer", overview.firstPendingApproval?.session?.id?.value)
        assertEquals(ApprovalId("a-newer"), overview.firstPendingApproval?.approval?.id)
    }

    @Test
    fun `no pending approval when no session waits`() {
        assertNull(Overview.of(listOf(session("1")), emptyList()).firstPendingApproval)
    }
}
