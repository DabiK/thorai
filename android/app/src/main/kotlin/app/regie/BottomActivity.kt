package app.regie

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.display.ScreenMode
import app.regie.input.GamepadInput
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
        keepOpenOnSystemBack()

        setScreen(
            approval = {
                val review by container.approvalReview.collectAsStateWithLifecycle()
                val overview by container.overview.collectAsStateWithLifecycle()
                // Le menu de choix de la validation (ticket 47).
                app.regie.ui.game.GameApprovalBottom(review, overview, { container.handleInput(GamepadInput.Pressed(it)) },
                    { container.touchTrigger(app.regie.input.Trigger.L2, it) }, container::selectApproval)
            },
            overview = { overview, control -> OverviewBottomScreen(overview, control, callbacks) },
            session = { shown, overview ->
                val live by container.live.collectAsStateWithLifecycle()
                val control by container.sessionController.state.collectAsStateWithLifecycle()
                val draft by container.sessionController.draft
                val approval by container.inlineApproval.collectAsStateWithLifecycle()
                val settings by container.sessionSettings.collectAsStateWithLifecycle()
                // La maison (ticket 46) : le fil brut est toujours en bas, y compris en mode console (ticket 25).
                val neighbours = overview.neighboursOf(shown.id)
                app.regie.ui.game.GameSessionBottom(live, gone = neighbours == null, neighbours, control, draft, settings, threadScroll, sessionCallbacks,
                    approval, container::answerInline, container::dismissInlineApproval, container::selectSessionSetting)
            },
        )

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
