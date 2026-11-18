package app.regie.game.ui.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.theme.GameTheme

/**
 * Bac à sable de la vue jeu, en debug seulement, pour le banc : la scène de démonstration avec
 * les atlas présents dans `assets/game/`. L'extra `screen=bottom` montre le kit pixel.
 *
 * `adb shell am start -n app.regie/app.regie.game.ui.demo.GameSandboxActivity [--display N --es screen bottom]`
 */
class GameSandboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bottom = intent.getStringExtra("screen") == "bottom"
        setContent {
            GameTheme {
                if (bottom) DemoBottomScreen() else DemoTopScreen(rememberGameAssets())
            }
        }
    }
}
