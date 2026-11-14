package app.regie.domain.port

import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionNotice

interface SessionNotifications {
    /** False when permission, application or channel prevents delivery. */
    fun show(notice: SessionNotice): Boolean
    fun cancel(id: SessionId)
}
