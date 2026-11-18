package app.regie.domain.port

import app.regie.domain.model.Agent
import app.regie.domain.model.AgentAuthentication

interface AgentAuthenticationGateway {
    suspend fun authentication(): List<AgentAuthentication> = emptyList()
    suspend fun startAuthentication(agent: Agent): AgentAuthentication = throw UnsupportedOperationException()
    suspend fun logoutAgent(agent: Agent): AgentAuthentication = throw UnsupportedOperationException()
    suspend fun cancelAuthentication(agent: Agent): AgentAuthentication = throw UnsupportedOperationException()
}
