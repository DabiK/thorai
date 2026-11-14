package app.regie.domain.model

enum class DiffKind { CONTEXT, ADDED, REMOVED }
data class DiffLine(val kind: DiffKind, val text: String, val oldLine: Int?, val newLine: Int?) {
    val number: Int? get() = newLine ?: oldLine
}
/** One reported edit, not a reconstruction of the working tree. */
data class DiffFragment(val path: String, val lines: List<DiffLine>, val fileCoordinates: Boolean = false)
data class DiffBlock(val id: String, val lines: List<DiffLine>, val fileCoordinates: Boolean = false)
data class DiffFile(val path: String, val blocks: List<DiffBlock>, val changes: LineChanges?)
