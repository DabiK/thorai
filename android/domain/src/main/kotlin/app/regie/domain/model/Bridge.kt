package app.regie.domain.model

/** Pourquoi la passerelle ne joint pas le pont. */
enum class UnreachableReason {
    /** L'app n'a pas de jeton pour se présenter au pont. */
    TOKEN_MISSING,

    /** Le pont a refusé le jeton (401). */
    TOKEN_REFUSED,

    /** Le pont ne répond pas : arrêté, ou encore en train de démarrer. */
    NOT_RESPONDING,
}

/** La connexion de la passerelle au pont. */
sealed interface BridgeConnection {
    data object Connecting : BridgeConnection

    data object Connected : BridgeConnection

    data class Unreachable(val reason: UnreachableReason) : BridgeConnection
}

/** Débogage sans fil, dont dépend l'accès des agents à Android (ticket 03). */
enum class WirelessDebugging {
    ALREADY_ON,

    /** Il était coupé (redémarrage de la console) : Régie l'a rallumé. */
    TURNED_ON,

    /** Régie n'a pas la permission de le rallumer. */
    PERMISSION_MISSING,
}

/** Ce qu'a donné une demande de démarrage ou d'arrêt du pont (RUN_COMMAND de Termux). */
enum class LaunchResult {
    SENT,

    /** L'utilisateur n'a pas accordé à Régie le droit de lancer des commandes dans Termux. */
    PERMISSION_MISSING,

    /** Termux est absent, ou refuse la commande. */
    TERMUX_MISSING,
}

/** Ce que l'utilisateur doit savoir quand le pont est injoignable : la marche à suivre. */
enum class BridgeHint {
    GRANT_RUN_COMMAND,
    TERMUX_MISSING,
    TOKEN_MISSING,
    TOKEN_REFUSED,

    /** Rien à faire : Régie relance le pont et se reconnecte seule. */
    RESTARTING,
}

/** L'état du pont tel que l'écran le montre. */
sealed interface BridgeStatus {
    data object Connecting : BridgeStatus

    data object Connected : BridgeStatus

    data class Unreachable(val hint: BridgeHint) : BridgeStatus

    companion object {
        fun of(connection: BridgeConnection, lastLaunch: LaunchResult?): BridgeStatus = when (connection) {
            BridgeConnection.Connecting -> Connecting
            BridgeConnection.Connected -> Connected
            is BridgeConnection.Unreachable -> Unreachable(
                when (connection.reason) {
                    UnreachableReason.TOKEN_MISSING -> BridgeHint.TOKEN_MISSING
                    UnreachableReason.TOKEN_REFUSED -> BridgeHint.TOKEN_REFUSED
                    UnreachableReason.NOT_RESPONDING -> when (lastLaunch) {
                        LaunchResult.PERMISSION_MISSING -> BridgeHint.GRANT_RUN_COMMAND
                        LaunchResult.TERMUX_MISSING -> BridgeHint.TERMUX_MISSING
                        LaunchResult.SENT, null -> BridgeHint.RESTARTING
                    }
                },
            )
        }
    }
}
