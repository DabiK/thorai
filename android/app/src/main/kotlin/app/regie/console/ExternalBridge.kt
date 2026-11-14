package app.regie.console

import app.regie.domain.model.LaunchResult
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.port.AndroidAccess
import app.regie.domain.port.BridgeLauncher

/** Pont lancé par le Mac, joint par adb reverse. Quitter utilise toujours bridge.shutdown. */
class ExternalBridge : BridgeLauncher, AndroidAccess {
    override fun start() = LaunchResult.EXTERNAL
    override fun stop() = LaunchResult.EXTERNAL
    override fun restoreWirelessDebugging() = WirelessDebugging.NOT_REQUIRED
}
