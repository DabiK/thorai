package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentAuthenticationGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** All confirmation, first-launch and refresh rules live behind the authentication port. */
class ReviewAgentAuthentication(private val gateway: AgentAuthenticationGateway, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(AgentAuthenticationReview())
    val state = mutable.asStateFlow()
    private var offered = false
    private var polling: Job? = null
    private var revision = 0
    private var refreshFailed = false
    private val attemptedLogins = mutableSetOf<Agent>()

    fun offerOnLaunch() {
        if (offered) return
        scope.launch {
            refreshNow()
            if (offered || state.value.agents.isEmpty()) return@launch
            offered = true
            val missing = state.value.agents.indexOfFirst { it.connected == false }
            if (missing >= 0 && !state.value.visible) {
                mutable.value = state.value.copy(visible = true, selected = missing)
                poll()
            }
        }
    }

    fun open() {
        if (state.value.visible) return
        mutable.value = state.value.copy(visible = true, notice = "")
        scope.launch { refreshNow() }
        poll()
    }

    private fun poll() {
        polling?.cancel()
        polling = scope.launch {
            while (state.value.visible) { delay(2_000); if (!state.value.busy) refreshNow() }
        }
    }

    fun refresh() {
        if (!state.value.busy) {
            if (!state.value.confirmingLogout) mutable.value = state.value.copy(notice = "")
            scope.launch { refreshNow() }
        }
    }
    private suspend fun refreshNow() {
        val version = revision
        try {
            val agents = gateway.authentication()
            if (revision == version) {
                agents.filter { it.phase in setOf(AuthenticationPhase.CONNECTED, AuthenticationPhase.DISCONNECTED, AuthenticationPhase.FAILED) }.forEach { attemptedLogins.remove(it.agent) }
                val previousAgent = state.value.current?.agent
                val selected = agents.indexOfFirst { it.agent == previousAgent }.takeIf { it >= 0 } ?: 0
                val targetChanged = previousAgent != agents.getOrNull(selected)?.agent
                mutable.value = state.value.copy(agents = agents, selected = selected,
                    confirmingLogout = state.value.confirmingLogout && !targetChanged, notice = when {
                    agents.isEmpty() -> "Connexion indisponible sur ce pont."
                    targetChanged -> ""
                    refreshFailed && !state.value.confirmingLogout -> ""
                    else -> state.value.notice
                })
                refreshFailed = false
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            if (revision == version) {
                refreshFailed = true
                mutable.value = state.value.copy(notice = "Pont injoignable. Actualise pour vérifier la connexion.", agents = state.value.agents.map { it.copy(url = null, code = null, connected = null, phase = AuthenticationPhase.UNKNOWN) })
            }
        }
    }

    fun select(index: Int) {
        if (state.value.busy || index !in state.value.agents.indices) return
        mutable.value = state.value.copy(selected = index, confirmingLogout = false, notice = "")
    }
    fun move(delta: Int) = select((state.value.selected + delta).coerceIn(0, (state.value.agents.size - 1).coerceAtLeast(0)))

    fun requestLogout() {
        if (state.value.busy || state.value.current?.connected != true) return
        mutable.value = state.value.copy(confirmingLogout = true, notice = "A pour déconnecter ce compte · B pour garder la connexion.")
    }

    fun confirm() {
        val review = state.value
        val agent = review.current ?: return
        if (review.busy) return
        if (agent.connected == true && !review.confirmingLogout) { requestLogout(); return }
        if (agent.pending) return
        revision++
        if (!review.confirmingLogout && agent.login.method == "device_code") attemptedLogins.add(agent.agent)
        mutable.value = review.copy(busy = true, notice = "")
        scope.launch {
            try {
                val result = if (review.confirmingLogout) gateway.logoutAgent(agent.agent) else gateway.startAuthentication(agent.agent)
                if (!result.pending) attemptedLogins.remove(result.agent)
                mutable.value = state.value.copy(agents = state.value.agents.map { if (it.agent == result.agent) result else it }, busy = false, confirmingLogout = false)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutable.value = state.value.copy(busy = false, confirmingLogout = false, notice = "Action non confirmée. Actualise. Si une session tourne, choisis Quitter puis rouvre Régie avant de changer de compte.")
            }
        }
    }

    fun submitKey(key: String, expectedAgent: Agent? = state.value.current?.agent) {
        val review = state.value
        val agent = review.current ?: return
        val provider = agent.login.provider ?: return
        if (review.busy || agent.agent != expectedAgent || agent.login.method != "api_key" || key.isBlank()) return
        revision++
        mutable.value = review.copy(busy = true, notice = "")
        scope.launch {
            try {
                val result = gateway.submitAgentKey(agent.agent, provider, key)
                mutable.value = state.value.copy(agents = state.value.agents.map { if (it.agent == result.agent) result else it }, busy = false)
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                mutable.value = state.value.copy(busy = false, notice = "Clé non confirmée. Actualise avant de réessayer.")
            }
        }
    }

    fun close() {
        val review = state.value
        if (review.busy) return
        if (review.confirmingLogout) { mutable.value = review.copy(confirmingLogout = false, notice = ""); return }
        revision++
        polling?.cancel()
        val cancel = (attemptedLogins + review.agents.filter { it.pending }.map { it.agent }).toSet()
        attemptedLogins.clear()
        mutable.value = AgentAuthenticationReview()
        scope.launch { for (agent in cancel) {
            try { gateway.cancelAuthentication(agent) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* A later status refresh verifies the result. */ }
        } }
    }
}
