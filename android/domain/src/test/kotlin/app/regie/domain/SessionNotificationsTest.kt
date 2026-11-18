package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.SessionNotifications
import app.regie.domain.usecase.NotifySessions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionNotificationsTest {
    @Test fun `an agent failure does not wait for a thread that cannot load`() = runTest {
        val gateway = StubGateway()
        val port = RecordingNotifications()
        gateway.sessions.value = listOf(session("s"))
        NotifySessions(gateway, MutableStateFlow(false), port, backgroundScope)
        runCurrent()
        gateway.sessions.value = listOf(session("s", SessionStatus.FAILED).copy(lastError = "Agent arrêté"))
        runCurrent()
        assertEquals(SessionNoticeKind.FAILED, port.shown.single().kind)
        assertTrue(port.shown.single().text.endsWith("Agent arrêté"))
    }

    @Test fun `background end opens its session once and initial history stays silent`() = runTest {
        val gateway = StubGateway()
        val foreground = MutableStateFlow(false)
        val port = RecordingNotifications()
        gateway.sessions.value = listOf(session("old", SessionStatus.IDLE), session("live"))
        gateway.threadOf(SessionId("live")).value = SessionThread(loaded = true)
        NotifySessions(gateway, foreground, port, backgroundScope)
        runCurrent()
        assertTrue(port.shown.isEmpty())
        gateway.sessions.value = listOf(session("old", SessionStatus.IDLE), session("live", SessionStatus.IDLE))
        runCurrent()
        assertEquals(listOf(SessionId("live")), port.shown.map { it.sessionId })
        assertEquals(SessionNoticeKind.FINISHED, port.shown.single().kind)
        foreground.value = true
        runCurrent()
        foreground.value = false
        runCurrent()
        assertEquals(1, port.shown.size)
    }
    @Test fun `a finished session notice quotes the agent last sentence, never a Régie report`() = runTest {
        val gateway = StubGateway()
        val port = RecordingNotifications()
        gateway.sessions.value = listOf(session("live"), session("quiet"))
        gateway.threadOf(SessionId("live")).value = SessionThread(listOf(ThreadEntry.UserMessage(1, "Corrige"),
            ThreadEntry.AgentText(2, "Le seuil est corrigé.\n\n[Point Régie]\nFait : x\nTests : y\nSuite : rien\nQuestion : aucune\n[/Point Régie]")), loaded = true)
        gateway.threadOf(SessionId("quiet")).value = SessionThread(listOf(ThreadEntry.UserMessage(1, "Lis")), loaded = true)
        NotifySessions(gateway, MutableStateFlow(false), port, backgroundScope)
        runCurrent()
        gateway.sessions.value = listOf(session("live", SessionStatus.IDLE), session("quiet", SessionStatus.IDLE))
        runCurrent()
        assertEquals(listOf("projet-live · Le seuil est corrigé.", "projet-quiet · Consulte le fil."), port.shown.map { it.text })
        assertTrue(port.shown.none { it.text.contains("Fait") || it.text.contains("bilan") })
    }

    @Test fun `question and failure alert only after ending and never for progress`() = runTest {
        val gateway = StubGateway()
        val foreground = MutableStateFlow(false)
        val port = RecordingNotifications()
        val id = SessionId("s")
        gateway.sessions.value = listOf(session("s"))
        gateway.threadOf(id).value = SessionThread(loaded = true)
        NotifySessions(gateway, foreground, port, backgroundScope)
        runCurrent()
        gateway.threadOf(id).value = SessionThread(listOf(ThreadEntry.AgentText(1, "Quel fichier ?")), loaded = true)
        runCurrent()
        assertTrue(port.shown.isEmpty())
        gateway.sessions.value = listOf(session("s", SessionStatus.IDLE))
        runCurrent()
        assertEquals(SessionNoticeKind.QUESTION, port.shown.single().kind)
        assertTrue(port.shown.single().text.endsWith("Quel fichier ?"))
        gateway.sessions.value = listOf(session("s"))
        runCurrent()
        assertEquals(listOf(id), port.cancelled)
        gateway.sessions.value = listOf(session("s", SessionStatus.FAILED).copy(lastError = "Processus arrêté"))
        runCurrent()
        assertEquals(SessionNoticeKind.FAILED, port.shown.last().kind)
        assertTrue(port.shown.last().text.endsWith("Processus arrêté"))
        assertEquals(2, port.shown.size)
    }

    @Test fun `foreground ending stays silent and blocked delivery retries on return to background`() = runTest {
        val gateway = StubGateway()
        val foreground = MutableStateFlow(true)
        val port = RecordingNotifications()
        gateway.sessions.value = listOf(session("s"))
        gateway.threadOf(SessionId("s")).value = SessionThread(loaded = true)
        NotifySessions(gateway, foreground, port, backgroundScope)
        runCurrent()
        gateway.sessions.value = listOf(session("s", SessionStatus.IDLE))
        runCurrent()
        foreground.value = false
        runCurrent()
        assertTrue(port.shown.isEmpty())
        port.allowed = false
        gateway.sessions.value = listOf(session("s"))
        runCurrent()
        gateway.sessions.value = listOf(session("s", SessionStatus.IDLE))
        runCurrent()
        assertTrue(port.shown.isEmpty())
        foreground.value = true
        runCurrent()
        port.allowed = true
        foreground.value = false
        runCurrent()
        assertEquals(1, port.shown.size)
    }

}

class RecordingNotifications : SessionNotifications {
    val shown = mutableListOf<SessionNotice>()
    val cancelled = mutableListOf<SessionId>()
    var allowed = true
    override fun show(notice: SessionNotice): Boolean {
        if (allowed) shown += notice
        return allowed
    }
    override fun cancel(id: SessionId) { cancelled += id }
}
