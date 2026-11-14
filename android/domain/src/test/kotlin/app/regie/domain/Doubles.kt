package app.regie.domain

import app.regie.domain.model.Agent
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalReply
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Delivery
import app.regie.domain.model.LaunchResult
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionThread
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.WirelessDebugging
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.AndroidAccess
import app.regie.domain.port.BridgeLauncher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/** Passerelle de test : l'état se règle à la main, et elle note ce qu'on lui demande. */
class StubGateway(
    var bridgeUp: Boolean = true,
    var shutdownHeard: Boolean = true,
) : AgentGateway {
    override val approvals = MutableStateFlow<List<Approval>>(emptyList())
    var approvalResult = ApprovalReply.ACCEPTED
    val answers = mutableListOf<Pair<ApprovalId, String>>()
    override suspend fun answerApproval(id: ApprovalId, optionId: String): ApprovalReply {
        answers += id to optionId
        return approvalResult
    }
    override val sessions = MutableStateFlow<List<Session>>(emptyList())
    override val agents = MutableStateFlow<List<AgentVersion>>(emptyList())
    override val connection = MutableStateFlow<BridgeConnection>(BridgeConnection.Connecting)
    var shutdowns = 0
    var healthChecks = 0
    val threads = mutableMapOf<SessionId, MutableStateFlow<SessionThread>>()
    var delivery = Delivery.PROMPT
    val sent = mutableListOf<Pair<SessionId, String>>()
    val interrupted = mutableListOf<SessionId>()
    var interruptHeard = true

    override suspend fun maintainConnection() = awaitCancellation()

    override suspend fun isBridgeUp(): Boolean {
        healthChecks++
        return bridgeUp
    }

    override suspend fun shutdownBridge(): Boolean {
        shutdowns++
        return shutdownHeard
    }

    fun threadOf(id: SessionId) = threads.getOrPut(id) { MutableStateFlow(SessionThread.EMPTY) }

    override fun thread(id: SessionId) = threadOf(id)

    override suspend fun sendMessage(id: SessionId, text: String): Delivery {
        sent += id to text
        return delivery
    }

    override suspend fun interrupt(id: SessionId): Boolean {
        interrupted += id
        return interruptHeard
    }
}

class StubAccess(var result: WirelessDebugging = WirelessDebugging.ALREADY_ON) : AndroidAccess {
    var calls = 0

    override fun restoreWirelessDebugging(): WirelessDebugging {
        calls++
        return result
    }
}

class StubLauncher(var result: LaunchResult = LaunchResult.SENT) : BridgeLauncher {
    val sent = mutableListOf<String>()

    override fun start(): LaunchResult {
        sent += "start"
        return result
    }

    override fun stop(): LaunchResult {
        sent += "stop"
        return result
    }
}

val T0: Instant = Instant.parse("2026-11-13T21:41:00Z")

fun session(
    id: String,
    status: SessionStatus = SessionStatus.WORKING,
    active: Boolean = true,
    updatedAt: Instant = T0,
    approval: Approval? = null,
) = Session(
    id = SessionId(id),
    agent = Agent.CLAUDE,
    project = "projet-$id",
    branch = null,
    title = "tâche $id",
    status = status,
    active = active,
    updatedAt = updatedAt,
    lastActivityAt = updatedAt,
    pendingApproval = approval,
)
