package app.regie.voice.local

import app.regie.voice.api.VoiceException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.math.sqrt

/** Recordings of corpus sentences, versioned in voice/testdata/wav. */
object TestWavs {
    fun bytes(name: String): ByteArray =
        requireNotNull(TestWavs::class.java.getResourceAsStream("/wav/$name")) { "missing test WAV $name" }
            .use { it.readBytes() }
}

/**
 * Energy-based segmenter, in windows of 512 samples like Silero: an utterance starts on a loud
 * window and ends after [silenceWindows] quiet ones (its span then covers the quiet windows).
 */
internal class FakeSegmenter(
    private val threshold: Float = 0.02f,
    private val silenceWindows: Int = 10,
) : SpeechSegmenter {
    private val window = 512
    private var leftover = FloatArray(0)
    /** Absolute index of the next window. */
    private var position = 0L
    private var utteranceStart: Long? = null
    private var quiet = 0
    private val done = ArrayDeque<SpeechSpan>()
    var released = false
        private set

    override fun accept(samples: FloatArray) {
        val all = leftover + samples
        var offset = 0
        while (offset + window <= all.size) {
            val w = all.copyOfRange(offset, offset + window)
            val rms = sqrt(w.fold(0.0) { acc, s -> acc + s * s } / window).toFloat()
            val speech = rms > threshold
            if (utteranceStart == null) {
                if (speech) {
                    utteranceStart = position
                    quiet = 0
                }
            } else {
                quiet = if (speech) 0 else quiet + 1
            }
            position += window
            if (utteranceStart != null && quiet >= silenceWindows) close(position)
            offset += window
        }
        leftover = all.copyOfRange(offset, all.size)
    }

    private fun close(end: Long) {
        utteranceStart?.let { start -> done.addLast(SpeechSpan(start, (end - start).toInt())) }
        utteranceStart = null
        quiet = 0
    }

    override fun isSpeechDetected() = utteranceStart != null

    override fun flush() {
        position += leftover.size
        leftover = FloatArray(0)
        close(position)
    }

    override fun drain(): List<SpeechSpan> = done.toList().also { done.clear() }

    override fun reset() {
        leftover = FloatArray(0)
        utteranceStart = null
        done.clear()
    }

    override fun release() {
        released = true
    }
}

/** Decoder that names what it was given: `[n samples]`, so texts can be checked; silence gives "". */
internal class FakeDecoder(private val failAfter: Int = Int.MAX_VALUE) : SegmentDecoder {
    val calls = mutableListOf<Pair<Int, String?>>()
    var released = false
        private set

    override fun decode(samples: FloatArray, hotwords: String?): String {
        if (calls.size >= failAfter) throw IllegalStateException("decoder crashed")
        calls += samples.size to hotwords
        return if (samples.all { it == 0f }) "" else " [${samples.size}] "
    }

    override fun release() {
        released = true
    }
}

/** Synthesizer that produces one chunk of [samplesPerSentence] per sentence (split on '.'). */
internal class FakeSynthesizer(
    override val sampleRateHz: Int = 22_050,
    private val samplesPerSentence: Int = 2_205,
    /** Time spent "generating" each sentence, like the blocking native call. */
    private val sentenceMillis: Long = 0,
) : SpeechSynthesizer {
    val generated = mutableListOf<Triple<String, String, Int>>()
    var chunksAccepted = 0
        private set
    var released = false
        private set

    override fun generate(text: String, language: String, speaker: Int, speed: Float, onAudio: (FloatArray) -> Boolean) {
        generated += Triple(text, language, speaker)
        for (sentence in text.split('.').filter { it.isNotBlank() }) {
            if (sentenceMillis > 0) Thread.sleep(sentenceMillis)
            val chunk = FloatArray(samplesPerSentence) { if (it % 2 == 0) 0.5f else -0.5f }
            if (!onAudio(chunk)) return
            chunksAccepted++
        }
    }

    override fun release() {
        released = true
    }
}

/** Factory of fakes, recording what was created. */
internal class FakeFactory : EngineFactory {
    val decoders = mutableListOf<Pair<FakeDecoder, DecoderSettings>>()
    val segmenters = mutableListOf<FakeSegmenter>()
    val synthesizers = mutableListOf<FakeSynthesizer>()

    override fun decoder(modelDir: File, settings: DecoderSettings): SegmentDecoder =
        FakeDecoder().also { decoders += it to settings }

    override fun segmenter(modelDir: File, settings: SegmenterSettings): SpeechSegmenter =
        FakeSegmenter().also { segmenters += it }

    override fun synthesizer(modelDir: File, threads: Int): SpeechSynthesizer =
        FakeSynthesizer().also { synthesizers += it }
}

/** Engine source of fakes; [prepare] runs before the engines are handed out (download, failure…). */
internal class FakeEngineSource(
    val decoder: FakeDecoder = FakeDecoder(),
    val synthesizer: FakeSynthesizer = FakeSynthesizer(),
    private val hotwords: Boolean = false,
    private val synthesizerMissing: VoiceException? = null,
    private val prepare: suspend ((Float?) -> Unit) -> Unit = {},
    override val engineDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : EngineSource {
    val segmenters = mutableListOf<FakeSegmenter>()
    var released = false
        private set

    override suspend fun recognition(onPreparing: (Float?) -> Unit): RecognitionEngines {
        prepare(onPreparing)
        return RecognitionEngines(decoder, segmenter(), hotwords)
    }

    override suspend fun synthesizer(): SpeechSynthesizer {
        synthesizerMissing?.let { throw it }
        return synthesizer
    }

    override suspend fun segmenter(): SpeechSegmenter = FakeSegmenter().also { segmenters += it }

    override suspend fun prepareAll(onProgress: (Float?) -> Unit) {
        prepare(onProgress)
        synthesizer()
    }

    override suspend fun release() {
        released = true
    }
}
