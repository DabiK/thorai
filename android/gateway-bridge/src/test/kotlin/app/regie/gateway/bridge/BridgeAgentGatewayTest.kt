package app.regie.gateway.bridge

import app.regie.domain.model.*
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Delivery
import app.regie.domain.model.LastAction
import app.regie.domain.model.SessionId
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.UnreachableReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** La connexion au pont, contre un faux pont local (MockWebServer) : jamais la console. */
class BridgeAgentGatewayTest {

    private val server = MockWebServer()
    private var token: String? = "0123abcd"
    private val lines = CopyOnWriteArrayList<String>()

    @BeforeTest
    fun start() = server.start()

    @AfterTest
    fun stop() = server.close()

    private fun gateway(unreachableAfter: Int = 2, requestTimeout: kotlin.time.Duration = 2.seconds) = BridgeAgentGateway(
        baseUrl = server.url("/").toString(),
        token = { token },
        retryDelay = { 20.milliseconds },
        unreachableAfter = unreachableAfter,
        historyCount = 4,
        shutdownTimeout = 2.seconds,
        requestTimeout = requestTimeout,
        log = { lines += it },
    )

    /** Un faux pont, une connexion : il reçoit les requêtes et répond à la main. */
    private class FakeBridge : WebSocketListener() {
        val requests = Channel<JsonObject>(Channel.UNLIMITED)
        val opened = Channel<WebSocket>(1)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.trySend(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            requests.trySend(Frames.parseObject(text)!!)
        }

        suspend fun socket() = opened.receive()

        suspend fun next(method: String): JsonObject {
            while (true) {
                val request = requests.receive()
                if (request.string("method") == method) return request
            }
        }
    }

    private fun upgrade(bridge: FakeBridge) = server.enqueue(MockResponse.Builder().webSocketUpgrade(bridge).build())

    private fun sessionJson(id: String, status: String, updatedAt: String, pending: String = "", active: Boolean = true) =
        """{"sessionId":"$id","agent":"codex","project":"demo","cwd":"/home/dev/projects/demo","branch":"main",
           "approvalMode":"ask","status":"$status","title":"Titre $id","createdAt":"2026-11-13T21:00:00Z",
           "updatedAt":"$updatedAt","lastError":null,"active":$active,"pendingApprovals":[$pending]}"""

    private fun helloResult(id: Long, journal: String, lastSeq: Long, sessions: String, approvals: String = "", resync: Boolean = false) =
        """{"id":$id,"result":{"bridge":{"version":"0.1.0"},"journal":"$journal","lastSeq":$lastSeq,"resync":$resync,
           "sessions":[$sessions],"approvals":[$approvals],"agents":[]}}"""

    private fun toolCall(seq: Long, call: String, command: String) =
        """{"event":{"seq":$seq,"at":"2026-11-13T21:00:${10 + seq}Z","sessionId":"s","type":"session_update",
           "data":{"sessionUpdate":"tool_call","toolCallId":"$call","title":"$command","kind":"execute","status":"in_progress",
           "rawInput":{"command":"$command"}}}}"""

    private fun versions(id: Long) =
        """{"id":$id,"result":{"agents":[{"agent":"codex","binary":"codex-cli 0.168.0","isTested":false}]}}"""

    private suspend fun BridgeAgentGateway.sessionsWhere(predicate: (List<Session>) -> Boolean) =
        withTimeout(5.seconds) { sessions.first(predicate) }

