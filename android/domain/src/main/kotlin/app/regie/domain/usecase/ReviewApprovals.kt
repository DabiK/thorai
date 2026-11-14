package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** L'identité reste sélectionnée après une réponse : un second A ne répond jamais à la suivante. */
data class ApprovalReview(
    val request: Approval? = null,
    val queue: List<Approval> = emptyList(),
    val pending: Boolean = false,
    val sending: Boolean = false,
    val answer: ApprovalAnswer? = null,
) {
    fun canAnswer(decision: ApprovalDecision) = pending && !sending &&
        (answer == null || answer.reply == ApprovalReply.NOT_SENT) && request?.optionFor(decision) != null
}

class ReviewApprovals(private val gateway: AgentGateway, scope: CoroutineScope) {
    private val answerApproval = AnswerApproval(gateway)
    private var selectedId: ApprovalId? = null
    private val answers = mutableMapOf<ApprovalId, ApprovalAnswer>()
    private val sending = mutableSetOf<ApprovalId>()
    private val mutable = MutableStateFlow(ApprovalReview())
    val state: StateFlow<ApprovalReview> = mutable.asStateFlow()

    init {
        scope.launch { gateway.approvals.collect { refresh() } }
    }

    fun open(id: ApprovalId) {
        selectedId = id
        val request = gateway.approvals.value.firstOrNull { it.id == id }
        mutable.value = ApprovalReview(request = request)
        refresh()
    }

    fun next() {
        val queue = gateway.approvals.value
        val index = queue.indexOfFirst { it.id == selectedId }
        (queue.getOrNull(index + 1) ?: queue.firstOrNull())?.let { open(it.id) }
    }

    suspend fun answer(decision: ApprovalDecision, instruction: String? = null) {
        val before = mutable.value
        if (!before.canAnswer(decision)) return
        val id = before.request?.id ?: return
        sending += id
        refresh()
        try {
            answers[id] = answerApproval(id, decision, instruction)
        } finally {
            sending -= id
            refresh()
        }
    }

    private fun refresh() {
        val queue = gateway.approvals.value
        val old = mutable.value
        val id = selectedId
        val pending = queue.firstOrNull { it.id == id }
        mutable.value = old.copy(
            request = pending ?: old.request,
            queue = queue,
            pending = pending != null,
            sending = id in sending,
            answer = answers[id],
        )
    }
}
