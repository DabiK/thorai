package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.AnswerApproval
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
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
    @Test
    fun `refusal delivers instruction only after a positive receipt and permits retry only when not sent`() = runTest {
        val gateway = StubGateway()
        val request = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.FILE_CHANGE, "x", options = listOf(
            ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
            ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Session"),
        ))
        gateway.approvals.value = listOf(request)
        val answer = AnswerApproval(gateway)
        gateway.approvalResult = ApprovalReply.NOT_SENT
        assertEquals(ApprovalReply.NOT_SENT, answer(request.id, ApprovalDecision.REJECT, "do this").reply)
        assertEquals(emptyList(), gateway.sent)
        gateway.approvalResult = ApprovalReply.ACCEPTED
        assertEquals(Delivery.PROMPT, answer(request.id, ApprovalDecision.REJECT, "  do this  ").instructionDelivery)
        assertEquals(listOf(request.sessionId to "do this"), gateway.sent)
        assertEquals(ApprovalReply.UNAVAILABLE, answer(request.id, ApprovalDecision.REJECT, "again").reply)
        assertEquals(1, gateway.sent.size)
        val sessionAnswer = AnswerApproval(gateway)
        assertEquals(ApprovalReply.ACCEPTED, sessionAnswer(request.id, ApprovalDecision.SESSION).reply)
        assertEquals(request.id to "allow_for_session", gateway.answers.last())
        gateway.approvals.value = emptyList()
        assertEquals(ApprovalReply.UNAVAILABLE, AnswerApproval(gateway)(request.id, ApprovalDecision.ONCE).reply)
    }

    @Test
    fun `unknown refusal never sends a follow up instruction`() = runTest {
        val gateway = StubGateway()
        val request = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "touch x", options = listOf(
            ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
        ))
        gateway.approvals.value = listOf(request)
        gateway.approvalResult = ApprovalReply.UNKNOWN
        assertNull(AnswerApproval(gateway)(request.id, ApprovalDecision.REJECT, "instead").instructionDelivery)
        assertEquals(emptyList(), gateway.sent)
    }

    @Test
    fun `two concurrent decisions submit only the first while waiting for the receipt`() = runTest {
        val base = StubGateway()
        val request = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "touch x", options = listOf(
            ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"),
            ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
        ))
        base.approvals.value = listOf(request)
        val receipt = kotlinx.coroutines.CompletableDeferred<ApprovalReply>()
        val gateway = object : app.regie.domain.port.AgentGateway by base {
            override suspend fun answerApproval(id: ApprovalId, optionId: String): ApprovalReply {
                base.answers += id to optionId
                return receipt.await()
            }
        }
        val answer = AnswerApproval(gateway)
        val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { answer(request.id, ApprovalDecision.ONCE) }
        assertEquals(ApprovalReply.UNAVAILABLE, answer(request.id, ApprovalDecision.REJECT).reply)
        receipt.complete(ApprovalReply.ACCEPTED)
        assertEquals(ApprovalReply.ACCEPTED, first.await().reply)
        assertEquals(listOf(request.id to "yes"), base.answers)
    }

}
