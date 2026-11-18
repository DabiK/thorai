package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.*
import app.regie.domain.usecase.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DynamicAgentsTest {
    @Test fun `a fourth agent is selectable from the bridge and removable on refresh`() = runTest {
        val fourth = Agent("fourth", "Quatrième agent")
        val gateway = StubGateway()
        gateway.agents.value = listOf(AgentVersion(fourth, "1.0", true))
        val form = ReviewNewSession(gateway, object : DictationCapture {
            override val state = kotlinx.coroutines.flow.MutableStateFlow(CaptureState())
            override fun start(vocabulary: List<String>) {}
            override fun finish() {}
            override fun cancel() {}
        }, backgroundScope) {}
        form.open(); runCurrent()
        form.change(1)
        assertEquals(fourth, form.state.value.agent)
        assertEquals(listOf(fourth), form.state.value.agents)
        gateway.agents.value = emptyList(); runCurrent()
        assertNull(form.state.value.agent)
        assertFalse(form.state.value.canLaunch)
    }

    @Test fun `voice resolves names against advertised agents including unknown future ids`() = runTest {
        val agent = Agent("fourth", "Quatrième agent")
        val interpreter = InterpretVoiceCommand(VoiceCommandModel { _, _ -> error("fixed rule expected") })
        val result = interpreter("lance Quatrième agent sur demo pour lire README", VoiceCommandContext(emptyList(), null, null, listOf("demo"), listOf(agent)))
        assertEquals(agent, assertIs<VoiceCommand.Launch>(result.command).draft.agent)
        assertNull(interpreter("lance Codex sur demo", VoiceCommandContext(emptyList(), null, null, listOf("demo"), listOf(agent))).command)
    }
}

class NativeAgentSelectorTest {
    @Test fun `native agent selector remains editable separately from approval mode`() {
        val option = SessionOption("mode", "Agent opencode", "agent", "build", listOf(OptionValue("build", "Build"), OptionValue("plan", "Plan")))
        assertEquals(listOf(option), editableSessionOptions(listOf(option)))
    }
}

class ConsoleCatalogueTest {
    @Test fun `console creation uses the only future connector rather than assuming Codex exists`() {
        val future = Agent("fourth", "Quatrième agent")
        val create = assertIs<ConsoleLaunch.Create>(ConsoleSession.choose(emptyList(), emptyList(), listOf(future)))
        assertEquals(future, create.draft.agent)
        assertEquals(ConsoleLaunch.Unavailable, ConsoleSession.choose(emptyList(), emptyList(), emptyList()))
    }
}
