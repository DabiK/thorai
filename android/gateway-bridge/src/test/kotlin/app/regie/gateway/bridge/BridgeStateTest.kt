package app.regie.gateway.bridge

import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.LastAction
import app.regie.domain.model.Progress
import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Le décodage du protocole, sur les enregistrements réels du pont (bridge/fixtures/bridge-ws). */
class BridgeStateTest {

    private val claudeId = "00000000-0000-4000-8000-000000000044"
    private val codexId = "00000000-0000-4000-8000-000000000018"

    /** Rejoue les événements d'une session jusqu'au `seq` donné inclus. */
    private fun replay(name: String, upTo: Long = Long.MAX_VALUE): Session {
        val state = BridgeState()
        Fixtures.events(name).filter { it.seq <= upTo }.forEach { state.apply(it) }
        return state.sessions().single()
    }

    private fun event(json: String) = Frames.parseEvent(Frames.parseObject(json)!!)!!

    private fun sessionJson(id: String, status: String, updatedAt: String, pending: String = "") =
        """{"sessionId":"$id","agent":"claude","project":"demo","cwd":"/home/dev/projects/demo","branch":"main",
           "approvalMode":"ask","status":"$status","title":"Titre $id","createdAt":"2026-11-13T21:00:00Z",
           "updatedAt":"$updatedAt","lastError":null,"active":true,"pendingApprovals":[$pending]}"""

    @Test
    fun `approval keeps exact command reason all files and choices until resolved`() {
        val state = BridgeState()
        state.apply(event("""{"seq":1,"type":"approval_request","sessionId":"s","data":{
          "approvalId":"a","sessionId":"s","toolCall":{"kind":"execute",
          "rawInput":{"command":"printf 'a\nb' > x","description":"Créer le résultat","cwd":"/project"},
          "locations":[{"path":"/project/x"},{"path":"/project/y"}]},
          "options":[{"optionId":"allow_once","kind":"allow_once","name":"Yes"},
          {"optionId":"allow_for_session","kind":"allow_always","name":"Session"},
          {"optionId":"cancel","kind":"reject_once","name":"No"}]}}"""))
        val approval = state.pendingApprovals().single()
        assertEquals("printf 'a\nb' > x", approval.subject)
        assertEquals("Créer le résultat", approval.reason)
        assertEquals(listOf("/project/x", "/project/y"), approval.paths)
        assertEquals("/project", approval.workingDirectory)
        assertEquals(listOf("allow_once", "allow_for_session", "cancel"), approval.options.map { it.id })
        state.apply(event("""{"seq":2,"type":"approval_resolved","sessionId":"s","data":{"approvalId":"a"}}"""))
        assertTrue(state.pendingApprovals().isEmpty())
    }

    @Test
    fun `a Claude session waits for the edit of a file, named relative to the project`() {
        val session = replay(Fixtures.CLAUDE_SESSION, upTo = 595)

        assertEquals(SessionStatus.AWAITING_APPROVAL, session.status)
        assertEquals(ApprovalKind.FILE_CHANGE, session.pendingApproval?.kind)
        assertEquals("todo.md", session.pendingApproval?.subject)
        assertEquals("00000000-0000-4000-8000-00000000003c", session.pendingApproval?.id?.value)
        assertEquals(LastAction("Edit todo.md", ActionStatus.PENDING), session.lastAction)
    }

    @Test
    fun `a finished Claude session is waiting for instructions, with its last action done`() {
        val session = replay(Fixtures.CLAUDE_SESSION)

        assertEquals(Agent.CLAUDE, session.agent)
        assertEquals("regie-demo", session.project)
        assertEquals("main", session.branch)
        assertEquals("Résumé de notes.md dans todo.md", session.title)
        assertEquals(SessionStatus.IDLE, session.status)
        assertNull(session.pendingApproval)
        assertEquals(LastAction("Edit todo.md", ActionStatus.COMPLETED), session.lastAction)
        assertEquals(Instant.parse("2026-11-13T21:41:21.534Z"), session.updatedAt)
        assertEquals(Instant.parse("2026-11-13T21:41:21.535Z"), session.lastActivityAt)
    }

    @Test
    fun `no plan from the agent means no progress, never an invented one`() {
        assertNull(replay(Fixtures.CLAUDE_SESSION).progress)
        assertNull(replay(Fixtures.CODEX_SESSION).progress)
    }

