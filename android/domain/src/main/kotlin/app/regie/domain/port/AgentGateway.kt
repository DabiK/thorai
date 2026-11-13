package app.regie.domain.port

import app.regie.domain.model.AgentVersion
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Session
import kotlinx.coroutines.flow.StateFlow

/**
 * Port vers les agents, à travers le pont (docs/protocole-pont.md). L'adapter réel parle au pont
 * par WebSocket ; la passerelle factice rejoue des enregistrements du vrai pont.
 */
interface AgentGateway {
    /** Les sessions que le pont connaît, en cours et historique, dans un ordre quelconque. */
    val sessions: StateFlow<List<Session>>

    /** Versions de Claude et de Codex installées sur la console. */
    val agents: StateFlow<List<AgentVersion>>

    val connection: StateFlow<BridgeConnection>

    /** Se connecte au pont et s'y reconnecte seule, jusqu'à ce que l'appelant l'annule. */
    suspend fun maintainConnection()

    /** Le pont est-il démarré ? (`GET /health`, sans jeton.) */
    suspend fun isBridgeUp(): Boolean

    /**
     * Demande au pont d'interrompre les sessions puis de s'arrêter (`bridge.shutdown`).
     * Faux si le pont n'a pas pu l'entendre (pas connecté, pas de réponse).
     */
    suspend fun shutdownBridge(): Boolean
}
