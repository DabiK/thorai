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
 * État de la manette partagé par les deux écrans : ligne qui a le focus, gâchettes maintenues,
 * et « Quitter » en cours.
 */
data class ControlState(
    val focusedIndex: Int = 0,
    /** La session qui a le focus : il la suit quand les lignes changent d'ordre. */
    val focusedId: SessionId? = null,
    val heldTriggers: Set<Trigger> = emptySet(),
    val quit: QuitStep? = null,
) {
    /**
     * La ligne qui a le focus parmi les sessions ([visible], dans l'ordre) : celle de la session
     * suivie, sinon l'index ramené dans la liste (session disparue).
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
    private val list = FocusList()
    private val _state = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = _state.asStateFlow()

    private val _scrollVelocity = MutableStateFlow(0f)

    /** Stick droit : la liste défile sans déplacer le focus ; la croix l'y ramène. */
    val scrollVelocity: StateFlow<Float> = _scrollVelocity.asStateFlow()

    private val sessions get() = overview.value.sessions

    /** Toutes les lignes de la liste peuvent recevoir le focus. */
    private val visibleIds get() = sessions.map { it.id }
    private val visibleCount get() = visibleIds.size
    private val focused get() = sessions.getOrNull(currentFocus())

    /**
     * À chaque nouvelle vue d'ensemble : le focus suit sa session si elle a changé de place ; si
     * elle a disparu, l'index est gardé, borné, et la ligne qui s'y trouve est suivie.
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
            is GamepadInput.Scroll -> _scrollVelocity.value = input.velocity
        }
    }

    /**
     * La session choisie ailleurs que dans la liste : celle du coursier, sur le hameau (ticket 45).
     * L2 (dictée), R2 (ordre vocal) et le mode console la visent comme la ligne qui a le focus.
     */
    fun focus(id: SessionId) {
        val index = visibleIds.indexOf(id)
        if (index >= 0 && _state.value.focusedId != id) setFocus(index)
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

    /**
     * Pendant la confirmation : A confirme, B annule, le reste attend. Une gâchette relâchée l'est
     * toujours ; la liste cesse de défiler.
     */
    private fun handleWhileQuitting(input: GamepadInput) {
        when {
            input is GamepadInput.Scroll -> _scrollVelocity.value = 0f
            input == GamepadInput.Pressed(Button.A) && _state.value.quit is QuitStep.Confirm -> confirmQuit()
            input == GamepadInput.Pressed(Button.B) -> cancelQuit()
            input is GamepadInput.TriggerUp -> onTrigger(input.trigger, held = false)
        }
    }

    private fun onPressed(button: Button) {
        when (button) {
            Button.UP -> setFocus(list.move(currentFocus(), -1, visibleCount))
            Button.DOWN -> setFocus(list.move(currentFocus(), 1, visibleCount))
            // Une seule colonne : gauche et droite n'ont rien à atteindre.
            Button.LEFT, Button.RIGHT -> Unit
            Button.L1 -> setFocus(list.previous(currentFocus(), visibleCount))
            Button.R1 ->
                if (overview.value.firstPendingApproval != null) {
                    openPendingApproval()
                } else {
                    setFocus(list.next(currentFocus(), visibleCount))
                }
            Button.A -> focused?.let { dispatch(OverviewAction.OpenSession(it.id)) }
            Button.X -> dispatch(OverviewAction.NewSession)
            // Le point Régie est retiré (ticket 22) ; Y reprend la session console (ticket 25).
            Button.Y -> dispatch(OverviewAction.ConsoleSession)
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

    private fun setFocus(index: Int) = _state.update { it.copy(focusedIndex = index, focusedId = visibleIds.getOrNull(index)) }
}
