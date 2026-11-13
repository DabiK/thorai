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
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Passerelle factice : rejoue l'enregistrement d'une démo sur le vrai pont (`demo-run.jsonl`,
 * ticket 02), à son rythme d'origine divisé par [speed], puis garde l'état final. Elle passe par
 * le même décodage que la vraie passerelle ; les réponses sont aiguillées par les requêtes
 * enregistrées, comme la vraie le fait avec les siennes.
 *
 * Elle sert à développer l'interface sans console. Aucune commande ne part.
 */
class ReplayAgentGateway internal constructor(
    private val load: () -> List<RecordedFrame>,
    private val speed: Double,
) : AgentGateway {

    constructor(speed: Double = 1.0) : this(Fixtures::demoRun, speed)

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    private val _agents = MutableStateFlow<List<AgentVersion>>(emptyList())
    override val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()
    override val agents: StateFlow<List<AgentVersion>> = _agents.asStateFlow()
    override val connection: StateFlow<BridgeConnection> = MutableStateFlow(BridgeConnection.Connected)

    override suspend fun maintainConnection() {
        // Près de 500 Ko de JSON : lus hors du fil de l'interface.
        val frames = withContext(Dispatchers.Default) { load() }
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

        /** L'état à la fin de l'enregistrement, sans attendre. */
        fun finalState(frames: List<RecordedFrame> = Fixtures.demoRun()): BridgeState {
            val state = BridgeState()
            val requests = HashMap<Long, Pair<String, JsonObject>>()
            frames.forEach { replay(state, requests, it) }
            return state
        }
    }
}
