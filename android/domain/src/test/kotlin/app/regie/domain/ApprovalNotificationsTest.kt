package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.ApprovalNotifications
import app.regie.domain.usecase.NotifyApprovals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalNotificationsTest {
    @Test
    fun `notifies once in background even if lower dashboard remains and cancels resolved requests`() = runTest {
        val gateway = StubGateway()
        val foreground = MutableStateFlow(true)
        val shown = mutableListOf<ApprovalId>()
        val removed = mutableListOf<ApprovalId>()
        val port = object : ApprovalNotifications {
            override fun show(request: Approval): Boolean { shown += request.id; return true }
            override fun cancel(id: ApprovalId) { removed += id }
        }
        NotifyApprovals(gateway, foreground, port, backgroundScope)
        runCurrent()
        val first = Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "touch x")
        gateway.approvals.value = listOf(first)
        runCurrent()
        assertTrue(shown.isEmpty())
        foreground.value = false
        runCurrent()
        assertEquals(listOf(first.id), shown)
        gateway.approvals.value = listOf(first.copy(reason = "reason"))
        runCurrent()
        assertEquals(1, shown.size)
        gateway.approvals.value = emptyList()
        runCurrent()
        assertEquals(listOf(first.id), removed)
    }
    @Test
    fun `a denied notification permission does not lose the pending alert forever`() = runTest {
        val gateway = StubGateway()
        val foreground = MutableStateFlow(false)
        var granted = false
        val delivered = mutableListOf<ApprovalId>()
        val port = object : ApprovalNotifications {
            override fun show(request: Approval): Boolean {
                if (granted) delivered += request.id
                return granted
            }
            override fun cancel(id: ApprovalId) = Unit
        }
        gateway.approvals.value = listOf(Approval(ApprovalId("a"), SessionId("s"), ApprovalKind.COMMAND, "touch x"))
        NotifyApprovals(gateway, foreground, port, backgroundScope)
        runCurrent()
        assertTrue(delivered.isEmpty())
        foreground.value = true
        runCurrent()
        granted = true
        foreground.value = false
        runCurrent()
        assertEquals(listOf(ApprovalId("a")), delivered)
    }

}
