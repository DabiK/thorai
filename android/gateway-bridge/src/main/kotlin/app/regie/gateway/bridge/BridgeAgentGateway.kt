package app.regie.gateway.bridge

import app.regie.domain.model.AgentVersion
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Session
import app.regie.domain.model.UnreachableReason
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Adapter du port [AgentGateway] vers le pont, par WebSocket (docs/protocole-pont.md, ADR 06).
 *
 * - Jeton dans l'en-tête `Authorization`, relu à chaque tentative : il peut être copié pendant que
 *   Régie tourne. Il n'est jamais journalisé.
 * - Reconnexion seule, de plus en plus espacée ([retryDelay]), avec `hello` porteur du dernier `seq`
 *   et de l'identifiant du journal : le pont renvoie ce qui a été manqué, sans doublon.
 * - Après une première connexion ou un `resync`, le journal des sessions affichées est rechargé
 *   (`session.events`) pour retrouver leur plan et leur dernière action.
 *
 * L'état vit en mémoire : au relancement de l'app, un `hello` sans `since` suffit.
 *
 * [log] reçoit une trace sobre de la connexion (ouverture, coupure, `hello`, bilan du rattrapage),
 * jamais le jeton ni le contenu des messages, et pas une ligne par événement en direct.
 */
class BridgeAgentGateway(
    baseUrl: String = DEFAULT_URL,
    private val token: () -> String?,
    private val client: OkHttpClient = defaultClient(),
    private val retryDelay: (attempt: Int) -> Duration = ::backoff,
    /** Échecs de suite avant de dire le pont injoignable : son démarrage prend quelques secondes. */
    private val unreachableAfter: Int = 5,
    /** Sessions dont on recharge le journal : celles que la vue d'ensemble affiche. */
    private val historyCount: Int = 4,
    private val shutdownTimeout: Duration = 3.seconds,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String) -> Unit = {},
) : AgentGateway {

    private val url = baseUrl.trimEnd('/')
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    private val _agents = MutableStateFlow<List<AgentVersion>>(emptyList())
    private val _connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connecting)
    override val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    override val agents: StateFlow<List<AgentVersion>> = _agents.asStateFlow()
    override val connection: StateFlow<BridgeConnection> = _connection.asStateFlow()

    /** Touché seulement par la boucle de connexion. */
    private val state = BridgeState()
    private val nextId = AtomicLong()

    /** La connexion ouverte, pour les requêtes venues d'ailleurs ([shutdownBridge]). */
    @Volatile
    private var socket: WebSocket? = null
    private val calls = ConcurrentHashMap<Long, CompletableDeferred<JsonObject?>>()

    override suspend fun maintainConnection(): Unit = withContext(dispatcher) {
        var failures = 0
        while (true) {
            when (val outcome = connectOnce(attempt = failures + 1)) {
                is Outcome.Lost -> {
                    failures = 0
                    _connection.value = BridgeConnection.Connecting
                    val wait = retryDelay(1)
                    log("pont : connexion perdue (${outcome.detail}), nouvel essai dans $wait")
                    delay(wait)
                }
                is Outcome.Failed -> {
                    failures++
                    if (outcome.reason != UnreachableReason.NOT_RESPONDING || failures >= unreachableAfter) {
                        _connection.value = BridgeConnection.Unreachable(outcome.reason)
                    }
                    val wait = retryDelay(failures)
                    log("pont : connexion impossible (${outcome.detail}, tentative $failures), prochain essai dans $wait")
                    delay(wait)
                }
            }
        }
    }

    override suspend fun isBridgeUp(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$url/health").build()
            client.newBuilder().callTimeout(HEALTH_TIMEOUT.toJavaDuration()).build().newCall(request).execute().use { response ->
                response.isSuccessful && Frames.parseObject(response.body.string())?.boolean("ok") == true
            }
        }.getOrDefault(false)
    }

    override suspend fun shutdownBridge(): Boolean {
        val ws = socket ?: return false
        if (_connection.value != BridgeConnection.Connected) return false
        val id = nextId.incrementAndGet()
        val reply = CompletableDeferred<JsonObject?>()
        calls[id] = reply
        if (!ws.send(Frames.request(id, "bridge.shutdown"))) {
            calls.remove(id)
            return false
        }
        return withTimeoutOrNull(shutdownTimeout) { reply.await() } != null
    }

    /** Une connexion, de l'ouverture à la coupure. */
    private suspend fun connectOnce(attempt: Int): Outcome {
        val secret = token()?.trim()?.ifEmpty { null }
            ?: return Outcome.Failed(UnreachableReason.TOKEN_MISSING, "jeton absent")
        val signals = Channel<Signal>(Channel.UNLIMITED)
        val request = Request.Builder().url(url).header("Authorization", "Bearer $secret").build()
        val ws = client.newWebSocket(request, Listener(signals))
        val requests = HashMap<Long, Pair<String, JsonObject>>()
        var greeted = false
        var catchUp: CatchUp? = null

        fun send(method: String, params: JsonObject = JsonObject(emptyMap())) {
            val id = nextId.incrementAndGet()
            requests[id] = method to params
            ws.send(Frames.request(id, method, params))
        }

        try {
            for (signal in signals) {
                when (signal) {
                    Signal.Opened -> {
                        socket = ws
                        log("pont : connexion ouverte (tentative $attempt)")
                        val since = state.lastSeq.takeIf { state.journal != null }
                        log(
                            if (since == null) {
                                "pont : hello envoyé (sans since)"
                            } else {
                                "pont : hello envoyé (since=$since, journal=${state.journal.orEmpty().take(8)})"
                            },
                        )
                        send(
                            "hello",
                            buildJsonObject {
                                if (since != null) {
                                    put("since", since)
                                    put("journal", state.journal)
                                }
                            },
                        )
                    }
                    is Signal.Message -> when (val incoming = Frames.parse(signal.text)) {
                        is Incoming.Event -> {
                            val expected = (state.lastSeq ?: 0) + 1
                            val applied = state.apply(incoming.event)
                            catchUp?.let { tally ->
                                tally.record(incoming.event.seq, expected, applied)
                                if (tally.done) {
                                    log(tally.summary())
                                    catchUp = null
                                }
                            }
                            if (applied) publish()
                        }
                        is Incoming.Response -> {
                            val id = incoming.id ?: continue
                            calls.remove(id)?.let { reply ->
                                reply.complete(incoming.result.takeIf { incoming.error == null })
                                continue
                            }
                            val (method, params) = requests.remove(id) ?: continue
                            val result = incoming.result
                            if (result == null) {
                                // Un `hello` refusé (pont en train de s'arrêter…) : on recommence plus tard.
                                if (method == "hello") return Outcome.Failed(UnreachableReason.NOT_RESPONDING, "hello refusé (${incoming.error})")
                                continue
                            }
                            val fresh = state.onResponse(method, params, result)
                            if (method == "hello") {
                                greeted = true
                                Hello.parse(result)?.let { hello ->
                                    log(
                                        "pont : réponse hello (lastSeq=${hello.lastSeq}, resync=${hello.resync}, " +
                                            "${count(hello.sessions.size, "session", "sessions")}, " +
                                            "${count(hello.approvals.size, "validation", "validations")})",
                                    )
                                    val since = params.long("since")
                                    val target = hello.lastSeq
                                    when {
                                        fresh -> log("pont : pas de rattrapage, état repris de la réponse à hello")
                                        since == null || target == null || target <= since -> log("pont : rattrapage vide")
                                        else -> catchUp = CatchUp(target)
                                    }
                                }
                                _connection.value = BridgeConnection.Connected
                                send("agents.versions")
                                if (fresh) {
                                    state.firstSessions(historyCount).forEach { send("session.events", buildJsonObject { put("sessionId", it) }) }
                                }
                            }
                            publish()
                        }
                        null -> Unit
                    }
                    is Signal.Failed -> {
                        val detail = signal.code?.let { "HTTP $it" } ?: signal.error
                        return if (greeted) Outcome.Lost(detail) else Outcome.Failed(reasonOf(signal.code), detail)
                    }
                    is Signal.Closed -> {
                        val detail = "fermée par le pont, code ${signal.code}"
                        return if (greeted) Outcome.Lost(detail) else Outcome.Failed(UnreachableReason.NOT_RESPONDING, detail)
                    }
                }
            }
            return Outcome.Lost("fin du flux")
        } finally {
            catchUp?.let { log("${it.summary()} — interrompu") }
            socket = null
            ws.cancel()
            calls.values.forEach { it.complete(null) }
            calls.clear()
        }
    }

    private fun publish() {
        _sessions.value = state.sessions()
        _agents.value = state.agents
    }

    private sealed interface Outcome {
        /** La connexion avait abouti, puis s'est coupée. */
        data class Lost(val detail: String) : Outcome

        data class Failed(val reason: UnreachableReason, val detail: String) : Outcome
    }

    private sealed interface Signal {
        data object Opened : Signal

        data class Message(val text: String) : Signal

        /** [error] : le type de l'erreur seulement, jamais son message. */
        data class Failed(val code: Int?, val error: String) : Signal

        data class Closed(val code: Int) : Signal
    }

    /** Bilan du rattrapage : ce que le pont renvoie après `hello`, jusqu'à son `lastSeq`. */
    private class CatchUp(private val target: Long) {
        private var received = 0
        private var first: Long? = null
        private var last: Long? = null
        private var ignored = 0
        private val gaps = mutableListOf<String>()
        var done = false
            private set

        fun record(seq: Long, expected: Long, applied: Boolean) {
            received++
            if (first == null) first = seq
            last = seq
            if (!applied) ignored++ else if (seq != expected) gaps += "attendu $expected, reçu $seq"
            if (seq >= target) done = true
        }

        fun summary() = "pont : rattrapage terminé (${count(received, "événement", "événements")}, seq $first → $last, " +
            "${count(ignored, "ignoré car déjà vu", "ignorés car déjà vus")}, " +
            (if (gaps.isEmpty()) "aucun trou" else "trous : ${gaps.joinToString("; ")}") + ")"
    }

    /** Appelé sur les fils d'OkHttp : il ne fait que passer les signaux à la boucle de connexion. */
    private class Listener(private val signals: SendChannel<Signal>) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            signals.trySend(Signal.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            signals.trySend(Signal.Message(text))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
            signals.trySend(Signal.Closed(code))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            signals.trySend(Signal.Closed(code))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            signals.trySend(Signal.Failed(response?.code, t::class.java.simpleName))
        }
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:47600"
        private const val NORMAL_CLOSURE = 1000
        private val HEALTH_TIMEOUT = 2.seconds
        private val FIRST_RETRY = 500.milliseconds
        private val LAST_RETRY = 5.seconds

        /** 0,5 s, 1 s, 2 s, 4 s, puis 5 s. */
        fun backoff(attempt: Int): Duration =
            (FIRST_RETRY * (1 shl (attempt - 1).coerceIn(0, 10))).coerceAtMost(LAST_RETRY)

        /** « 0 session », « 1 session », « 2 sessions » : le pluriel français commence à 2. */
        private fun count(n: Int, one: String, many: String) = "$n ${if (n < 2) one else many}"

        private fun reasonOf(code: Int?): UnreachableReason =
            if (code == 401) UnreachableReason.TOKEN_REFUSED else UnreachableReason.NOT_RESPONDING

        /** Le pont envoie un ping toutes les 20 s ; le nôtre repère une connexion à moitié morte. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(15.seconds.toJavaDuration())
            .connectTimeout(3.seconds.toJavaDuration())
            .build()
    }
}
