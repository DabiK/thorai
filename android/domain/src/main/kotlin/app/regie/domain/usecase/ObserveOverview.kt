package app.regie.domain.usecase

import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Construit la vue d'ensemble à partir de la passerelle et du dernier lancement. */
class ObserveOverview(private val gateway: AgentGateway, private val startUp: Flow<StartUpReport?>) {
    operator fun invoke(): Flow<Overview> =
        combine(gateway.sessions, gateway.agents, gateway.connection, startUp) { sessions, agents, connection, report ->
            Overview.of(
                sessions = sessions,
                agents = agents,
                bridge = BridgeStatus.of(connection, report?.launch),
                androidAccessMissing = report?.wirelessDebugging == WirelessDebugging.PERMISSION_MISSING,
            )
        }
}
