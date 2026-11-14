package app.regie.domain.usecase

import app.regie.domain.model.ApprovalId
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.ApprovalNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Le tableau de bord du bas peut rester visible : c'est le haut qui définit le premier plan. */
class NotifyApprovals(
    gateway: AgentGateway,
    foreground: StateFlow<Boolean>,
    notifications: ApprovalNotifications,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            val shown = mutableSetOf<ApprovalId>()
            combine(gateway.approvals, foreground) { requests, visible -> requests to visible }.collect { (requests, visible) ->
                val pending = requests.map { it.id }.toSet()
                (shown - pending).forEach { notifications.cancel(it); shown -= it }
                if (!visible) requests.filter { it.id !in shown }.forEach {
                    if (notifications.show(it)) shown += it.id
                }
            }
        }
    }
}
