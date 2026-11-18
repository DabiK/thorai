package app.regie.domain.model

/** Three bounded statements, attributed to the agent; missing evidence stays explicit. */
data class WrittenSummary(val lines: List<String>, val fromAgent: Boolean, val question: String? = null) {
    companion object {
        fun of(session: Session, thread: SessionThread): WrittenSummary {
            // A new user message invalidates the previous turn's report.
            val current = thread.entries.drop(thread.entries.indexOfLast { it is ThreadEntry.UserMessage } + 1)
            val text = current.filterIsInstance<ThreadEntry.AgentText>().lastOrNull()?.text.orEmpty()
            val report = text.substringAfterLast(RegieReport.OPEN, "")
            val block = report.substringBefore(RegieReport.CLOSE)
            fun field(name: String): String? = block.lineSequence().map { it.trim() }
                .firstOrNull { it.startsWith("$name :") }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
            val done = field("Fait")
            val tests = field("Tests")
            val next = field("Suite")
            val complete = report.contains(RegieReport.CLOSE) && done != null && tests != null && next != null
            if (complete) return WrittenSummary(
                listOf("Fait : $done", "Tests : $tests", "Suite : $next").map(::short), true,
                field("Question")?.takeUnless { it.equals("aucune", ignoreCase = true) || it.equals("aucune.", ignoreCase = true) }?.let(::short),
            )
            val completed = current.filterIsInstance<ThreadEntry.ToolCall>().count { it.status == ActionStatus.COMPLETED }
            val remaining = thread.plan.orEmpty().filter { it.status != PlanStepStatus.DONE }.joinToString(" ; ") { it.text }
            return WrittenSummary(listOf(
                if (completed > 0) "Fait : $completed actions terminées ; bilan non communiqué." else "Fait : bilan non communiqué par l’agent.",
                "Tests : résultats non communiqués dans un bilan.",
                when {
                    session.pendingApproval != null -> "Suite : validation attendue — ${session.pendingApproval.subject ?: "ouvrir la demande"}"
                    session.status == SessionStatus.FAILED -> "Suite : échec — ${session.lastError ?: "consulter le fil"}"
                    remaining.isNotEmpty() -> "Suite : $remaining"
                    session.status.isBusy -> "Suite : ${session.progress?.currentStep ?: session.lastAction?.description ?: "agent au travail"}"
                    else -> "Suite : non communiquée ; consulter le fil."
                },
            ).map(::short), false, question = if (!session.status.isBusy) {
                LiveSession.lastSentence(text)?.takeIf { it.endsWith('?') && !it.contains('`') }?.let(::short)
            } else null)
        }

        private fun short(text: String): String = text.replace(Regex("\\s+"), " ").trim().let {
            if (it.length <= 240) it else it.take(239) + "…"
        }
    }
}

/**
 * The report block that bridges before ticket 22 asked the agent to append to each turn (ADR 10,
 * replaced by ADR 17). It is Régie's request, not the agent's answer: the thread hides it, even
 * unfinished while it streams. Only the exact shape is hidden: an opening marker alone on its
 * line, then field lines (Fait, Tests, Suite, Question), then the closing marker alone on its
 * line, or the end of the text while it streams. A marker quoted or discussed by the agent stays.
 * A text without such a block is returned untouched.
 */
object RegieReport {
    const val OPEN = "[Point Régie]"
    const val CLOSE = "[/Point Régie]"
    private val FIELD = Regex("""^(Fait|Tests|Suite|Question)[ \t\u00A0]*:""")
    private val NAMES = listOf("Fait", "Tests", "Suite", "Question")

    fun strip(text: String): String {
        if (!text.contains(OPEN)) return text
        val lines = text.split('\n')
        val kept = ArrayList<String>(lines.size)
        var index = 0
        var found = false
        while (index < lines.size) {
            val end = if (lines[index].trim() == OPEN) blockEnd(lines, index) else null
            if (end == null) { kept += lines[index]; index++; continue }
            found = true
            // Drop the blank lines that separated the block from the text before it.
            while (kept.isNotEmpty() && kept.last().isBlank()) kept.removeAt(kept.lastIndex)
            index = end + 1
            if (kept.isNotEmpty()) while (index < lines.size && lines[index].isBlank()) index++
            if (kept.isNotEmpty() && index < lines.size) kept += ""
        }
        return if (found) kept.joinToString("\n").trim('\n', '\r') else text
    }

    /** Last line of the block opened at [open], or null when the following lines are not one. */
    private fun blockEnd(lines: List<String>, open: Int): Int? {
        var index = open + 1
        while (index < lines.size) {
            val line = lines[index].trim()
            when {
                line == CLOSE -> return index
                line.isEmpty() || FIELD.containsMatchIn(line) -> index++
                // Streaming: the last line may be a field name or the closing marker being written.
                index == lines.lastIndex && (CLOSE.startsWith(line) || NAMES.any { it.startsWith(line) }) -> return index
                else -> return null
            }
        }
        return lines.lastIndex // Unfinished block at the end of the text.
    }
}
