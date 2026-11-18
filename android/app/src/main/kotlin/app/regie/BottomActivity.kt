package app.regie

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.display.ScreenMode
import app.regie.input.GamepadInput
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
            overview = { overview, control ->
                // Le carnet du hameau (ticket 45) : la ligne choisie suit le coursier quand le haut le montre.
                val scene by container.village.scene.collectAsStateWithLifecycle()
                val topVisible by container.topStarted.collectAsStateWithLifecycle()
                val mapOpen by container.village.mapOpen.collectAsStateWithLifecycle()
                val mapStreet by container.village.mapStreet.collectAsStateWithLifecycle()
                val shownScene = scene
                // Pendant un chantier (ticket 48), la carte du hameau ; B la ferme et le carnet revient.
                if (mapOpen && topVisible && shownScene != null) app.regie.ui.game.GameVillageMap(shownScene, mapStreet, mapCallbacks)
                else app.regie.ui.game.GameOverviewBottom(scene, villageShown = topVisible, overview, control, setupLeft(overview), carnetCallbacks,
                    scrollVelocity = container.controller.scrollVelocity)
            },
            session = { shown, overview ->
                val live by container.live.collectAsStateWithLifecycle()
                val control by container.sessionController.state.collectAsStateWithLifecycle()
                val draft by container.sessionController.draft
                val approval by container.inlineApproval.collectAsStateWithLifecycle()
                val settings by container.sessionSettings.collectAsStateWithLifecycle()
                // La maison (ticket 46) : le fil brut est toujours en bas. Mode console : une autre appli
                // occupe le haut, le carnet nomme la session suivie (ticket 25).
                val topVisible by container.topStarted.collectAsStateWithLifecycle()
                val neighbours = overview.neighboursOf(shown.id)
                val session = live?.session?.takeIf { it.id == shown.id } ?: overview.sessions.firstOrNull { it.id == shown.id }
                // Vue jeu (ticket 49) : la session console, quand une autre appli occupe le haut, a la tour en
                // en-tête ; le carnet du 46 reste dessous, avec son fil, ses avis et son brouillon.
                if (!topVisible && session?.project == app.regie.domain.usecase.ConsoleSession.PROJECT) {
                    app.regie.ui.game.ConsoleSessionBottom(session, live, gone = neighbours == null, neighbours, control, draft, settings, threadScroll,
                        sessionCallbacks, approval, container::answerInline, container::dismissInlineApproval,
                        container::selectSessionSetting)
                } else app.regie.ui.game.GameSessionBottom(live, gone = neighbours == null, neighbours, control, draft, settings, threadScroll, sessionCallbacks,
                    approval, container::answerInline, container::dismissInlineApproval, container::selectSessionSetting,
                    console = !topVisible)
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(container.displays.mode, container.topAlive) { mode, topAlive -> belongsHere(mode, topAlive) }
                    .collect { if (!it) finish() }
            }
        }
    }

    /** La carte du hameau, au doigt : une rue touchée y envoie le coursier. */
    private val mapCallbacks by lazy {
        object : app.regie.game.ui.chantier.MapCallbacks {
            override fun onGoTo(street: Int) = container.village.goToStreet(street)
            override fun onMove(delta: Int) = container.village.moveOnMap(delta)
            override fun onClose() = container.village.closeMap()
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
