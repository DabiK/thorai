package app.regie.console

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import app.regie.domain.model.SetupPermission
import app.regie.domain.port.SetupMemory

/**
 * Ce que le parcours d'accueil (ticket 28) lit et ouvre sur la console : permissions, Termux,
 * service d'accessibilité, débogage sans fil, écrans des réglages. Rien n'est accordé à la place de
 * l'utilisateur : l'app ouvre l'écran où il fait le geste.
 */
class AndroidSetup(private val context: Context, private val consoleService: ComponentName) : SetupMemory {
    private val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    override val finished: Boolean get() = prefs.getBoolean(FINISHED, false)

    override fun finish() = prefs.edit { putBoolean(FINISHED, true) }

    fun termuxInstalled(): Boolean = TermuxBridgeLauncher.isInstalled(context)

    fun granted(permission: SetupPermission): Boolean = when (permission) {
        SetupPermission.RUN_COMMAND -> granted(TermuxBridgeLauncher.PERMISSION)
        SetupPermission.MICROPHONE -> granted(Manifest.permission.RECORD_AUDIO)
        SetupPermission.NOTIFICATIONS -> granted(Manifest.permission.POST_NOTIFICATIONS) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    /** `WRITE_SECURE_SETTINGS`, que l'utilisateur accorde par adb au bout de l'association. */
    fun androidAccess(): Boolean = granted(Manifest.permission.WRITE_SECURE_SETTINGS)

    fun wirelessDebugging(): Boolean = Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1

    /** Le service du mode console est activé dans Accessibilité, même s'il ne s'est pas encore relié. */
    fun consoleModeEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == consoleService }
    }

    fun openTermuxReleases(display: Int) = open(Intent(Intent.ACTION_VIEW, TERMUX_RELEASES.toUri()), display)

    fun openTermux(display: Int) {
        context.packageManager.getLaunchIntentForPackage(TERMUX)?.let { open(it, display) }
    }

    fun openAccessibility(display: Int) = open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), display)

    fun openAppInfo(display: Int) =
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)), display)

    /** Les options pour les développeurs, ou les réglages s'ils ne sont pas encore activés. */
    fun openDeveloperOptions(display: Int) {
        if (!open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), display)) open(Intent(Settings.ACTION_SETTINGS), display)
    }

    /** Des commandes à coller dans Termux : jamais de secret, seulement des commandes publiques. */
    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Ouvert sur l'écran tactile ([display]) : le geste de l'utilisateur s'y fait au doigt. */
    private fun open(intent: Intent, display: Int): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle())
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FINISHED = "finished"
        const val TERMUX = "com.termux"
        const val TERMUX_RELEASES = "https://github.com/termux/termux-app/releases"
        /** `Settings.Global.ADB_WIFI_ENABLED`, caché dans le SDK. */
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    }
}
