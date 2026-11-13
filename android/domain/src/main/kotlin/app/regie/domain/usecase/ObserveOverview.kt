package app.regie.domain.usecase

import app.regie.domain.model.Overview
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

/** Construit la vue d'ensemble à partir des événements de la passerelle. */
class ObserveOverview(private val gateway: AgentGateway) {
    operator fun invoke(): Flow<Overview> = gateway.events.scan(Overview.EMPTY) { overview, event -> overview.apply(event) }
}
