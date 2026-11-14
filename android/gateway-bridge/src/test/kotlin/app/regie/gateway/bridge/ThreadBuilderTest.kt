package app.regie.gateway.bridge

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.ContextUsage
import app.regie.domain.model.Agent
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ModifiedFile
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.ThreadLine
import kotlin.time.Instant
import app.regie.domain.model.LineChanges
import app.regie.domain.model.PlanStep
import app.regie.domain.model.PlanStepStatus
import app.regie.domain.model.SessionThread
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.ToolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le fil d'une session, sur les enregistrements réels du pont (bridge/fixtures/bridge-ws). */
class ThreadBuilderTest {

    private val claudeId = "00000000-0000-4000-8000-000000000044"
    private val codexId = "00000000-0000-4000-8000-000000000018"
    private val prompt = "Lis notes.md et ajoute son résumé en une seule ligne à la fin de todo.md. Ne modifie aucun autre fichier."

    private fun replay(name: String, id: String, upTo: Long = Long.MAX_VALUE): SessionThread {
        val state = BridgeState()
        Fixtures.events(name).filter { it.seq <= upTo }.forEach { state.apply(it) }
        return state.thread(id, loaded = true)!!
    }

    /** Une session vide, puis des mises à jour écrites à la main (forme ACP des enregistrements bruts). */
    private fun thread(vararg updates: String): SessionThread {
        val builder = ThreadBuilder(cwd = { "/home/dev/projects/demo" })
        updates.forEachIndexed { index, json ->
            val data = Frames.parseObject(json)!!
            val type = if (data.string("sessionUpdate") != null) "session_update" else data.string("type")!!
            builder.apply(BridgeEvent(index.toLong(), null, "s", type, data))
        }
        return builder.snapshot(loaded = true)
    }

    private fun ThreadEntry.ToolCall.essentials() = listOf(kind, title, command, path, changes, status, result)

    @Test
    fun `a Claude session reads two files, edits one with its diff counted, then answers`() {
        val thread = replay(Fixtures.CLAUDE_SESSION, claudeId)
        val entries = thread.entries

        assertEquals(ThreadEntry.UserMessage(entries[0].id, prompt), entries[0])
        val (notes, todo, edit) = entries.subList(1, 4).map { it as ThreadEntry.ToolCall }
        assertEquals(listOf(ToolKind.READ, "Read notes.md", null, "notes.md", null, ActionStatus.COMPLETED, null), notes.essentials())
        assertEquals("todo.md", todo.path)
        assertEquals(listOf(ToolKind.EDIT, "Edit todo.md", null, "todo.md", LineChanges(1, 0), ActionStatus.COMPLETED, null), edit.essentials())
        val answer = entries[4] as ThreadEntry.AgentText
        assertTrue(answer.text.startsWith("J'ai ajouté une ligne de résumé à la fin de `todo.md`. Aucun autre fichier n'a été modifié."))
        assertTrue(answer.text.endsWith("parce que c'est un résumé et pas une tâche."))
        assertEquals(5, entries.size)
        // Pas de plan dans l'enregistrement : rien d'inventé ; le contexte vient de usage_update.
        assertNull(thread.plan)
        assertEquals(ContextUsage(48729, 1_000_000), thread.context)
        assertEquals(5, thread.context?.percent)
    }

    @Test
    fun `while Claude waits for approval, the edit is pending with the diff it showed`() {
        val edit = replay(Fixtures.CLAUDE_SESSION, claudeId, upTo = 595).entries.last() as ThreadEntry.ToolCall

        assertEquals(ActionStatus.PENDING, edit.status)
        assertEquals(LineChanges(1, 0), edit.changes)
    }

    @Test
    fun `a Codex session streams tiny chunks into one text per run, and its commands give their last line`() {
        val entries = replay(Fixtures.CODEX_SESSION, codexId).entries

        assertEquals(ThreadEntry.UserMessage(entries[0].id, prompt), entries[0])
        assertEquals(
            "Je vais lire `notes.md` et `todo.md`, puis ajouter une seule ligne de résumé à la fin de `todo.md`.\n",
            (entries[1] as ThreadEntry.AgentText).text,
        )
        val cat = entries[2] as ThreadEntry.ToolCall
        assertEquals(listOf(ToolKind.EXECUTE, "cat notes.md todo.md", "cat notes.md todo.md", null, null, ActionStatus.COMPLETED), cat.essentials().dropLast(1))
        assertTrue(cat.result!!.startsWith("- Résumé des notes : sur les deux écrans de la Thor"), cat.result)
        val python = entries[3] as ThreadEntry.ToolCall
        assertTrue(python.command!!.startsWith("python3 -c"))
        assertEquals(ActionStatus.COMPLETED, python.status)
        assertNull(python.result, "sortie vide : pas de résultat")
        assertEquals(
            "Résumé ajouté en une seule ligne à la fin de `todo.md`. Aucun autre fichier modifié.",
            (entries[4] as ThreadEntry.AgentText).text,
        )
        assertEquals(5, entries.size)
    }

