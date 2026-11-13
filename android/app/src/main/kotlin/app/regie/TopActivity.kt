package app.regie

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.display.ScreenMode
import app.regie.ui.screens.OverviewTopScreen
import kotlinx.coroutines.launch

/** Écran du haut (display 0). Il lance l'écran du bas quand celui-ci est allumé. */
class TopActivity : GamepadActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDebugExtras(intent)
        container.topAlive.value = true

        setScreen { overview, control ->
            val mode by container.displays.mode.collectAsStateWithLifecycle()
            OverviewTopScreen(overview, control, singleScreen = mode == ScreenMode.Single, callbacks = callbacks)
        }

        lifecycleScope.launch {
            // Repris à chaque retour au premier plan et à chaque changement d'écran.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.displays.mode.collect { mode ->
                    if (mode is ScreenMode.Dual && !container.bottomStarted.value) launchBottom(mode.bottomDisplayId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugExtras(intent)
    }

    override fun onDestroy() {
        if (isFinishing) container.topAlive.value = false
        super.onDestroy()
    }

    private fun launchBottom(displayId: Int) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        startActivity(
            Intent(this, BottomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            options.toBundle(),
        )
    }

    /** En debug seulement : `--ez force_single_screen true` force le mode un seul écran. */
    private fun applyDebugExtras(intent: Intent) {
        if (BuildConfig.DEBUG && intent.hasExtra(EXTRA_FORCE_SINGLE_SCREEN)) {
            container.displays.forceSingleScreen(intent.getBooleanExtra(EXTRA_FORCE_SINGLE_SCREEN, false))
        }
    }

    companion object {
        const val EXTRA_FORCE_SINGLE_SCREEN = "force_single_screen"
    }
}
