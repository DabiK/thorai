package app.regie.domain

import app.regie.domain.model.Delivery
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.SessionThread
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.ThreadLine
import app.regie.domain.usecase.DictatedMessages
import app.regie.domain.usecase.InterruptSession
import app.regie.domain.usecase.OpenSession
import app.regie.domain.usecase.SendMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class LiveSessionsTest {

    private val gateway = StubGateway()
    private val dictated = DictatedMessages()
    private val id = SessionId("s")

    @Test
    fun `opening a session follows the session and its thread`() = runTest {
        gateway.sessions.value = listOf(session("s"))
        gateway.threadOf(id).value = SessionThread(listOf(ThreadEntry.AgentText(1, "Je lis")), loaded = true)

        val live = OpenSession(gateway, dictated)(id).first { it != null }!!

        assertEquals(listOf<ThreadLine>(ThreadLine.AgentText(1, "Je lis", streaming = true)), live.lines)
    }

    @Test
    fun `an unknown session opens as nothing`() = runTest {
        assertNull(OpenSession(gateway, dictated)(id).first())
    }

    @Test
    fun `a dictated message is remembered before it leaves, and shown as dictated`() = runTest {
        gateway.sessions.value = listOf(session("s"))
        gateway.delivery = Delivery.STEERING

        assertEquals(Delivery.STEERING, SendMessage(gateway, dictated)(id, "  Arrête-toi là  ", dictated = true))
        gateway.threadOf(id).value = SessionThread(listOf(ThreadEntry.UserMessage(1, "Arrête-toi là")), loaded = true)

        assertEquals(listOf(id to "Arrête-toi là"), gateway.sent)
        val live = OpenSession(gateway, dictated)(id).first { it != null }!!
        assertEquals(listOf<ThreadLine>(ThreadLine.UserMessage(1, "Arrête-toi là", dictated = true)), live.lines)
    }

    @Test
    fun `a dictated message that did not leave is forgotten, and an empty one never leaves`() = runTest {
        gateway.delivery = Delivery.NOT_SENT

        assertEquals(Delivery.NOT_SENT, SendMessage(gateway, dictated)(id, "x", dictated = true))
        assertEquals(emptySet(), dictated.texts.value[id])
        assertNull(SendMessage(gateway, dictated)(id, "   "))
        assertEquals(listOf(id to "x"), gateway.sent)
    }

    @Test
    fun `a cancelled message is not retained as a delivered dictation`() = runTest {
        gateway.delivery = Delivery.CANCELLED
        assertEquals(Delivery.CANCELLED, SendMessage(gateway, dictated)(id, "Annule", dictated = true))
        assertEquals(emptySet(), dictated.texts.value[id])
    }

    @Test
    fun `unknown delivery keeps the dictated origin when the message arrives later`() = runTest {
        gateway.sessions.value = listOf(session("s"))
        gateway.delivery = Delivery.UNKNOWN
        assertEquals(Delivery.UNKNOWN, SendMessage(gateway, dictated)(id, "Relance les tests", dictated = true))
        gateway.threadOf(id).value = SessionThread(listOf(ThreadEntry.UserMessage(1, "Relance les tests")), loaded = true)
        val live = OpenSession(gateway, dictated)(id).first { it != null }!!
        assertEquals(listOf<ThreadLine>(ThreadLine.UserMessage(1, "Relance les tests", dictated = true)), live.lines)
        assertEquals(listOf(id to "Relance les tests"), gateway.sent)
    }

    @Test
    fun `only certain non delivery allows retry`() {
        assertEquals(setOf(Delivery.NOT_SENT, Delivery.CANCELLED), Delivery.entries.filter { it.canRetry }.toSet())
    }

    @Test
    fun `interrupting asks the bridge only for a session at work`() = runTest {
        val interrupt = InterruptSession(gateway)

        assertEquals(true, interrupt(LiveSession.of(session("s"), SessionThread.EMPTY)))
        assertNull(interrupt(LiveSession.of(session("t", SessionStatus.IDLE), SessionThread.EMPTY)))
        assertEquals(listOf(id), gateway.interrupted)
    }

    @Test
    fun `neighbours follow the overview order without wrapping`() {
        val overview = Overview.of(
            listOf(session("a", updatedAt = T0), session("b", updatedAt = T0 - 1.minutes), session("c", updatedAt = T0 - 2.minutes)),
            emptyList(),
        )

        val first = overview.neighboursOf(SessionId("a"))!!
        assertEquals(null, first.previous)
        assertEquals("b", first.next?.id?.value)
        assertEquals(1 to 3, first.position to first.count)
        val middle = overview.neighboursOf(SessionId("b"))!!
        assertEquals("a" to "c", middle.previous?.id?.value to middle.next?.id?.value)
        assertEquals(null, overview.neighboursOf(SessionId("c"))!!.next)
        assertNull(overview.neighboursOf(SessionId("z")))
    }
}
