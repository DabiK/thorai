package app.regie.domain.model

/** Context frozen at R2 down, never silently retargeted while the user reviews. */
data class VoiceCommandContext(val sessions: List<Session>, val current: SessionId?, val approval: Approval?, val projects: List<String>)

/** Untrusted proposal from rules or a model. Resolved and checked by the domain. */
data class VoiceIntent(val action: String, val target: String? = null, val agent: String? = null,
    val project: String? = null, val branch: String? = null, val instruction: String? = null,
    val approvalMode: String? = null, val newBranch: Boolean = false, val baseBranch: String? = null, val newProject: Boolean = false)

data class NewSessionDraft(val agent: Agent, val project: String, val branch: String? = null, val instruction: String? = null, val approvalMode: String = "plan",
    val newBranch: Boolean = false, val baseBranch: String? = null, val newProject: Boolean = false, val configValues: Map<String, String> = emptyMap())
data class SessionCreation(val id: SessionId? = null, val uncertain: Boolean = false, val error: String? = null)

sealed interface VoiceCommand {
    data class Reply(val approval: Approval, val decision: ApprovalDecision) : VoiceCommand
    data class Pause(val ids: List<SessionId>) : VoiceCommand
    data class Summary(val ids: List<SessionId>) : VoiceCommand
    data class Navigate(val id: SessionId) : VoiceCommand
    data class Launch(val draft: NewSessionDraft) : VoiceCommand
}

data class CommandInterpretation(val command: VoiceCommand?, val description: String)
enum class CommandPhase { CLOSED, RECORDING, FINALIZING, INTERPRETING, REVIEW, RUNNING, DONE, UNKNOWN }
data class VoiceCommandReview(
    val phase: CommandPhase = CommandPhase.CLOSED, val text: String = "", val command: VoiceCommand? = null,
    val notice: String = "", val level: Float = 0f, val durationMs: Long = 0,
) {
    val visible: Boolean get() = phase != CommandPhase.CLOSED
    val canConfirm: Boolean get() = phase == CommandPhase.REVIEW && command != null
}