    @Test
    fun `a Codex session waits for a command, shown on one line`() {
        val session = replay(Fixtures.CODEX_SESSION, upTo = 658)

        assertEquals(Agent.CODEX, session.agent)
        assertEquals(SessionStatus.AWAITING_APPROVAL, session.status)
        assertEquals(ApprovalKind.COMMAND, session.pendingApproval?.kind)
        assertTrue(session.pendingApproval!!.subject!!.startsWith("python3 -c 'from pathlib import Path;"))
        assertFalse(session.pendingApproval!!.subject!!.contains('\n'))
        assertEquals(ActionStatus.IN_PROGRESS, session.lastAction?.status)
    }

    @Test
    fun `a finished Codex session keeps its last command and its new title`() {
        val session = replay(Fixtures.CODEX_SESSION)

        assertEquals(SessionStatus.IDLE, session.status)
        assertEquals(
            "Lis notes.md et ajoute son résumé en une seule ligne à la fin de todo.md. Ne modifie aucun autre fichier.",
            session.title,
        )
        assertTrue(session.lastAction!!.description.startsWith("python3 -c"))
        assertEquals(ActionStatus.COMPLETED, session.lastAction?.status)
    }

    @Test
    fun `the whole demo ends with the two demo sessions idle and the history stopped`() {
        val state = ReplayAgentGateway.finalState()
        val sessions = state.sessions().associateBy { it.id.value }

        assertEquals(SessionStatus.IDLE, sessions.getValue(claudeId).status)
        assertEquals(SessionStatus.IDLE, sessions.getValue(codexId).status)
        assertEquals(LastAction("Edit todo.md", ActionStatus.COMPLETED), sessions.getValue(claudeId).lastAction)
        assertTrue(sessions.getValue(codexId).lastAction!!.description.startsWith("python3 -c"))
        val history = sessions.values.filter { it.id.value != claudeId && it.id.value != codexId }
        assertEquals(9, history.size)
        assertTrue(history.all { it.status == SessionStatus.STOPPED && !it.active })
        assertEquals(689, state.lastSeq)
        assertEquals("00000000-0000-4000-8000-000000000040", state.journal)
        assertEquals(listOf(claudeId, codexId).sorted(), state.firstSessions(2).sorted())
    }

    @Test
    fun `the demo reads the agent versions, and both are tested`() {
        assertEquals(
            listOf(AgentVersion(Agent.CLAUDE, "2.1.322", isTested = true), AgentVersion(Agent.CODEX, "0.167.2", isTested = true)),
            ReplayAgentGateway.finalState().agents,
        )
    }

    @Test
    fun `while disconnected, the Codex approval was taken from the hello state`() {
        val frames = Fixtures.demoRun()
        // Jusqu'à la réponse au second hello (rattrapage), avant les événements rejoués.
        val secondHello = frames.indexOfFirst { it.fromBridge && it.frame.obj("result")?.long("lastSeq") == 658L }
        val state = ReplayAgentGateway.finalState(frames.take(secondHello + 1))
        val codex = state.sessions().single { it.id.value == codexId }

        assertEquals(SessionStatus.AWAITING_APPROVAL, codex.status)
        assertEquals("00000000-0000-4000-8000-00000000005e", codex.pendingApproval?.id?.value)
        assertEquals(ApprovalKind.COMMAND, codex.pendingApproval?.kind)
        assertEquals(618, state.lastSeq)
    }

    @Test
    fun `an event already seen is ignored`() {
        val state = BridgeState()
        val events = Fixtures.events(Fixtures.CLAUDE_SESSION)
        events.forEach { state.apply(it) }
        val before = state.sessions()

        assertFalse(state.apply(events.first { it.type == "approval_request" }))
        assertEquals(before, state.sessions())
    }

    @Test
    fun `the plan gives the step and the step in progress`() {
        val state = BridgeState()
        state.apply(event("""{"seq":1,"at":"2026-11-13T21:00:01Z","sessionId":"s","type":"session","data":${sessionJson("s", "working", "2026-11-13T21:00:01Z")}}"""))
        state.apply(
            event(
                """{"seq":2,"at":"2026-11-13T21:00:02Z","sessionId":"s","type":"session_update","data":{"sessionUpdate":"plan","entries":[
                {"content":"Lire","priority":"high","status":"completed"},
                {"content":"Écrire","priority":"high","status":"completed"},
                {"content":"Lancer   les tests","priority":"high","status":"in_progress"},
                {"content":"Corriger","priority":"low","status":"pending"},
                {"content":"Résumer","priority":"low","status":"pending"}]}}""",
            ),
        )

        val progress = state.sessions().single().progress
        assertEquals(Progress(done = 2, total = 5, currentStep = "Lancer les tests"), progress)
        assertEquals(3, progress?.step)
    }

