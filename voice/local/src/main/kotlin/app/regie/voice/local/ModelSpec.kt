package app.regie.voice.local

/**
 * A model published by k2-fsa/sherpa-onnx, downloaded on first use into the app storage.
 *
 * Sizes are those of the release files (13 November 2026); they drive the progress when the server
 * gives no length, and the free-space check before downloading.
 */
data class ModelSpec(
    /** Directory of the installed model, under the models root. */
    val name: String,
    val url: String,
    /** A `.tar.bz2` to extract, or a single file kept as is. */
    val archive: Boolean,
    val downloadBytes: Long,
    /** Size once extracted. */
    val installedBytes: Long,
) {
    /** Name of the downloaded file. */
    val fileName: String get() = url.substringAfterLast('/')
}

/** The models of the local provider, from the official sherpa-onnx releases only. */
object LocalModels {
    private const val RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

    /** NVIDIA Parakeet TDT 0.6B v3, int8: 25 European languages, French included. */
    val PARAKEET = ModelSpec(
        name = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        url = "$RELEASES/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
        archive = true,
        downloadBytes = 487_000_000,
        installedBytes = 680_000_000,
    )

    /** Silero VAD: cuts the audio into utterances. */
    val SILERO_VAD = ModelSpec(
        name = "silero-vad",
        url = "$RELEASES/asr-models/silero_vad.onnx",
        archive = false,
        downloadBytes = 650_000,
        installedBytes = 650_000,
    )

    /**
     * Supertonic 3, int8: 31 languages, French included, 10 voices, with its own text frontend.
     * Piper is not usable with sherpa-onnx 2.x: espeak-ng was removed and its voices need
     * phonemes computed by the app.
     */
    val SUPERTONIC = ModelSpec(
        name = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
        url = "$RELEASES/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2",
        archive = true,
        downloadBytes = 128_774_318,
        installedBytes = 150_000_000,
    )

    val SPEECH_TO_TEXT = listOf(SILERO_VAD, PARAKEET)
    val TEXT_TO_SPEECH = listOf(SUPERTONIC)
    val ALL = SPEECH_TO_TEXT + TEXT_TO_SPEECH
}
