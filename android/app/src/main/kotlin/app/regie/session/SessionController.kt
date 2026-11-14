package app.regie.session

import androidx.compose.runtime.mutableStateOf
import app.regie.domain.model.Delivery
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Ce que l'écran d'une session demande. La racine de composition appelle le cas d'usage. */
sealed interface SessionAction {
    /** L1 / R1 : passer à une session voisine. */
    data class Open(val id: SessionId) : SessionAction

    /** R1 : ouvrir la validation en attente. */
    data class OpenApproval(val id: app.regie.domain.model.ApprovalId) : SessionAction

    /** B : revenir à la vue d'ensemble. */
    data object Back : SessionAction

    data class Send(val id: SessionId, val text: String) : SessionAction
    data class Interrupt(val id: SessionId) : SessionAction
    data class ShowSummary(val id: SessionId) : SessionAction
    data class StartDictation(val id: SessionId) : SessionAction
    data object StopDictation : SessionAction
    data object StartVoiceCommand : SessionAction
    data object StopVoiceCommand : SessionAction
}

/** Un avis bref, à la place de la phrase sous le micro. */
sealed interface SessionNotice {
    /** X : l'écran du diff n'existe pas encore. */
    data object DiffUnavailable : SessionNotice

    data object Sending : SessionNotice

    data class Delivered(val delivery: Delivery) : SessionNotice

    data object Interrupting : SessionNotice

    /** [heard] : le pont a pris l'interruption. */
    data class Interrupted(val heard: Boolean) : SessionNotice
}

/** État de l'écran d'une session, partagé par les deux écrans de la console. */
data class SessionControlState(
    val heldTriggers: Set<Trigger> = emptySet(),
    /** Y : le point écrit est ouvert en haut. */
    val summaryOpen: Boolean = false,
    val dismissedSummary: String? = null,
    val notice: SessionNotice? = null,
) {
    fun showsSummary(live: LiveSession): Boolean = summaryOpen ||
        (!live.session.status.isBusy && dismissedSummary != "${live.session.id.value}:${live.session.updatedAt}")
}

/**
 * Applique les commandes du PRD à l'écran d'une session : L1 / R1 voisines, B retour, X diff,
 * Y point, Start interrompre, croix et stick droit pour faire défiler le fil, L2 / R2 maintenues.
 * Le texte de secours ([draft], tapé au doigt) ne part qu'avec A, ou le bouton « Envoyer ».
 */
