package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.SessionNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Only decisions/endings: no text chunks or tool progress; no flood from historical sessions. */
class NotifySessions(
    gateway: AgentGateway,
    foreground: StateFlow<Boolean>,
    notifications: SessionNotifications,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            val watched = mutableMapOf<SessionId, Job>()
            gateway.sessions.collect { sessions ->
                val ids = sessions.map { it.id }.toSet()
                (watched.keys - ids).forEach { watched.remove(it)?.cancel(); notifications.cancel(it) }
                sessions.filter { it.active && it.id !in watched }.forEach { initial ->
                    watched[initial.id] = launch {
                        var previous = initial.status
                        var pending = false
                        combine(
                            gateway.sessions.map { list -> list.find { it.id == initial.id } }.distinctUntilChanged(),
                            gateway.thread(initial.id), foreground,
                        ) { session, thread, visible -> Triple(session, thread, visible) }.collect { (session, thread, visible) ->
                            if (session == null) return@collect
                            if (session.status != previous) {
                                val ended = previous.isBusy && !session.status.isBusy
                                val failed = session.status == SessionStatus.FAILED
                                pending = (ended || failed) && !visible
                                if (session.status.isBusy) notifications.cancel(session.id)
                                previous = session.status
                            }
                            if (pending && !visible && (thread.loaded || session.status == SessionStatus.FAILED)) {
                                val summary = WrittenSummary.of(session, thread)
                                val kind = when {
                                    session.status == SessionStatus.FAILED -> SessionNoticeKind.FAILED
                                    summary.question != null -> SessionNoticeKind.QUESTION
                                    else -> SessionNoticeKind.FINISHED
                                }
                                val title = when (kind) {
                                    SessionNoticeKind.FAILED -> "Session en échec"
                                    SessionNoticeKind.QUESTION -> "L’agent te pose une question"
                                    SessionNoticeKind.FINISHED -> "Session terminée"
                                }
                                val text = when (kind) {
                                    SessionNoticeKind.FAILED -> session.lastError ?: "Consulte le fil"
                                    SessionNoticeKind.QUESTION -> summary.question.orEmpty()
                                    // The agent's own last sentence: Régie adds no report (ticket 22).
                                    SessionNoticeKind.FINISHED -> thread.entries.filterIsInstance<ThreadEntry.AgentText>().lastOrNull()
                                        ?.let { LiveSession.lastSentence(RegieReport.strip(it.text)) } ?: "Consulte le fil."
                                }
                                if (notifications.show(SessionNotice(session.id, kind, title, "${session.project} · $text"))) pending = false
                            }
                        }
                    }
                }
            }
        }
    }
}
