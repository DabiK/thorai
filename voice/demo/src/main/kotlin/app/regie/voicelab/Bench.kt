package app.regie.voicelab

import app.regie.voice.api.AudioFrame
import app.regie.voice.api.AudioSource
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.SpeechToText
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.WavAudioSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One recording of the corpus and the sentence it says. */
data class BenchCase(val id: String, val wav: File, val expected: String?)

/** Result of one recording. */
data class BenchItem(
    val case: BenchCase,
    val obtained: String?,
    /** End of the recording → final text. */
    val latencyMs: Long?,
    val audioMs: Long,
    val wer: Double?,
    val realTimeFactor: Double?,
    val terms: TermScore?,
    val error: String?,
)

data class BenchReport(val provider: String, val settings: Map<String, String>, val startedAt: String, val items: List<BenchItem>) {
    val latency: Stat? get() = Stat.of(items.mapNotNull { it.latencyMs?.toDouble() })
    val wer: Stat? get() = Stat.of(items.mapNotNull { it.wer })
    val realTimeFactor: Stat? get() = Stat.of(items.mapNotNull { it.realTimeFactor })
    val termsExpected: Int get() = items.sumOf { it.terms?.expected ?: 0 }
    val termsFound: Int get() = items.sumOf { it.terms?.found?.size ?: 0 }
    val failures: Int get() = items.count { it.error != null }

    fun toJson(): JsonObject = buildJsonObject {
        put("provider", provider)
        put("settings", JsonObject(settings.mapValues { JsonPrimitive(it.value) }))
        put("startedAt", startedAt)
        put("items", JsonArray(items.map { it.toJson() }))
        put(
            "summary",
            buildJsonObject {
                put("count", items.size)
                put("failures", failures)
                put("latencyMs", latency.toJson())
                put("wer", wer.toJson())
                put("realTimeFactor", realTimeFactor.toJson())
                put("termsExpected", termsExpected)
                put("termsFound", termsFound)
            },
        )
    }

    fun toCsv(): String = buildString {
        appendLine(CSV_HEADER.joinToString(","))
        for (item in items) {
            val row = listOf(
                item.case.id, item.case.wav.name, item.case.expected, item.obtained, item.latencyMs?.toString(),
                item.audioMs.toString(), item.wer?.format(), item.realTimeFactor?.format(),
                item.terms?.found?.joinToString(" "), item.terms?.missed?.joinToString(" "), item.error,
            )
            appendLine(row.joinToString(",") { csv(it) })
        }
        for ((label, pick) in listOf<Pair<String, (Stat) -> Double>>("moyenne" to { it.mean }, "médiane" to { it.median })) {
            val row = listOf(
                label, null, null, null, latency?.let(pick)?.format(), null, wer?.let(pick)?.format(), realTimeFactor?.let(pick)?.format(),
                "$termsFound/$termsExpected", null, null,
            )
            appendLine(row.joinToString(",") { csv(it) })
        }
    }

    private fun BenchItem.toJson(): JsonObject = buildJsonObject {
        put("id", case.id)
        put("wav", case.wav.name)
        put("expected", case.expected)
        put("obtained", obtained)
        put("latencyMs", latencyMs)
        put("audioMs", audioMs)
        put("wer", wer)
        put("realTimeFactor", realTimeFactor)
        put("termsFound", terms?.found.toJsonArray())
        put("termsMissed", terms?.missed.toJsonArray())
        put("error", error)
    }

    private fun Stat?.toJson(): JsonElement = this?.let {
        buildJsonObject {
            put("mean", it.mean)
            put("median", it.median)
            put("max", it.max)
        }
    } ?: JsonNull

    private fun List<String>?.toJsonArray(): JsonElement = this?.let { list -> JsonArray(list.map(::JsonPrimitive)) } ?: JsonNull

    companion object {
        private val CSV_HEADER = listOf(
            "id", "wav", "attendu", "obtenu", "latence_ms", "duree_audio_ms", "wer", "facteur_temps_reel",
            "termes_retrouves", "termes_manques", "erreur",
        )

        private fun Double.format() = String.format(java.util.Locale.ROOT, "%.3f", this)

        private fun csv(value: String?): String {
            if (value == null) return ""
            return if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
        }
    }
}

/**
 * Replays the recordings of a corpus into a transcription provider, at real pace, as if they
 * were dictated, and scores the results.
 *
 * - The recordings are the `*.wav` files of [corpusDir]; each one is matched to its sentence by
 *   the last number of its name (`07.wav` and `consigne-07.wav` both go with `07|…`).
 * - The end of the recording is the end of speech: latency runs from there to the final text.
 */
