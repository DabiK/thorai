package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.ReviewDiff
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QueuedExplanationReviewTest {
    @Test fun `queued explanation does not display the preceding turn answer`() = runTest {
        val gateway = StubGateway()
        val id = SessionId("s")
        gateway.sessions.value = listOf(session("s").copy(status = SessionStatus.WORKING))
        val initial = listOf(
            ThreadEntry.UserMessage(0, "Lance les tests"),
            ThreadEntry.ToolCall(1, ToolKind.EDIT, "Edit", status = ActionStatus.COMPLETED,
                diffs = listOf(DiffFragment("a.kt", listOf(DiffLine(DiffKind.ADDED, "val x = 1", null, 1)), true)))
        )
        gateway.threadOf(id).value = SessionThread(initial, loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(id); runCurrent()
        review.explain()
        val prompt = review.state.value.explanation!!.prompt
        gateway.delivery = Delivery.QUEUED
        review.confirmExplanation(); runCurrent()
        // Bridge.startTurn emits user_message immediately, before the preceding turn has ended.
        gateway.threadOf(id).value = SessionThread(initial + listOf(
            ThreadEntry.UserMessage(2, prompt),
            ThreadEntry.AgentText(3, "Les tests du tour précédent sont verts.", responseTo = 0)
        ), loaded = true)
        runCurrent()
        assertEquals(Delivery.QUEUED, review.state.value.explanation!!.delivery)
        assertEquals("", review.state.value.explanation!!.reply,
            "The preceding turn is still running; the explanation has not started")
        assertEquals(false, review.state.value.explanation!!.ended)
        // A later queued message must neither close nor steal this explanation.
        val replies = initial + listOf(
            ThreadEntry.UserMessage(2, prompt),
            ThreadEntry.AgentText(3, "Les tests du tour précédent sont verts.", responseTo = 0),
            ThreadEntry.UserMessage(4, "Question suivante"),
            ThreadEntry.AgentText(5, "Ce bloc initialise x à 1.", responseTo = 2),
        )
        gateway.threadOf(id).value = SessionThread(replies, loaded = true, completedMessages = setOf(0))
        runCurrent()
        assertEquals("Ce bloc initialise x à 1.", review.state.value.explanation!!.reply)
        assertEquals(false, review.state.value.explanation!!.ended)
        gateway.threadOf(id).value = SessionThread(replies +
            ThreadEntry.AgentText(6, "Réponse suivante", responseTo = 4), loaded = true,
            completedMessages = setOf(0, 2))
        runCurrent()
        assertEquals("Ce bloc initialise x à 1.", review.state.value.explanation!!.reply)
        assertEquals(true, review.state.value.explanation!!.ended)
        review.close()
    }
}
