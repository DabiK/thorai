package app.regie.overview

import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Où en est « Quitter ». */
sealed interface QuitStep {
    /** [busy] sessions travaillent encore : A confirme, B annule. */
    data class Confirm(val busy: Int) : QuitStep

    /** Les sessions et le pont s'arrêtent, Régie va se fermer. */
    data object Stopping : QuitStep
}

/**
 * État de la manette partagé par les deux écrans : carte qui a le focus, gâchettes maintenues,
 * et « Quitter » en cours.
 */
data class ControlState(
    val focusedIndex: Int = 0,
    /** La session qui a le focus : il la suit quand les cartes changent d'ordre. */
    val focusedId: SessionId? = null,
    val heldTriggers: Set<Trigger> = emptySet(),
    val quit: QuitStep? = null,
) {
    /**
     * La case qui a le focus parmi les cartes affichées ([visible], dans l'ordre) : celle de la
     * session suivie, sinon l'index ramené dans la liste (session disparue des cartes affichées).
     */
    fun focusIn(visible: List<SessionId>): Int =
        focusedId?.let(visible::indexOf)?.takeIf { it >= 0 }
            ?: focusedIndex.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
}

/**
 * Applique les commandes du PRD à la vue d'ensemble. Une seule instance par processus :
 * les deux activités lui envoient leurs entrées et observent [state].
 */
class OverviewController(
    private val overview: StateFlow<Overview>,
    private val dispatch: (OverviewAction) -> Unit,
) {
    private val grid = FocusGrid(columns = OverviewGrid.COLUMNS)
    private val _state = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = _state.asStateFlow()

    private val sessions get() = overview.value.sessions

    /** Seules les cartes affichées peuvent recevoir le focus. */
    private val visibleIds get() = sessions.take(OverviewGrid.VISIBLE_CARDS).map { it.id }
    private val visibleCount get() = visibleIds.size
    private val focused get() = sessions.getOrNull(currentFocus())

    /**
     * À chaque nouvelle vue d'ensemble : le focus suit sa session si elle a changé de place ; si
     * elle a quitté les cartes affichées, l'index est gardé, borné, et la carte qui s'y trouve est suivie.
     */
    fun syncFocus() {
        val visible = visibleIds
        _state.update { state ->
            val index = state.focusIn(visible)
            state.copy(focusedIndex = index, focusedId = visible.getOrNull(index))
        }
    }

    fun handle(input: GamepadInput) {
        if (_state.value.quit != null) return handleWhileQuitting(input)
        when (input) {
            is GamepadInput.Pressed -> onPressed(input.button)
            is GamepadInput.TriggerDown -> onTrigger(input.trigger, held = true)
            is GamepadInput.TriggerUp -> onTrigger(input.trigger, held = false)
            // Rien à faire défiler sur la vue d'ensemble : 4 cartes tiennent à l'écran.
            is GamepadInput.Scroll -> Unit
        }
    }

    /** Toucher le bandeau de validation sur l'écran du bas : comme R1. */
    fun openPendingApproval() {
        overview.value.firstPendingApproval?.let { dispatch(OverviewAction.OpenApproval(it.approval.id)) }
    }

    /**
     * « Quitter », au toucher seulement : le PRD ne lui donne pas de bouton de manette. Si une
     * session travaille encore, on demande confirmation.
     */
    fun requestQuit() {
        if (_state.value.quit != null) return
        val busy = overview.value.busyCount
        if (busy > 0) _state.update { it.copy(quit = QuitStep.Confirm(busy)) } else confirmQuit()
    }

    fun confirmQuit() {
        if (_state.value.quit == QuitStep.Stopping) return
        _state.update { it.copy(quit = QuitStep.Stopping) }
        dispatch(OverviewAction.Quit)
    }

    /** L'écran change : les gâchettes maintenues sont relâchées ici, une seule fois. */
    fun releaseTriggers() {
        _state.value.heldTriggers.forEach { onTrigger(it, held = false) }
    }

    fun cancelQuit() {
        if (_state.value.quit is QuitStep.Confirm) _state.update { it.copy(quit = null) }
    }

    /** Pendant la confirmation : A confirme, B annule, le reste attend. Une gâchette relâchée l'est toujours. */
    private fun handleWhileQuitting(input: GamepadInput) {
        when {
            input == GamepadInput.Pressed(Button.A) && _state.value.quit is QuitStep.Confirm -> confirmQuit()
            input == GamepadInput.Pressed(Button.B) -> cancelQuit()
            input is GamepadInput.TriggerUp -> onTrigger(input.trigger, held = false)
        }
    }

    private fun onPressed(button: Button) {
        when (button) {
            Button.UP -> moveFocus(Direction.UP)
            Button.DOWN -> moveFocus(Direction.DOWN)
            Button.LEFT -> moveFocus(Direction.LEFT)
            Button.RIGHT -> moveFocus(Direction.RIGHT)
            Button.L1 -> setFocus(grid.previous(currentFocus(), visibleCount))
            Button.R1 ->
                if (overview.value.firstPendingApproval != null) {
                    openPendingApproval()
                } else {
                    setFocus(grid.next(currentFocus(), visibleCount))
                }
            Button.A -> focused?.let { dispatch(OverviewAction.OpenSession(it.id)) }
            Button.X -> dispatch(OverviewAction.NewSession)
            Button.Y -> dispatch(OverviewAction.ShowSummary(focused?.id))
            Button.START -> dispatch(OverviewAction.PauseAll)
            // La vue d'ensemble est le premier écran : B n'a rien à annuler.
            Button.B -> Unit
        }
    }

    private fun onTrigger(trigger: Trigger, held: Boolean) {
        val before = _state.value.heldTriggers
        if ((trigger in before) == held) return
        _state.update { it.copy(heldTriggers = if (held) before + trigger else before - trigger) }
        dispatch(
            when (trigger) {
                Trigger.L2 -> if (held) OverviewAction.StartDictation(focused?.id) else OverviewAction.StopDictation
                Trigger.R2 -> if (held) OverviewAction.StartVoiceCommand else OverviewAction.StopVoiceCommand
            },
        )
    }

    private fun currentFocus() = _state.value.focusIn(visibleIds)

    private fun moveFocus(direction: Direction) = setFocus(grid.move(currentFocus(), direction, visibleCount))

    private fun setFocus(index: Int) = _state.update { it.copy(focusedIndex = index, focusedId = visibleIds.getOrNull(index)) }
}
