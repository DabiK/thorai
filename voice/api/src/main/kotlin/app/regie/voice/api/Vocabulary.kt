package app.regie.voice.api

/**
 * Terms to favour during transcription: file, function and branch names of the repository.
 *
 * Order is priority: when a provider caps the list, the first terms are kept. Each adapter
 * applies its own limits (count, length); this type stays provider-agnostic.
 */
data class Vocabulary(val terms: List<String>) {
    companion object {
        val EMPTY = Vocabulary(emptyList())

        fun of(vararg terms: String) = Vocabulary(terms.toList())

        /** One term per line; blank lines and lines starting with `#` are ignored. */
        fun parse(text: String) = Vocabulary(
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList(),
        )
    }
}