    @Test
    fun `while Codex waits to run a command, it is in progress without a result`() {
        val cat = replay(Fixtures.CODEX_SESSION, codexId, upTo = 615).entries.last() as ThreadEntry.ToolCall

        assertEquals(ActionStatus.IN_PROGRESS, cat.status)
        assertNull(cat.result)
    }

    @Test
    fun `Codex output pieces are added up, and the final output replaces them`() {
        val call = """{"sessionUpdate":"tool_call","toolCallId":"e","title":"npm test","kind":"execute","status":"in_progress","content":[],"rawInput":{"command":["bash","-lc","npm test"]}}"""
        fun output(text: String, mode: String) =
            """{"sessionUpdate":"tool_call_update","toolCallId":"e","content":[{"type":"content","content":{"type":"text","text":"$text"}}],"_meta":{"regie":{"output":"$mode"}}}"""

        val running = thread(call, output("> test\\n42 pass", "delta"), output("ing\\n", "delta"))
        assertEquals("42 passing", (running.entries.single() as ThreadEntry.ToolCall).result)

        val done = thread(call, output("42 pass", "delta"), output("68 passing\\n2 failing\\n\\n", "final"))
        assertEquals("2 failing", (done.entries.single() as ThreadEntry.ToolCall).result)
        assertEquals("bash -lc npm test", (done.entries.single() as ThreadEntry.ToolCall).command)
    }

    @Test
    fun `a Claude command output loses its code fences`() {
        val thread = thread(
            """{"sessionUpdate":"tool_call","toolCallId":"b","title":"Terminal","kind":"execute","status":"pending","rawInput":{"command":"wc -l todo.md"}}""",
            """{"sessionUpdate":"tool_call_update","toolCallId":"b","status":"completed","content":[{"type":"content","content":{"type":"text","text":"```console\n4 todo.md\n```"}}],"rawOutput":"4 todo.md"}""",
        )

        assertEquals("4 todo.md", (thread.entries.single() as ThreadEntry.ToolCall).result)
    }

    @Test
    fun `a call hidden by the bridge leaves the thread, and the plan is read as steps`() {
        val thread = thread(
            """{"sessionUpdate":"plan","entries":[{"content":"Lire la logique","priority":"high","status":"completed"},{"content":"Lancer   les tests","priority":"high","status":"in_progress"},{"content":"Résumer","priority":"low","status":"pending"}]}""",
            """{"sessionUpdate":"tool_call","toolCallId":"a","title":"rg x","kind":"execute","status":"in_progress","rawInput":{"command":"rg x"}}""",
            """{"sessionUpdate":"tool_call_update","toolCallId":"a","status":"failed","content":[],"_meta":{"regie":{"hidden":"sandbox_unavailable"}}}""",
            """{"sessionUpdate":"agent_thought_chunk","content":{"type":"text","text":"je réfléchis"}}""",
        )

        assertEquals(emptyList(), thread.entries)
        assertEquals(
            listOf(
                PlanStep("Lire la logique", PlanStepStatus.DONE),
                PlanStep("Lancer les tests", PlanStepStatus.IN_PROGRESS),
                PlanStep("Résumer", PlanStepStatus.PENDING),
            ),
            thread.plan,
        )
        assertNull(thread("""{"sessionUpdate":"plan","entries":[]}""").plan)
    }

    @Test
    fun `an interrupted turn and a failed turn each leave a line, a finished turn none`() {
        val thread = thread(
            """{"type":"turn_end","stopReason":"cancelled"}""",
            """{"type":"turn_end","stopReason":"end_turn"}""",
            """{"type":"session_error","message":"L'agent s'est arrêté."}""",
        )

        assertEquals(listOf(ThreadEntry.Interrupted::class, ThreadEntry.TurnFailed::class), thread.entries.map { it::class })
        assertEquals("L'agent s'est arrêté.", (thread.entries[1] as ThreadEntry.TurnFailed).message)
    }