    private fun <T> withGateway(gateway: BridgeAgentGateway, block: suspend CoroutineScope.() -> T): T = runBlocking {
        val job = launch(Dispatchers.Default) { gateway.maintainConnection() }
        try {
            withTimeout(15.seconds) { block() }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `future agent key is sent once without being echoed in connection logs`() {
        val bridge = FakeBridge(); upgrade(bridge); val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket(); val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val saving = async { gateway.submitAgentKey(Agent("future"), "future-provider", "test-only-key") }
            val request = bridge.next("auth.key")
            assertEquals("future", request.obj("params")?.string("agent"))
            assertEquals("future-provider", request.obj("params")?.string("provider"))
            assertEquals("test-only-key", request.obj("params")?.string("key"))
            ws.send("""{"id":${request.long("id")},"result":{"authentication":{"agent":"future","connected":true,"phase":"connected"}}}""")
            assertEquals(Agent("future"), saving.await().agent)
            assertTrue(saving.await().connected == true)
            assertFalse(lines.joinToString().contains("test-only-key"))
        }
    }

    @Test fun `authentication commands carry only agent identifiers and decode the public challenge`() {
        val bridge = FakeBridge(); upgrade(bridge); val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket(); val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val status = async { gateway.authentication() }
            val request = bridge.next("auth.status")
            ws.send("""{"id":${request.long("id")},"result":{"agents":[{"agent":"codex","connected":false,"phase":"disconnected"}]}}""")
            assertEquals(false, status.await().single().connected)
            for (method in listOf("auth.start", "auth.cancel", "auth.logout")) {
                val action = async {
                    when (method) {
                        "auth.start" -> gateway.startAuthentication(Agent.CODEX)
                        "auth.cancel" -> gateway.cancelAuthentication(Agent.CODEX)
                        else -> gateway.logoutAgent(Agent.CODEX)
                    }
                }
                val command = bridge.next(method)
                assertEquals("codex", command.obj("params")?.string("agent"))
                assertEquals(setOf("agent"), command.obj("params")?.keys)
                ws.send("""{"id":${command.long("id")},"result":{"authentication":{"agent":"codex","connected":false,"phase":"waiting","url":"https://auth.openai.com/codex/device","code":"ABCD-EFGHI","access_token":"not-for-the-app"}}}""")
                assertEquals("ABCD-EFGHI", action.await().code)
            }
            assertTrue(lines.none { "ABCD-EFGHI" in it || "not-for-the-app" in it })
        }
    }

