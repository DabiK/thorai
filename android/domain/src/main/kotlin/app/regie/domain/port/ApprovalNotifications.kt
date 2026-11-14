package app.regie.domain.port

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId

interface ApprovalNotifications {
    /** Faux si Android ne peut pas afficher la notification (permission, app ou canal bloqué). */
    fun show(request: Approval): Boolean
    fun cancel(id: ApprovalId)
}
