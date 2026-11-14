package app.regie.gateway.bridge

import app.regie.domain.model.*
import com.github.difflib.DiffUtils

/** Compte des lignes d'un diff d'agent (`oldText` → `newText`), par java-diff-utils (ADR 07). */
internal object LineDiff {
    fun render(oldText: String?, newText: String): List<DiffLine> {
        val old = linesOf(oldText.orEmpty())
        val new = linesOf(newText)
        val result = mutableListOf<DiffLine>()
        var before = 0
        var after = 0
        for (delta in DiffUtils.diff(old, new).deltas) {
            while (before < delta.source.position) {
                result += DiffLine(DiffKind.CONTEXT, old[before], ++before, ++after)
            }
            repeat(delta.source.size()) { result += DiffLine(DiffKind.REMOVED, old[before], ++before, null) }
            repeat(delta.target.size()) { result += DiffLine(DiffKind.ADDED, new[after], null, ++after) }
        }
        while (before < old.size) result += DiffLine(DiffKind.CONTEXT, old[before], ++before, ++after)
        return result
    }

    private val NEWLINE = Regex("\r?\n")

    /** [oldText] absent : un fichier créé, toutes ses lignes sont ajoutées. */
    fun changes(oldText: String?, newText: String): LineChanges {
        val patch = DiffUtils.diff(linesOf(oldText.orEmpty()), linesOf(newText))
        return patch.deltas.fold(LineChanges(0, 0)) { total, delta ->
            total + LineChanges(added = delta.target.size(), removed = delta.source.size())
        }
    }

    /** « a\nb\n » fait deux lignes, pas trois : le saut final ne crée pas de ligne. */
    private fun linesOf(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.removeSuffix("\n").removeSuffix("\r").split(NEWLINE)
}
