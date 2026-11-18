package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Message volant (ticket 25). Quand Régie n'est pas visible sur l'écran du haut ([away]), un message
 * terminé de l'agent, sa question ou une validation qu'il demande devient une carte ([card]).
 *
 * - Jamais un fragment en cours de flux : un texte est terminé quand une autre entrée le suit dans le
 *   fil, ou quand le tour est fini.
 * - Jamais l'historique : ce qui est déjà là au premier chargement du fil, ou arrivé pendant que Régie
 *   était visible, ne fait pas de carte.
 * - La carte s'efface seule ([FloatingKind.shownFor]), avec [dismiss] (B), dès que Régie revient, ou
 *   quand sa validation est réglée ailleurs. [pin] la garde tant qu'on y répond.
 * - Une carte plus importante encore affichée n'est pas remplacée (validation, puis question, puis
 *   message) ; à importance égale, la plus récente gagne. Une carte épinglée ne cède qu'à une carte
 *   plus importante : une validation demandée pendant qu'on répond s'affiche quand même.
 * - Une validation qui n'a pas pu s'afficher attend : elle passe dès que la carte en cours s'en va.
 */
class FloatingMessages(
    private val gateway: AgentGateway,
    private val away: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow<FloatingCard?>(null)
    val card: StateFlow<FloatingCard?> = mutable.asStateFlow()
    private var timer: Job? = null
    private var pinned = false

    /** La carte s'en va : une validation restée en attente peut passer. */
    private val left = MutableStateFlow(0)

    init {
        scope.launch { away.collect { if (!it) dismiss() } }
        scope.launch { watchApprovals() }
        scope.launch {
            val watched = mutableMapOf<SessionId, Job>()
            gateway.sessions.collect { sessions ->
                val ids = sessions.map { it.id }.toSet()
                (watched.keys - ids).forEach { watched.remove(it)?.cancel() }
                sessions.filter { it.active && it.id !in watched }.forEach { session ->
                    watched[session.id] = launch { watchMessages(session.id) }
                }
            }
        }
    }

    /** B : la carte s'efface. */
    fun dismiss() {
        pinned = false
        timer?.cancel()
        if (mutable.value != null) {
            mutable.value = null
            left.value++
        }
    }

    /**
     * On répond depuis la carte : elle reste jusqu'à [release], sans céder à une carte moins
     * importante. Retourne la clé de la carte épinglée.
     */
    fun pin(): String? = mutable.value?.key?.also { pinned = true }

    /** La réponse est finie : la carte épinglée s'en va, pas une carte arrivée depuis. */
    fun release(key: String?) {
        if (key != null && mutable.value?.key == key) dismiss()
    }

    private suspend fun watchMessages(id: SessionId) {
        var seen: MutableSet<Long>? = null
        combine(
            gateway.sessions.map { list -> list.find { it.id == id } }.distinctUntilChanged(),
            gateway.thread(id), away,
        ) { session, thread, isAway -> Triple(session, thread, isAway) }.collect { (session, thread, isAway) ->
            if (session == null) return@collect
            val done = finishedMessages(session, thread)
            val known = seen
            if (known == null) {
                // The first loaded thread is history: it never pops a card.
                if (thread.loaded) seen = done.map { it.id }.toMutableSet()
                return@collect
            }
            val fresh = done.filter { it.id !in known }
            known += fresh.map { it.id }
            val last = fresh.lastOrNull() ?: return@collect
            if (!isAway) return@collect
            val text = RegieReport.strip(last.text)
            val question = !session.status.isBusy && last.id == done.last().id && thread.entries.last().id == last.id &&
                LiveSession.lastSentence(text)?.endsWith('?') == true
            show(FloatingCard("message:${id.value}:${last.id}", id, session.agent, session.project,
                if (question) FloatingKind.QUESTION else FloatingKind.MESSAGE, FloatingCard.excerpt(text)))
        }
    }

    private suspend fun watchApprovals() {
        // Seen inside Régie or already shown as a card: never shown again.
        var seen: MutableSet<ApprovalId>? = null
        combine(gateway.approvals, away, left) { approvals, isAway, _ -> approvals to isAway }.collect { (approvals, isAway) ->
            val ids = approvals.map { it.id }.toSet()
            mutable.value?.approval?.let { if (it.id !in ids) dismiss() }
            val known = seen
            if (known == null || !isAway) {
                seen = (known ?: mutableSetOf()).apply { addAll(ids) }
                return@collect
            }
            val fresh = approvals.lastOrNull { it.id !in known } ?: return@collect
            val session = gateway.sessions.value.find { it.id == fresh.sessionId } ?: return@collect
            val card = FloatingCard("approval:${fresh.id.value}", session.id, session.agent, session.project,
                FloatingKind.APPROVAL, FloatingCard.excerpt(fresh.subject ?: fresh.reason.orEmpty()), fresh)
            // An approval that cannot show yet waits for the current card to leave.
            if (show(card)) known += fresh.id
        }
    }

    private fun show(next: FloatingCard): Boolean {
        val current = mutable.value
        if (current != null && current.kind.priority > next.kind.priority) return false
        if (current != null && pinned && current.kind.priority >= next.kind.priority) return false
        pinned = false
        mutable.value = next
        timer?.cancel()
        timer = scope.launch {
            delay(next.kind.shownFor)
            if (mutable.value == next && !pinned) {
                mutable.value = null
                left.value++
            }
        }
        return true
    }

    companion object {
        /** The agent's texts that are over: another entry follows them, or the turn has ended. */
        fun finishedMessages(session: Session, thread: SessionThread): List<ThreadEntry.AgentText> {
            val entries = thread.entries
            return entries.withIndex()
                .filter { (index, entry) -> entry is ThreadEntry.AgentText && (index < entries.lastIndex || !session.status.isBusy) }
                .map { it.value as ThreadEntry.AgentText }
                .filter { RegieReport.strip(it.text).isNotBlank() }
        }
    }
}
