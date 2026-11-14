package app.regie.domain

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.LastAction
import app.regie.domain.model.LineChanges
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ModifiedFile
import app.regie.domain.model.Progress
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.SessionThread
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.ThreadLine
import app.regie.domain.model.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveSessionTest {

    private fun read(id: Long, path: String?, title: String = "Read File", status: ActionStatus? = ActionStatus.COMPLETED) =
        ThreadEntry.ToolCall(id, ToolKind.READ, title, path = path, status = status)

    private fun edit(id: Long, path: String, changes: LineChanges?, status: ActionStatus = ActionStatus.COMPLETED) =
        ThreadEntry.ToolCall(id, ToolKind.EDIT, "Edit $path", path = path, changes = changes, status = status)

    private fun live(vararg entries: ThreadEntry, status: SessionStatus = SessionStatus.WORKING, modified: List<String> = emptyList()) =
        LiveSession.of(session("s", status).copy(modifiedFiles = modified), SessionThread(entries.toList(), loaded = true))

    @Test
    fun `consecutive reads are grouped on one line, and another action breaks the group`() {
        val lines = live(
            read(1, "PassageCheck.ts"),
            read(2, "room.ts"),
            read(3, "passage.test.ts"),
            edit(4, "src/check/PassageCheck.ts", LineChanges(12, 3)),
            read(5, "room.ts"),
        ).lines

        assertEquals(
            listOf(
                ThreadLine.Read(1, listOf("PassageCheck.ts", "room.ts", "passage.test.ts"), inProgress = false),
                ThreadLine.Edit(4, "src/check/PassageCheck.ts", LineChanges(12, 3), ActionStatus.COMPLETED),
                ThreadLine.Read(5, listOf("room.ts"), inProgress = false),
            ),
            lines,
        )
    }

    @Test
    fun `a read without a path yet shows its title, and is in progress until it ends`() {
        val lines = live(read(1, "notes.md"), read(2, null, status = ActionStatus.PENDING)).lines

        assertEquals(listOf(ThreadLine.Read(1, listOf("notes.md", "Read File"), inProgress = true)), lines)
    }

    @Test
    fun `failed and interrupted reads are not grouped as successfully read files`() {
        val lines = live(
            read(1, "ok.md"),
            read(2, "missing.md", status = ActionStatus.FAILED),
            read(3, "cancelled.md", status = ActionStatus.INTERRUPTED),
            read(4, "next.md"),
        ).lines

        assertEquals(
            listOf(
                ThreadLine.Read(1, listOf("ok.md"), inProgress = false),
                ThreadLine.Other(2, "missing.md", ActionStatus.FAILED),
                ThreadLine.Other(3, "cancelled.md", ActionStatus.INTERRUPTED),
                ThreadLine.Read(4, listOf("next.md"), inProgress = false),
            ),
            lines,
        )
    }

    @Test
    fun `the last text is being written while the session works, and only then`() {
        val entries = arrayOf(ThreadEntry.UserMessage(1, "Fais-le"), ThreadEntry.AgentText(2, "Je lis"))

        assertEquals(ThreadLine.AgentText(2, "Je lis", streaming = true), live(*entries).lines.last())
        assertEquals(ThreadLine.AgentText(2, "Je lis", streaming = false), live(*entries, status = SessionStatus.IDLE).lines.last())
        val withAction = live(*entries, ThreadEntry.ToolCall(3, ToolKind.EXECUTE, "npm test", command = "npm test")).lines
        assertEquals(ThreadLine.AgentText(2, "Je lis", streaming = false), withAction[1])
    }

    @Test
    fun `a message the app sent dictated says so`() {
        val session = session("s")
        val thread = SessionThread(listOf(ThreadEntry.UserMessage(1, "dicté"), ThreadEntry.UserMessage(2, "écrit")), loaded = true)

        val lines = LiveSession.of(session, thread, dictated = setOf("dicté")).lines

        assertEquals(
            listOf(ThreadLine.UserMessage(1, "dicté", dictated = true), ThreadLine.UserMessage(2, "écrit", dictated = false)),
            lines,
        )
    }

    @Test
    fun `a pending approval ends the thread`() {
        val approval = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "rm -rf build")
        val live = LiveSession.of(session("s", SessionStatus.AWAITING_APPROVAL, approval = approval), SessionThread(loaded = true))

        assertEquals(listOf<ThreadLine>(ThreadLine.AwaitingApproval("rm -rf build")), live.lines)
    }

    @Test
    fun `commands, other actions, failures and interruptions each make a line`() {
        val lines = live(
            ThreadEntry.ToolCall(1, ToolKind.EXECUTE, "Terminal", command = "npm test", status = ActionStatus.FAILED, result = "2 failed"),
            ThreadEntry.ToolCall(2, ToolKind.EXECUTE, "cat notes.md", status = ActionStatus.IN_PROGRESS),
            ThreadEntry.ToolCall(3, ToolKind.OTHER, "Recherche web", status = ActionStatus.COMPLETED),
            ThreadEntry.TurnFailed(4, "agent arrêté"),
            ThreadEntry.Interrupted(5),
        ).lines

        assertEquals(
            listOf(
                ThreadLine.Run(1, "npm test", ActionStatus.FAILED, "2 failed"),
                ThreadLine.Run(2, "cat notes.md", ActionStatus.IN_PROGRESS, null),
                ThreadLine.Other(3, "Recherche web", ActionStatus.COMPLETED),
                ThreadLine.TurnFailed(4, "agent arrêté"),
                ThreadLine.Interrupted(5),
            ),
            lines,
        )
    }

    @Test
    fun `modified files come from the bridge, then from finished edits, with their lines added up`() {
        val live = live(
            edit(1, "todo.md", LineChanges(1, 0)),
            edit(2, "src/a.ts", LineChanges(12, 3)),
            edit(3, "src/a.ts", LineChanges(2, 1)),
            edit(4, "src/b.ts", LineChanges(5, 0), status = ActionStatus.PENDING),
            modified = listOf("README.md", "src/a.ts"),
        )

        assertEquals(
            listOf(ModifiedFile("README.md", null), ModifiedFile("src/a.ts", LineChanges(14, 4)), ModifiedFile("todo.md", LineChanges(1, 0))),
            live.modifiedFiles,
        )
    }

    @Test
    fun `a session not loaded yet, with nothing live, is loading`() {
        assertTrue(LiveSession.of(session("s"), SessionThread.EMPTY).loading)
        assertFalse(LiveSession.of(session("s"), SessionThread(loaded = true)).loading)
        assertFalse(LiveSession.of(session("s"), SessionThread(listOf(ThreadEntry.AgentText(1, "x")))).loading)
    }

    @Test
    fun `only a session at work or awaiting approval can be interrupted`() {
        assertTrue(live(status = SessionStatus.WORKING).canInterrupt)
        assertTrue(live(status = SessionStatus.AWAITING_APPROVAL).canInterrupt)
        assertFalse(live(status = SessionStatus.IDLE).canInterrupt)
        assertFalse(live(status = SessionStatus.STOPPED).canInterrupt)
    }

    @Test
    fun `the summary tells the status, the step, the last action, the files and the last sentence`() {
        val session = session("s").copy(
            progress = Progress(2, 5, "Lancer les tests"),
            lastAction = LastAction("npm test", ActionStatus.IN_PROGRESS),
            modifiedFiles = listOf("src/a.ts"),
        )
        val thread = SessionThread(listOf(ThreadEntry.AgentText(1, "J'ai corrigé le minimum. Je lance les tests\n\n")), loaded = true)

        val summary = LiveSession.of(session, thread).summary

        assertEquals(SessionStatus.WORKING, summary.status)
        assertEquals(3, summary.progress?.step)
        assertEquals(LastAction("npm test", ActionStatus.IN_PROGRESS), summary.lastAction)
        assertEquals(listOf("src/a.ts"), summary.modifiedFiles)
        assertEquals("Je lance les tests", summary.lastSentence)
        assertNull(summary.pendingApproval)
    }

    @Test
    fun `the last sentence ignores trailing blanks and stays on one line`() {
        assertEquals("Aucun autre fichier modifié.", LiveSession.lastSentence("Résumé ajouté. Aucun autre fichier modifié."))
        assertEquals("Fin ?", LiveSession.lastSentence("Un.\nDeux ! Fin ?  \n"))
        assertNull(LiveSession.lastSentence("  \n"))
    }
}
