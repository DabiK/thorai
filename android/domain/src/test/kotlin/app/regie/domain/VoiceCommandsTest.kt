package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.*
import app.regie.domain.usecase.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceCommandsTest {
    private val one = session("one").copy(title = "Atelier", project = "atelier", agent = Agent.CODEX)
    private val two = session("two").copy(title = "Cuisine", project = "recettes")
    private val approval = Approval(ApprovalId("a"), one.id, ApprovalKind.COMMAND, "npm test", options = listOf(
        ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Oui"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "Non")))
    private val context = VoiceCommandContext(listOf(one, two), one.id, approval, listOf("atelier", "recettes"))
    private val noModel = VoiceCommandModel { _, _ -> error("The fixed rules must recognize the corpus") }

    @Test fun `twenty prepared commands resolve exact identities without a model`() = runTest {
        val cases = listOf(
            "Approuve" to VoiceCommand.Reply(approval, ApprovalDecision.ONCE),
            "Autorise" to VoiceCommand.Reply(approval, ApprovalDecision.ONCE),
            "Valide cette demande" to VoiceCommand.Reply(approval, ApprovalDecision.ONCE),
            "Refuse" to VoiceCommand.Reply(approval, ApprovalDecision.REJECT),
            "Refuse cette validation" to VoiceCommand.Reply(approval, ApprovalDecision.REJECT),
            "Mets en pause" to VoiceCommand.Pause(listOf(one.id)),
            "Pause" to VoiceCommand.Pause(listOf(one.id)),
            "Interromps cette session" to VoiceCommand.Pause(listOf(one.id)),
            "Mets tout en pause" to VoiceCommand.Pause(listOf(one.id, two.id)),
            "Mets toutes les sessions en pause" to VoiceCommand.Pause(listOf(one.id, two.id)),
            // « Où en est … » opens the conversation, like L1/R1 (ticket 25): no written report.
            "Fais le point" to VoiceCommand.Navigate(one.id),
            "Où en est Codex ?" to VoiceCommand.Navigate(one.id),
            "Fais le point sur Cuisine" to VoiceCommand.Navigate(two.id),
            "Fais le point sur toutes les sessions" to VoiceCommand.ShowOverview,
            "Où en sont toutes les sessions ?" to VoiceCommand.ShowOverview,
            "Passe à Atelier" to VoiceCommand.Navigate(one.id),
            "Ouvre la session Cuisine" to VoiceCommand.Navigate(two.id),
            "Va sur atelier" to VoiceCommand.Navigate(one.id),
            "Lance Codex sur atelier" to VoiceCommand.Launch(NewSessionDraft(Agent.CODEX, "atelier")),
            "Lance Claude sur recettes pour corriger les tests" to VoiceCommand.Launch(NewSessionDraft(Agent.CLAUDE, "recettes", instruction = "corriger les tests")),
        )
        val interpreter = InterpretVoiceCommand(noModel)
        cases.forEach { (phrase, expected) -> assertEquals(expected, interpreter(phrase, context).command, phrase) }
    }

    @Test fun `common ASR homophones keep the same bounded pause intent`() = runTest {
        val interpreter = InterpretVoiceCommand(noModel)
        listOf("Mais en pause.", "Met en pause", "Interrompt cette session.").forEach {
            assertEquals(VoiceCommand.Pause(listOf(one.id)), interpreter(it, context).command, it)
        }
    }

    @Test fun `conditional request is never reduced to an unconditional approval`() = runTest {
        val interpreter = InterpretVoiceCommand(VoiceCommandModel { _, _ -> VoiceIntent("approve") })
        assertNull(interpreter("approuve si les tests passent", context).command)
    }

    @Test fun `ambiguous phrase falls back once but never executes`() = runTest {
        var calls = 0
        val interpreter = InterpretVoiceCommand(VoiceCommandModel { text, ctx ->
            calls++; assertEquals("montre-moi comment avance la cuisine", text); assertEquals(context, ctx)
            VoiceIntent("summary", "Cuisine")
        })
        assertEquals(VoiceCommand.Navigate(two.id), interpreter("montre-moi comment avance la cuisine", context).command)
        assertEquals(1, calls)
    }

    @Test fun `untrusted model cannot invent targets permissions or launch fields`() = runTest {
        listOf(VoiceIntent("navigate", "absent"), VoiceIntent("approve", "Cuisine"), VoiceIntent("shell", "rm"),
            VoiceIntent("launch", agent = "other", project = "atelier"), VoiceIntent("launch", agent = "codex", project = "../secret"))
            .forEach { intent -> assertNull(InterpretVoiceCommand(VoiceCommandModel { _, _ -> intent })("phrase ambiguë", context).command) }
    }

    @Test fun `duplicates missing context negations and multiple actions never guess`() = runTest {
        val interpreter = InterpretVoiceCommand(VoiceCommandModel { _, _ -> null })
        assertNull(interpreter("Passe à atelier", context.copy(sessions = listOf(one, two.copy(project = "atelier")))).command)
        assertNull(interpreter("Approuve", context.copy(approval = null)).command)
        assertNull(interpreter("Pause", context.copy(current = null)).command)
        val malicious = InterpretVoiceCommand(VoiceCommandModel { _, _ -> VoiceIntent("approve") })
        listOf("N'approuve pas", "approuve puis refuse", "ne refuse pas", "approuve et mets tout en pause").forEach {
            assertNull(malicious(it, context).command, it)
        }
    }

    @Test fun `R2 final waits for release and A executes only once`() = runTest {
        val capture = CommandCapture()
        val executed = mutableListOf<VoiceCommand>()
        val review = ReviewVoiceCommand(capture, InterpretVoiceCommand(noModel), { executed += it; "Fait" }, backgroundScope)
        review.start(context)
        capture.events.value = CaptureState(text = "Approuve", complete = true)
        runCurrent(); review.confirm(); runCurrent()
        assertTrue(executed.isEmpty())
        review.finish(); runCurrent()
        assertEquals(VoiceCommand.Reply(approval, ApprovalDecision.ONCE), review.state.value.command)
        assertTrue(executed.isEmpty())
        review.confirm(); review.confirm(); runCurrent()
        assertEquals(listOf<VoiceCommand>(VoiceCommand.Reply(approval, ApprovalDecision.ONCE)), executed)
    }

    @Test fun `where is the console session opens it on R2 release without review or A`() = runTest {
        val console = two.copy(project = "console")
        val gateway = StubGateway().apply { sessions.value = listOf(one, console) }
        val opened = mutableListOf<SessionId>()
        val capture = CommandCapture()
        val executor = ExecuteVoiceCommand(gateway, AnswerApproval(gateway), { opened += it })
        val review = ReviewVoiceCommand(capture, InterpretVoiceCommand(noModel), executor::invoke, backgroundScope)
        review.start(context.copy(sessions = gateway.sessions.value))
        capture.events.value = CaptureState(text = "Où en est la session console ?", complete = true)
        runCurrent()
        review.confirm(); runCurrent()
        assertTrue(opened.isEmpty(), "even a final transcript must wait for R2 release")
        review.finish(); runCurrent()
        assertEquals(listOf(console.id), opened)
        assertFalse(review.state.value.visible, "navigation leaves no review panel over the session")
        review.confirm(); review.finish(); runCurrent()
        assertEquals(listOf(console.id), opened, "no duplicate navigation")
        assertTrue(gateway.sent.isEmpty())
        assertTrue(gateway.creations.isEmpty())
        assertTrue(gateway.answers.isEmpty())
        assertTrue(gateway.interrupted.isEmpty())
    }

    @Test fun `navigation to the active session or overview needs no A but mutations still do`() = runTest {
        val cases = listOf(
            "Où en est cette session ?" to VoiceCommand.Navigate(one.id),
            "Où en est ?" to VoiceCommand.Navigate(one.id),
            "Où en sont toutes les sessions ?" to VoiceCommand.ShowOverview,
            "Approuve" to VoiceCommand.Reply(approval, ApprovalDecision.ONCE),
            "Refuse" to VoiceCommand.Reply(approval, ApprovalDecision.REJECT),
            "Mets tout en pause" to VoiceCommand.Pause(listOf(one.id, two.id)),
            "Lance Codex sur atelier" to VoiceCommand.Launch(NewSessionDraft(Agent.CODEX, "atelier")),
        )
        cases.forEachIndexed { index, (phrase, expected) ->
            val capture = CommandCapture()
            val executed = mutableListOf<VoiceCommand>()
            val review = ReviewVoiceCommand(capture, InterpretVoiceCommand(noModel), { executed += it; "Fait" }, backgroundScope)
            review.start(context)
            capture.events.value = CaptureState(text = phrase, complete = true)
            review.finish(); runCurrent()
            if (index < 3) {
                assertEquals(listOf(expected), executed, phrase)
                assertFalse(review.state.value.visible, phrase)
            } else {
                assertTrue(executed.isEmpty(), phrase)
                assertTrue(review.state.value.canConfirm, phrase)
                review.confirm(); review.confirm(); runCurrent()
                assertEquals(listOf(expected), executed, phrase)
            }
            review.discard()
        }
    }

    @Test fun `unknown and duplicate session names never navigate without confirmation`() = runTest {
        listOf(context, context.copy(sessions = listOf(one, two.copy(project = one.project)))).forEachIndexed { index, snapshot ->
            val capture = CommandCapture()
            val review = ReviewVoiceCommand(capture, InterpretVoiceCommand(VoiceCommandModel { _, _ -> null }),
                { error("an unresolved target must never execute") }, backgroundScope)
            review.start(snapshot)
            capture.events.value = CaptureState(text = if (index == 0) "Où en est absente ?" else "Où en est atelier ?", complete = true)
            review.finish(); runCurrent()
            assertEquals(CommandPhase.UNKNOWN, review.state.value.phase)
            assertFalse(review.state.value.canConfirm)
        }
    }

    @Test fun `discard cancels late interpretation and never sends a partial on capture error`() = runTest {
        val capture = CommandCapture()
        val answer = CompletableDeferred<VoiceIntent?>()
        val review = ReviewVoiceCommand(capture, InterpretVoiceCommand(VoiceCommandModel { _, _ -> answer.await() }), { error("must not execute") }, backgroundScope)
        review.start(context)
        capture.events.value = CaptureState(text = "phrase ambiguë", complete = true)
        review.finish(); runCurrent(); review.discard()
        answer.complete(VoiceIntent("approve")); runCurrent()
        assertEquals(CommandPhase.CLOSED, review.state.value.phase)
        review.start(context)
        capture.events.value = CaptureState(text = "Approuve", error = "Micro coupé")
        review.finish(); runCurrent(); review.confirm()
        assertNull(review.state.value.command)
        assertTrue(review.state.value.notice.contains("Micro coupé"))
    }

    @Test fun `confirmed commands use the exact approval and never send the spoken text as a prompt`() = runTest {
        val gateway = StubGateway().apply { sessions.value = listOf(one, two); approvals.value = listOf(approval) }
        val opened = mutableListOf<SessionId>()
        val executor = ExecuteVoiceCommand(gateway, AnswerApproval(gateway), { opened += it })
        assertEquals("Validation autorisée une fois.", executor(VoiceCommand.Reply(approval, ApprovalDecision.ONCE)))
        executor(VoiceCommand.Reply(approval, ApprovalDecision.ONCE))
        assertEquals(listOf(approval.id to "yes"), gateway.answers)
        executor(VoiceCommand.Navigate(two.id))
        assertEquals(listOf(two.id), opened)
        assertTrue(gateway.sent.isEmpty())
        gateway.approvals.value = listOf(approval.copy(id = ApprovalId("b")))
        executor(VoiceCommand.Reply(gateway.approvals.value.single(), ApprovalDecision.REJECT))
        assertEquals(ApprovalId("b") to "no", gateway.answers.last())
    }

    @Test fun `a vanished session is reported without proposing an authorization`() = runTest {
        val gateway = StubGateway()
        val opened = mutableListOf<SessionId>()
        val executor = ExecuteVoiceCommand(gateway, AnswerApproval(gateway), { opened += it })
        assertEquals("Session indisponible.", executor(VoiceCommand.Navigate(one.id)))
        assertTrue(opened.isEmpty())
        assertTrue(gateway.answers.isEmpty())
    }

    @Test fun `model timeout exception and no tool call cannot leave a confirmable order`() = runTest {
        val models = listOf(
            VoiceCommandModel { _, _ -> kotlinx.coroutines.delay(20_000); VoiceIntent("approve") },
            VoiceCommandModel { _, _ -> throw IllegalStateException("network failure") },
            VoiceCommandModel { _, _ -> null },
        )
        models.forEach { assertNull(InterpretVoiceCommand(it)("une phrase ambiguë", context).command) }
    }

    @Test fun `changed same approval id invalidates the confirmation`() = runTest {
        val gateway = StubGateway().apply { approvals.value = listOf(approval.copy(subject = "different command")) }
        ExecuteVoiceCommand(gateway, AnswerApproval(gateway), {})(VoiceCommand.Reply(approval, ApprovalDecision.ONCE))
        assertTrue(gateway.answers.isEmpty())
    }

    @Test fun `launch rechecks project and opens only an acknowledged session without retrying`() = runTest {
        val gateway = StubGateway()
        val opened = mutableListOf<SessionId>()
        val executor = ExecuteVoiceCommand(gateway, AnswerApproval(gateway), { opened += it })
        val launch = VoiceCommand.Launch(NewSessionDraft(Agent.CODEX, "atelier", instruction = "Prépare un plan"))
        executor(launch)
        assertTrue(gateway.creations.isEmpty())
        gateway.projectNames = listOf("atelier")
        gateway.creation = SessionCreation(uncertain = true)
        assertTrue(executor(launch).contains("incertaine"))
        assertEquals(listOf(launch.draft), gateway.creations)
        assertTrue(opened.isEmpty())
        gateway.creation = SessionCreation(one.id)
        executor(launch)
        assertEquals(listOf(one.id), opened)
        assertEquals("plan", gateway.creations.last().approvalMode)
    }

    @Test fun `where is a session opens its conversation and never builds a written report`() = runTest {
        val interpreter = InterpretVoiceCommand(noModel)
        // No session named: the active one.
        assertEquals(VoiceCommand.Navigate(one.id), interpreter("Où en est cette session ?", context).command)
        assertEquals("Ouvrir\u00A0: Atelier · CODEX", interpreter("Où en est Atelier ?", context).description)
        assertNull(interpreter("Fais le point", context.copy(current = null)).command)
        val gateway = StubGateway().apply { sessions.value = listOf(one, two) }
        val opened = mutableListOf<SessionId>()
        var overview = 0
        val executor = ExecuteVoiceCommand(gateway, AnswerApproval(gateway), { opened += it }, { overview++ })
        assertEquals("Session ouverte.", executor(interpreter("Où en est Cuisine ?", context).command!!))
        assertEquals("Vue d’ensemble ouverte.", executor(interpreter("Où en sont toutes les sessions ?", context).command!!))
        assertEquals(listOf(two.id), opened)
        assertEquals(1, overview)
        assertTrue(gateway.threads.isEmpty(), "no thread is read to write a report")
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun `confirmation rechecks original approval and pause snapshot`() = runTest {
        val gateway = StubGateway().apply { sessions.value = listOf(one, two); approvals.value = listOf(approval) }
        val executor = ExecuteVoiceCommand(gateway, AnswerApproval(gateway), {})
        val command = VoiceCommand.Reply(approval, ApprovalDecision.ONCE)
        gateway.approvals.value = listOf(approval.copy(id = ApprovalId("new")))
        executor(command)
        assertTrue(gateway.answers.isEmpty())
        executor(VoiceCommand.Pause(listOf(one.id)))
        assertEquals(listOf(one.id), gateway.interrupted)
        assertTrue(gateway.sent.isEmpty())
    }
}

private class CommandCapture : DictationCapture {
    val events = MutableStateFlow(CaptureState())
    override val state = events
    override fun start(vocabulary: List<String>) { events.value = CaptureState(recording = true) }
    override fun finish() = Unit
    override fun cancel() = Unit
}
