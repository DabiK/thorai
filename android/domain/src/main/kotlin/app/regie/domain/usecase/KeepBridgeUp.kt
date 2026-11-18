package app.regie.domain.usecase

import app.regie.domain.model.BridgeConnection
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tant que le pont ne répond pas, Régie le relance (« Régie relance le pont… ») : une tentative
 * de [StartUp] dès que la connexion se perd, puis une à chaque échéance (le délai que [StartUp]
 * impose entre deux démarrages), jusqu'à ce que la connexion change d'état.
 *
 * [canStart] : Régie peut démarrer le pont maintenant (un de ses écrans est visible, elle ne quitte
 * pas). Sinon on revérifie toutes les [idleCheck].
 */
class KeepBridgeUp(
    private val gateway: AgentGateway,
    private val startUp: StartUp,
    private val canStart: () -> Boolean,
    private val idleCheck: Duration = 5.seconds,
) {
    suspend fun run() {
        gateway.connection.collectLatest { connection ->
            if (connection !is BridgeConnection.Unreachable) return@collectLatest
            while (true) {
                val wait = if (canStart()) {
                    val report = startUp()
                    report.retryIn ?: startUp.restartInterval
                } else {
                    idleCheck
                }
                delay(wait)
            }
        }
    }
}
