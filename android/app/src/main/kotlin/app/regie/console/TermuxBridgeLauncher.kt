package app.regie.console

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.regie.domain.model.LaunchResult
import app.regie.domain.port.BridgePairing
import app.regie.domain.port.BridgeLauncher
import java.io.File

/**
 * Démarre et arrête le pont par l'intent RUN_COMMAND de Termux (docs/protocole-pont.md,
 * « Démarrage et arrêt »). Les scripts sont installés par `thor/bridge/install.sh`.
 */
class TermuxBridgeLauncher(
    private val context: Context,
    private val transfer: (String, Int) -> String = BridgeTokenTransfer()::open,
) : BridgeLauncher, BridgePairing {

    override fun start() = run("regie-bridge-start")

    override fun stop() = run("regie-bridge-stop")

    override fun configure(): LaunchResult {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return LaunchResult.PERMISSION_MISSING
        val uid = try {
            context.packageManager.getPackageUid(TERMUX, PackageManager.PackageInfoFlags.of(0))
        } catch (_: PackageManager.NameNotFoundException) {
            return LaunchResult.TERMUX_MISSING
        }
        return try {
            val token = BridgeTokenFile(File(context.filesDir, "bridge-token")).readOrCreate()
            // Le paquet nomme le jeton de cette app sur le pont, à côté de celui des autres (ADR 22).
            run("regie-bridge-configure", arrayOf(transfer(token, uid), context.packageName))
        } catch (_: java.io.IOException) {
            LaunchResult.TERMUX_MISSING
        } catch (_: android.system.ErrnoException) {
            LaunchResult.TERMUX_MISSING
        }
    }

    private fun run(script: String, arguments: Array<String>? = null): LaunchResult {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return LaunchResult.PERMISSION_MISSING
        val intent = Intent(ACTION)
            .setClassName(TERMUX, SERVICE)
            .putExtra(EXTRA_PATH, "$TERMUX_HOME/bin/$script")
            .putExtra(EXTRA_BACKGROUND, true)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments)
        return try {
            val component = try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                // Régie n'est plus au premier plan (Quitter) : Android exige un service de premier plan.
                context.startForegroundService(intent)
            }
            if (component == null) LaunchResult.TERMUX_MISSING else LaunchResult.SENT
        } catch (_: SecurityException) {
            LaunchResult.PERMISSION_MISSING
        } catch (_: IllegalStateException) {
            LaunchResult.TERMUX_MISSING
        }
    }

    companion object {
        fun isInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(TERMUX, PackageManager.PackageInfoFlags.of(0))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        /** Permission de Termux, demandée à l'utilisateur au premier lancement. */
        const val PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val TERMUX = "com.termux"
        private const val SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION = "com.termux.RUN_COMMAND"
        private const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        /** Le dossier de Termux, pas celui de Régie : le chemin est fixé par le protocole du pont. */
        @SuppressLint("SdCardPath")
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    }
}
