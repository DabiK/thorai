package app.regie.voicelab

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.AudioSource
import app.regie.voice.api.DictationState
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.Wav
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.io.File

/** A sentence to read: its number in `consignes.txt` and its text. */
data class Prompt(val id: Int, val text: String) {
    /** `07`: the name of its recording and its id in the reports, as in the bench. */
    val label: String get() = "%02d".format(id)
}

/**
 * Guided reading, to measure a provider on the user's own voice: the sentences of the corpus are
 * shown one by one, the user holds L2 and reads each of them.
 *
 * Each dictation is saved as `NN.wav` in [dir] (16 kHz mono, the bench format), so that the same
 * recordings can be replayed later in the other provider by the bench; it is scored like a bench
 * item ([Bench.score]) and the reading moves to the next sentence. [redo] goes back one sentence.
 */
class GuidedReading(
    val provider: String,
    private val prompts: List<Prompt>,
    private val dir: File,
    private val vocabulary: Vocabulary,
) {
    private val items = mutableListOf<BenchItem>()

    val total: Int get() = prompts.size

    /** Results so far, one per sentence read, in order. */
    val results: List<BenchItem> get() = items.toList()

    /** The sentence to read now; null once every sentence is read. */
    val current: Prompt? get() = prompts.getOrNull(items.size)

    val finished: Boolean get() = current == null

    /** Saves the recording of the current sentence, scores it and moves to the next sentence. */
    fun record(audio: ShortArray, format: AudioFormat, terminal: DictationState, latencyMs: Long?, realTimeFactor: Double?): BenchItem {
        val prompt = checkNotNull(current) { "every sentence is already read" }
        dir.mkdirs()
        val wav = File(dir, "${prompt.label}.wav")
        wav.writeBytes(Wav.encode(audio, format))
        val audioMs = audio.size * 1000L / format.sampleRateHz
        val item = Bench.score(BenchCase(prompt.label, wav, prompt.text), terminal, latencyMs, audioMs, realTimeFactor, vocabulary)
        items += item
        return item
    }

    /** Goes back to the last sentence read and drops its result, to read it again. False if none was read. */
    fun redo(): Boolean = items.removeLastOrNull() != null

    fun report(settings: Map<String, String>, startedAt: String): BenchReport = BenchReport(provider, settings, startedAt, results)

    companion object {
        /** The sentences of `consignes.txt`, by number. */
        fun prompts(sentences: Map<Int, String>): List<Prompt> = sentences.toSortedMap().map { (id, text) -> Prompt(id, text) }
    }
}

/** Keeps every frame read from [source], to measure its duration and save it as a WAV. */
class CapturingSource(private val source: AudioSource) : AudioSource {
    override val format: AudioFormat = source.format

    private val captured = mutableListOf<ShortArray>()

    @Volatile
    private var count = 0L

    val millis: Long get() = count * 1000 / format.sampleRateHz

    /** Every sample captured so far. */
    fun samples(): ShortArray = synchronized(captured) {
        val all = ShortArray(captured.sumOf { it.size })
        var at = 0
        for (chunk in captured) {
            chunk.copyInto(all, at)
            at += chunk.size
        }
        all
    }

    override fun frames(): Flow<AudioFrame> = source.frames().onEach { frame ->
        synchronized(captured) { captured += frame.samples.copyOf() }
        count += frame.samples.size
    }
}
