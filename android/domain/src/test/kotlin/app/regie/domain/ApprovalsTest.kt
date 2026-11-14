package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.AnswerApproval
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApprovalsTest {
    @Test
    fun `answers only the offered choice once even when the receipt is lost`() = runTest {
        val gateway = StubGateway()
        val approval = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "touch x", options = listOf(
            ApprovalOption("allow_once", ApprovalOptionKind.ALLOW_ONCE, "Yes"),
            ApprovalOption("accept_execpolicy_amendment", ApprovalOptionKind.ALLOW_ALWAYS, "Always"),
            ApprovalOption("allow-with-updates", ApprovalOptionKind.ALLOW_ALWAYS, "Yes, remember"),
            ApprovalOption("cancel", ApprovalOptionKind.REJECT_ONCE, "No"),
        ))
        gateway.approvals.value = listOf(approval)
        gateway.approvalResult = ApprovalReply.UNKNOWN
        val answer = AnswerApproval(gateway)
        assertNull(approval.optionFor(ApprovalDecision.SESSION))
        assertEquals(ApprovalReply.UNAVAILABLE, answer(approval.id, ApprovalDecision.SESSION).reply)
        assertEquals(ApprovalReply.UNKNOWN, answer(approval.id, ApprovalDecision.ONCE).reply)
        assertEquals(ApprovalReply.UNAVAILABLE, answer(approval.id, ApprovalDecision.ONCE).reply)
        assertEquals(listOf(approval.id to "allow_once"), gateway.answers)
    }
}