class SessionController(
    private val live: StateFlow<LiveSession?>,
    private val overview: StateFlow<Overview>,
    /** La session affichée. */
    private val current: () -> SessionId?,
    private val dispatch: (SessionAction) -> Unit,
) {
    private val _state = MutableStateFlow(SessionControlState())
    val state: StateFlow<SessionControlState> = _state.asStateFlow()

    /** Le message de secours en cours de frappe. État Compose : le champ de texte le suit sans décalage. */
    val draft = mutableStateOf("")

    private val _scrollVelocity = MutableStateFlow(0f)

    /** Stick droit : vitesse de défilement, de -1 (vers le haut, le plus ancien) à 1 (vers la fin). */
    val scrollVelocity: StateFlow<Float> = _scrollVelocity.asStateFlow()

    private val _scrollSteps = MutableSharedFlow<Int>(extraBufferCapacity = 16)

    /** Croix : un pas de défilement, -1 vers le haut (le plus ancien), 1 vers la fin. */
    val scrollSteps: SharedFlow<Int> = _scrollSteps.asSharedFlow()

    fun handle(input: GamepadInput) {
        when (input) {
            is GamepadInput.Pressed -> onPressed(input.button)
            is GamepadInput.TriggerDown -> onTrigger(input.trigger, held = true)
            is GamepadInput.TriggerUp -> onTrigger(input.trigger, held = false)
            is GamepadInput.Scroll -> _scrollVelocity.value = input.velocity
        }
    }

    fun onDraftChange(text: String) {
        draft.value = text
    }

    /** A, ou « Envoyer » au doigt : le texte saisi part à la session affichée. */
    fun send() {
        val id = current() ?: return
        val text = draft.value.trim()
        if (text.isEmpty()) return
        draft.value = ""
        _state.update { it.copy(notice = SessionNotice.Sending) }
        dispatch(SessionAction.Send(id, text))
    }

    /** Réponse du pont à un envoi. Un message qui n'est pas parti revient dans le champ, s'il est vide. */
    fun onDelivered(id: SessionId, text: String, delivery: Delivery) {
        if (current() != id) return
        if (delivery.canRetry && draft.value.isEmpty()) draft.value = text
        _state.update { it.copy(notice = SessionNotice.Delivered(delivery)) }
    }

    fun onInterrupted(id: SessionId, heard: Boolean) {
        if (current() == id) _state.update { it.copy(notice = SessionNotice.Interrupted(heard)) }
    }

    /** L'avis a été lu (il s'efface seul après quelques secondes). */
    fun dismissNotice(notice: SessionNotice) {
        _state.update { if (it.notice == notice) it.copy(notice = null) else it }
    }

    /** Une autre session s'ouvre : rien de la précédente ne reste, pas même le texte en cours. */
    fun reset() {
        draft.value = ""
        _scrollVelocity.value = 0f
        _state.update { SessionControlState(heldTriggers = it.heldTriggers) }
    }

    /** L'écran change : les gâchettes maintenues sont relâchées ici, une seule fois. */
    fun releaseTriggers() {
        _state.value.heldTriggers.forEach { onTrigger(it, held = false) }
    }

    private fun onPressed(button: Button) {
        val id = current() ?: return
        val summaryOpen = live.value?.let { _state.value.showsSummary(it) } ?: _state.value.summaryOpen
        when (button) {
            Button.A -> send()
            Button.B -> if (summaryOpen) closeSummary() else dispatch(SessionAction.Back)
            Button.Y -> if (summaryOpen) {
                closeSummary()
            } else {
                openSummary()
                dispatch(SessionAction.ShowSummary(id))
            }
            Button.X -> _state.update { it.copy(notice = SessionNotice.DiffUnavailable) }
            Button.START -> if (live.value?.takeIf { it.session.id == id }?.canInterrupt == true) {
                _state.update { it.copy(notice = SessionNotice.Interrupting) }
                dispatch(SessionAction.Interrupt(id))
            }
            // R1 donne la priorité à la validation en attente.
            Button.L1 -> overview.value.neighboursOf(id)?.previous?.let { dispatch(SessionAction.Open(it.id)) }
            Button.R1 -> overview.value.firstPendingApproval?.let { dispatch(SessionAction.OpenApproval(it.approval.id)) }
                ?: overview.value.neighboursOf(id)?.next?.let { dispatch(SessionAction.Open(it.id)) }
            Button.UP -> _scrollSteps.tryEmit(-1)
            Button.DOWN -> _scrollSteps.tryEmit(1)
            Button.LEFT, Button.RIGHT -> Unit
        }
    }

    fun openSummary() = _state.update { it.copy(summaryOpen = true) }

    private fun closeSummary() = _state.update { state ->
        val shown = live.value?.session
        state.copy(summaryOpen = false, dismissedSummary = shown?.let { "${it.id.value}:${it.updatedAt}" })
    }

    private fun onTrigger(trigger: Trigger, held: Boolean) {
        val before = _state.value.heldTriggers
        if ((trigger in before) == held) return
        _state.update { it.copy(heldTriggers = if (held) before + trigger else before - trigger) }
        val action = when (trigger) {
            Trigger.L2 -> if (held) current()?.let(SessionAction::StartDictation) else SessionAction.StopDictation
            Trigger.R2 -> if (held) SessionAction.StartVoiceCommand else SessionAction.StopVoiceCommand
        }
        action?.let(dispatch)
    }
}
