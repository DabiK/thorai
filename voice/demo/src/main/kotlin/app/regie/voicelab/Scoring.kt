package app.regie.voicelab

import java.text.Normalizer
import java.util.Locale

/** How a transcript compares with what was said. */
object Scoring {
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    /** Lower case, punctuation removed: `Ouvre AgentGateway.kt !` → `ouvre agentgateway kt`. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.ROOT).replace(separators, " ").trim()

    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Word error rate of [obtained] against [expected], on normalized words:
     * (substitutions + deletions + insertions) / number of expected words.
     */
    fun wordErrorRate(expected: String, obtained: String): Double {
        val ref = words(expected)
        val hyp = words(obtained)
        if (ref.isEmpty()) return if (hyp.isEmpty()) 0.0 else 1.0
        var previous = IntArray(hyp.size + 1) { it }
        for (i in 1..ref.size) {
            val current = IntArray(hyp.size + 1)
            current[0] = i
            for (j in 1..hyp.size) {
                val substitution = previous[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
                current[j] = minOf(substitution, previous[j] + 1, current[j - 1] + 1)
            }
            previous = current
        }
        return previous[hyp.size].toDouble() / ref.size
    }

    /** Whether [term] appears in [text], as a sequence of normalized words. */
    fun contains(text: String, term: String): Boolean {
        val needle = words(term)
        if (needle.isEmpty()) return false
        return words(text).windowed(needle.size).any { it == needle }
    }

    /** The [terms] present in [expected], split into found and missed in [obtained]. */
    fun terms(expected: String, obtained: String, terms: List<String>): TermScore {
        val present = terms.filter { contains(expected, it) }
        val (found, missed) = present.partition { contains(obtained, it) }
        return TermScore(found, missed)
    }
}

data class TermScore(val found: List<String>, val missed: List<String>) {
    val expected: Int get() = found.size + missed.size
}

/** Mean and median, null for an empty list. */
data class Stat(val mean: Double, val median: Double, val max: Double) {
    companion object {
        fun of(values: List<Double>): Stat? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val middle = sorted.size / 2
            val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
            return Stat(values.average(), median, sorted.last())
        }
    }
}
