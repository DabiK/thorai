package app.regie.gateway.bridge

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.usecase.ReviewDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ResyncedExplanationTest {
    @Test fun `explanation survives rebuilt local ids and excludes identical historical requests`() = runTest {
        val state = BridgeState()
        val meta = Frames.parseObject("""{"sessionId":"s","agent":"codex","project":"demo","status":"working","active":true}""")!!
        val hello = Hello("journal", 100, true, listOf(meta), emptyList())
        state.onHello(hello, null)
        val edit = BridgeEvent(10, null, "s", "session_update", Frames.parseObject("""{
            "sessionUpdate":"tool_call","toolCallId":"edit","kind":"edit","status":"completed",
            "content":[{"type":"diff","path":"a.kt","oldText":null,"newText":"val x = 1"}]}""")!!)
        val history = mutableListOf(edit)
        state.onHistory("s", history)
        // A second history load advances HEAD's local counter: the edit now has id=1.
        state.onHistory("s", history)
        val content = MutableStateFlow(state.thread("s", true)!!)
        val gateway = object : AgentGateway by ReplayAgentGateway() {
            override val sessions = MutableStateFlow(state.sessions())
            override fun thread(id: SessionId) = content
            override suspend fun sendMessage(id: SessionId, text: String): Delivery {
                history += BridgeEvent(20, null, "s", "user_message", buildJsonObject {
                    put("text", text); put("delivery", "prompt")
                })
                return Delivery.PROMPT
            }
        }
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.explain(); review.confirmExplanation(); runCurrent()
        fun resync() {
            state.onHello(hello, 100)
            state.onHistory("s", history)
            content.value = state.thread("s", true)!!
        }
        resync(); runCurrent()
        history += BridgeEvent(30, null, "s", "session_update", Frames.parseObject(
            """{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Ce bloc initialise x à 1."}}""")!!)
        history += BridgeEvent(40, null, "s", "turn_end", Frames.parseObject("""{"stopReason":"end_turn"}""")!!)
        resync(); runCurrent()
        assertEquals("Ce bloc initialise x à 1.", review.state.value.explanation!!.reply)
        assertEquals(true, review.state.value.explanation!!.ended)

        review.dismissExplanation(); review.explain()
        // An identical historical prompt must not supply the new explanation's reply.
        resync()
        state.onHistory("s", history)
        state.onHistory("s", history)
        content.value = state.thread("s", true)!!
        runCurrent()
        assertEquals("", review.state.value.explanation!!.reply)
        assertEquals(false, review.state.value.explanation!!.ended)
        review.close()
    }
}
