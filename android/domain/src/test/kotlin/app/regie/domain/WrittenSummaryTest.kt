package app.regie.domain

import app.regie.domain.model.*
import kotlin.test.*

class WrittenSummaryTest {
    @Test fun `a previous closing marker cannot complete a newer unfinished report`() {
        val old = "[Point Régie]\nFait : ancien\nTests : anciens\nSuite : rien\n[/Point Régie]"
        val partial = "[Point Régie]\nFait : nouveau\nTests : nouveaux\nSuite : à"
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, "$old\n$partial")), loaded = true)
        assertFalse(LiveSession.of(session("s"), thread).writtenSummary.fromAgent)
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
        val summary = LiveSession.of(session("s", SessionStatus.IDLE), thread).writtenSummary
        assertEquals(listOf("Fait : Minimum corrigé dans PassageCheck.ts.", "Tests : 68 tests passent.", "Suite : Commit à valider."), summary.lines)
        assertEquals("Veux-tu le commit ?", summary.question)
        assertTrue(summary.fromAgent)
    }

    @Test fun `a new turn cannot reuse successful tests from the previous turn`() {
        val old = ThreadEntry.AgentText(1, "[Point Régie]\nFait : Corrigé\nTests : 68 passent\nSuite : rien\n[/Point Régie]")
        val thread = SessionThread(listOf(old, ThreadEntry.UserMessage(2, "Recommence"), ThreadEntry.AgentText(3, "Je commence.")), loaded = true)
        val summary = LiveSession.of(session("s"), thread).writtenSummary
        assertFalse(summary.fromAgent)
        assertEquals("Tests : résultats non communiqués dans un bilan.", summary.lines[1])
        assertNull(summary.question)
    }

    @Test fun `partial malformed and huge reports stay bounded and explicit`() {
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, "[Point Régie]\nFait : terminé\nTests : passent")), loaded = true)
        assertFalse(LiveSession.of(session("s"), thread).writtenSummary.fromAgent)
        val huge = thread.copy(entries = listOf(ThreadEntry.AgentText(2, "[Point Régie]\nFait : ${"x".repeat(2000)}\nTests : non communiqués\nSuite : à vérifier\nQuestion : aucune\n[/Point Régie]")))
        val summary = LiveSession.of(session("s", SessionStatus.IDLE), huge).writtenSummary
        assertEquals(3, summary.lines.size)
        assertTrue(summary.lines.all { it.length <= 240 && !it.contains('\n') })
        assertNull(summary.question)
    }
}