    @Test
    fun `a new file counts all its lines as added, and an edit of several diffs adds them up`() {
        assertEquals(LineChanges(3, 0), LineDiff.changes(null, "a\nb\nc\n"))
        assertEquals(LineChanges(1, 1), LineDiff.changes("a\nb\nc", "a\nB\nc"))
        assertEquals(LineChanges(0, 2), LineDiff.changes("a\nb\nc\n", "a\n"))

        val thread = thread(
            """{"sessionUpdate":"tool_call","toolCallId":"w","title":"Write","kind":"edit","status":"completed","content":[
               {"type":"diff","path":"/home/dev/projects/demo/src/a.ts","oldText":"x\n","newText":"x\ny\n"},
               {"type":"diff","path":"/home/dev/projects/demo/src/b.ts","oldText":null,"newText":"1\n2\n"}]}""",
        )
        val edit = thread.entries.single() as ThreadEntry.ToolCall
        assertEquals("src/a.ts", edit.path)
        assertEquals(LineChanges(3, 0), edit.changes)
    }

    @Test
    fun `a multi file edit keeps each file count in the live panel and names every file in the action`() {
        val thread = thread(
            """{"sessionUpdate":"tool_call","toolCallId":"w","title":"Write","kind":"edit","status":"completed","content":[
               {"type":"diff","path":"/home/dev/projects/demo/src/a.ts","oldText":"x\n","newText":"x\ny\n"},
               {"type":"diff","path":"/home/dev/projects/demo/src/b.ts","oldText":null,"newText":"1\n2\n"}]}""",
        )
        val at = Instant.fromEpochMilliseconds(0)
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.IDLE, true, at, at)
        val live = LiveSession.of(session, thread)

        assertEquals(
            listOf(ModifiedFile("src/a.ts", LineChanges(1, 0)), ModifiedFile("src/b.ts", LineChanges(2, 0))),
            live.modifiedFiles,
        )
        val action = live.lines.single() as ThreadLine.Edit
        assertEquals("src/a.ts, src/b.ts", action.path)
        assertEquals(LineChanges(3, 0), action.changes)
    }

    @Test
    fun `the thread keeps only its last entries`() {
        val builder = ThreadBuilder(cwd = { null }, maxEntries = 3)
        repeat(5) { builder.addUserMessage("m$it") }

        val texts = builder.snapshot(loaded = true).entries.map { (it as ThreadEntry.UserMessage).text }
        assertEquals(listOf("m2", "m3", "m4"), texts)
    }

    @Test
    fun `the bridge's modified files reach the session`() {
        val state = BridgeState()
        state.apply(
            Frames.parseEvent(
                Frames.parseObject(
                    """{"seq":1,"sessionId":"s","type":"session","data":{"sessionId":"s","agent":"codex","project":"demo","status":"idle",
                       "updatedAt":"2026-11-13T21:00:00Z","active":true,"pendingApprovals":[],"modifiedFiles":["todo.md","src/a.ts"]}}""",
                )!!,
            )!!,
        )

        assertEquals(listOf("todo.md", "src/a.ts"), state.sessions().single().modifiedFiles)
    }

    private val sleep = """{"sessionUpdate":"tool_call","toolCallId":"exec-1","title":"sleep 90; echo fini","kind":"execute","status":"in_progress","content":[],"rawInput":{"command":"sleep 90; echo fini"}}"""
    private val read = """{"sessionUpdate":"tool_call","toolCallId":"r","title":"Read notes.md","kind":"read","status":"completed","locations":[{"path":"/home/dev/projects/demo/notes.md"}]}"""

    @Test
    fun `an interrupted turn marks the command Codex never finished as interrupted, not in progress`() {
        val thread = thread(read, sleep, """{"type":"turn_end","stopReason":"cancelled"}""")

        val (done, killed) = thread.entries.take(2).map { it as ThreadEntry.ToolCall }
        assertEquals(ActionStatus.COMPLETED, done.status)
        assertEquals(ActionStatus.INTERRUPTED, killed.status)
        assertTrue(thread.entries.last() is ThreadEntry.Interrupted)
    }

    @Test
    fun `a turn ending otherwise leaves an unfinished action without a status`() {
        val thread = thread(sleep, """{"type":"turn_end","stopReason":"end_turn"}""")

        assertNull((thread.entries.single() as ThreadEntry.ToolCall).status)
    }
}
