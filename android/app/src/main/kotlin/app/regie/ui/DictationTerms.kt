package app.regie.ui

/**
 * Les termes du dépôt reconnus dans une transcription, à leurs bornes exactes : une branche nommée
 * main ne colore ni maintenant ni mainframe.
 */
fun recognizedTerms(text: String, vocabulary: List<String>): List<MatchResult> = vocabulary
    .filter { it.isNotBlank() }
    .flatMap { Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(it) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE).findAll(text).toList() }
    .sortedBy { it.range.first }
