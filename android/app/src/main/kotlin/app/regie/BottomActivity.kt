package app.regie

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.display.ScreenMode
import app.regie.ui.screens.OverviewBottomScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Écran du bas (display 4) : la voix et les actions. Il se ferme en mode un seul écran. */
class BottomActivity : GamepadActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!container.topAlive.value) {
            finish()
            return
        }

        setScreen { overview, control -> OverviewBottomScreen(overview, control, callbacks) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(container.displays.mode, container.topAlive) { mode, topAlive -> belongsHere(mode, topAlive) }
                    .collect { if (!it) finish() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.bottomStarted.value = true
    }

    override fun onResume() {
        super.onResume()
        // Si Android a déplacé la tâche vers un autre écran (écran du bas retiré), on se retire.
        if (!belongsHere(container.displays.mode.value, container.topAlive.value)) finish()
    }

    override fun onStop() {
        container.bottomStarted.value = false
        super.onStop()
    }

    private fun belongsHere(mode: ScreenMode, topAlive: Boolean) =
        topAlive && mode is ScreenMode.Dual && mode.bottomDisplayId == display.displayId
}
