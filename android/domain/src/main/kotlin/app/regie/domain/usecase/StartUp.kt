package app.regie.domain.usecase

import app.regie.domain.model.BridgeConnection
import app.regie.domain.port.BridgePairing
import app.regie.domain.model.LaunchResult
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.AndroidAccess
import app.regie.domain.port.BridgeLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Ce qu'a fait ce lancement-ci. [bridgeUp] : le pont répondait déjà. [launch] : le résultat de la
 * demande de démarrage envoyée cette fois, absent si aucune n'est partie (pont déjà là, ou
 * démarrage demandé il y a peu). [retryIn] : le pont ne répond pas mais un démarrage vient d'être
 * demandé ; un nouveau pourra partir dans ce délai.
 */
data class StartUpReport(
    val wirelessDebugging: WirelessDebugging,
    val bridgeUp: Boolean,
    val launch: LaunchResult?,
    val retryIn: Duration? = null,
)

/**
 * Au lancement de Régie, et à chaque retour au premier plan (rien ne démarre au boot) :
 * 1. le débogage sans fil est rallumé s'il est coupé, pour que les agents retrouvent Android ;
 * 2. le pont est démarré s'il ne répond pas, ou si le débogage sans fil vient d'être rallumé :
 *    `regie-bridge-start` relie alors les agents au nouveau port, même si le pont tournait déjà.
 *
 * Un démarrage déjà demandé il y a moins de [restartInterval] n'est pas redemandé : le pont met
 * quelques secondes à répondre.
 */
class StartUp(
    private val gateway: AgentGateway,
    private val access: AndroidAccess,
    private val launcher: BridgeLauncher,
    val restartInterval: Duration = 30.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val pairing: BridgePairing? = null,
) {
    private val mutex = Mutex()
    /** Dernier démarrage effectivement envoyé. */
    private var lastStart: TimeMark? = null
    private val _report = MutableStateFlow<StartUpReport?>(null)
    val report: StateFlow<StartUpReport?> = _report.asStateFlow()

    suspend operator fun invoke(): StartUpReport = mutex.withLock {
        val wireless = access.restoreWirelessDebugging()
        val up = gateway.isBridgeUp()
        val wait = lastStart?.let { restartInterval - it.elapsedNow() }?.takeIf { it.isPositive() }
        val mustPair = pairing != null && gateway.connection.value != BridgeConnection.Connected && wait == null
        val mustStart = wireless == WirelessDebugging.TURNED_ON || (!up && wait == null)
        val launch = when {
            mustPair -> pairing.configure()
            mustStart -> launcher.start()
            else -> null
        }?.also { if (it == LaunchResult.SENT) lastStart = timeSource.markNow() }
        StartUpReport(wireless, up, launch, retryIn = wait.takeIf { !up && launch == null })
            .also { _report.value = it }
    }
}
