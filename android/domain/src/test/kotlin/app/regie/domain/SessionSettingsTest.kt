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

    @Test fun `blocked resume offers refreshed models while preserving the removed selection until A`() = runTest {
        val gateway = StubGateway()
        val id = SessionId("old")
        gateway.sessions.value = listOf(session("old", active = false).copy(configOptions = listOf(
            model.copy(currentValue = "removed", options = listOf(OptionValue("replacement", "Replacement"))),
            effort,
        )))
        val review = ReviewSessionSettings(gateway, backgroundScope)
        review.open(id); runCurrent()
        review.select(review.state.value.rows.indexOfFirst { it.id == "remote-model" })
        assertEquals("removed", review.state.value.value)
        review.change(1)
        assertEquals("replacement", review.state.value.value)
        assertTrue(gateway.configurations.isEmpty())
        review.apply(); runCurrent()
        assertEquals(listOf(id to SessionSetting(configValues = mapOf("remote-model" to "replacement"))), gateway.configurations)
        assertTrue(gateway.sent.isEmpty())
    }
    @Test fun `inactive session retains edits across rows and confirms both replacements together`() = runTest {
        val gateway = StubGateway()
        val id = SessionId("old")
        gateway.sessions.value = listOf(session("old", active = false).copy(approvalMode = "allow-all", configOptions = listOf(
            model.copy(currentValue = "old", options = listOf(OptionValue("new", "New"))),
            effort.copy(currentValue = "high", options = listOf(OptionValue("low", "Low"))),
        )))
        val review = ReviewSessionSettings(gateway, backgroundScope)
        review.open(id); runCurrent()
        review.select(1); review.change(1)
        assertFalse(review.state.value.canApply) // Effort still needs an explicit replacement.
        review.select(2); review.change(1)
        review.select(1)
        assertEquals("new", review.state.value.value)
        assertTrue(gateway.configurations.isEmpty())
        assertEquals(listOf("old", "high"), review.state.value.session!!.configOptions.map { it.currentValue })
        review.select(3) // Resume also confirms all prepared replacements.
        gateway.settingsWait = CompletableDeferred()
        review.apply(); review.apply(); runCurrent()
        assertTrue(review.state.value.busy)
        gateway.settingsWait!!.complete(SettingsResult.REFUSED); runCurrent()
        assertTrue(review.state.value.canApply)
        review.select(1); assertEquals("new", review.state.value.value)
        review.select(2); assertEquals("low", review.state.value.value)
        gateway.sessions.value = gateway.sessions.value.map { it.copy(lastError = "Temporary refusal") }
        runCurrent()
        assertEquals(mapOf("remote-model" to "new", "remote-effort" to "low"), review.state.value.replacements)
        review.close(); review.open(id); runCurrent()
        assertTrue(review.state.value.replacements.isEmpty()) // B discards only the unconfirmed edits.
        assertEquals(listOf(id to SessionSetting(configValues = mapOf("remote-model" to "new", "remote-effort" to "low"))), gateway.configurations)
        assertTrue(gateway.resumed.isEmpty())
        assertTrue(gateway.sent.isEmpty())
    }

    @Test fun `an option without a saved selection does not block resume replacements`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("old", active = false).copy(configOptions = listOf(
            model.copy(currentValue = "old"),
            SessionOption("optional", "Optional", null, null, emptyList()),
        )))
        val review = ReviewSessionSettings(gateway, backgroundScope)
        review.open(SessionId("old")); runCurrent()
        review.select(1); review.change(1)
        assertTrue(review.state.value.canApply)
        review.apply(); runCurrent()
        assertEquals(SessionSetting(configValues = mapOf("remote-model" to "small")), gateway.configurations.single().second)
    }

}

private class SettingsCapture : DictationCapture {
    override val state = kotlinx.coroutines.flow.MutableStateFlow(CaptureState())
    override fun start(vocabulary: List<String>) = Unit
    override fun finish() = Unit
    override fun cancel() = Unit
}
