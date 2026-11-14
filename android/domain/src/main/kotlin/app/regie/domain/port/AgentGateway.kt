package app.regie.domain.port

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalReply
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Delivery
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Port vers les agents, à travers le pont (docs/protocole-pont.md). L'adapter réel parle au pont
 * par WebSocket ; la passerelle factice rejoue des enregistrements du vrai pont.
 */
interface AgentGateway {
    /** Toutes les demandes en attente, y compris plusieurs pour une même session. */
    val approvals: StateFlow<List<Approval>>

    /** Répond avec un identifiant de choix fourni par l’agent, sans renvoi automatique. */
    suspend fun answerApproval(id: ApprovalId, optionId: String): ApprovalReply

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

    /**
     * Le fil d'une session, en direct. L'observer charge le journal de la session (`session.events`)
     * s'il ne l'est pas encore ; jusque-là, le fil ne contient que ce qui est arrivé en direct.
     */
    fun thread(id: SessionId): Flow<SessionThread>

    /** Envoie un message à la session (`session.prompt`) et dit comment il est parti. */
    suspend fun sendMessage(id: SessionId, text: String): Delivery

    /** Interrompt le tour en cours de la session (`session.cancel`). Faux si le pont ne l'a pas fait. */
    suspend fun interrupt(id: SessionId): Boolean
}
