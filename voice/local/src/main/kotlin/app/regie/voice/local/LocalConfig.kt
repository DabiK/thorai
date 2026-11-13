package app.regie.voice.local

/** Settings of the local provider. [hotwords] is read before each dictation. */
class LocalConfig(
    /**
     * Favour the dictation vocabulary with sherpa-onnx hotwords. It switches Parakeet from greedy
     * search to modified beam search, which is slower: off by default, to be judged on measures.
     */
    val hotwords: () -> Boolean = { false },
    /** A provisional decode of the utterance in progress is made every [partialIntervalMs] of audio. */
    val partialIntervalMs: Int = 700,
    /** Silence that ends an utterance. */
    val minSilenceSeconds: Float = 0.3f,
    /** An utterance longer than this is cut by the VAD. */
    val maxSpeechSeconds: Float = 12f,
    val decoderThreads: Int = 4,
    val synthesisThreads: Int = 4,
    /** Speed of the synthesized voice; 1 is the model's own pace. */
    val speechSpeed: Float = 1f,
    /** Synthesis voice, one of [LocalTextToSpeech.voices] (Supertonic has 10), read at each use. */
    val speaker: () -> Int = { 0 },
)
