package app.regie.voice.local

/**
 * Simulated streaming on top of an offline model.
 *
 * The segmenter (Silero) only says where utterances end. The decoder (Parakeet) then decodes the
 * whole audio between two cuts, not just what the segmenter kept: words said before the
 * segmenter triggers (start of the dictation, short words) are not lost. While speech goes on,
 * the audio since the last cut gets a provisional decode every [partialIntervalSamples] samples.
 *
 * Not thread-safe: all calls come from the engine thread.
 */
internal class SegmentedTranscriber(
    private val decoder: SegmentDecoder,
    private val segmenter: SpeechSegmenter,
    private val partialIntervalSamples: Int,
    private val hotwords: String?,
    private val clock: () -> Long = System::nanoTime,
) {
    private val finished = mutableListOf<String>()
    private var provisional = ""

    /** Audio since the last cut; [cut] is the absolute index of its first sample. */
    private var pending = FloatArray(INITIAL_CAPACITY)
    private var pendingSize = 0
    private var cut = 0L
    private var speechSinceCut = false
    private var samplesSincePartial = 0

    /** Statistics of this dictation. */
    var stats = TranscriptionStats()
        private set

    /** Text so far: finished utterances, then the provisional one. */
    val text: String get() = (finished + provisional).filter { it.isNotEmpty() }.joinToString(" ")

    /** Feeds 16 kHz PCM samples; returns the new partial text, or null if it did not change. */
    fun accept(pcm: ShortArray): String? {
        val samples = toFloat(pcm)
        appendPending(samples)
        stats = stats.copy(audioSamples = stats.audioSamples + samples.size)
        segmenter.accept(samples)
        var changed = cutAtUtterances()
        if (segmenter.isSpeechDetected()) speechSinceCut = true
        if (speechSinceCut) {
            samplesSincePartial += samples.size
            if (samplesSincePartial >= partialIntervalSamples) {
                samplesSincePartial = 0
                val next = decode(pendingSize, provisionalDecode = true)
                if (next != provisional) {
                    provisional = next
                    changed = true
                }
            }
        }
        return if (changed) text else null
    }

    /** End of speech: decodes what is left since the last cut and returns the final text. */
    fun finish(): String {
        segmenter.flush()
        cutAtUtterances()
        // Audio after the last utterance: decoded if speech was heard there, or if nothing was
        // recognised at all (a word too short for the segmenter).
        if (pendingSize >= MIN_TAIL_SAMPLES && (speechSinceCut || finished.isEmpty())) {
            closeUtterance(pendingSize)
        }
        provisional = ""
        return text
    }

    /** Decodes up to the end of each utterance the segmenter has closed. */
    private fun cutAtUtterances(): Boolean {
        val spans = segmenter.drain()
        for (span in spans) {
            val length = (minOf(span.end, stats.audioSamples) - cut).toInt()
            if (length > 0) closeUtterance(length)
        }
        return spans.isNotEmpty()
    }

    private fun closeUtterance(length: Int) {
        val decoded = decode(length, provisionalDecode = false)
        if (decoded.isNotEmpty()) finished += decoded
        stats = stats.copy(segments = stats.segments + 1)
        pending.copyInto(pending, 0, length, pendingSize)
        pendingSize -= length
        cut += length
        provisional = ""
        speechSinceCut = false
        samplesSincePartial = 0
    }

    private fun decode(length: Int, provisionalDecode: Boolean): String {
        val start = clock()
        val decoded = decoder.decode(pending.copyOf(length), hotwords).trim()
        val elapsed = clock() - start
        stats = if (provisionalDecode) {
            stats.copy(provisionalDecodeNanos = stats.provisionalDecodeNanos + elapsed)
        } else {
            stats.copy(finalDecodeNanos = stats.finalDecodeNanos + elapsed)
        }
        return decoded
    }

    private fun appendPending(samples: FloatArray) {
        if (pendingSize + samples.size > pending.size) {
            pending = pending.copyOf(maxOf(pending.size * 2, pendingSize + samples.size))
        }
        samples.copyInto(pending, pendingSize)
        pendingSize += samples.size
    }

    companion object {
        private const val INITIAL_CAPACITY = 16_000 * 4

        /** A quarter of a second: shorter tails are noise, not words. */
        private const val MIN_TAIL_SAMPLES = 4_000

        fun toFloat(pcm: ShortArray): FloatArray = FloatArray(pcm.size) { pcm[it] / 32768f }
    }
}

/** What a local dictation cost, to compute its real-time factor. */
data class TranscriptionStats(
    val audioSamples: Long = 0,
    val segments: Int = 0,
    /** Time spent decoding finished utterances. */
    val finalDecodeNanos: Long = 0,
    /** Time spent on provisional decodes of the utterance in progress. */
    val provisionalDecodeNanos: Long = 0,
) {
    val audioMillis: Long get() = audioSamples * 1000 / 16_000

    /** Decoding time over audio duration: below 1, the model is faster than speech. */
    val realTimeFactor: Double?
        get() = if (audioMillis == 0L) null else (finalDecodeNanos + provisionalDecodeNanos) / 1e6 / audioMillis
}
