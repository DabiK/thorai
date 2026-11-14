package app.regie.gateway.bridge

import app.regie.domain.model.*
import kotlinx.serialization.json.JsonObject

/** ACP old/new text can be excerpts. Only structured patches or new files prove file coordinates. */
internal object DiffDecoder {
    fun read(path: String, item: JsonObject, update: JsonObject): List<DiffFragment> {
        val response = update.obj("_meta")?.obj("claudeCode")?.obj("toolResponse")
        val patches = response?.takeIf { it.string("filePath") == item.string("path") }?.array("structuredPatch")?.objects()
        if (!patches.isNullOrEmpty()) {
            return patches.mapNotNull { patch ->
                var old = patch.long("oldStart")?.toInt() ?: return@mapNotNull null
                var new = patch.long("newStart")?.toInt() ?: return@mapNotNull null
                val lines = patch.array("lines")?.strings() ?: return@mapNotNull null
                DiffFragment(path, lines.mapNotNull { line -> when (line.firstOrNull()) {
                    ' ' -> DiffLine(DiffKind.CONTEXT, line.drop(1), old++, new++)
                    '-' -> DiffLine(DiffKind.REMOVED, line.drop(1), old++, null)
                    '+' -> DiffLine(DiffKind.ADDED, line.drop(1), null, new++)
                    else -> null
                } }, fileCoordinates = true)
            }
        }
        val old = item.string("oldText")
        val new = item.string("newText") ?: return emptyList()
        return listOf(DiffFragment(path, LineDiff.render(old, new), fileCoordinates = old == null || item.obj("_meta")?.string("kind") == "add"))
    }
}
