package app.regie.gateway.bridge

import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Delivery
import app.regie.domain.model.SessionId
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReplayAgentGatewayTest {

    @Test
    fun `fake gateway resolves only a choice actually offered by the recording`() = runTest {
        val gateway = ReplayAgentGateway()
        val job = launch { gateway.maintainConnection() }
        val request = gateway.approvals.first { it.isNotEmpty() }.first()
        assertEquals(app.regie.domain.model.ApprovalReply.UNAVAILABLE, gateway.answerApproval(request.id, "invented"))
        assertTrue(gateway.approvals.value.any { it.id == request.id })
        assertEquals(app.regie.domain.model.ApprovalReply.ACCEPTED, gateway.answerApproval(request.id, request.options.first().id))
        assertTrue(gateway.approvals.value.none { it.id == request.id })
        assertEquals(app.regie.domain.model.ApprovalReply.UNAVAILABLE, gateway.answerApproval(request.id, request.options.first().id))
        job.cancel()
    }

    @Test
    fun `the fake gateway replays the demo at its own pace, through the bridge decoding`() = runTest {
        val gateway = ReplayAgentGateway()
        val job = launch { gateway.maintainConnection() }

        // Temps virtuel : la démo dure une vingtaine de secondes.
        val sessions = gateway.sessions.first { list -> list.count { it.status == SessionStatus.IDLE } == 2 && list.size == 11 }
        assertEquals(BridgeConnection.Connected, gateway.connection.value)
        assertTrue(gateway.agents.value.isNotEmpty())
        assertTrue(sessions.any { it.lastAction?.description == "Edit todo.md" })
        assertTrue(gateway.shutdownBridge())
        job.cancel()
    }

    @Test
    fun `the replayed dates are moved so that the demo seems to happen now`() {
        val now = Instant.parse("2026-11-14T10:00:00Z")
        val frames = Fixtures.demoRun()
        val shifted = ReplayAgentGateway.shiftedToNow(frames, now)

        // Le premier événement arrive 942 ms après le début de l'enregistrement : il porte cette date-là.
        val first = shifted.first { it.frame.obj("event") != null }
        assertEquals(now + (first.atMillis - frames.first().atMillis).milliseconds, first.frame.obj("event")?.instant("at"))

        // À la fin de la démo, rien n'a plus que la durée de l'enregistrement (~22 s).
        val sessions = ReplayAgentGateway.finalState(shifted).sessions()
        val demo = sessions.filter { it.active }
        assertEquals(2, demo.size)
        assertTrue(demo.all { it.lastActivityAt in now..(now + 30.seconds) }, demo.map { it.lastActivityAt }.toString())
        assertTrue(demo.all { it.updatedAt in now..(now + 30.seconds) })
        // L'historique garde son écart avec la démo : 25 minutes plus tôt, pas dans le futur.
        assertTrue(sessions.filter { !it.active }.all { it.updatedAt < now })
    }

    @Test
    fun `the fake gateway shows recent ages while replaying`() = runTest {
        val now = Instant.parse("2026-11-14T10:00:00Z")
        val gateway = ReplayAgentGateway(Fixtures::demoRun, speed = 1.0, clock = object : Clock {
            override fun now() = now
        })
        val job = launch { gateway.maintainConnection() }

        val working = gateway.sessions.first { list -> list.any { it.status == SessionStatus.WORKING } }
            .filter { it.status == SessionStatus.WORKING }
        assertTrue(working.all { it.lastActivityAt >= now }, working.map { it.lastActivityAt }.toString())
        job.cancel()
    }

    @Test
    fun `the fake gateway shows the replayed thread and adds a message sent to it`() = runTest {
        val gateway = ReplayAgentGateway(Fixtures::demoRun, speed = Double.POSITIVE_INFINITY, clock = Clock.System)
        val job = launch { gateway.maintainConnection() }
        gateway.sessions.first { it.size == 11 }
        val claude = SessionId("00000000-0000-4000-8000-000000000044")

        val replayed = gateway.thread(claude).first { it.entries.isNotEmpty() }
        assertTrue(replayed.loaded)
        assertEquals(5, replayed.entries.size)

        assertEquals(Delivery.PROMPT, gateway.sendMessage(claude, "Merci"))
        assertTrue(gateway.interrupt(claude))
        val after = gateway.thread(claude).first { it.entries.size == 7 }
        assertEquals("Merci", (after.entries[5] as ThreadEntry.UserMessage).text)
        assertTrue(after.entries[6] is ThreadEntry.Interrupted)
        assertEquals(Delivery.NOT_SENT, gateway.sendMessage(SessionId("inconnue"), "x"))
        job.cancel()
    }
}
