package app.regie.console

import android.content.Context
import app.regie.domain.port.AndroidAccess
import app.regie.domain.port.BridgeLauncher
import app.regie.domain.port.BridgePairing

/**
 * Termux s'il est installé, au moment de chaque demande ; sinon le pont du Mac (banc d'essai).
 * Le parcours d'accueil (ticket 28) fait installer Termux pendant que l'app tourne : le pont doit
 * alors démarrer sans rouvrir l'app.
 */
class ConsoleBridge(private val context: Context) : BridgeLauncher, BridgePairing, AndroidAccess {
    private val termux = TermuxBridgeLauncher(context)
    private val wireless = WirelessDebuggingAccess(context)
    private val external = ExternalBridge()

    val hasTermux: Boolean get() = TermuxBridgeLauncher.isInstalled(context)

    override fun start() = if (hasTermux) termux.start() else external.start()

    override fun stop() = if (hasTermux) termux.stop() else external.stop()

    override fun configure() = if (hasTermux) termux.configure() else external.start()

    override fun restoreWirelessDebugging() = if (hasTermux) wireless.restoreWirelessDebugging() else external.restoreWirelessDebugging()
}