    @Test fun `agent options and confirmed session settings cross the real gateway`() {
        val bridge = FakeBridge(); upgrade(bridge); val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket(); val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val options = """[{"id":"custom-model","name":"Remote model","category":"model","currentValue":"remote","options":[{"value":"remote","name":"Remote"}]}]"""
            val discovery = async { gateway.agentOptions(Agent.CODEX) }
            val lookup = bridge.next("agent.options")
            assertEquals("codex", lookup.obj("params")!!.string("agent"))
            ws.send("""{"id":${lookup.long("id")},"result":{"configOptions":$options}}""")
            assertEquals("remote", discovery.await().single().currentValue)
            val change = async { gateway.configureSession(SessionId("s"), SessionSetting(approvalMode = "allow-all")) }
            val request = bridge.next("session.configure")
            assertEquals("allow-all", request.obj("params")!!.string("approvalMode"))
            val session = sessionJson("s", "idle", "2026-11-14T00:00:00Z").replace("\"ask\"", "\"allow-all\"").dropLast(1) + ",\"configOptions\":$options}"
            ws.send("""{"id":${request.long("id")},"result":{"session":$session}}""")
            assertEquals(SettingsResult.APPLIED, change.await())
            assertEquals("allow-all", gateway.sessions.value.single().approvalMode)
            assertEquals("Remote", gateway.sessions.value.single().configOptions.single().valueLabel)
        }
    }

    @Test fun `joint replacements cross the gateway in one configuration request`() {
        val bridge = FakeBridge(); upgrade(bridge); val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket(); val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val change = async { gateway.configureSession(SessionId("s"), SessionSetting(configValues = mapOf("model" to "new", "effort" to "low"))) }
            val request = bridge.next("session.configure")
            assertEquals("new", request.obj("params")!!.obj("configValues")?.string("model"))
            assertEquals("low", request.obj("params")!!.obj("configValues")?.string("effort"))
            val session = sessionJson("s", "idle", "2026-11-14T00:00:00Z")
            ws.send("""{"id":${request.long("id")},"result":{"session":$session}}""")
            assertEquals(SettingsResult.APPLIED, change.await())
        }
    }

    @Test fun `new project flag crosses websocket and collisions are certain readable refusals`() {
        val bridge = FakeBridge(); upgrade(bridge); val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket(); val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val creation = async { gateway.createSession(app.regie.domain.model.NewSessionDraft(app.regie.domain.model.Agent.CODEX, "meteo", newProject = true)) }
            val request = bridge.next("session.create")
            assertEquals("true", request.obj("params")!!["newProject"].toString())
            ws.send("""{"id":${request.long("id")},"error":{"code":"project_exists","message":"Le dossier existe déjà."}}""")
            val result = creation.await()
            assertTrue(!result.uncertain)
            assertEquals("Le dossier existe déjà.", result.error)
        }
    }

    @Test
    fun `voice launch uses project catalog and creates once with plan mode`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket()
            val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val projects = async { gateway.projects() }
            val catalog = bridge.next("projects.list")
            ws.send("""{"id":${catalog.long("id")},"result":{"projects":[{"name":"demo","branch":"main"}]}}""")
            assertEquals(listOf("demo"), projects.await())
            val choices = async { gateway.projectCatalog() }
            val catalogRequest = bridge.next("projects.list")
            ws.send("""{"id":${catalogRequest.long("id")},"result":{"projects":[{"name":"demo","branch":"main","branches":["main","feature/x"]},{"name":"plain","branch":null}]}}""")
            assertEquals(listOf(app.regie.domain.model.ProjectChoice("demo", "main", listOf("main", "feature/x")),
                app.regie.domain.model.ProjectChoice("plain")), choices.await())
            val creation = async { gateway.createSession(app.regie.domain.model.NewSessionDraft(app.regie.domain.model.Agent.CODEX, "demo", "feature", "Corrige le test")) }
            val request = bridge.next("session.create")
            val params = request.obj("params")!!
            assertEquals("codex", params.string("agent"))
            assertEquals("demo", params.string("project"))
            assertEquals("plan", params.string("approvalMode"))
            assertEquals("feature", params.string("branch"))
            assertEquals("Corrige le test", params.string("prompt"))
            ws.send("""{"id":${request.long("id")},"result":{"session":${sessionJson("created", "idle", "2026-11-14T00:00:00Z")}}}""")
            assertEquals(SessionId("created"), creation.await().id)
            val lost = async { gateway.createSession(app.regie.domain.model.NewSessionDraft(app.regie.domain.model.Agent.CODEX, "demo")) }
            bridge.next("session.create")
            ws.close(1000, "lost receipt")
            assertTrue(lost.await().uncertain)
        }
    }

    @Test
    fun `approval answer sends exact ids and a lost receipt stays unknown`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()
        withGateway(gateway) {
            val ws = bridge.socket()
            val hello = bridge.next("hello")
            ws.send(helloResult(hello.long("id")!!, "j", 0, ""))
            gateway.connection.first { it == BridgeConnection.Connected }
            val answer = async { gateway.answerApproval(app.regie.domain.model.ApprovalId("approval-1"), "allow_once") }
            val request = bridge.next("approval.answer")
            assertEquals("approval-1", request.obj("params")!!.string("approvalId"))
            assertEquals("allow_once", request.obj("params")!!.string("optionId"))
            ws.close(1000, "lost receipt")
            assertEquals(app.regie.domain.model.ApprovalReply.UNKNOWN, answer.await())
        }
    }

    @Test
    fun `the token goes in the Authorization header and the first hello has no since`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = bridge.next("hello")
            assertEquals(JsonObject(emptyMap()), hello.obj("params"))
            assertEquals("Bearer 0123abcd", server.takeRequest().headers["Authorization"])
        }
    }

    @Test
    fun `after a cut, the app reconnects alone and catches up without duplicate nor loss`() {
        val first = FakeBridge()
        upgrade(first)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = first.next("hello")
            val socket = first.socket()
            socket.send(helloResult(hello.long("id")!!, "j1", lastSeq = 10, sessions = sessionJson("s", "working", "2026-11-13T21:00:10Z")))
            val history = first.next("session.events")
            assertEquals("s", history.obj("params")?.string("sessionId"))
            socket.send("""{"id":${history.long("id")},"result":{"events":[]}}""")
            socket.send(toolCall(11, "a", "ls"))
            gateway.sessionsWhere { it.singleOrNull()?.lastAction?.description == "ls" }
            assertEquals(BridgeConnection.Connected, gateway.connection.value)

            // Le pont s'arrête, puis revient.
            val second = FakeBridge()
            upgrade(second)
            socket.close(1001, "arrêt du pont")

            val reconnect = second.next("hello")
            assertEquals(11, reconnect.obj("params")?.long("since"))
            assertEquals("j1", reconnect.obj("params")?.string("journal"))
            val socket2 = second.socket()
            socket2.send(
                helloResult(
                    reconnect.long("id")!!, "j1", lastSeq = 13,
                    sessions = sessionJson("s", "awaiting_approval", "2026-11-13T21:00:30Z", pending = "\"p\""),
                    approvals = """{"approvalId":"p","sessionId":"s","toolCall":{"kind":"execute","title":"rm x","rawInput":{"command":"rm x"}},"options":[]}""",
                ),
            )
            // Rattrapage : un doublon de l'événement 11, puis les manqués 12 et 13.
            socket2.send(toolCall(11, "a", "ls"))
            socket2.send(toolCall(12, "b", "cat notes.md"))
            socket2.send(toolCall(13, "c", "rm x"))
            socket2.send(toolCall(12, "b", "cat notes.md"))

            val sessions = gateway.sessionsWhere { it.singleOrNull()?.lastAction?.description == "rm x" }
            // La trace prouve le rattrapage : le doublon est compté comme ignoré, sans trou.
            assertTrue("pont : hello envoyé (since=11, journal=j1)" in lines, lines.toString())
            assertTrue("pont : réponse hello (lastSeq=13, resync=false, 1 session, 1 validation)" in lines, lines.toString())
            assertTrue(
                "pont : rattrapage terminé (3 événements, seq 11 → 13, 1 ignoré car déjà vu, aucun trou)" in lines,
                lines.toString(),
            )
            assertTrue(lines.any { it.startsWith("pont : connexion perdue (fermée par le pont, code 1001") }, lines.toString())
            assertTrue(lines.none { "0123abcd" in it }, "le jeton ne doit jamais apparaître dans la trace")
            val session = sessions.single()
            assertEquals(SessionStatus.AWAITING_APPROVAL, session.status)
            assertEquals("rm x", session.pendingApproval?.subject)
            assertEquals(LastAction("rm x", ActionStatus.IN_PROGRESS), session.lastAction)

            // Le rattrapage ne recharge pas les journaux : rien n'est perdu, tout est arrivé par le direct.
            val next = second.requests.receive()
            assertEquals("agents.list", next.string("method"))
            socket2.send(versions(next.long("id")!!))
            withTimeout(5.seconds) { gateway.agents.first { it.isNotEmpty() } }
            assertTrue(second.requests.tryReceive().isFailure)
        }
    }

    @Test
    fun `a resync forgets the old state and reloads the journal of the sessions shown`() {
        val first = FakeBridge()
        upgrade(first)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = first.next("hello")
            val socket = first.socket()
            socket.send(helloResult(hello.long("id")!!, "ancien", lastSeq = 50, sessions = sessionJson("s", "idle", "2026-11-13T21:00:10Z")))
            gateway.sessionsWhere { it.size == 1 }

            val second = FakeBridge()
            upgrade(second)
            socket.close(1001, "arrêt du pont")
            val reconnect = second.next("hello")
            assertEquals(50, reconnect.obj("params")?.long("since"))
            val socket2 = second.socket()
            socket2.send(helloResult(reconnect.long("id")!!, "nouveau", lastSeq = 3, sessions = sessionJson("t", "working", "2026-11-13T22:00:00Z"), resync = true))
            gateway.sessionsWhere { it.map { s -> s.id.value } == listOf("t") }

            val history = second.next("session.events")
            assertEquals("t", history.obj("params")?.string("sessionId"))
            socket2.send(
                """{"id":${history.long("id")},"result":{"events":[
                   {"seq":2,"at":"2026-11-13T22:00:01Z","sessionId":"t","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"x","title":"npm test","kind":"execute","status":"in_progress","rawInput":{"command":"npm test"}}}]}}""",
            )
            val session = gateway.sessionsWhere { it.singleOrNull()?.lastAction != null }.single()
            assertEquals(LastAction("npm test", ActionStatus.IN_PROGRESS), session.lastAction)
        }
    }

    @Test
    fun `a refused token is reported at once`() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).build())
        val gateway = gateway(unreachableAfter = 10)

        withGateway(gateway) {
            withTimeout(5.seconds) { gateway.connection.first { it is BridgeConnection.Unreachable } }
            assertEquals(BridgeConnection.Unreachable(UnreachableReason.TOKEN_REFUSED), gateway.connection.value)
        }
    }

    @Test
    fun `a missing token is reported without calling the bridge`() {
        token = null
        val gateway = gateway()

        withGateway(gateway) {
            withTimeout(5.seconds) { gateway.connection.first { it is BridgeConnection.Unreachable } }
            assertEquals(BridgeConnection.Unreachable(UnreachableReason.TOKEN_MISSING), gateway.connection.value)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `a bridge still starting (503) is retried, and only then said unreachable`() {
        repeat(3) { server.enqueue(MockResponse.Builder().code(503).build()) }
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway(unreachableAfter = 2)

        withGateway(gateway) {
            withTimeout(5.seconds) { gateway.connection.first { it is BridgeConnection.Unreachable } }
            assertEquals(BridgeConnection.Unreachable(UnreachableReason.NOT_RESPONDING), gateway.connection.value)
            val hello = bridge.next("hello")
            bridge.socket().send(helloResult(hello.long("id")!!, "j", lastSeq = 0, sessions = ""))
            withTimeout(5.seconds) { gateway.connection.first { it == BridgeConnection.Connected } }
        }
    }

    @Test
    fun `quitting asks the bridge to shut down`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = bridge.next("hello")
            val socket = bridge.socket()
            socket.send(helloResult(hello.long("id")!!, "j", lastSeq = 0, sessions = ""))
            withTimeout(5.seconds) { gateway.connection.first { it == BridgeConnection.Connected } }

            val stopped = async { gateway.shutdownBridge() }
            val shutdown = bridge.next("bridge.shutdown")
            socket.send("""{"id":${shutdown.long("id")},"result":{}}""")
            assertTrue(stopped.await())
        }
    }

    @Test
    fun `without a connection, the shutdown is not heard`() = runBlocking {
        assertFalse(gateway().shutdownBridge())
    }

    @Test
    fun `the health check says whether the bridge is up`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"ok":true}""").build())
        assertTrue(gateway().isBridgeUp())
        assertEquals("/health", server.takeRequest().url.encodedPath)
        server.enqueue(MockResponse.Builder().code(500).build())
        assertFalse(gateway().isBridgeUp())
    }

    @Test
    fun `the retry delay grows from half a second to five`() {
        assertEquals(
            listOf(500, 1000, 2000, 4000, 5000, 5000).map { it.milliseconds },
            (1..6).map(BridgeAgentGateway::backoff),
        )
    }

    @Test
    fun `a gap in the catch-up is reported`() {
        val first = FakeBridge()
        upgrade(first)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = first.next("hello")
            val socket = first.socket()
            socket.send(helloResult(hello.long("id")!!, "journal-long-1234", lastSeq = 11, sessions = sessionJson("s", "working", "2026-11-13T21:00:10Z")))
            withTimeout(5.seconds) { gateway.connection.first { it == BridgeConnection.Connected } }

            val second = FakeBridge()
            upgrade(second)
            socket.close(1001, "arrêt du pont")
            val reconnect = second.next("hello")
            second.socket().apply {
                send(helloResult(reconnect.long("id")!!, "journal-long-1234", lastSeq = 14, sessions = sessionJson("s", "working", "2026-11-13T21:00:20Z")))
                send(toolCall(12, "a", "ls"))
                send(toolCall(14, "b", "rm x"))
            }
            gateway.sessionsWhere { it.singleOrNull()?.lastAction?.description == "rm x" }

            assertTrue("pont : hello envoyé (since=11, journal=journal-)" in lines, lines.toString())
            assertTrue(
                "pont : rattrapage terminé (2 événements, seq 12 → 14, 0 ignoré car déjà vu, trous : attendu 13, reçu 14)" in lines,
                lines.toString(),
            )
            assertTrue(lines.none { "0123abcd" in it })
        }
    }

    @Test
    fun `a session entering the cards shown gets its journal loaded once, without what the live will bring`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = bridge.next("hello")
            val socket = bridge.socket()
            val shown = (1..4).map { sessionJson("a$it", "idle", "2026-11-13T21:00:0${it}Z") }
            val history = sessionJson("h", "stopped", "2026-11-13T20:00:00Z", active = false)
            socket.send(helloResult(hello.long("id")!!, "j", lastSeq = 20, sessions = (shown + history).joinToString(",")))

            // Les 4 cartes affichées d'abord, pas l'historique.
            val first = (1..4).map { bridge.next("session.events") }
            assertEquals((1..4).map { "a$it" }.toSet(), first.map { it.obj("params")?.string("sessionId") }.toSet())
            first.forEach { socket.send("""{"id":${it.long("id")},"result":{"events":[]}}""") }

            // « h » est reprise : elle passe en tête des cartes, son journal est chargé.
            socket.send(
                """{"event":{"seq":21,"at":"2026-11-13T21:01:00Z","sessionId":"h","type":"session",
                   "data":${sessionJson("h", "working", "2026-11-13T21:01:00Z")}}}""",
            )
            val load = bridge.next("session.events")
            assertEquals("h", load.obj("params")?.string("sessionId"))
            socket.send(toolCall(22, "live", "npm test").replace("\"sessionId\":\"s\"", "\"sessionId\":\"h\""))
            socket.send(
                """{"id":${load.long("id")},"result":{"events":[
                   {"seq":5,"at":"2026-11-13T20:00:00Z","sessionId":"h","type":"session_update","data":{"sessionUpdate":"plan","entries":[{"content":"Lire","status":"completed"},{"content":"Tester","status":"in_progress"}]}},
                   {"seq":6,"at":"2026-11-13T20:00:01Z","sessionId":"h","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"old","title":"ls","kind":"execute","status":"completed","rawInput":{"command":"ls"}}},
                   {"seq":22,"at":"2026-11-13T21:01:02Z","sessionId":"h","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"live","title":"npm test","kind":"execute","status":"in_progress","rawInput":{"command":"npm test"}}},
                   {"seq":23,"at":"2026-11-13T21:01:03Z","sessionId":"h","type":"session_update","data":{"sessionUpdate":"tool_call","toolCallId":"future","title":"rm x","kind":"execute","status":"pending","rawInput":{"command":"rm x"}}}]}}""",
            )

            val h = gateway.sessionsWhere { list -> list.any { it.id.value == "h" && it.progress != null } }.single { it.id.value == "h" }
            assertEquals(1, h.progress?.done)
            // seq 22 est déjà là par le direct, seq 23 viendra par le direct : rien d'inventé ni de doublé.
            assertEquals(LastAction("npm test", ActionStatus.IN_PROGRESS), h.lastAction)

            // Un autre événement de « h » ne recharge pas son journal.
            socket.send(toolCall(23, "future", "rm x").replace("\"sessionId\":\"s\"", "\"sessionId\":\"h\""))
            gateway.sessionsWhere { list -> list.any { it.lastAction?.description == "rm x" } }
            assertTrue(bridge.requests.tryReceive().getOrNull()?.string("method") != "session.events")
        }
    }

    @Test
    fun `a bridge closing right after each hello ends up unreachable, with spaced retries`() {
        val bridges = (1..3).map { FakeBridge().also(::upgrade) }
        val gateway = gateway(unreachableAfter = 2)

        withGateway(gateway) {
            for (bridge in bridges.take(2)) {
                val hello = bridge.next("hello")
                val socket = bridge.socket()
                socket.send(helloResult(hello.long("id")!!, "j", lastSeq = 0, sessions = ""))
                socket.close(1001, "plantage")
            }
            withTimeout(5.seconds) { gateway.connection.first { it is BridgeConnection.Unreachable } }
            assertEquals(BridgeConnection.Unreachable(UnreachableReason.NOT_RESPONDING), gateway.connection.value)
            assertTrue(lines.any { it.contains("tenue moins de 10s, tentative 1") }, lines.toString())
            assertTrue(lines.any { it.contains("tenue moins de 10s, tentative 2") }, lines.toString())
        }
    }

    /** Connecté, une seule session « s » affichée et son journal (vide) chargé. */
    private suspend fun connected(bridge: FakeBridge, gateway: BridgeAgentGateway): WebSocket {
        val hello = bridge.next("hello")
        val socket = bridge.socket()
        socket.send(helloResult(hello.long("id")!!, "j", lastSeq = 10, sessions = sessionJson("s", "working", "2026-11-13T21:00:10Z")))
        val history = bridge.next("session.events")
        socket.send("""{"id":${history.long("id")},"result":{"events":[]}}""")
        withTimeout(5.seconds) { gateway.connection.first { it == BridgeConnection.Connected } }
        return socket
    }

    @Test
    fun `a message goes to the session and says how it left`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()

        withGateway(gateway) {
            val socket = connected(bridge, gateway)

            val sent = async { gateway.sendMessage(SessionId("s"), "Arrête-toi après le premier fichier.") }
            val prompt = bridge.next("session.prompt")
            assertEquals("s", prompt.obj("params")?.string("sessionId"))
            assertEquals("Arrête-toi après le premier fichier.", prompt.obj("params")?.string("text"))
            socket.send("""{"id":${prompt.long("id")},"result":{"delivery":"steering"}}""")
            assertEquals(Delivery.STEERING, sent.await())

            val queued = async { gateway.sendMessage(SessionId("s"), "Et ensuite ?") }
            socket.send("""{"id":${bridge.next("session.prompt").long("id")},"result":{"delivery":"queued"}}""")
            assertEquals(Delivery.QUEUED, queued.await())

            val refused = async { gateway.sendMessage(SessionId("zz"), "?") }
            socket.send("""{"id":${bridge.next("session.prompt").long("id")},"error":{"code":"unknown_session","message":"Session inconnue."}}""")
            assertEquals(Delivery.NOT_SENT, refused.await())
        }
    }

    @Test
    fun `a message echoed in the thread before disconnection is never reported as not sent`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()
        withGateway(gateway) {
            val socket = connected(bridge, gateway)
            val thread = gateway.thread(SessionId("s")).stateIn(this)
            val sent = async { gateway.sendMessage(SessionId("s"), "Relance les tests") }
            bridge.next("session.prompt")
            socket.send(
                """{"event":{"seq":11,"at":"2026-11-13T21:00:11Z","sessionId":"s","type":"user_message",
                   "data":{"text":"Relance les tests","delivery":"prompt"}}}""",
            )
            withTimeout(5.seconds) {
                thread.first { t -> t.entries.any { it is ThreadEntry.UserMessage && it.text == "Relance les tests" } }
            }
            val reconnected = FakeBridge()
            upgrade(reconnected)
            socket.close(1001, "accusé perdu")
            assertEquals(Delivery.UNKNOWN, sent.await())
            val hello = reconnected.next("hello")
            assertEquals(11, hello.obj("params")?.long("since"))
            val nextSocket = reconnected.socket()
            nextSocket.send(helloResult(hello.long("id")!!, "j", lastSeq = 11, sessions = sessionJson("s", "working", "2026-11-13T21:00:11Z")))
            val request = reconnected.requests.receive()
            assertEquals("agents.list", request.string("method"))
            nextSocket.send(versions(request.long("id")!!))
            withTimeout(5.seconds) { gateway.agents.first { it.isNotEmpty() } }
            assertTrue(reconnected.requests.tryReceive().isFailure, "aucun prompt rejoué après reconnexion")
            assertEquals(1, thread.value.entries.filterIsInstance<ThreadEntry.UserMessage>().size)
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun `a disconnect without an echo leaves delivery unknown too`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()
        withGateway(gateway) {
            val socket = connected(bridge, gateway)
            val sent = async { gateway.sendMessage(SessionId("s"), "x") }
            bridge.next("session.prompt")
            socket.close(1001, "coupure")
            assertEquals(Delivery.UNKNOWN, sent.await())
        }
    }

    @Test
    fun `a timed out message is unknown and a late reply does not resend it`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway(requestTimeout = 1.seconds)
        withGateway(gateway) {
            val socket = connected(bridge, gateway)
            val sent = async { gateway.sendMessage(SessionId("s"), "x") }
            val prompt = bridge.next("session.prompt")
            assertEquals(Delivery.UNKNOWN, sent.await())
            socket.send("""{"id":${prompt.long("id")},"result":{"delivery":"prompt"}}""")
            val interrupted = async { gateway.interrupt(SessionId("s")) }
            val cancel = bridge.requests.receive()
            assertEquals("session.cancel", cancel.string("method"))
            socket.send("""{"id":${cancel.long("id")},"result":{}}""")
            assertTrue(interrupted.await())
            assertTrue(bridge.requests.tryReceive().isFailure)
        }
    }

    @Test
    fun `an unspecified bridge failure does not prove non delivery`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()
        withGateway(gateway) {
            val socket = connected(bridge, gateway)
            val sent = async { gateway.sendMessage(SessionId("s"), "x") }
            val prompt = bridge.next("session.prompt")
            socket.send("""{"id":${prompt.long("id")},"error":{"code":"internal_error","message":"Erreur"}}""")
            assertEquals(Delivery.UNKNOWN, sent.await())
        }
    }

    @Test
    fun `a vanished approval or option is unavailable rather than an uncertain answer`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()
        withGateway(gateway) {
            val socket = connected(bridge, gateway)
            for (code in listOf("unknown_approval", "unknown_option")) {
                val answer = async { gateway.answerApproval(app.regie.domain.model.ApprovalId("a"), "allow_once") }
                val request = bridge.next("approval.answer")
                socket.send("""{"id":${request.long("id")},"error":{"code":"$code","message":"Indisponible"}}""")
                assertEquals(app.regie.domain.model.ApprovalReply.UNAVAILABLE, answer.await())
            }
        }
    }

    @Test
    fun `interrupting sends session cancel`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()

        withGateway(gateway) {
            val socket = connected(bridge, gateway)

            val interrupted = async { gateway.interrupt(SessionId("s")) }
            val cancel = bridge.next("session.cancel")
            assertEquals("s", cancel.obj("params")?.string("sessionId"))
            socket.send("""{"id":${cancel.long("id")},"result":{}}""")
            assertTrue(interrupted.await())
        }
    }

    @Test
    fun `without a connection, nothing is sent`() = runBlocking {
        assertEquals(Delivery.NOT_SENT, gateway().sendMessage(SessionId("s"), "x"))
        assertFalse(gateway().interrupt(SessionId("s")))
    }

    @Test
    fun `opening a session beyond the cards shown loads its journal, then follows it live`() {
        val bridge = FakeBridge()
        upgrade(bridge)
        val gateway = gateway()

        withGateway(gateway) {
            val hello = bridge.next("hello")
            val socket = bridge.socket()
            val shown = (1..4).map { sessionJson("a$it", "idle", "2026-11-13T21:00:0${it}Z") }
            val old = sessionJson("h", "stopped", "2026-11-13T20:00:00Z", active = false)
            socket.send(helloResult(hello.long("id")!!, "j", lastSeq = 20, sessions = (shown + old).joinToString(",")))
            (1..4).map { bridge.next("session.events") }.forEach { socket.send("""{"id":${it.long("id")},"result":{"events":[]}}""") }

            val thread = gateway.thread(SessionId("h")).stateIn(this)
            val load = bridge.next("session.events")
            assertEquals("h", load.obj("params")?.string("sessionId"))
            socket.send(
                """{"id":${load.long("id")},"result":{"events":[
                   {"seq":5,"at":"2026-11-13T20:00:00Z","sessionId":"h","type":"user_message","data":{"text":"Lis notes.md","delivery":"prompt"}},
                   {"seq":6,"at":"2026-11-13T20:00:01Z","sessionId":"h","type":"session_update","data":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Je "}}}]}}""",
            )
            val loaded = withTimeout(5.seconds) { thread.first { it.loaded } }
            assertEquals(listOf("Lis notes.md"), loaded.entries.filterIsInstance<ThreadEntry.UserMessage>().map { it.text })

            socket.send(
                """{"event":{"seq":21,"at":"2026-11-13T21:01:00Z","sessionId":"h","type":"session_update",
                   "data":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"lis."}}}}""",
            )
            val live = withTimeout(5.seconds) { thread.first { t -> t.entries.any { it is ThreadEntry.AgentText && it.text == "Je lis." } } }
            assertEquals(2, live.entries.size)
            coroutineContext.cancelChildren()
        }
    }
}
