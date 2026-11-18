package app.regie.domain.port

import app.regie.domain.model.LaunchResult
import app.regie.domain.model.WirelessDebugging

/** Réglages d'Android dont dépend l'accès des agents à la console (ticket 03). */
interface AndroidAccess {
    /** Rallume le débogage sans fil s'il est coupé, par exemple après un redémarrage de la console. */
    fun restoreWirelessDebugging(): WirelessDebugging
}

/** Démarre et arrête le pont dans Termux (RUN_COMMAND, docs/protocole-pont.md « Démarrage et arrêt »). */
interface BridgeLauncher {
    /** `regie-bridge-start` : démarre le pont s'il ne tourne pas, et relie à nouveau les agents à Android. */
    fun start(): LaunchResult

    /** `regie-bridge-stop` : arrête les sessions, puis le pont. */
    fun stop(): LaunchResult
}

/** Installe le secret partagé et lance le pont, sans exposer le jeton à l'interface. */
fun interface BridgePairing {
    fun configure(): LaunchResult
}
