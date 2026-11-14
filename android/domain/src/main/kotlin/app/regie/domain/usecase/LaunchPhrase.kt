package app.regie.domain.usecase

import app.regie.domain.model.VoiceIntent

/** A bounded launch header; the instruction after `pour` is preserved verbatim. */
internal fun launchPhrase(text: String): VoiceIntent? {
    val match = Regex("(?:lance|démarre|demarre) (Claude|Codex) sur ([\\p{L}\\p{N}_.-]+)(.*)", RegexOption.IGNORE_CASE)
        .matchEntire(text.trim().trimEnd('.', '!', '?')) ?: return null
    val rest = match.groupValues[3]
    val separator = Regex("(?:,?\\s+(?:et\\s+)?demande-lui de\\s+|,?\\s+pour\\s+)", RegexOption.IGNORE_CASE).find(rest)
    val instruction = separator?.let { rest.substring(it.range.last + 1).trim() }
    var header = (separator?.let { rest.substring(0, it.range.first) } ?: rest).trim(' ', ',', '.', '!', '?')
    var mode: String? = null
    val modes = listOf("plan d’abord" to "plan", "plan d'abord" to "plan", "modifs auto" to "auto-edit", "modif auto" to "auto-edit", "tout valider" to "ask", "tout validé" to "ask")
    for ((label, value) in modes) {
        val found = Regex("(?:^|,?\\s+)(?:en (?:mode )?)?${Regex.escape(label)}$", RegexOption.IGNORE_CASE).find(header)
        if (found != null) { mode = value; header = header.substring(0, found.range.first).trim(' ', ',', '.', '!', '?'); break }
    }
    val ref = "([A-Za-z0-9][A-Za-z0-9_./-]*)"
    val new = Regex("nouvelle branche(?: $ref)? depuis $ref", RegexOption.IGNORE_CASE).matchEntire(header)
    val existing = Regex("(?:sur (?:la )?)?branche(?: existante)? $ref", RegexOption.IGNORE_CASE).matchEntire(header)
    if (header.isNotEmpty() && new == null && existing == null) return null
    return VoiceIntent("launch", agent = match.groupValues[1], project = match.groupValues[2],
        branch = new?.groupValues?.get(1)?.ifBlank { null } ?: existing?.groupValues?.get(1), instruction = instruction,
        approvalMode = mode, newBranch = new != null, baseBranch = new?.groupValues?.get(2))
}

fun validBranchName(value: String): Boolean = value.length <= 200 &&
    value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_./-]*")) && !value.contains("..") &&
    value.split('/').all { it.isNotEmpty() && !it.startsWith('.') && !it.endsWith('.') && !it.endsWith(".lock") }
