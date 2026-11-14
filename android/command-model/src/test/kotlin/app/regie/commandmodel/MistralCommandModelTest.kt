package app.regie.commandmodel

import app.regie.domain.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.test.*

class MistralCommandModelTest {
    private val context = VoiceCommandContext(emptyList(), null, null, listOf("atelier"))
    private fun response(arguments: String, name: String = "propose_command") = buildJsonObject {
        putJsonArray("choices") { addJsonObject { put("finish_reason", "tool_calls"); putJsonObject("message") { putJsonArray("tool_calls") {
            addJsonObject { put("type", "function"); putJsonObject("function") { put("name", name); put("arguments", arguments) } }
        } } } }
    }.toString()

    @Test fun `sends one bounded function request and only returns a proposal`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body(response("""{"action":"summary_all"}""")).build())
            val model = MistralCommandModel({ "test-key" }, endpoint = server.url("/v1/chat/completions").toString())
            assertEquals(VoiceIntent("summary_all"), model.interpret("Fais un bilan global", context))
            val request = server.takeRequest()
            assertEquals("Bearer test-key", request.headers["Authorization"])
            val json = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertEquals("ministral-3b-2512", json["model"]!!.jsonPrimitive.content)
            assertEquals(false, json["parallel_tool_calls"]!!.jsonPrimitive.boolean)
            assertEquals("auto", json["tool_choice"]!!.jsonPrimitive.content)
            assertEquals(1, json["tools"]!!.jsonArray.size)
            assertTrue(json["messages"].toString().contains("Fais un bilan global"))
        }
    }

    @Test fun `truncated function response and multiple calls are not proposals`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val valid = response("""{"action":"approve"}""")
            val root = Json.parseToJsonElement(valid).jsonObject
            val choice = root["choices"]!!.jsonArray.single().jsonObject
            val message = choice["message"]!!.jsonObject
            val calls = message["tool_calls"]!!.jsonArray
            val multiple = JsonObject(root + ("choices" to JsonArray(listOf(JsonObject(choice + ("message" to JsonObject(message + ("tool_calls" to JsonArray(calls + calls)))))))))
            val model = MistralCommandModel({ "test" }, endpoint = server.url("/").toString())
            listOf(valid.replace("tool_calls\",\"message", "length\",\"message"), multiple.toString()).forEach { body ->
                server.enqueue(MockResponse.Builder().body(body).build())
                assertNull(model.interpret("x", context))
            }
        }
    }

    @Test fun `no key means no request and malformed or wrong function is rejected`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val model = MistralCommandModel({ null }, endpoint = server.url("/").toString())
            assertNull(model.interpret("x", context)); assertEquals(0, server.requestCount)
            val configured = MistralCommandModel({ "test" }, endpoint = server.url("/").toString())
            listOf("{}", response("not json"), response("{}", "execute_shell"), response("""{"action":"approve","extra":"evil"}"""),
                response("""{"action":"approve","target":42}""")).forEach { body ->
                server.enqueue(MockResponse.Builder().body(body).build())
                assertNull(configured.interpret("x", context), body)
            }
            server.enqueue(MockResponse.Builder().code(429).body("quota").build())
            assertNull(configured.interpret("x", context))
        }
    }
}
