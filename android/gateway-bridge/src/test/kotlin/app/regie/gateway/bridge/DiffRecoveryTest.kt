package app.regie.gateway.bridge

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.usecase.ReviewDiff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiffRecoveryTest {
    private class Fixture {
        val state = BridgeState()
        val meta = Frames.parseObject("""{"sessionId":"s","agent":"codex","project":"demo","status":"working","active":true}""")!!
        val hello = Hello("journal", 100, true, listOf(meta), emptyList())
        val history = mutableListOf(
            edit(5, "other-file", "a.kt", "first"),
            edit(10, "earlier", "b.kt", "first"),
            edit(20, "selected-edit", "b.kt", "first\nselected"),
            edit(30, "later", "b.kt", "first\nother"),
        )
        val content = MutableStateFlow(SessionThread.EMPTY)
        val sessions = MutableStateFlow(emptyList<Session>())
        var delivery = Delivery.PROMPT
        val gateway = object : AgentGateway by ReplayAgentGateway() {
            override val sessions = this@Fixture.sessions
            override fun thread(id: SessionId) = content
            override suspend fun sendMessage(id: SessionId, text: String): Delivery {
                history += BridgeEvent(40, null, "s", "user_message", buildJsonObject {
                    put("text", text); put("delivery", delivery.name.lowercase())
                })
                reload()
                return delivery
            }
        }
        init { state.onHello(hello, null); reload() }
        fun reload() {
            state.onHistory("s", history)
            content.value = state.thread("s", true)!!
            sessions.value = state.sessions()
        }
        fun beginResync() {
            state.onHello(hello, 100)
            content.value = state.thread("s", false)!!
            sessions.value = state.sessions()
        }
        fun reply() {
            history += BridgeEvent(50, null, "s", "session_update", Frames.parseObject(
                """{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Ce bloc initialise"}}""")!!)
            reload()
        }
        fun stop(status: String = "failed", active: Boolean = false) {
            state.apply(BridgeEvent((state.lastSeq ?: 100) + 1, null, "s", "session", buildJsonObject {
                meta.forEach { (key, value) -> put(key, value) }
                put("status", status); put("active", active); put("lastError", "Codex s’est arrêté.")
            }))
            sessions.value = state.sessions()
        }
        companion object {
            fun edit(seq: Long, id: String, path: String, text: String) = BridgeEvent(seq, null, "s", "session_update",
                Frames.parseObject("""{"sessionUpdate":"tool_call","toolCallId":"$id","kind":"edit","status":"completed",
                    "content":[{"type":"diff","path":"$path","oldText":null,"newText":${kotlinx.serialization.json.JsonPrimitive(text)}}]}""")!!)
        }
    }

    @Test fun `resync preserves file block and internal line through empty snapshots and rebuilt ids`() = runTest {
        val fixture = Fixture()
        fixture.reload() // Force different local ids before resync.
        val review = ReviewDiff(fixture.gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.moveFile(1); review.moveLine(2)
        val target = DictationTarget.DiffComment(SessionId("s"), "b.kt", 2, selectedText = "selected")
        assertEquals(target, review.commentTarget())
        fixture.beginResync(); runCurrent()
        assertTrue(review.state.value.loading)
        assertEquals("selected", review.state.value.selected?.line?.text)
        review.moveFile(-1); review.moveLine(-1); review.explain()
        assertNull(review.commentTarget())
        assertNull(review.state.value.explanation)
        fixture.reload(); runCurrent()
        assertEquals(target, review.commentTarget())
        fixture.beginResync(); runCurrent()
        fixture.history.removeAt(1)
        fixture.reload(); runCurrent()
        assertEquals(target, review.commentTarget())
        review.explain()
        assertEquals("b.kt · ligne 2", review.state.value.explanation!!.label)
        assertTrue(review.state.value.explanation!!.prompt.endsWith("-/1 + first\n-/2 + selected"))
        review.close()
    }

    @Test fun `agent crash ends explanation without turn end and preserves partial reply through resync`() = runTest {
        val fixture = Fixture()
        val review = ReviewDiff(fixture.gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.explain(); review.confirmExplanation(); runCurrent()
        fixture.reply(); runCurrent()
        assertEquals("Ce bloc initialise", review.state.value.explanation!!.reply)
        fixture.stop(active = true); runCurrent()
        assertFalse(review.state.value.explanation!!.ended)
        fixture.beginResync(); runCurrent()
        fixture.stop(); runCurrent()
        assertTrue(review.state.value.explanation!!.ended)
        assertEquals("Ce bloc initialise\n\nÉchec : Codex s’est arrêté.", review.state.value.explanation!!.reply)
        fixture.reload(); runCurrent()
        assertEquals("Ce bloc initialise\n\nÉchec : Codex s’est arrêté.", review.state.value.explanation!!.reply)
        review.close()
    }

    @Test fun `queued explanation ends on stopped session even without any reply`() = runTest {
        val fixture = Fixture()
        fixture.delivery = Delivery.QUEUED
        val review = ReviewDiff(fixture.gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.explain(); review.confirmExplanation(); runCurrent()
        fixture.stop("stopped"); runCurrent()
        assertTrue(review.state.value.explanation!!.ended)
        assertEquals("Session arrêtée avant la fin de l’explication.", review.state.value.explanation!!.reply)
        review.close()
    }

    @Test fun `unconfirmed and completed explanations do not inherit a later agent crash`() = runTest {
        val fixture = Fixture()
        val review = ReviewDiff(fixture.gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.explain()
        fixture.stop(); runCurrent()
        assertFalse(review.state.value.explanation!!.ended)
        assertEquals("", review.state.value.explanation!!.reply)
        fixture.beginResync(); fixture.reload(); runCurrent()
        review.confirmExplanation(); runCurrent()
        fixture.reply()
        fixture.history += BridgeEvent(60, null, "s", "turn_end", Frames.parseObject("""{"stopReason":"end_turn"}""")!!)
        fixture.reload(); runCurrent()
        assertTrue(review.state.value.explanation!!.ended)
        fixture.stop(); runCurrent()
        assertEquals("Ce bloc initialise", review.state.value.explanation!!.reply)
        review.close()
    }
}
