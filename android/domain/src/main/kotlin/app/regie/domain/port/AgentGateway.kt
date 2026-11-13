package app.regie.domain.port

import app.regie.domain.model.SessionEvent
import kotlinx.coroutines.flow.Flow

/** Port vers les agents. L'adapter réel parle au pont (ticket 05) ; la passerelle factice rejoue la maquette. */
interface AgentGateway {
    val events: Flow<SessionEvent>
}
