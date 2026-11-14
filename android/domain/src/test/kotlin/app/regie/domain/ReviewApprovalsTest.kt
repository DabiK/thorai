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

}
