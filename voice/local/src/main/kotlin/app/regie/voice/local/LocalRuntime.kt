package app.regie.voice.local

import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What a dictation needs: the shared decoder and a fresh segmenter. */
internal class RecognitionEngines(val decoder: SegmentDecoder, val segmenter: SpeechSegmenter, val hotwords: Boolean)

/** Where the engines come from; faked in tests. */
internal interface EngineSource {
    /** Dispatcher on which every engine call is made, one at a time. */
    val engineDispatcher: CoroutineDispatcher

    /** Installs (reporting progress in 0..1) and loads the recognition models. */
    suspend fun recognition(onPreparing: (Float?) -> Unit): RecognitionEngines

    /** The loaded synthesizer; throws [VoiceException] with [VoiceError.ModelMissing] if not installed. */
    suspend fun synthesizer(): SpeechSynthesizer

    /** A fresh segmenter, installing the VAD model if needed. */
    suspend fun segmenter(): SpeechSegmenter

    /** Installs every model, then loads the engines, so that the first use is fast. */
    suspend fun prepareAll(onProgress: (Float?) -> Unit)

    /** Frees the native engines. They are loaded again on next use. */
    suspend fun release()
}

/**
 * Installs the models, loads each engine once on [engineDispatcher] (never on the main thread),
 * and frees them on [release]. Changing the hotwords setting reloads the decoder.
 */
internal class LocalRuntime(
    private val store: ModelStore,
    private val factory: EngineFactory,
    private val config: LocalConfig,
    override val engineDispatcher: CoroutineDispatcher,
) : EngineSource {
    private val mutex = Mutex()
    private var decoder: SegmentDecoder? = null
    private var decoderSettings: DecoderSettings? = null
    private var synthesizer: SpeechSynthesizer? = null

    private val segmenterSettings get() = SegmenterSettings(config.minSilenceSeconds, config.maxSpeechSeconds)

    override suspend fun recognition(onPreparing: (Float?) -> Unit): RecognitionEngines {
        install(LocalModels.SPEECH_TO_TEXT, onPreparing)
        val settings = DecoderSettings(config.decoderThreads, config.hotwords())
        val loaded = mutex.withLock {
            decoder?.takeIf { decoderSettings == settings } ?: run {
                onPreparing(null)
                load {
                    decoder?.release()
                    decoder = null
                    factory.decoder(store.directory(LocalModels.PARAKEET), settings)
                }.also {
                    decoder = it
                    decoderSettings = settings
                }
            }
        }
        return RecognitionEngines(loaded, segmenter(), settings.hotwords)
    }

    override suspend fun segmenter(): SpeechSegmenter {
        install(listOf(LocalModels.SILERO_VAD)) {}
        return load { factory.segmenter(store.directory(LocalModels.SILERO_VAD), segmenterSettings) }
    }

    override suspend fun synthesizer(): SpeechSynthesizer = mutex.withLock {
        synthesizer ?: run {
            if (store.missing(LocalModels.TEXT_TO_SPEECH).isNotEmpty()) throw VoiceException(VoiceError.ModelMissing)
            load { factory.synthesizer(store.directory(LocalModels.SUPERTONIC), config.synthesisThreads) }.also { synthesizer = it }
        }
    }

    override suspend fun prepareAll(onProgress: (Float?) -> Unit) {
        install(LocalModels.ALL, onProgress)
        recognition(onProgress).segmenter.release()
        synthesizer()
    }

    private suspend fun install(specs: List<ModelSpec>, onProgress: (Float?) -> Unit) {
        if (store.missing(specs).isEmpty()) return
        onProgress(0f)
        coroutineScope {
            val watcher = launch { store.progress.filterNotNull().collect { onProgress(it) } }
            try {
                store.install(specs)
            } finally {
                watcher.cancel()
            }
        }
    }

    private suspend fun <T> load(block: () -> T): T = withContext(engineDispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A model that cannot be loaded is as good as missing.
            throw VoiceException(VoiceError.Provider("model_load", e.message), e)
        }
    }

    override suspend fun release() = mutex.withLock {
        withContext(engineDispatcher) {
            decoder?.release()
            synthesizer?.release()
        }
        decoder = null
        decoderSettings = null
        synthesizer = null
    }
}
