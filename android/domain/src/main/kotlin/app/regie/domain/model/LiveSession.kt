package app.regie.domain.model

/**
 * Une ligne du fil, telle que l'écran « session en direct » la montre. [key] reste le même quand la
 * ligne change (texte qui s'allonge, lecture ajoutée au groupe) : l'écran garde sa place.
 */
sealed interface ThreadLine {
    val key: Long

    data class UserMessage(override val key: Long, val text: String, val dictated: Boolean) : ThreadLine

    /** [streaming] : l'agent écrit encore ce texte (curseur en fin de texte). */
    data class AgentText(override val key: Long, val text: String, val streaming: Boolean) : ThreadLine

    /** Lectures consécutives, regroupées : « lu · 3 fichiers · a.ts, b.ts, c.ts ». */
    data class Read(override val key: Long, val files: List<String>, val inProgress: Boolean) : ThreadLine

    data class Edit(override val key: Long, val path: String, val changes: LineChanges?, val status: ActionStatus?) : ThreadLine

    data class Run(override val key: Long, val command: String, val status: ActionStatus?, val result: String?) : ThreadLine

    data class Other(override val key: Long, val title: String, val status: ActionStatus?) : ThreadLine

    data class TurnFailed(override val key: Long, val message: String?) : ThreadLine

    data class Interrupted(override val key: Long) : ThreadLine

    /** La session attend une validation : la dernière ligne du fil. */
    data class AwaitingApproval(val subject: String?) : ThreadLine {
        override val key: Long get() = Long.MAX_VALUE
    }
}

/** Un fichier modifié par l'agent ; [changes] cumule les diffs vus dans le fil, absent sans diff. */
data class ModifiedFile(val path: String, val changes: LineChanges?)

/**
 * Les faits exposés par une session, sans synthèse vocale : ce que l'agent a exposé, rien d'autre.
 * [lastSentence] : la dernière phrase de l'agent.
 */
data class SessionSummary(
    val status: SessionStatus,
    val progress: Progress?,
    val lastAction: LastAction?,
    val pendingApproval: Approval?,
    val modifiedFiles: List<String>,
    val lastSentence: String?,
)

/**
 * Une session ouverte sur l'écran « session en direct » : la session, son fil mis en lignes, son plan,
 * ses fichiers modifiés et son contexte.
 */
