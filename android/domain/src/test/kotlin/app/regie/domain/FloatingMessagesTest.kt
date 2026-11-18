package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.ConsoleLaunch
import app.regie.domain.usecase.ConsoleSession
import app.regie.domain.usecase.FloatingMessages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingMessagesTest {
    private val id = SessionId("s")
    private val gateway = StubGateway()
    private val away = MutableStateFlow(true)
    private val thread get() = gateway.threadOf(id)

    private fun TestScope.start(history: List<ThreadEntry> = listOf(ThreadEntry.UserMessage(1, "Ouvre YouTube"))): FloatingMessages {
        gateway.sessions.value = listOf(session("s"))
        thread.value = SessionThread(history, loaded = true)
        return FloatingMessages(gateway, away, backgroundScope).also { runCurrent() }
    }

    private fun add(vararg entries: ThreadEntry) { thread.value = thread.value.copy(entries = thread.value.entries + entries) }

    @Test fun `a streaming fragment never pops a card, the finished message does`() = runTest {
        val cards = start()
        add(ThreadEntry.AgentText(2, "J’ouvre"))
        runCurrent()
        assertNull(cards.card.value)
        thread.value = thread.value.copy(entries = thread.value.entries.dropLast(1) + ThreadEntry.AgentText(2, "J’ouvre YouTube."))
        runCurrent()
        assertNull(cards.card.value, "still the last entry of a working session")
        add(ThreadEntry.ToolCall(3, ToolKind.OTHER, "mobile_open_url", status = ActionStatus.IN_PROGRESS))
        runCurrent()
        val card = assertNotNull(cards.card.value)
        assertEquals(FloatingKind.MESSAGE, card.kind)
        assertEquals("J’ouvre YouTube.", card.text)
        assertEquals("projet-s", card.project)
        // The same message never makes a second card.
        cards.dismiss()
        add(ThreadEntry.ToolCall(4, ToolKind.OTHER, "mobile_type", status = ActionStatus.IN_PROGRESS))
        runCurrent()
        assertNull(cards.card.value)
    }

    @Test fun `history and messages seen inside Régie stay silent`() = runTest {
        val cards = start(listOf(ThreadEntry.UserMessage(1, "Bonjour"), ThreadEntry.AgentText(2, "Ancienne réponse ?")))
        assertNull(cards.card.value)
        away.value = false
        add(ThreadEntry.AgentText(3, "Vu dans Régie."), ThreadEntry.ToolCall(4, ToolKind.READ, "Read"))
        runCurrent()
        away.value = true
        runCurrent()
        assertNull(cards.card.value)
    }

    @Test fun `a turn ending on a question makes a question card that lasts longer`() = runTest {
        val cards = start()
        add(ThreadEntry.AgentText(2, "La recherche lofi est lancée. Je lance la première vidéo ?"))
        gateway.sessions.value = listOf(session("s", SessionStatus.IDLE))
        runCurrent()
        assertEquals(FloatingKind.QUESTION, cards.card.value?.kind)
        advanceTimeBy(9.seconds); runCurrent()
        assertNotNull(cards.card.value, "a question stays more than a plain message")
        advanceTimeBy(7.seconds); runCurrent()
        assertNull(cards.card.value)
    }

    @Test fun `a plain message fades by itself, B or Régie coming back removes it`() = runTest {
        val cards = start()
        add(ThreadEntry.AgentText(2, "C’est fait."))
        gateway.sessions.value = listOf(session("s", SessionStatus.IDLE))
        runCurrent()
        assertEquals(FloatingKind.MESSAGE, cards.card.value?.kind)
        advanceTimeBy(8.seconds); runCurrent()
        assertNull(cards.card.value)

        gateway.sessions.value = listOf(session("s"))
        add(ThreadEntry.UserMessage(3, "Encore"), ThreadEntry.AgentText(4, "Encore fait."), ThreadEntry.ToolCall(5, ToolKind.READ, "Read"))
        runCurrent()
        assertNotNull(cards.card.value)
        away.value = false
        runCurrent()
        assertNull(cards.card.value)
    }

    @Test fun `a pinned card stays while the user answers and only an approval outranks it`() = runTest {
        val cards = start()
        add(ThreadEntry.AgentText(2, "Premier."), ThreadEntry.ToolCall(3, ToolKind.READ, "Read"))
        runCurrent()
        val pinned = cards.pin()
        add(ThreadEntry.AgentText(4, "Second."), ThreadEntry.ToolCall(5, ToolKind.READ, "Read"))
        advanceTimeBy(30.seconds); runCurrent()
        assertEquals("Premier.", cards.card.value?.text)
        // The agent asks for an approval while the user is still answering: it shows anyway.
        gateway.approvals.value = listOf(Approval(ApprovalId("a"), id, ApprovalKind.COMMAND, "am start"))
        runCurrent()
        assertEquals(FloatingKind.APPROVAL, cards.card.value?.kind)
        // The answer ends: only the pinned card would leave, not the approval that replaced it.
        cards.release(pinned)
        assertEquals(FloatingKind.APPROVAL, cards.card.value?.kind)
        cards.dismiss()
        assertNull(cards.card.value)
    }

    @Test fun `a second approval waits for the first card to leave`() = runTest {
        val cards = start()
        val first = Approval(ApprovalId("a"), id, ApprovalKind.COMMAND, "un")
        val second = Approval(ApprovalId("b"), id, ApprovalKind.COMMAND, "deux")
        // Two approvals in the same update: one card at a time.
        gateway.approvals.value = listOf(first, second)
        runCurrent()
        assertEquals("deux", cards.card.value?.text)
        cards.dismiss(); runCurrent()
        assertEquals("un", cards.card.value?.text, "the waiting approval shows once the card leaves")
        cards.dismiss(); runCurrent()
        assertNull(cards.card.value)
    }

    @Test fun `an approval card outranks messages and leaves once answered elsewhere`() = runTest {
        val cards = start()
        val request = Approval(ApprovalId("a"), id, ApprovalKind.COMMAND, "am start youtube")
        gateway.approvals.value = listOf(request)
        runCurrent()
        val card = assertNotNull(cards.card.value)
        assertEquals(FloatingKind.APPROVAL, card.kind)
        assertEquals("am start youtube", card.text)
        assertEquals(request, card.approval)
        add(ThreadEntry.AgentText(2, "Un message moins important."), ThreadEntry.ToolCall(3, ToolKind.READ, "Read"))
        runCurrent()
        assertEquals(FloatingKind.APPROVAL, cards.card.value?.kind)
        gateway.approvals.value = emptyList()
        runCurrent()
        assertNull(cards.card.value)
    }

    @Test fun `approvals already pending at start stay silent`() = runTest {
        gateway.approvals.value = listOf(Approval(ApprovalId("old"), id, ApprovalKind.COMMAND, "ls"))
        val cards = start()
        assertNull(cards.card.value)
    }

    @Test fun `a long message keeps its end, cut between sentences`() {
        val long = "Début. " + "Phrase de remplissage assez longue. ".repeat(10) + "Je lance la vidéo ?"
        val excerpt = FloatingCard.excerpt(long)
        assertTrue(excerpt.startsWith("… Phrase"), excerpt)
        assertTrue(excerpt.endsWith("Je lance la vidéo ?"))
        assertTrue(excerpt.length <= FloatingCard.MAX_LENGTH)
        assertEquals("Court.", FloatingCard.excerpt("  Court.\n"))
    }

    @Test fun `the console shortcut resumes the latest console session or prepares one`() {
        val old = session("old", SessionStatus.IDLE, active = false, updatedAt = T0).copy(project = "console")
        val live = session("live", SessionStatus.IDLE, updatedAt = T0 - 60.seconds).copy(project = "console")
        val other = session("other", updatedAt = T0 + 60.seconds).copy(project = "atelier", agent = Agent.CLAUDE)
        assertEquals(ConsoleLaunch.Resume(live.id), ConsoleSession.choose(listOf(old, live, other), listOf("console")))
        assertEquals(ConsoleLaunch.Resume(old.id), ConsoleSession.choose(listOf(old, other), listOf("console")))
        val create = ConsoleSession.choose(listOf(other), listOf("atelier")) as ConsoleLaunch.Create
        assertEquals(NewSessionDraft(Agent.CLAUDE, "console", approvalMode = "allow-all", newProject = true), create.draft)
        val existing = ConsoleSession.choose(emptyList(), listOf("console")) as ConsoleLaunch.Create
        assertEquals(Agent.CODEX, existing.draft.agent)
        assertEquals("allow-all", existing.draft.approvalMode)
        assertFalse(existing.draft.newProject)
    }
}
