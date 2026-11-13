package app.regie.voice.local

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** The engines, with sherpa-onnx (v2.0.1). Only this file knows the sherpa-onnx API. */
internal object SherpaOnnxEngines : EngineFactory {
    private const val SAMPLE_RATE = 16_000

    override fun decoder(modelDir: File, settings: DecoderSettings): SegmentDecoder {
        val tokens = File(modelDir, "tokens.txt")
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = modelDir.onnx("encoder"),
                    decoder = modelDir.onnx("decoder"),
                    joiner = modelDir.onnx("joiner"),
                ),
                tokens = tokens.path,
                modelType = "nemo_transducer",
                numThreads = settings.threads,
                // Hotwords are given as words: they need a BPE vocabulary to be encoded.
                modelingUnit = if (settings.hotwords) "bpe" else "",
                bpeVocab = if (settings.hotwords) bpeVocab(tokens).path else "",
            ),
            // Hotwords only work with modified_beam_search.
            decodingMethod = if (settings.hotwords) "modified_beam_search" else "greedy_search",
            hotwordsScore = HOTWORDS_SCORE,
        )
        val recognizer = OfflineRecognizer(null, config)
        return object : SegmentDecoder {
            override fun decode(samples: FloatArray, hotwords: String?): String {
                val stream = if (settings.hotwords && !hotwords.isNullOrBlank()) {
                    recognizer.createStream(hotwords)
                } else {
                    recognizer.createStream()
                }
                try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    recognizer.decode(stream)
                    return recognizer.getResult(stream).text
                } finally {
                    stream.release()
                }
            }

            override fun release() = recognizer.release()
        }
    }

    override fun segmenter(modelDir: File, settings: SegmenterSettings): SpeechSegmenter {
        val vad = Vad(
            null,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = File(modelDir, LocalModels.SILERO_VAD.fileName).path,
                    threshold = 0.5f,
                    minSilenceDuration = settings.minSilenceSeconds,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = settings.maxSpeechSeconds,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
            ),
        )
        return object : SpeechSegmenter {
            override fun accept(samples: FloatArray) = vad.acceptWaveform(samples)

            override fun isSpeechDetected() = vad.isSpeechDetected()

            override fun flush() = vad.flush()

            override fun drain(): List<SpeechSpan> {
                val spans = mutableListOf<SpeechSpan>()
                while (!vad.empty()) {
                    val segment = vad.front()
                    spans += SpeechSpan(segment.start, segment.samples.size)
                    vad.pop()
                }
                return spans
            }

            override fun reset() = vad.reset()

            override fun release() = vad.release()
        }
    }

    override fun synthesizer(modelDir: File, threads: Int): SpeechSynthesizer {
        val tts = OfflineTts(
            null,
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = OfflineTtsSupertonicModelConfig(
                        durationPredictor = modelDir.onnx("duration_predictor"),
                        textEncoder = modelDir.onnx("text_encoder"),
                        vectorEstimator = modelDir.onnx("vector_estimator"),
                        vocoder = modelDir.onnx("vocoder"),
                        ttsJson = File(modelDir, "tts.json").path,
                        unicodeIndexer = File(modelDir, "unicode_indexer.bin").path,
                        voiceStyle = File(modelDir, "voice.bin").path,
                    ),
                    numThreads = threads,
                ),
                // One sentence per callback: the first sentence plays while the next is generated.
                maxNumSentences = 1,
            ),
        )
        return object : SpeechSynthesizer {
            override val sampleRateHz: Int = tts.sampleRate()

            override fun generate(text: String, language: String, speaker: Int, speed: Float, onAudio: (FloatArray) -> Boolean) {
                val config = GenerationConfig(sid = speaker, speed = speed, numSteps = SUPERTONIC_STEPS, extra = mapOf("lang" to language))
                tts.generateWithConfigAndCallback(text, config, AudioCallback(onAudio))
            }

            override fun release() = tts.release()
        }
    }

    /**
     * The native code calls `invoke([F)Ljava/lang/Integer;` on the callback. A Kotlin lambda,
     * compiled with invokedynamic then desugared by D8, only has the erased `invoke(Object)`: the
     * JNI lookup fails and the process aborts. A class keeps the typed method.
     */
    private class AudioCallback(private val onAudio: (FloatArray) -> Boolean) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int = if (onAudio(samples)) 1 else 0
    }

    /** The `.onnx` file of [modelDir] whose name starts with [prefix] (int8 preferred). */
    private fun File.onnx(prefix: String): String {
        val candidates = listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".onnx") }.orEmpty()
        val file = candidates.firstOrNull { it.name.contains(".int8.") } ?: candidates.firstOrNull()
        return requireNotNull(file) { "no $prefix*.onnx in $this" }.path
    }

    /**
     * Parakeet releases ship no `bpe.vocab`. As in the sherpa-onnx hotwords example for Parakeet,
     * one is derived from `tokens.txt`: equal scores make the encoder a longest match.
     */
    private fun bpeVocab(tokens: File): File {
        val vocab = File(tokens.parentFile, "bpe.vocab")
        if (!vocab.isFile) {
            val lines = tokens.readLines().filter { it.isNotBlank() }.map { "${it.substringBefore(' ')}\t-1.0" }
            vocab.writeText(lines.joinToString("\n", postfix = "\n"))
        }
        return vocab
    }

    private const val HOTWORDS_SCORE = 2.0f

    /** Denoising steps of Supertonic, as in the sherpa-onnx examples. */
    private const val SUPERTONIC_STEPS = 8
}
