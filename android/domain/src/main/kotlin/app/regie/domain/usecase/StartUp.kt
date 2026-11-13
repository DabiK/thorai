package app.regie.domain.usecase

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
 * Ce qu'a fait le dernier lancement. [lastLaunch] est le résultat de la dernière demande de
 * démarrage du pont, absent si aucune n'a été nécessaire.
 */
data class StartUpReport(val wirelessDebugging: WirelessDebugging, val lastLaunch: LaunchResult?)

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
    private val restartInterval: Duration = 30.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val mutex = Mutex()
    /** Dernier démarrage effectivement envoyé. */
    private var lastStart: TimeMark? = null
    private val _report = MutableStateFlow<StartUpReport?>(null)
    val report: StateFlow<StartUpReport?> = _report.asStateFlow()

    suspend operator fun invoke(): StartUpReport = mutex.withLock {
        val wireless = access.restoreWirelessDebugging()
        val recentlyStarted = lastStart?.let { it.elapsedNow() < restartInterval } ?: false
        val mustStart = wireless == WirelessDebugging.TURNED_ON || (!recentlyStarted && !gateway.isBridgeUp())
        val launch = if (mustStart) {
            launcher.start().also { if (it == LaunchResult.SENT) lastStart = timeSource.markNow() }
        } else {
            _report.value?.lastLaunch
        }
        StartUpReport(wireless, launch).also { _report.value = it }
    }
}