class Bench(
    private val vocabulary: Vocabulary,
    private val realtime: Boolean = true,
    private val timeout: Duration = 60.seconds,
    private val clock: () -> Long = System::nanoTime,
) {
    data class Progress(val done: Int, val total: Int, val current: String?)

    suspend fun run(
        stt: SpeechToText,
        cases: List<BenchCase>,
        realTimeFactor: () -> Double? = { null },
        onProgress: (Progress, BenchItem?) -> Unit = { _, _ -> },
    ): List<BenchItem> {
        val items = mutableListOf<BenchItem>()
        for ((index, case) in cases.withIndex()) {
            onProgress(Progress(index, cases.size, case.wav.name), null)
            val item = runOne(stt, case, realTimeFactor)
            items += item
            onProgress(Progress(index + 1, cases.size, null), item)
        }
        return items
    }

    private suspend fun runOne(stt: SpeechToText, case: BenchCase, realTimeFactor: () -> Double?): BenchItem {
        val source = try {
            WavAudioSource.fromFile(case.wav, realtime = realtime)
        } catch (e: IllegalArgumentException) {
            return BenchItem(case, null, null, 0, null, null, null, "wav: ${e.message}")
        }
        val audioMs = source.samples.size * 1000L / source.format.sampleRateHz
        val timed = EndMarkingSource(source)
        val terminal = withTimeoutOrNull(timeout) {
            var last: DictationState? = null
            stt.transcribe(timed.frames(), DictationRequest(language = "fr", vocabulary = vocabulary)).collect { state ->
                if (state.isTerminal && last?.isTerminal != true) timed.finalAt = clock()
                last = state
            }
            last
        }
        if (terminal == null) return BenchItem(case, null, null, audioMs, null, null, null, "timeout after $timeout")
        val latencyMs = (timed.finalAt - timed.endedAt).coerceAtLeast(0) / 1_000_000
        return score(case, terminal, latencyMs, audioMs, realTimeFactor(), vocabulary)
    }

    /**
     * Marks the end of speech. A replayed frame is emitted at the start of the 100 ms it covers,
     * a microphone delivers it at the end: in real time, the end waits for the last frame to be
     * "spoken", as with a microphone.
     */
    private inner class EndMarkingSource(private val source: AudioSource) : AudioSource {
        override val format = source.format
        var endedAt = 0L
        var finalAt = 0L

        override fun frames(): Flow<AudioFrame> = flow {
            var last: AudioFrame? = null
            source.frames().collect {
                emit(it)
                last = it
            }
            if (realtime) last?.let { delay(it.durationMs) }
            endedAt = clock()
        }
    }

    companion object {
        private val number = Regex("""(\d+)\D*$""")

        /**
         * The result of one dictation of [case]: the text obtained and its scores when it is
         * [DictationState.Final], the error otherwise. Shared by the bench and the guided reading.
         */
        fun score(
            case: BenchCase,
            terminal: DictationState,
            latencyMs: Long?,
            audioMs: Long,
            realTimeFactor: Double?,
            vocabulary: Vocabulary,
        ): BenchItem = when (terminal) {
            is DictationState.Final -> BenchItem(
                case, terminal.text, latencyMs, audioMs,
                case.expected?.let { Scoring.wordErrorRate(it, terminal.text) },
                realTimeFactor,
                case.expected?.let { Scoring.terms(it, terminal.text, vocabulary.terms) },
                null,
            )
            is DictationState.Failed -> BenchItem(case, null, null, audioMs, null, null, null, terminal.error.toString())
            else -> BenchItem(case, null, null, audioMs, null, null, null, "ended in state $terminal")
        }

        /** Sentences of `consignes.txt`: `NN|text` per line, `#` for comments. */
        fun parseSentences(text: String): Map<Int, String> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
            .mapNotNull { line -> line.substringBefore('|').trim().toIntOrNull()?.let { it to line.substringAfter('|').trim() } }
            .toMap()

        /** The recordings of [dir], sorted by name, with their sentence when one matches. */
        fun cases(dir: File, sentences: Map<Int, String>, only: Set<Int>? = null): List<BenchCase> =
            dir.listFiles { f -> f.isFile && f.name.endsWith(".wav", ignoreCase = true) }.orEmpty()
                .sortedBy { it.name }
                .map { wav ->
                    val n = number.find(wav.nameWithoutExtension)?.groupValues?.get(1)?.toIntOrNull()
                    BenchCase(n?.let { "%02d".format(it) } ?: wav.nameWithoutExtension, wav, n?.let(sentences::get))
                }
                .filter { case -> only == null || case.id.toIntOrNull() in only }
    }
}