data class LiveSession(
    val session: Session,
    val lines: List<ThreadLine>,
    val plan: List<PlanStep>?,
    val modifiedFiles: List<ModifiedFile>,
    val context: ContextUsage?,
    /** Le journal de la session n'est pas encore lu et rien n'est arrivé en direct. */
    val loading: Boolean,
    val writtenSummary: WrittenSummary,
    /** Identifies a turn even if its title or branch changes after completion. */
    val summaryTurn: String,
    val timestamps: Map<Long, kotlin.time.Instant> = emptyMap(),
    val actionDetails: Map<Long, ThreadEntry.ToolCall> = emptyMap(),
) {
    /** Start n'interrompt qu'une session qui travaille ou attend une validation. */
    val canInterrupt: Boolean get() = session.status.isBusy

    val summary: SessionSummary
        get() = SessionSummary(
            status = session.status,
            progress = session.progress,
            lastAction = session.lastAction,
            pendingApproval = session.pendingApproval,
            modifiedFiles = modifiedFiles.map { it.path },
            lastSentence = lines.lastOrNull { it is ThreadLine.AgentText }?.let { lastSentence((it as ThreadLine.AgentText).text) },
        )

    companion object {
        /**
         * Met le fil en lignes. [dictated] : les textes que l'app a envoyés dictés à cette session ; le
         * pont ne dit pas d'où vient un message, l'app s'en souvient.
         */
        fun of(session: Session, thread: SessionThread, dictated: Set<String> = emptySet()): LiveSession {
            val lines = linesOf(thread.entries, dictated, streaming = session.status == SessionStatus.WORKING)
            val approval = session.pendingApproval?.let { ThreadLine.AwaitingApproval(it.subject) }
            return LiveSession(
                session = session,
                timestamps = thread.timestamps,
                actionDetails = thread.entries.filterIsInstance<ThreadEntry.ToolCall>().associateBy { it.id },
                lines = if (approval != null) lines + approval else lines,
                plan = thread.plan,
                modifiedFiles = modifiedFiles(session.modifiedFiles, thread.entries),
                context = thread.context,
                loading = !thread.loaded && thread.entries.isEmpty(),
                writtenSummary = WrittenSummary.of(session, thread),
                summaryTurn = "${session.id.value}:${thread.entries.lastOrNull { it is ThreadEntry.UserMessage }?.id}",
            )
        }

        private fun linesOf(entries: List<ThreadEntry>, dictated: Set<String>, streaming: Boolean): List<ThreadLine> {
            val lines = ArrayList<ThreadLine>(entries.size)
            for (entry in entries) {
                val line = when (entry) {
                    is ThreadEntry.UserMessage -> ThreadLine.UserMessage(entry.id, entry.text, dictated = entry.text in dictated)
                    is ThreadEntry.AgentText -> ThreadLine.AgentText(entry.id, entry.text, streaming = false)
                    is ThreadEntry.TurnFailed -> ThreadLine.TurnFailed(entry.id, entry.message)
                    is ThreadEntry.Interrupted -> ThreadLine.Interrupted(entry.id)
                    is ThreadEntry.ToolCall -> {
                        val previous = lines.lastOrNull()
                        if (entry.kind == ToolKind.READ && !entry.status.isFailed && previous is ThreadLine.Read) {
                            lines[lines.lastIndex] = previous.copy(
                                files = (previous.files + readLabel(entry)).distinct(),
                                inProgress = previous.inProgress || entry.status.isUnfinished,
                            )
                            continue
                        }
                        lineOf(entry)
                    }
                }
                lines += line
            }
            // Le texte qui finit le fil s'écrit encore tant que la session travaille.
            val last = lines.lastOrNull()
            if (streaming && last is ThreadLine.AgentText) lines[lines.lastIndex] = last.copy(streaming = true)
            return lines
        }

        private fun lineOf(call: ThreadEntry.ToolCall): ThreadLine {
            val title = call.title ?: call.command ?: call.path ?: ""
            return when (call.kind) {
                ToolKind.READ -> if (call.status.isFailed) {
                    ThreadLine.Other(call.id, readLabel(call), call.status)
                } else {
                    ThreadLine.Read(call.id, listOf(readLabel(call)), inProgress = call.status.isUnfinished)
                }
                ToolKind.EDIT -> ThreadLine.Edit(call.id, call.editedFiles.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.path } ?: title, call.changes, call.status)
                ToolKind.EXECUTE -> ThreadLine.Run(call.id, call.command ?: title, call.status, call.result)
                ToolKind.OTHER -> ThreadLine.Other(call.id, title, call.status)
            }
        }

        private fun readLabel(call: ThreadEntry.ToolCall): String = call.path ?: call.title ?: ""

        private val ActionStatus?.isFailed get() = this == ActionStatus.FAILED || this == ActionStatus.INTERRUPTED

        private val ActionStatus?.isUnfinished get() = this == ActionStatus.PENDING || this == ActionStatus.IN_PROGRESS

        /**
         * Les fichiers modifiés : ceux que le pont a relevés, puis ceux des éditions terminées vues dans
         * le fil, avec les lignes cumulées de leurs diffs quand le fil les a.
         */
        private fun modifiedFiles(fromBridge: List<String>, entries: List<ThreadEntry>): List<ModifiedFile> {
            val changes = LinkedHashMap<String, LineChanges?>()
            fromBridge.forEach { changes[it] = null }
            entries.forEach { entry ->
                if (entry !is ThreadEntry.ToolCall || entry.kind != ToolKind.EDIT || entry.status != ActionStatus.COMPLETED) return@forEach
                for (file in entry.editedFiles) {
                    val before = changes[file.path]
                    changes[file.path] = when {
                        file.changes == null -> before
                        before == null -> file.changes
                        else -> before + file.changes
                    }
                }
            }
            return changes.map { (path, lines) -> ModifiedFile(path, lines) }
        }

        private val ThreadEntry.ToolCall.editedFiles: List<ModifiedFile>
            get() = files.ifEmpty { path?.let { listOf(ModifiedFile(it, changes)) }.orEmpty() }

        private val SENTENCE_END = Regex("""(?<=[.!?…])\s+|\n+""")

        /** La dernière phrase non vide d'un texte, sur une ligne. */
        fun lastSentence(text: String): String? =
            text.split(SENTENCE_END).map { it.trim() }.lastOrNull { it.isNotEmpty() }
    }
}
