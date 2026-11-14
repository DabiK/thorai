package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.*
import app.regie.domain.usecase.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NewSessionTest {
    @Test fun `three launch phrases preserve branch base mode and instruction without a model`() = runTest {
        val interpret = InterpretVoiceCommand(VoiceCommandModel { _, _ -> null })
        val context = VoiceCommandContext(emptyList(), null, null, listOf("demo"))
        val cases = listOf(
            "Lance Codex sur demo pour lire les notes" to NewSessionDraft(Agent.CODEX, "demo", instruction = "lire les notes"),
            "Lance Codex sur demo, branche main, tout valider, pour lire les notes" to NewSessionDraft(Agent.CODEX, "demo", branch = "main", instruction = "lire les notes", approvalMode = "ask"),
            "Lance Codex sur demo, nouvelle branche essai depuis main, modifs auto, pour ajouter un test" to NewSessionDraft(Agent.CODEX, "demo", branch = "essai", instruction = "ajouter un test", approvalMode = "auto-edit", newBranch = true, baseBranch = "main"),
        )
        for ((phrase, draft) in cases) assertEquals(VoiceCommand.Launch(draft), interpret(phrase, context).command, phrase)
    }
    @Test fun `reference mockup phrase preserves unnamed new branch and negative instruction`() = runTest {
        val interpret = InterpretVoiceCommand(VoiceCommandModel { _, _ -> null })
        val context = VoiceCommandContext(emptyList(), null, null, listOf("atelier-meubles"))
        val command = interpret("Lance Claude sur atelier-meubles, nouvelle branche depuis main, et demande-lui de ne pas modifier les tests", context)
        assertEquals(VoiceCommand.Launch(NewSessionDraft(Agent.CLAUDE, "atelier-meubles", instruction = "ne pas modifier les tests", newBranch = true, baseBranch = "main")), command.command)
    }

    @Test fun `local transcription punctuation and homophones still describe the chosen validation`() = runTest {
        val interpret = InterpretVoiceCommand(VoiceCommandModel { _, _ -> null })
        val result = interpret("Lance Codex sur test Branche Lecture Tout validé. Pour afficher la branche courante.", VoiceCommandContext(emptyList(), null, null, listOf("test")))
        assertEquals(VoiceCommand.Launch(NewSessionDraft(Agent.CODEX, "test", "Lecture", "afficher la branche courante", "ask")), result.command)
    }

    @Test fun `unique case variants resolve existing references but never pick an ambiguous branch`() = runTest {
        val gateway = FormGateway()
        val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo", "MAIN")); runCurrent()
        assertEquals("main", form.state.value.branch)
        assertTrue(form.state.value.canLaunch)
        form.open(NewSessionDraft(Agent.CODEX, "demo", "essai", newBranch = true, baseBranch = "Main")); runCurrent()
        assertEquals("main", form.state.value.base)
        gateway.catalog = listOf(ProjectChoice("demo", "main", listOf("main", "MAIN")))
        form.open(NewSessionDraft(Agent.CODEX, "demo", "Main")); runCurrent()
        assertFalse(form.state.value.canLaunch)
    }

    @Test fun `R2 prepares the editable form after release without executing or needing an extra A`() = runTest {
        val capture = FormCapture(); val drafts = mutableListOf<Pair<NewSessionDraft, String>>()
        val voice = ReviewVoiceCommand(capture, InterpretVoiceCommand(VoiceCommandModel { _, _ -> null }),
            { error("launch must go to form") }, backgroundScope, onLaunch = { draft, text -> drafts += draft to text })
        voice.start(VoiceCommandContext(emptyList(), null, null, listOf("demo")))
        capture.events.value = CaptureState(text = "Lance Codex sur demo pour lire", complete = true); runCurrent()
        assertTrue(drafts.isEmpty())
        voice.finish(); runCurrent()
        assertEquals(listOf(NewSessionDraft(Agent.CODEX, "demo", instruction = "lire") to "Lance Codex sur demo pour lire"), drafts)
        assertFalse(voice.state.value.visible)
    }

    @Test fun `first R2 captures immediately while its launch catalog loads before interpretation`() = runTest {
        val capture = FormCapture(); val catalog = CompletableDeferred<List<String>>(); val drafts = mutableListOf<NewSessionDraft>()
        val voice = ReviewVoiceCommand(capture, InterpretVoiceCommand(VoiceCommandModel { _, _ -> null }),
            { error("no execution") }, backgroundScope, onLaunch = { draft, _ -> drafts += draft }, loadProjects = { catalog.await() })
        voice.start(VoiceCommandContext(emptyList(), null, null, emptyList()))
        assertTrue(capture.state.value.recording)
        capture.events.value = CaptureState(text = "Lance Codex sur demo pour lire", complete = true)
        voice.finish(); runCurrent()
        assertTrue(drafts.isEmpty()); assertFalse(voice.state.value.canConfirm)
        catalog.complete(listOf("demo")); runCurrent()
        assertEquals(listOf(NewSessionDraft(Agent.CODEX, "demo", instruction = "lire")), drafts)
    }

    @Test fun `empty form requires agent project and only A creates once then opens live session`() = runTest {
        val gateway = FormGateway()
        val opened = mutableListOf<SessionId>()
        val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) { opened += it }
        form.open(); runCurrent()
        assertNull(form.state.value.agent); assertNull(form.state.value.project)
        form.launch(); runCurrent(); assertTrue(gateway.creations.isEmpty())
        form.select(NewSessionField.AGENT); form.change(1)
        form.select(NewSessionField.PROJECT); form.change(1)
        assertTrue(form.state.value.canLaunch)
        assertTrue(gateway.creations.isEmpty())
        form.launch(); form.launch(); runCurrent()
        assertEquals(listOf(NewSessionDraft(Agent.CLAUDE, "demo")), gateway.creations)
        assertEquals(listOf(SessionId("created")), opened)
        assertFalse(form.state.value.visible)
    }

    @Test fun `directional corrections cover every choice and changing project clears its references`() = runTest {
        val gateway = FormGateway(); gateway.catalog += ProjectChoice("other", "trunk", listOf("trunk"))
        val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) {}
        form.open(); runCurrent()
        form.change(1); form.change(1); assertEquals(Agent.CODEX, form.state.value.agent)
        form.move(1); form.change(1); assertEquals("demo", form.state.value.project)
        form.move(1); form.change(1); assertEquals("main", form.state.value.branch)
        form.change(1); assertEquals("develop", form.state.value.branch)
        form.change(1); form.editBranch("feature/test"); assertEquals(BranchChoice.NEW, form.state.value.branchChoice)
        assertFalse(form.state.value.canLaunch)
        form.move(1); assertEquals(NewSessionField.BASE, form.state.value.field)
        form.change(1); form.change(1); assertEquals("develop", form.state.value.base)
        form.move(1); form.change(1); assertEquals("auto-edit", form.state.value.approvalMode)
        form.change(1); assertEquals("ask", form.state.value.approvalMode)
        form.move(1); form.editInstruction("Consigne conservée")
        form.select(NewSessionField.PROJECT); form.change(1)
        assertEquals("other", form.state.value.project)
        assertEquals(BranchChoice.CURRENT, form.state.value.branchChoice)
        assertEquals("", form.state.value.branch); assertEquals("", form.state.value.base)
        assertEquals("Consigne conservée", form.state.value.instruction)
        assertTrue(gateway.creations.isEmpty())
    }

    @Test fun `branch choices and base are rechecked and transmitted as agent instructions`() = runTest {
        val gateway = FormGateway()
        val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo", "essai", "Ajoute un test", "auto-edit", true, "main")); runCurrent()
        assertTrue(form.state.value.canLaunch)
        form.launch(); runCurrent()
        val request = gateway.creations.single()
        assertNull(request.branch, "Do not activate the legacy bridge prefix that always creates from main")
        assertEquals("auto-edit", request.approvalMode)
        assertEquals("Crée une nouvelle branche essai depuis main, puis place-toi dessus avant d’exécuter la consigne. Si le nom existe déjà, demande confirmation.\n\nAjoute un test", request.instruction)
        form.open(NewSessionDraft(Agent.CODEX, "demo", "main")); runCurrent()
        gateway.catalog = emptyList()
        form.launch(); runCurrent()
        assertEquals(1, gateway.creations.size)
        assertTrue(form.state.value.notice.contains("indisponible"))
    }

    @Test fun `non main base and existing branch have no contradictory legacy branch prefix`() = runTest {
        val gateway = FormGateway(); val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo", "essai", newBranch = true, baseBranch = "develop")); runCurrent()
        form.launch(); runCurrent()
        assertEquals(NewSessionDraft(Agent.CODEX, "demo", instruction = "Crée une nouvelle branche essai depuis develop, puis place-toi dessus avant d’exécuter la consigne. Si le nom existe déjà, demande confirmation."), gateway.creations.single())
        form.open(NewSessionDraft(Agent.CODEX, "demo", "main")); runCurrent(); form.launch(); runCurrent()
        assertEquals(NewSessionDraft(Agent.CODEX, "demo", instruction = "Place-toi sur la branche existante main avant d’exécuter la consigne. Ne la crée pas si elle est absente ; explique le problème."), gateway.creations.last())
    }

    @Test fun `unnamed new branch stays explicit and branch collisions or a missing base block launch`() = runTest {
        val gateway = FormGateway(); val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo", newBranch = true, baseBranch = "main")); runCurrent()
        assertEquals("Nouvelle · nom choisi par l’agent", form.state.value.branchLabel)
        form.launch(); runCurrent()
        assertEquals("Crée une nouvelle branche au nom de ton choix depuis main, puis place-toi dessus avant d’exécuter la consigne. Si le nom existe déjà, demande confirmation.", gateway.creations.single().instruction)
        for (name in listOf("main", "bad..ref", "a.lock", "a/", "-b")) {
            form.open(NewSessionDraft(Agent.CODEX, "demo", name, newBranch = true, baseBranch = "main")); runCurrent()
            form.launch(); runCurrent()
            assertEquals(1, gateway.creations.size, name)
        }
        form.open(NewSessionDraft(Agent.CODEX, "demo", "new", newBranch = true, baseBranch = "develop")); runCurrent()
        gateway.catalog = listOf(ProjectChoice("demo", "main", listOf("main")))
        form.launch(); runCurrent()
        assertEquals(1, gateway.creations.size)
        assertFalse(form.state.value.canLaunch)
    }

    @Test fun `capture error and empty final keep the previous instruction`() = runTest {
        val capture = FormCapture(); val form = ReviewNewSession(FormGateway(), capture, backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo", instruction = "Originale")); runCurrent()
        form.record(); capture.events.value = CaptureState(text = "Partiel", error = "Micro indisponible"); runCurrent(); form.finish(); runCurrent()
        assertEquals("Originale", form.state.value.instruction)
        assertEquals("Micro indisponible", form.state.value.notice)
        form.record(); form.finish(); capture.events.value = CaptureState(complete = true); runCurrent()
        assertEquals("Originale", form.state.value.instruction)
        assertTrue(form.state.value.canLaunch)
    }

    @Test fun `redictation waits for release preserves form and B cancels without creating`() = runTest {
        val gateway = FormGateway(); val capture = FormCapture()
        val form = ReviewNewSession(gateway, capture, backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo", instruction = "Ancienne")); runCurrent()
        form.record()
        capture.events.value = CaptureState(text = "Nouvelle consigne", complete = true); runCurrent()
        form.launch(); runCurrent(); assertTrue(gateway.creations.isEmpty())
        assertEquals("Ancienne", form.state.value.instruction)
        form.finish(); runCurrent()
        assertEquals("Nouvelle consigne", form.state.value.instruction)
        assertEquals(Agent.CODEX, form.state.value.agent)
        form.record(); form.back()
        capture.events.value = CaptureState(text = "Tardive", complete = true); runCurrent()
        assertEquals("Nouvelle consigne", form.state.value.instruction)
        assertTrue(form.state.value.visible)
        form.back(); assertFalse(form.state.value.visible)
        assertTrue(gateway.creations.isEmpty())
    }

    @Test fun `in flight creation freezes every field ignores cancel and opens only its receipt`() = runTest {
        val gateway = FormGateway(); val receipt = CompletableDeferred<SessionCreation>(); gateway.pending = receipt
        val opened = mutableListOf<SessionId>(); val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) { opened += it }
        form.open(NewSessionDraft(Agent.CODEX, "demo", instruction = "Originale")); runCurrent()
        form.launch(); runCurrent()
        form.change(1); form.editInstruction("Autre"); form.editBranch("Autre"); form.back()
        form.open(NewSessionDraft(Agent.CLAUDE, "absent")); form.launch(); form.record(); runCurrent()
        assertTrue(form.state.value.sending)
        assertEquals("Originale", form.state.value.instruction)
        assertEquals(Agent.CODEX, form.state.value.agent)
        assertEquals(1, gateway.creations.size); assertTrue(opened.isEmpty())
        receipt.complete(SessionCreation(SessionId("receipt"))); runCurrent()
        assertEquals(listOf(SessionId("receipt")), opened)
        assertFalse(form.state.value.visible)
    }

    @Test fun `unknown creation cannot be sent again or edited and definite rejection can retry`() = runTest {
        val gateway = FormGateway(); val form = ReviewNewSession(gateway, FormCapture(), backgroundScope) {}
        gateway.result = SessionCreation(uncertain = true)
        form.open(NewSessionDraft(Agent.CODEX, "demo")); runCurrent()
        form.launch(); runCurrent(); form.launch(); form.change(1); runCurrent()
        assertEquals(1, gateway.creations.size)
        assertTrue(form.state.value.uncertain)
        form.back(); form.open(NewSessionDraft(Agent.CODEX, "demo")); runCurrent()
        gateway.result = SessionCreation()
        form.launch(); runCurrent()
        assertFalse(form.state.value.uncertain)
        assertTrue(form.state.value.canLaunch)
    }

}

private class FormCapture : DictationCapture {
    val events = MutableStateFlow(CaptureState())
    override val state = events
    override fun start(vocabulary: List<String>) { events.value = CaptureState(recording = true) }
    override fun finish() = Unit
    override fun cancel() = Unit
}
private class FormGateway : AgentGateway by StubGateway() {
    var catalog = listOf(ProjectChoice("demo", "main", listOf("main", "develop")))
    val creations = mutableListOf<NewSessionDraft>()
    var result = SessionCreation(SessionId("created"))
    var pending: CompletableDeferred<SessionCreation>? = null
    override suspend fun projectCatalog() = catalog
    override suspend fun createSession(draft: NewSessionDraft): SessionCreation { creations += draft; return pending?.await() ?: result }
}