    @Test
    fun `a call hidden by the bridge (Codex sandbox) leaves the previous action`() {
        val state = BridgeState()
        state.apply(event("""{"seq":1,"at":"2026-11-13T21:00:01Z","sessionId":"s","type":"session","data":${sessionJson("s", "working", "2026-11-13T21:00:01Z")}}"""))
        state.apply(event("""{"seq":2,"sessionId":"s","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"a","title":"ls","kind":"execute","status":"completed","rawInput":{"command":["bash","-lc","ls"]}}}"""))
        state.apply(event("""{"seq":3,"sessionId":"s","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"b","title":"rg x","kind":"execute","status":"in_progress","rawInput":{"command":"rg x"}}}"""))
        assertEquals(LastAction("rg x", ActionStatus.IN_PROGRESS), state.sessions().single().lastAction)

        state.apply(event("""{"seq":4,"sessionId":"s","type":"session_update","data":{"sessionUpdate":"tool_call_update","toolCallId":"b","status":"failed","content":[],"_meta":{"regie":{"hidden":"sandbox_unavailable"}}}}"""))

        assertEquals(LastAction("bash -lc ls", ActionStatus.COMPLETED), state.sessions().single().lastAction)
    }

    @Test
    fun `a catch-up replaying older session states does not move the session back`() {
        val state = BridgeState()
        val hello = Hello.parse(
            Frames.parseObject(
                """{"journal":"j","lastSeq":10,"resync":false,"sessions":[${sessionJson("s", "idle", "2026-11-13T21:00:09Z")}],"approvals":[]}""",
            )!!,
        )!!
        state.onHello(hello, sentSince = null)
        state.onHello(hello, sentSince = 8)
        state.apply(event("""{"seq":9,"sessionId":"s","type":"session","data":${sessionJson("s", "working", "2026-11-13T21:00:05Z")}}"""))

        assertEquals(SessionStatus.IDLE, state.sessions().single().status)
    }

    @Test
    fun `a resync forgets everything and starts again from the hello state`() {
        val state = BridgeState()
        Fixtures.events(Fixtures.CLAUDE_SESSION).forEach { state.apply(it) }
        val hello = Hello.parse(
            Frames.parseObject(
                """{"journal":"nouveau","lastSeq":3,"resync":true,"sessions":[${sessionJson("t", "working", "2026-11-13T22:00:00Z")}],"approvals":[]}""",
            )!!,
        )!!

        assertTrue(state.onHello(hello, sentSince = 653))
        assertEquals(listOf("t"), state.sessions().map { it.id.value })
        assertEquals(3, state.lastSeq)
        assertEquals("nouveau", state.journal)
    }

    @Test
    fun `the version number is read from the binary`() {
        assertEquals("2.1.322", BridgeState.versionNumber("2.1.322 (Claude Code)"))
        assertEquals("0.167.2", BridgeState.versionNumber("codex-cli 0.167.2"))
        assertNull(BridgeState.versionNumber("codex-cli"))
    }

    @Test
    fun `an untested version and an unknown one are reported as such`() {
        val state = BridgeState()
        state.onVersions(
            Frames.parseObject(
                """{"agents":[{"agent":"claude","binary":"2.2.0 (Claude Code)","isTested":false},{"agent":"codex","binary":null,"isTested":false}]}""",
            )!!,
        )

        assertEquals(
            listOf(AgentVersion(Agent.CLAUDE, "2.2.0", isTested = false), AgentVersion(Agent.CODEX, null, isTested = false)),
            state.agents,
        )
    }

    @Test
    fun `after an interruption the card's last action is interrupted, no longer in progress`() {
        val state = BridgeState()
        state.apply(event("""{"seq":1,"at":"2026-11-13T21:00:01Z","sessionId":"s","type":"session","data":${sessionJson("s", "working", "2026-11-13T21:00:01Z")}}"""))
        state.apply(event("""{"seq":2,"sessionId":"s","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"e","title":"sleep 90","kind":"execute","status":"in_progress","rawInput":{"command":"sleep 90; echo fini"}}}"""))
        state.apply(event("""{"seq":3,"sessionId":"s","type":"turn_end","data":{"stopReason":"cancelled","usage":null}}"""))

        assertEquals(LastAction("sleep 90; echo fini", ActionStatus.INTERRUPTED), state.sessions().single().lastAction)
    }
}
