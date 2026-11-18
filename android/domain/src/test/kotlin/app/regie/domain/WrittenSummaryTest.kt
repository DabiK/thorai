package app.regie.domain

import app.regie.domain.model.*
import kotlin.test.*

class WrittenSummaryTest {
    @Test fun `a previous closing marker cannot complete a newer unfinished report`() {
        val old = "[Point Régie]\nFait : ancien\nTests : anciens\nSuite : rien\n[/Point Régie]"
        val partial = "[Point Régie]\nFait : nouveau\nTests : nouveaux\nSuite : à"
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, "$old\n$partial")), loaded = true)
        assertFalse(WrittenSummary.of(session("s"), thread).fromAgent)
    }

    @Test fun `completed turn exposes the three agent statements without inventing tests`() {
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, """
            Explication longue.
            [Point Régie]
            Fait : Minimum corrigé dans PassageCheck.ts.
            Tests : 68 tests passent.
            Suite : Commit à valider.
            Question : Veux-tu le commit ?
            [/Point Régie]
        """.trimIndent())), loaded = true)
        val summary = WrittenSummary.of(session("s", SessionStatus.IDLE), thread)
        assertEquals(listOf("Fait : Minimum corrigé dans PassageCheck.ts.", "Tests : 68 tests passent.", "Suite : Commit à valider."), summary.lines)
        assertEquals("Veux-tu le commit ?", summary.question)
        assertTrue(summary.fromAgent)
    }

    @Test fun `a new turn cannot reuse successful tests from the previous turn`() {
        val old = ThreadEntry.AgentText(1, "[Point Régie]\nFait : Corrigé\nTests : 68 passent\nSuite : rien\n[/Point Régie]")
        val thread = SessionThread(listOf(old, ThreadEntry.UserMessage(2, "Recommence"), ThreadEntry.AgentText(3, "Je commence.")), loaded = true)
        val summary = WrittenSummary.of(session("s"), thread)
        assertFalse(summary.fromAgent)
        assertEquals("Tests : résultats non communiqués dans un bilan.", summary.lines[1])
        assertNull(summary.question)
    }

    @Test fun `partial malformed and huge reports stay bounded and explicit`() {
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, "[Point Régie]\nFait : terminé\nTests : passent")), loaded = true)
        assertFalse(WrittenSummary.of(session("s"), thread).fromAgent)
        val huge = thread.copy(entries = listOf(ThreadEntry.AgentText(2, "[Point Régie]\nFait : ${"x".repeat(2000)}\nTests : non communiqués\nSuite : à vérifier\nQuestion : aucune\n[/Point Régie]")))
        val summary = WrittenSummary.of(session("s", SessionStatus.IDLE), huge)
        assertEquals(3, summary.lines.size)
        assertTrue(summary.lines.all { it.length <= 240 && !it.contains('\n') })
        assertNull(summary.question)
    }

    @Test fun `the thread hides the report block Régie asked for and keeps the agent words`() {
        val text = "J’ai corrigé le seuil.\n\n[Point Régie]\nFait : seuil\nTests : non communiqués\nSuite : rien\nQuestion : aucune\n[/Point Régie]"
        assertEquals("J’ai corrigé le seuil.", RegieReport.strip(text))
        assertEquals("Avant\n\nAprès", RegieReport.strip("Avant\n[Point Régie]\nFait : x\n[/Point Régie]\nAprès"))
        // Unfinished while it streams: everything from the opening marker stays hidden.
        assertEquals("Je regarde.", RegieReport.strip("Je regarde.\n\n[Point Régie]\nFait : en c"))
        assertEquals("", RegieReport.strip("[Point Régie]\nFait : x\n[/Point Régie]"))
        val untouched = "  Code :\n```\n[Point] x\n```\n"
        assertSame(untouched, RegieReport.strip(untouched))
    }

    @Test fun `a thread line made only of the report disappears and the last sentence is the agent one`() {
        val thread = SessionThread(listOf(
            ThreadEntry.UserMessage(1, "Corrige"),
            ThreadEntry.AgentText(2, "Corrigé. Veux-tu un commit ?"),
            ThreadEntry.ToolCall(3, ToolKind.EXECUTE, "check", command = "npm test", status = ActionStatus.COMPLETED),
            ThreadEntry.AgentText(4, "[Point Régie]\nFait : x\nTests : y\nSuite : rien\nQuestion : aucune\n[/Point Régie]"),
        ), loaded = true)
        val live = LiveSession.of(session("s", SessionStatus.IDLE), thread)
        assertEquals(listOf(1L, 2L, 3L), live.lines.map { it.key })
        assertEquals("Veux-tu un commit ?", live.summary.lastSentence)
        // The raw report still feeds notifications.
        assertTrue(WrittenSummary.of(live.session, thread).fromAgent)
    }

    @Test fun `an empty answer that just started streaming keeps its cursor line`() {
        val thread = SessionThread(listOf(ThreadEntry.UserMessage(1, "Go"), ThreadEntry.AgentText(2, "")), loaded = true)
        val live = LiveSession.of(session("s", SessionStatus.WORKING), thread)
        assertEquals(ThreadLine.AgentText(2, "", streaming = true), live.lines.last())
    }
}
