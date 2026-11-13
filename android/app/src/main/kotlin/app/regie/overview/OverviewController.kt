package app.regie.overview

import app.regie.domain.model.Overview
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** État de la manette partagé par les deux écrans : carte qui a le focus et gâchettes maintenues. */
data class ControlState(
    val focusedIndex: Int = 0,
    val heldTriggers: Set<Trigger> = emptySet(),
) {
    /** Le focus ramené dans la liste, si des sessions ont disparu. */
    fun focusIn(count: Int): Int = focusedIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
}

/**
 * Applique les commandes du PRD à la vue d'ensemble. Une seule instance par processus :
 * les deux activités lui envoient leurs entrées et observent [state].
 */
class OverviewController(
    private val overview: StateFlow<Overview>,
    private val dispatch: (OverviewAction) -> Unit,
) {
    private val grid = FocusGrid(columns = 2)
    private val _state = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = _state.asStateFlow()

    private val sessions get() = overview.value.sessions
    private val focused get() = sessions.getOrNull(_state.value.focusIn(sessions.size))

    fun handle(input: GamepadInput) {
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

    private fun onPressed(button: Button) {
        when (button) {
            Button.UP -> moveFocus(Direction.UP)
            Button.DOWN -> moveFocus(Direction.DOWN)
            Button.LEFT -> moveFocus(Direction.LEFT)
            Button.RIGHT -> moveFocus(Direction.RIGHT)
            Button.L1 -> setFocus(grid.previous(currentFocus(), sessions.size))
            Button.R1 ->
                if (overview.value.firstPendingApproval != null) {
                    openPendingApproval()
                } else {
                    setFocus(grid.next(currentFocus(), sessions.size))
                }
            Button.A -> focused?.let { dispatch(OverviewAction.OpenSession(it.id)) }
            Button.X -> dispatch(OverviewAction.NewSession)
            Button.Y -> dispatch(OverviewAction.SpeakSummary(focused?.id))
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

    private fun currentFocus() = _state.value.focusIn(sessions.size)

    private fun moveFocus(direction: Direction) = setFocus(grid.move(currentFocus(), direction, sessions.size))

    private fun setFocus(index: Int) = _state.update { it.copy(focusedIndex = index) }
}
