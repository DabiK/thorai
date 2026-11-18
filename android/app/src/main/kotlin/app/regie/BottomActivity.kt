package app.regie

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.display.ScreenMode
import app.regie.input.GamepadInput
import app.regie.ui.screens.ApprovalBottomScreen
import app.regie.ui.screens.OverviewBottomScreen
import app.regie.ui.screens.SessionBottomScreen
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
                ApprovalBottomScreen(review, { container.handleInput(GamepadInput.Pressed(it)) })
            },
            overview = { overview, control ->
                val console by container.consoleService.collectAsStateWithLifecycle()
                OverviewBottomScreen(overview, control, callbacks, consoleOff = !console && !container.replaying)
            },
            session = { shown, overview ->
                val live by container.live.collectAsStateWithLifecycle()
                val control by container.sessionController.state.collectAsStateWithLifecycle()
                val draft by container.sessionController.draft
                val approval by container.inlineApproval.collectAsStateWithLifecycle()
                val settings by container.sessionSettings.collectAsStateWithLifecycle()
                // Mode console : une autre appli occupe le haut, le fil compact passe en bas (ticket 25).
                val topVisible by container.topStarted.collectAsStateWithLifecycle()
                val session = live?.session?.takeIf { it.id == shown.id } ?: overview.sessions.firstOrNull { it.id == shown.id }
                // Vue jeu (ticket 49) : la session console, quand une autre appli occupe le haut, est la tour.
                if (!topVisible && !settings.visible && session?.project == app.regie.domain.usecase.ConsoleSession.PROJECT) {
                    app.regie.game.ui.theme.GameTheme { ConsoleTower(session, live, approval) }
                } else SessionBottomScreen(live, overview.neighboursOf(shown.id), control, draft, sessionCallbacks, approval = approval,
                    onDecision = container::answerInline, onNextApproval = container::nextInlineApproval, onDismissApproval = container::dismissInlineApproval,
                    settings = settings, onSelectSetting = container::selectSessionSetting, console = threadScroll.takeUnless { topVisible })
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(container.displays.mode, container.topAlive) { mode, topAlive -> belongsHere(mode, topAlive) }
                    .collect { if (!it) finish() }
            }
        }
    }

    /** La tour de la console (ticket 49) : l'habitant, l'état, le fil compact et les touches de la session. */
    @androidx.compose.runtime.Composable
    private fun ConsoleTower(session: app.regie.domain.model.Session, live: app.regie.domain.model.LiveSession?, review: app.regie.domain.usecase.ApprovalReview) {
        val assets = app.regie.game.ui.atlas.rememberGameAssets()
        val request = review.request?.takeIf { it.sessionId == session.id && review.pending && !review.sending }
        app.regie.game.ui.console.ConsoleTowerScreen(live, session, assets, approval = request,
            scroll = app.regie.game.ui.console.TowerScroll(container.sessionController.scrollSteps, container.sessionController.scrollVelocity),
            callbacks = app.regie.game.ui.console.TowerCallbacks(
                press = { key -> towerButton(key)?.let { container.handleInput(GamepadInput.Pressed(it)) } },
                hold = { container.touchTrigger(app.regie.input.Trigger.L2, it) },
            ))
    }

    private fun towerButton(key: String): app.regie.input.Button? = when (key) {
        "A" -> app.regie.input.Button.A
        "B" -> app.regie.input.Button.B
        "X" -> app.regie.input.Button.X
        "Start" -> app.regie.input.Button.START
        "→" -> app.regie.input.Button.RIGHT
        else -> null
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
