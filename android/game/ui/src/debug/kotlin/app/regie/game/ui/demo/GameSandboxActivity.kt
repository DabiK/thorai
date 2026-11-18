package app.regie.game.ui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.theme.GameTheme

/**
 * Bac à sable de la vue jeu, en debug seulement, pour le banc : le hameau généré avec les atlas
 * de `assets/game/`. Extras : `screen` (`top`, `bottom`, `kit` ou `coursier`, ticket 44) et `sessions` (3 par défaut en
 * haut, 8 en bas).
 *
 * `adb shell am start -n app.regie/app.regie.game.ui.demo.GameSandboxActivity --display N --es screen bottom --ei sessions 8`
 */
class GameSandboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "top"
        val sessions = intent.getIntExtra("sessions", if (screen == "bottom") 8 else 3)
        setContent {
            GameTheme {
                val assets = rememberGameAssets()
                when (screen) {
                    "kit" -> DemoKitScreen()
                    "coursier" -> CourierSandbox(assets, sessions)
                    "bottom" -> DemoBottomScreen(assets, sessions = sessions)
                    else -> DemoTopScreen(assets, sessions = sessions)
                }
            }
        }
    }
}
