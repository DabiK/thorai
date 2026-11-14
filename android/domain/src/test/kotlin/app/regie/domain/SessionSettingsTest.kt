package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.*
import app.regie.domain.usecase.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionSettingsTest {
    private val model = SessionOption("remote-model", "Model", "model", "small", listOf(OptionValue("small", "Small"), OptionValue("large", "Large")))
    private val effort = SessionOption("remote-effort", "Effort", "thought_level", "low", listOf(OptionValue("low", "Low"), OptionValue("high", "High")))

    @Test fun `creation offers full access and agent supplied choices only then sends them on A`() = runTest {
        val gateway = StubGateway().apply { projectNames = listOf("demo"); connection.value = BridgeConnection.Connected; options = listOf(model, effort) }
        val form = ReviewNewSession(gateway, SettingsCapture(), backgroundScope) {}
        form.open(NewSessionDraft(Agent.CODEX, "demo")); runCurrent()
        form.select(NewSessionField.VALIDATION)
        repeat(3) { form.change(1) }
        assertEquals("Tout autoriser", form.state.value.modeLabel)
        form.select(NewSessionField.MODEL); form.change(1); runCurrent()
        form.select(NewSessionField.THOUGHT); form.change(1); runCurrent()
        assertTrue(gateway.creations.isEmpty())
        form.launch(); runCurrent()
        assertEquals("allow-all", gateway.creations.single().approvalMode)
        assertEquals(mapOf("remote-model" to "large", "remote-effort" to "high"), gateway.creations.single().configValues)
    }

    @Test fun `live changes wait for A and acknowledgement with no double apply`() = runTest {
        val gateway = StubGateway()
        val id = SessionId("session")
        gateway.sessions.value = listOf(session("session").copy(approvalMode = "ask", configOptions = listOf(model, effort)))
        val wait = CompletableDeferred<SettingsResult>(); gateway.settingsWait = wait
        val review = ReviewSessionSettings(gateway, backgroundScope)
        review.open(id); runCurrent()
        review.change(1)
        assertEquals("allow-all", review.state.value.value)
        assertTrue(gateway.configurations.isEmpty())
        review.apply(); review.apply(); runCurrent()
        assertEquals(1, gateway.configurations.size)
        assertTrue(review.state.value.busy)
        assertEquals("ask", gateway.sessions.value.single().approvalMode)
        wait.complete(SettingsResult.UNKNOWN); runCurrent()
        assertTrue(review.state.value.notice.contains("incertain", ignoreCase = true))
        assertEquals("ask", review.state.value.session?.approvalMode)
    }

    @Test fun `unexposed choices cannot be invented and old session can be resumed without prompt`() = runTest {
        val gateway = StubGateway()
        val id = SessionId("old")
        gateway.sessions.value = listOf(session("old", active = false))
        val review = ReviewSessionSettings(gateway, backgroundScope)
        review.open(id); runCurrent()
        assertEquals(listOf("approvalMode", "resume"), review.state.value.rows.map { it.id })
        review.move(1); review.apply(); runCurrent()
        assertEquals(listOf(id), gateway.resumed)
        assertTrue(gateway.sent.isEmpty())
    }
}

private class SettingsCapture : DictationCapture {
    override val state = kotlinx.coroutines.flow.MutableStateFlow(CaptureState())
    override fun start(vocabulary: List<String>) = Unit
    override fun finish() = Unit
    override fun cancel() = Unit
}
