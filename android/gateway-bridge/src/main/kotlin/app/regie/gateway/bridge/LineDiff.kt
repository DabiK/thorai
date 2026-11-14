package app.regie.gateway.bridge

import app.regie.domain.model.LineChanges
import com.github.difflib.DiffUtils

/** Compte des lignes d'un diff d'agent (`oldText` → `newText`), par java-diff-utils (ADR 07). */
internal object LineDiff {
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
