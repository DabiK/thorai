package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DeliverDictationTest {
    @Test fun `removed excerpt comment carries side coordinate uncertainty and exact text`() = runTest {
        val gateway = StubGateway()
        val deliver = DeliverDictation(SendMessage(gateway, DictatedMessages()), AnswerApproval(gateway))
        deliver(DictationTarget.DiffComment(SessionId("s"), "a.kt", 7, removed = true, fileCoordinates = false, selectedText = "const old = 4"), "Garde ceci")
        assertEquals("Sur a.kt, ligne 7 supprimée de l’extrait (position dans le fichier non communiquée) :\nLigne sélectionnée : const old = 4\nGarde ceci", gateway.sent.single().second)
    }

    @Test fun `diff comment keeps selected path and line and is marked dictated`() = runTest {
        val gateway = StubGateway()
        val dictated = DictatedMessages()
        val deliver = DeliverDictation(SendMessage(gateway, dictated), AnswerApproval(gateway))
        val target = DictationTarget.DiffComment(SessionId("s"), "src/PassageCheck.ts", 42)
        assertNull(deliver(target, "Ajoute un test").retryTarget)
        assertEquals(listOf(SessionId("s") to "Sur src/PassageCheck.ts, ligne 42 :\nAjoute un test"), gateway.sent)
        assertTrue(dictated.texts.value[SessionId("s")]!!.contains(gateway.sent.single().second))
    }
    @Test fun `rejection with failed instruction retries only instruction and uncertain refusal never sends it`() = runTest {
        val gateway = StubGateway()
        val request = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "touch x", options = listOf(ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
        gateway.approvals.value = listOf(request)
        val target = DictationTarget.Rejection(request.sessionId, request.id)
        val deliver = DeliverDictation(SendMessage(gateway, DictatedMessages()), AnswerApproval(gateway))
        gateway.approvalResult = ApprovalReply.UNKNOWN
        assertNull(deliver(target, "Lis seulement").retryTarget)
        assertTrue(gateway.sent.isEmpty())
        gateway.approvalResult = ApprovalReply.ACCEPTED
        gateway.delivery = Delivery.NOT_SENT
        val second = DeliverDictation(SendMessage(gateway, DictatedMessages()), AnswerApproval(gateway))
        val result = second(target, "Lis seulement")
        assertEquals(DictationTarget.Message(request.sessionId), result.retryTarget)
        gateway.delivery = Delivery.PROMPT
        second(result.retryTarget!!, "Lis seulement")
        assertEquals(2, gateway.answers.size)
        assertEquals(listOf(request.sessionId to "Lis seulement", request.sessionId to "Lis seulement"), gateway.sent)
    }
}
