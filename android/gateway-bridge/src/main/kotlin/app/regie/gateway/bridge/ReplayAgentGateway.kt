package app.regie.gateway.bridge

import app.regie.domain.model.AgentVersion
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Session
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Passerelle factice : rejoue l'enregistrement d'une démo sur le vrai pont (`demo-run.jsonl`,
 * ticket 02), à son rythme d'origine divisé par [speed], puis garde l'état final. Elle passe par
 * le même décodage que la vraie passerelle ; les réponses sont aiguillées par les requêtes
 * enregistrées, comme la vraie le fait avec les siennes.
 *
 * Les dates enregistrées sont décalées pour que la démo semble se dérouler maintenant : sans cela,
 * une session « En cours » afficherait l'ancienneté de l'enregistrement.
 *
 * Elle sert à développer l'interface sans console. Aucune commande ne part.
 */
class ReplayAgentGateway internal constructor(
    private val load: () -> List<RecordedFrame>,
    private val speed: Double,
    private val clock: Clock,
) : AgentGateway {

    constructor(speed: Double = 1.0) : this(Fixtures::demoRun, speed, Clock.System)

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    private val _agents = MutableStateFlow<List<AgentVersion>>(emptyList())
    override val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    override val agents: StateFlow<List<AgentVersion>> = _agents.asStateFlow()
    override val connection: StateFlow<BridgeConnection> = MutableStateFlow(BridgeConnection.Connected)

    override suspend fun maintainConnection() {
        // Près de 500 Ko de JSON : lus hors du fil de l'interface.
        val frames = withContext(Dispatchers.Default) { shiftedToNow(load(), clock.now()) }
        val state = BridgeState()
        val requests = HashMap<Long, Pair<String, JsonObject>>()
        var clock = frames.firstOrNull()?.atMillis ?: 0
        for (recorded in frames) {
            if (speed.isFinite()) delay(((recorded.atMillis - clock) / speed).milliseconds)
            clock = recorded.atMillis
            if (replay(state, requests, recorded)) {
                _sessions.value = state.sessions()
                _agents.value = state.agents
            }
        }
        awaitCancellation()
    }

    override suspend fun isBridgeUp() = true

    override suspend fun shutdownBridge() = true

    internal companion object {
        /** Rejoue une trame enregistrée ; vrai si l'état a changé. */
        fun replay(state: BridgeState, requests: MutableMap<Long, Pair<String, JsonObject>>, recorded: RecordedFrame): Boolean {
            val frame = recorded.frame
            if (!recorded.fromBridge) {
                val id = frame.long("id") ?: return false
                val method = frame.string("method") ?: return false
                // Les numéros de requête repartent de 1 à chaque connexion du client de démo.
                if (method == "hello") requests.clear()
                requests[id] = method to (frame.obj("params") ?: JsonObject(emptyMap()))
                return false
            }
            return when (val incoming = Frames.parse(frame)) {
                is Incoming.Event -> state.apply(incoming.event)
                is Incoming.Response -> {
                    val (method, params) = incoming.id?.let(requests::remove) ?: return false
                    val result = incoming.result ?: return false
                    state.onResponse(method, params, result)
                    true
                }
                null -> false
            }
        }

        private val DATES = setOf("at", "updatedAt", "createdAt", "startedAt")

        /**
         * Décale toutes les dates de l'enregistrement (`at`, `updatedAt`, `createdAt`, `startedAt`) de
         * `now − début`, où le début est la date du premier événement moins son délai dans
         * l'enregistrement : rejouée à partir de [now], chaque trame porte sa date d'arrivée.
         */
        fun shiftedToNow(frames: List<RecordedFrame>, now: Instant): List<RecordedFrame> {
            val origin = frames.firstOrNull()?.atMillis ?: return frames
            val start = frames.firstNotNullOfOrNull { recorded ->
                recorded.frame.obj("event")?.instant("at")?.let { it - (recorded.atMillis - origin).milliseconds }
            } ?: return frames
            val offset = now - start
            return frames.map { it.copy(frame = shift(it.frame, offset) as JsonObject) }
        }

        private fun shift(element: JsonElement, offset: Duration): JsonElement = when (element) {
            is JsonObject -> JsonObject(
                element.mapValues { (key, value) ->
                    val date = if (key in DATES) (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { runCatching { Instant.parse(it) }.getOrNull() } else null
                    if (date != null) JsonPrimitive((date + offset).toString()) else shift(value, offset)
                },
            )
            is JsonArray -> JsonArray(element.map { shift(it, offset) })
            else -> element
        }

        /** L'état à la fin de l'enregistrement, sans attendre. */
        fun finalState(frames: List<RecordedFrame> = Fixtures.demoRun()): BridgeState {
            val state = BridgeState()
            val requests = HashMap<Long, Pair<String, JsonObject>>()
            frames.forEach { replay(state, requests, it) }
            return state
        }
    }
}
