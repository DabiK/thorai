package app.regie.voice.local

import java.io.File

// Small seams around sherpa-onnx, so that the logic of the adapter (segments, partials, final
// text, interruption) is tested on the JVM with fakes, without native code nor models.

/** Decodes one utterance (Parakeet). Not thread-safe: called from one thread at a time. */
internal interface SegmentDecoder {
    /** [samples] in -1..1 at 16 kHz; [hotwords] are phrases separated by `/`, or null. */
    fun decode(samples: FloatArray, hotwords: String?): String

    fun release()
}

/** Cuts audio into utterances (Silero VAD). One instance per dictation. */
internal interface SpeechSegmenter {
    /** Feeds samples in -1..1 at 16 kHz, of any length. */
    fun accept(samples: FloatArray)

    /** Whether speech is going on at the end of what was fed. */
    fun isSpeechDetected(): Boolean

    /** Ends the current utterance, if any, so that [drain] returns it. */
    fun flush()

    /** Removes and returns the utterances completed so far, oldest first. */
    fun drain(): List<SpeechSpan>

    fun reset()

    fun release()
}

/** An utterance found by the segmenter: [start] counts samples since the segmenter was created. */
internal data class SpeechSpan(val start: Long, val length: Int) {
    val end: Long get() = start + length
}

/** Speaks text (Supertonic). Not thread-safe. */
internal interface SpeechSynthesizer {
    val sampleRateHz: Int

    /**
     * Generates [text] in [language] (ISO 639-1) with voice [speaker], calling [onAudio] as soon as
     * each sentence is ready; generation stops when [onAudio] returns false.
     */
    fun generate(text: String, language: String, speaker: Int, speed: Float, onAudio: (FloatArray) -> Boolean)

    fun release()
}

/** Creates the engines from installed model directories. */
internal interface EngineFactory {
    fun decoder(modelDir: File, settings: DecoderSettings): SegmentDecoder

    fun segmenter(modelDir: File, settings: SegmenterSettings): SpeechSegmenter

    fun synthesizer(modelDir: File, threads: Int): SpeechSynthesizer
}

internal data class DecoderSettings(val threads: Int, val hotwords: Boolean)

internal data class SegmenterSettings(val minSilenceSeconds: Float, val maxSpeechSeconds: Float)
