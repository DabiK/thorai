package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.ReviewApprovals
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewApprovalsTest {
    private fun request(id: String) = Approval(ApprovalId(id), SessionId("s"), ApprovalKind.COMMAND, "touch $id", options = listOf(
        ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"),
        ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
    ))

    @Test
    fun `opening the last request still lets the gamepad visit the entire queue without answering`() = runTest {
        val gateway = StubGateway()
        val first = request("a")
        val last = request("b")
        gateway.approvals.value = listOf(first, last)
        val review = ReviewApprovals(gateway, backgroundScope)
        runCurrent()
        review.open(last.id)

        review.next()
        assertEquals(first, review.state.value.request)
        assertTrue(review.state.value.canAnswer(ApprovalDecision.ONCE))
        review.next()
        assertEquals(last, review.state.value.request)
        assertEquals(listOf(first, last), review.state.value.queue)
        assertTrue(gateway.answers.isEmpty())
    }

    @Test
    fun `next tolerates an empty queue and keeps a single answered request disabled`() = runTest {
        val gateway = StubGateway()
        val review = ReviewApprovals(gateway, backgroundScope)
        runCurrent()
        review.next()
        assertNull(review.state.value.request)

        val only = request("a")
        gateway.approvals.value = listOf(only)
        runCurrent()
        review.next()
        assertEquals(only, review.state.value.request)
        review.answer(ApprovalDecision.REJECT)
        review.next()
        assertEquals(only, review.state.value.request)
        assertFalse(review.state.value.canAnswer(ApprovalDecision.REJECT))
        review.answer(ApprovalDecision.REJECT)
        assertEquals(listOf(only.id to "no"), gateway.answers)

        gateway.approvals.value = emptyList()
        runCurrent()
        review.next()
        assertEquals(only, review.state.value.request)
        assertFalse(review.state.value.pending)
    }

    @Test
    fun `answering keeps the next request safe until explicitly selected`() = runTest {
        val gateway = StubGateway()
        val first = request("a")
        val next = request("b")
        gateway.approvals.value = listOf(first, next)
        val review = ReviewApprovals(gateway, backgroundScope)
        runCurrent()
        review.open(first.id)
        review.answer(ApprovalDecision.ONCE)
        gateway.approvals.value = listOf(next)
        runCurrent()
        review.answer(ApprovalDecision.ONCE)
        assertEquals(listOf(first.id to "yes"), gateway.answers)
        assertEquals(ApprovalReply.ACCEPTED, review.state.value.answer?.reply)
        review.next()
        assertEquals(next, review.state.value.request)
        review.answer(ApprovalDecision.REJECT)
        assertEquals(listOf(first.id to "yes", next.id to "no"), gateway.answers)
    }

    @Test
    fun `uncertain answer stays disabled after leaving and reopening while not sent can retry`() = runTest {
        val gateway = StubGateway()
        val request = request("a")
        gateway.approvals.value = listOf(request)
        val review = ReviewApprovals(gateway, backgroundScope)
        runCurrent()
        review.open(request.id)
        gateway.approvalResult = ApprovalReply.NOT_SENT
        review.answer(ApprovalDecision.ONCE)
        assertTrue(review.state.value.canAnswer(ApprovalDecision.ONCE))
        gateway.approvalResult = ApprovalReply.UNKNOWN
        review.answer(ApprovalDecision.ONCE)
        review.open(request.id)
        assertFalse(review.state.value.canAnswer(ApprovalDecision.ONCE))
        assertEquals(ApprovalReply.UNKNOWN, review.state.value.answer?.reply)
    }
    @Test
    fun `opening from a notification waits for sync and later queue updates keep the chosen identity`() = runTest {
        val gateway = StubGateway()
        val first = request("a")
        val next = request("b")
        val review = ReviewApprovals(gateway, backgroundScope)
        review.open(first.id)
        runCurrent()
        assertNull(review.state.value.request)
        gateway.approvals.value = listOf(first, next)
        runCurrent()
        assertEquals(first, review.state.value.request)
        review.next()
        gateway.approvals.value = listOf(first.copy(reason = "updated"), next)
        runCurrent()
        assertEquals(next, review.state.value.request)
        review.answer(ApprovalDecision.ONCE)
        assertEquals(listOf(next.id to "yes"), gateway.answers)
    }

    @Test fun `inline review follows only its session and never advances on a double answer`() = runTest {
        val gateway = StubGateway()
        val other = request("other").copy(sessionId = SessionId("other"))
        val first = request("first")
        val second = request("second")
        val review = ReviewApprovals(gateway, backgroundScope)
        review.followSession(SessionId("s"))
        gateway.approvals.value = listOf(other, first, second)
        runCurrent()
        assertEquals(first, review.state.value.request)
        assertEquals(listOf(first, second), review.state.value.queue)
        review.answer(ApprovalDecision.ONCE)
        gateway.approvals.value = listOf(other, second)
        runCurrent()
        review.answer(ApprovalDecision.ONCE)
        assertEquals(listOf(first.id to "yes"), gateway.answers)
        assertEquals(first, review.state.value.request)
        review.next()
        assertEquals(second, review.state.value.request)
        review.answer(ApprovalDecision.REJECT)
        assertEquals(listOf(first.id to "yes", second.id to "no"), gateway.answers)
        review.followSession(SessionId("other"))
        assertEquals(other, review.state.value.request)
        review.followSession(SessionId("empty"))
        assertNull(review.state.value.request)
        review.answer(ApprovalDecision.ONCE)
        assertEquals(2, gateway.answers.size)
    }

    @Test fun `an answer scheduled for a previous card cannot answer the new selection`() = runTest {
        val gateway = StubGateway()
        val first = request("first")
        val second = request("second")
        gateway.approvals.value = listOf(first, second)
        val review = ReviewApprovals(gateway, backgroundScope)
        review.open(first.id)
        review.next()
        review.answer(ApprovalDecision.ONCE, expectedId = first.id)
        assertTrue(gateway.answers.isEmpty())
        review.answer(ApprovalDecision.ONCE, expectedId = second.id)
        assertEquals(listOf(second.id to "yes"), gateway.answers)
    }

    @Test fun `closing a resolved inline card restores the next incoming request without closing a pending one`() = runTest {
        val gateway = StubGateway()
        val request = request("a")
        gateway.approvals.value = listOf(request)
        val review = ReviewApprovals(gateway, backgroundScope)
        review.followSession(request.sessionId)
        review.dismissResolved()
        assertEquals(request, review.state.value.request)
        review.answer(ApprovalDecision.ONCE)
        gateway.approvals.value = emptyList(); runCurrent()
        review.dismissResolved()
        assertNull(review.state.value.request)
        gateway.approvals.value = listOf(request("b")); runCurrent()
        assertEquals(ApprovalId("b"), review.state.value.request?.id)
    }

}
