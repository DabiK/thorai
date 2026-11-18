package app.regie.domain.model

enum class AuthenticationPhase { UNKNOWN, DISCONNECTED, STARTING, WAITING, CONNECTED, FAILED, MANUAL, KEY }

data class AgentLogin(val method: String = "device_code", val label: String = "Connecter", val help: String = "", val provider: String? = null)

data class AgentAuthentication(
    val agent: Agent,
    val connected: Boolean? = null,
    val account: String? = null,
    val phase: AuthenticationPhase = when (connected) { true -> AuthenticationPhase.CONNECTED; false -> AuthenticationPhase.DISCONNECTED; null -> AuthenticationPhase.UNKNOWN },
    val url: String? = null,
    val code: String? = null,
    val message: String? = null,
    val login: AgentLogin = AgentLogin(),
) {
    val pending: Boolean get() = phase == AuthenticationPhase.STARTING || phase == AuthenticationPhase.WAITING
}

data class AgentAuthenticationReview(
    val visible: Boolean = false,
    val agents: List<AgentAuthentication> = emptyList(),
    val selected: Int = 0,
    val busy: Boolean = false,
    val confirmingLogout: Boolean = false,
    val notice: String = "",
) {
    val current: AgentAuthentication? get() = agents.getOrNull(selected)
}
