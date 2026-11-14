package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.VoiceCommandModel
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class InterpretVoiceCommand(private val model: VoiceCommandModel) {
    suspend operator fun invoke(text: String, context: VoiceCommandContext): CommandInterpretation {
        val normalized = normalize(text)
        val unknown = CommandInterpretation(null, "Ordre non compris : « ${text.take(1000)} ». Reformule un seul ordre avec le nom de la session.")
        val launch = launchPhrase(text)
        if (normalized.isBlank() || text.length > 2000 || (launch == null && Regex("\\b(pas|jamais|ne|puis|ensuite|sinon|si)\\b|\\bn[' ]").containsMatchIn(normalized)) ||
            (launch == null && Regex("\\bet (approuve|refuse|mets|lance|ouvre|passe|interromps)\\b").containsMatchIn(normalized))) return unknown
        val fixed = launch ?: fixed(text, normalized)
        val intent = fixed ?: try {
            withTimeoutOrNull(12_000) { model.interpret(text, context) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val command = intent?.let { resolve(it, context) } ?: return unknown
        fun name(id: SessionId) = context.sessions.firstOrNull { it.id == id }?.let { "${it.title ?: it.project} · ${it.agent}" } ?: id.value
        val description = when (command) {
            is VoiceCommand.Reply -> "${if (command.decision == ApprovalDecision.ONCE) "Autoriser une fois" else "Refuser"} : ${command.approval.subject ?: "validation affichée"}"
            is VoiceCommand.Pause -> "Interrompre le tour de : ${command.ids.joinToString { name(it) }}"
            is VoiceCommand.Summary -> "Faire le point écrit sur : ${command.ids.joinToString { name(it) }}"
            is VoiceCommand.Navigate -> "Ouvrir : ${name(command.id)}"
            is VoiceCommand.Launch -> with(command.draft) {
                "Lancer $agent sur $project\nValidation : plan d’abord\nBranche : ${branch ?: "courante"}\nConsigne : ${instruction ?: "aucune (session vide)"}"
            }
        }
        return CommandInterpretation(command, description)
    }

    private fun fixed(original: String, text: String): VoiceIntent? {
        if (text in setOf("approuve", "autorise", "valide", "approuve cette validation", "autorise cette validation", "valide cette demande")) return VoiceIntent("approve")
        if (text in setOf("refuse", "refuse cette validation", "refuse cette demande")) return VoiceIntent("reject")
        if (text in setOf("mets tout en pause", "mets toutes les sessions en pause", "pause tout", "interromps tout")) return VoiceIntent("pause_all")
        if (text in setOf("pause", "mets en pause", "met en pause", "mais en pause", "mets cette session en pause", "interromps cette session", "interrompt cette session")) return VoiceIntent("pause")
        if (text in setOf("fais le point sur tout", "fais le point sur toutes les sessions", "ou en sont toutes les sessions", "fais le point sur toutes")) return VoiceIntent("summary_all")
        if (text in setOf("fais le point", "ou en est cette session")) return VoiceIntent("summary")
        Regex("(?:fais le point sur|ou en est) (.+)").matchEntire(text)?.let { return VoiceIntent("summary", it.groupValues[1]) }
        Regex("(?:passe a|ouvre|va sur) (?:la session )?(.+)").matchEntire(text)?.let { return VoiceIntent("navigate", it.groupValues[1]) }
        Regex("(?:mets|met) (.+) en pause").matchEntire(text)?.let { return VoiceIntent("pause", it.groupValues[1]) }
        Regex("(?:lance|demarre) (Claude|Codex) sur ([\\p{L}\\p{N}_.-]+)(?: pour (.+))?[.!?]?", RegexOption.IGNORE_CASE)
            .matchEntire(original.trim().trimEnd('.', '!', '?'))?.let {
                return VoiceIntent("launch", agent = it.groupValues[1].lowercase(Locale.ROOT), project = it.groupValues[2], instruction = it.groupValues[3].ifBlank { null })
            }
        return null
    }

    private fun resolve(intent: VoiceIntent, context: VoiceCommandContext): VoiceCommand? {
        if (intent.action != "launch" && (listOf(intent.agent, intent.project, intent.branch, intent.instruction, intent.approvalMode, intent.baseBranch).any { it != null } || intent.newBranch || intent.newProject)) return null
        fun target(): SessionId? {
            val name = intent.target?.let(::normalize)
            if (name == null) return context.current?.takeIf { id -> context.sessions.any { it.id == id } }
            return context.sessions.filter { session ->
                listOfNotNull(session.title, session.project.substringAfterLast('/'), session.agent.name).any { normalize(it) == name }
            }.singleOrNull()?.id
        }
        return when (intent.action) {
            "approve", "reject" -> if (intent.target != null) null else context.approval?.let { approval ->
                val decision = if (intent.action == "approve") ApprovalDecision.ONCE else ApprovalDecision.REJECT
                approval.optionFor(decision)?.let { VoiceCommand.Reply(approval, decision) }
            }
            "pause" -> target()?.takeIf { id -> context.sessions.any { it.id == id && it.status.isBusy } }?.let { VoiceCommand.Pause(listOf(it)) }
            "pause_all" -> if (intent.target != null) null else context.sessions.filter { it.status.isBusy }.map { it.id }.takeIf { it.isNotEmpty() }?.let(VoiceCommand::Pause)
            "summary" -> target()?.let { VoiceCommand.Summary(listOf(it)) }
            "summary_all" -> if (intent.target != null) null else context.sessions.map { it.id }.takeIf { it.isNotEmpty() }?.let(VoiceCommand::Summary)
            "navigate" -> if (intent.target == null) null else target()?.let(VoiceCommand::Navigate)
            "launch" -> {
                if (intent.target != null) return null
                val agent = Agent.entries.singleOrNull { it.name.equals(intent.agent, ignoreCase = true) } ?: return null
                val project = if (intent.newProject) projectFolderName(intent.project.orEmpty()) ?: return null
                    else context.projects.singleOrNull { normalize(it) == normalize(intent.project.orEmpty()) } ?: return null
                if (intent.newProject && (intent.branch != null || intent.newBranch || intent.baseBranch != null)) return null
                val branch = intent.branch
                if (branch != null && (branch.length > 200 || !branch.matches(Regex("[A-Za-z0-9][A-Za-z0-9_./-]*")) || branch.contains(".."))) return null
                if (intent.baseBranch != null && !validBranchName(intent.baseBranch)) return null
                if (intent.approvalMode != null && intent.approvalMode !in listOf("plan", "auto-edit", "ask", "allow-all")) return null
                if ((intent.instruction?.length ?: 0) > 2000) return null
                VoiceCommand.Launch(NewSessionDraft(agent, project, branch, intent.instruction, intent.approvalMode ?: "plan", intent.newBranch, intent.baseBranch, intent.newProject))
            }
            else -> null
        }
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).replace('’', '\'')
        .trim().trimEnd('.', '?', '!').trim().replace(Regex("\\s+"), " ")
}
