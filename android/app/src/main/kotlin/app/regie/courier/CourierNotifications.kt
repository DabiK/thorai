package app.regie.courier

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionNotice
import app.regie.domain.model.SessionNoticeKind
import app.regie.domain.port.ApprovalNotifications
import app.regie.domain.port.SessionNotifications
import app.regie.game.engine.courier.Courier
import app.regie.game.engine.courier.Letter
import app.regie.game.engine.courier.LetterKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Le courrier du jeu, branché sur les mêmes cas d'usage que les notifications Android
 * (`NotifySessions`, `NotifyApprovals`) : ils décident quoi prévenir, cet adapter range la lettre.
 * Les notifications Android restent ; le courrier, lui, reçoit aussi ce qui arrive pendant que Régie
 * est visible. [approvalText] donne le titre et le détail d'une validation (agent, projet, commande).
 */
class CourierNotifications(
    private val courier: MutableStateFlow<Courier>,
    private val approvalText: (Approval) -> Pair<String, String>,
    private val now: () -> Instant = { Clock.System.now() },
) : SessionNotifications, ApprovalNotifications {
    private var received = 0L

    override fun show(notice: SessionNotice): Boolean {
        val kind = when (notice.kind) {
            SessionNoticeKind.QUESTION -> LetterKind.QUESTION
            SessionNoticeKind.FINISHED -> LetterKind.FINISHED
            SessionNoticeKind.FAILED -> LetterKind.FAILED
        }
        val at = now()
        received++
        courier.update { it.receive(Letter("session:${notice.sessionId.value}:$received", kind, notice.sessionId, null, notice.title, notice.text, at)) }
        return true
    }

    /** Une session repart : ses lettres restent, c'est un historique récent. */
    override fun cancel(id: SessionId) = Unit

    override fun show(request: Approval): Boolean {
        val (title, detail) = approvalText(request)
        courier.update { it.receive(Letter("approval:${request.id.value}", LetterKind.APPROVAL, request.sessionId, request.id, title, detail, now())) }
        return true
    }

    /** La validation n'est plus en attente : sa lettre est lue. */
    override fun cancel(id: ApprovalId) = courier.update { it.settle(id) }
}
