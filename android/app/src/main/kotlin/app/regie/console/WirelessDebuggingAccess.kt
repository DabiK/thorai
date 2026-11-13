package app.regie.console

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.port.AndroidAccess

/**
 * Rallume le débogage sans fil, coupé à chaque redémarrage de la console : les agents s'en
 * servent pour agir sur Android (ticket 03). Il faut `WRITE_SECURE_SETTINGS`, que l'utilisateur
 * accorde une fois depuis le Mac (`adb shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS`).
 */
class WirelessDebuggingAccess(private val context: Context) : AndroidAccess {

    override fun restoreWirelessDebugging(): WirelessDebugging {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return WirelessDebugging.PERMISSION_MISSING
        }
        val resolver = context.contentResolver
        if (Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0) == 1) return WirelessDebugging.ALREADY_ON
        return try {
            if (Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 1)) WirelessDebugging.TURNED_ON else WirelessDebugging.PERMISSION_MISSING
        } catch (_: SecurityException) {
            WirelessDebugging.PERMISSION_MISSING
        }
    }

    private companion object {
        /** `Settings.Global.ADB_WIFI_ENABLED`, caché dans le SDK. */
        const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    }
}
