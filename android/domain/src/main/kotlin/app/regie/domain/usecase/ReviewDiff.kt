package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.AgentGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

fun sessionDiff(session: Session, thread: SessionThread): List<DiffFile> {
    val blocks = linkedMapOf<String, MutableList<DiffBlock>>()
    session.modifiedFiles.forEach { blocks[it] = mutableListOf() }
    for (call in thread.entries.filterIsInstance<ThreadEntry.ToolCall>()) {
        if (call.kind != ToolKind.EDIT || call.status != ActionStatus.COMPLETED) continue
        (call.files.map { it.path } + listOfNotNull(call.path)).forEach { blocks.getOrPut(it) { mutableListOf() } }
        call.diffs.forEachIndexed { index, fragment ->
            blocks.getOrPut(fragment.path) { mutableListOf() } += DiffBlock("${call.persistentId}:$index", fragment.lines, fragment.fileCoordinates)
        }
    }
    return blocks.map { (path, edits) -> DiffFile(path, edits, edits.takeIf { it.isNotEmpty() }?.let {
        val lines = it.flatMap { block -> block.lines }
        LineChanges(lines.count { line -> line.kind == DiffKind.ADDED }, lines.count { line -> line.kind == DiffKind.REMOVED })
    }) }
}

data class DiffSelection(val block: DiffBlock, val lineIndex: Int) {
    val line: DiffLine get() = block.lines[lineIndex]
}
data class DiffReview(
    val sessionId: SessionId? = null,
    val project: String = "",
    val files: List<DiffFile> = emptyList(),
    val fileIndex: Int = 0,
    val lineIndex: Int = 0,
    val loading: Boolean = true,
    val explanation: DiffExplanation? = null,
    val selectionRevision: Long = 0,
) {
    val file: DiffFile? get() = files.getOrNull(fileIndex)
    val rows: List<DiffSelection> get() = file?.blocks.orEmpty().flatMap { block -> block.lines.indices.map { DiffSelection(block, it) } }
    val selected: DiffSelection? get() = rows.getOrNull(lineIndex)
}
data class DiffExplanation(val prompt: String, val label: String, val sending: Boolean = false,
    val delivery: Delivery? = null, val reply: String = "", val ended: Boolean = false)

