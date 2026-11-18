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
 * unfinished while it streams. A text without the opening marker is returned untouched.
 */
object RegieReport {
    const val OPEN = "[Point Régie]"
    const val CLOSE = "[/Point Régie]"

    fun strip(text: String): String {
        if (!text.contains(OPEN)) return text
        val kept = mutableListOf<String>()
        var rest = text
        while (true) {
            val start = rest.indexOf(OPEN)
            if (start < 0) { kept += rest; break }
            kept += rest.substring(0, start)
            val end = rest.indexOf(CLOSE, start + OPEN.length)
            if (end < 0) break
            rest = rest.substring(end + CLOSE.length)
        }
        return kept.mapIndexed { index, part -> if (index == 0) part.trimEnd() else part.trimStart('\n', '\r').trimEnd() }
            .filter { it.isNotBlank() }.joinToString("\n\n")
    }
}
