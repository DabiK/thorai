package app.regie.gateway.bridge

import kotlin.test.*

class DynamicAgentsTest {
    @Test fun `future catalog metadata and session ids survive decoding`() {
        val state = BridgeState()
        state.onResponse("agents.list", Frames.parseObject("{}")!!, Frames.parseObject("""{"agents":[{"agent":"fourth","name":"Quatrième agent","binary":"v4.2.1","available":true,"isTested":true,"authentication":{"connected":true}}]}""")!!)
        assertEquals("fourth", state.agents.single().agent.id)
        assertEquals("Quatrième agent", state.agents.single().agent.label)
        state.onResponse("hello", Frames.parseObject("{}")!!, Frames.parseObject("""{"journal":"j","lastSeq":0,"sessions":[{"sessionId":"s","agent":"fourth","project":"demo","status":"idle","active":true}]}""")!!)
        assertEquals(state.agents.single().agent, state.sessions().single().agent)
        assertEquals("Quatrième agent", state.sessions().single().agent.label)
    }
}