class ReviewDiff(private val gateway: AgentGateway, private val scope: CoroutineScope) {
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(DiffReview())
    val state: kotlinx.coroutines.flow.StateFlow<DiffReview> = mutable
    private var observing: Job? = null
    private var thread = SessionThread.EMPTY
    fun open(id: SessionId) {
        close()
        mutable.value = DiffReview(sessionId = id)
        observing = scope.launch {
            combine(gateway.sessions, gateway.thread(id)) { sessions, content -> sessions.firstOrNull { it.id == id } to content }
                .collect { (session, content) ->
                    thread = content
                    val before = mutable.value
                    // A resync publishes an incomplete snapshot before replaying history.
                    val files = if (!content.loaded) before.files else session?.let { sessionDiff(it, content) }.orEmpty()
                    val index = files.indexOfFirst { it.path == before.file?.path }.coerceAtLeast(0)
                    val updated = before.copy(project = session?.project.orEmpty(), files = files, fileIndex = index, loading = !content.loaded)
                    val selected = before.selected
                    val rows = updated.rows
                    // Earlier edits can disappear or finish: the flattened index is not an identity.
                    val blockStart = if (updated.file?.path == before.file?.path && selected != null)
                        rows.indexOfFirst { it.block.id == selected.block.id } else -1
                    val lineIndex = if (blockStart >= 0 && selected != null)
                        blockStart + selected.lineIndex.coerceAtMost(rows[blockStart].block.lines.lastIndex)
                    else before.lineIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                    mutable.value = updated.copy(lineIndex = lineIndex,
                        explanation = before.explanation?.let { refreshExplanation(it, session, content) })
                }
        }
    }
    private fun refreshExplanation(explanation: DiffExplanation, session: Session?, content: SessionThread): DiffExplanation {
        // Once ended, a later crash/restart belongs to another turn.
        if (explanation.ended) return explanation
        val anchor = content.entries.filterIsInstance<ThreadEntry.UserMessage>()
            .firstOrNull { it.sequence > afterSequence && it.text == explanation.prompt }
        val reply = if (!content.loaded || anchor == null) explanation.reply else content.entries.mapNotNull { entry ->
            when (entry) {
                is ThreadEntry.AgentText -> entry.text.takeIf { entry.responseTo == anchor.id }
                is ThreadEntry.TurnFailed -> ("Échec : ${entry.message ?: "sans détail"}").takeIf { entry.responseTo == anchor.id }
                is ThreadEntry.Interrupted -> "Tour interrompu.".takeIf { entry.responseTo == anchor.id }
                else -> null
            }
        }.joinToString("\n\n")
        if (anchor != null && anchor.id in content.completedMessages) return explanation.copy(reply = reply, ended = true)
        // Agent exit has no turn_end. A disconnected bridge alone is not evidence of an exit.
        val stopped = explanation.delivery?.canRetry == false && session?.active == false &&
            session.status in setOf(SessionStatus.FAILED, SessionStatus.STOPPED)
        val error = if (!stopped) null else if (session.status == SessionStatus.FAILED)
            "Échec : ${session.lastError ?: "L’agent s’est arrêté."}"
        else "Session arrêtée avant la fin de l’explication."
        return explanation.copy(reply = listOfNotNull(reply.takeIf { it.isNotBlank() }, error).joinToString("\n\n"), ended = stopped)
    }
    fun close() { generation++; observing?.cancel(); observing = null; thread = SessionThread.EMPTY; mutable.value = DiffReview() }
    fun moveLine(delta: Int) {
        val state = mutable.value
        if (state.loading) return
        mutable.value = state.copy(selectionRevision = state.selectionRevision + 1, lineIndex = (state.lineIndex + delta).coerceIn(0, (state.rows.size - 1).coerceAtLeast(0)))
    }
    fun moveFile(delta: Int) = selectFile(mutable.value.fileIndex + delta)
    fun selectFile(index: Int) {
        val state = mutable.value
        if (state.loading || state.files.isEmpty() || state.explanation != null) return
        mutable.value = state.copy(fileIndex = index.coerceIn(0, state.files.lastIndex), lineIndex = 0, selectionRevision = state.selectionRevision + 1)
    }
    fun commentTarget(): DictationTarget.DiffComment? {
        val state = mutable.value
        if (state.loading) return null
        val selected = state.selected ?: return null
        return DictationTarget.DiffComment(state.sessionId ?: return null, state.file?.path ?: return null, selected.line.number ?: return null,
            removed = selected.line.kind == DiffKind.REMOVED, fileCoordinates = selected.block.fileCoordinates, selectedText = selected.line.text)
    }
    private var afterSequence = -1L
    private var generation = 0
    fun explain() {
        val state = mutable.value
        if (state.loading || state.explanation != null) return
        val selected = state.selected ?: return
        val line = selected.line
        val label = "${state.file!!.path} · ligne ${line.number}" +
            (if (selected.block.fileCoordinates) "" else " de l’extrait") +
            (if (line.kind == DiffKind.REMOVED) " supprimée" else "")
        val start = (selected.lineIndex - 30).coerceAtLeast(0)
        val excerpt = selected.block.lines.drop(start).take(61).joinToString("\n") {
            "${it.oldLine ?: "-"}/${it.newLine ?: "-"} ${when (it.kind) { DiffKind.ADDED -> "+"; DiffKind.REMOVED -> "-"; DiffKind.CONTEXT -> " " }} ${it.text}"
        }
        val prompt = "Explique-moi ce bloc, sans modifier les fichiers. Réponds en français par écrit.\n$label\n" +
            "Édition historique exposée par l’agent ; numéros ancien/nouveau" +
            (if (selected.block.fileCoordinates) "." else " relatifs à l’extrait, position dans le fichier non communiquée.") +
            "\nExtrait autour de la sélection :\n$excerpt"
        generation++
        afterSequence = thread.lastSequence
        mutable.value = state.copy(explanation = DiffExplanation(prompt, label))
    }
    fun dismissExplanation() { generation++; mutable.value = mutable.value.copy(explanation = null) }
    fun confirmExplanation() {
        val state = mutable.value
        val explanation = state.explanation ?: return
        if (explanation.sending || explanation.delivery?.canRetry == false) return
        val id = state.sessionId ?: return
        val token = generation
        mutable.value = state.copy(explanation = explanation.copy(sending = true))
        scope.launch {
            val delivery = gateway.sendMessage(id, explanation.prompt)
            if (token == generation) mutable.value = mutable.value.copy(explanation = mutable.value.explanation?.let {
                refreshExplanation(it.copy(sending = false, delivery = delivery), gateway.sessions.value.firstOrNull { it.id == id }, thread)
            })
        }
    }
}
