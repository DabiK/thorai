package app.regie.domain.usecase

import app.regie.domain.model.LaunchResult
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.BridgeLauncher

/** Comment le pont a été arrêté en quittant. */
enum class QuitOutcome {
    /** Le pont a reçu `bridge.shutdown`. */
    SHUTDOWN_SENT,

    /** Le pont n'était pas joignable : `regie-bridge-stop` est parti par RUN_COMMAND. */
    STOP_COMMAND_SENT,

    /** Ni l'un ni l'autre : le pont n'a pas pu être prévenu. */
    NOT_STOPPED,
}

/**
 * « Quitter » : arrête les sessions puis le pont. La confirmation, quand une session travaille
 * encore ([app.regie.domain.model.Overview.busyCount]), est demandée avant.
 */
class Quit(private val gateway: AgentGateway, private val launcher: BridgeLauncher) {
    suspend operator fun invoke(): QuitOutcome = when {
        gateway.shutdownBridge() -> QuitOutcome.SHUTDOWN_SENT
        launcher.stop() == LaunchResult.SENT -> QuitOutcome.STOP_COMMAND_SENT
        else -> QuitOutcome.NOT_STOPPED
    }
}
