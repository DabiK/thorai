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
import java.util.concurrent.atomic.AtomicBoolean

/** A use of a shared engine; [close] gives it back. Closing twice has no effect. */
internal class Lease<T : Any>(val engine: T, private val onClose: () -> Unit = {}) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}

/**
 * What a dictation needs: the shared decoder and a fresh segmenter. [close] frees the segmenter
 * and gives the decoder back; it must be called once the dictation no longer uses them.
 */
internal class RecognitionEngines(
    val decoder: SegmentDecoder,
    val segmenter: SpeechSegmenter,
    val hotwords: Boolean,
    private val decoderLease: Lease<SegmentDecoder> = Lease(decoder),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            segmenter.release()
        } finally {
            decoderLease.close()
        }
    }
}

/**
 * A native engine shared by the dictations (or the speeches) in progress. Once [retire]d (a new
 * setting, or [EngineSource.release]), it is freed as soon as no use holds it any more: freeing it
 * under a dictation would crash the native code.
 */
internal class SharedEngine<T : Any>(private val engine: T, private val free: (T) -> Unit) {
    private var users = 0
    private var retired = false
    private var freed = false

    @Synchronized
    fun lease(): Lease<T> {
        check(!retired) { "engine already retired" }
        users++
        return Lease(engine) { giveBack() }
    }

    /** No new use; freed now if unused, otherwise when the last use ends. */
    @Synchronized
    fun retire() {
        retired = true
        freeIfUnused()
    }

    @Synchronized
    private fun giveBack() {
        users--
        freeIfUnused()
    }

    private fun freeIfUnused() {
        if (retired && users == 0 && !freed) {
            freed = true
            free(engine)
        }
    }
}

/** Where the engines come from; faked in tests. */
internal interface EngineSource {
    /** Dispatcher on which every engine call is made, one at a time. */
    val engineDispatcher: CoroutineDispatcher

    /** Installs (reporting progress in 0..1) and loads the recognition models. Close the result after use. */
    suspend fun recognition(onPreparing: (Float?) -> Unit): RecognitionEngines

    /**
     * The loaded synthesizer, to close after use; throws [VoiceException] with
     * [VoiceError.ModelMissing] if not installed.
     */
    suspend fun synthesizer(): Lease<SpeechSynthesizer>

    /** A fresh segmenter, installing the VAD model if needed. */
    suspend fun segmenter(): SpeechSegmenter

    /** Installs every model, then loads the engines, so that the first use is fast. */
    suspend fun prepareAll(onProgress: (Float?) -> Unit)

    /** Frees the native engines once the uses in progress end. They are loaded again on next use. */
    suspend fun release()
}

/**
 * Installs the models, loads each engine once on [engineDispatcher] (never on the main thread),
 * and frees them on [release]. Changing the hotwords setting loads a new decoder; the previous one
 * is freed once the dictations that hold it end.
 */
internal class LocalRuntime(
    private val store: ModelStore,
    private val factory: EngineFactory,
    private val config: LocalConfig,
    override val engineDispatcher: CoroutineDispatcher,
) : EngineSource {
    private val mutex = Mutex()
    private var decoder: SharedEngine<SegmentDecoder>? = null
    private var decoderSettings: DecoderSettings? = null
    private var synthesizer: SharedEngine<SpeechSynthesizer>? = null

    private val segmenterSettings get() = SegmenterSettings(config.minSilenceSeconds, config.maxSpeechSeconds)

    override suspend fun recognition(onPreparing: (Float?) -> Unit): RecognitionEngines {
        install(LocalModels.SPEECH_TO_TEXT, onPreparing)
        val settings = DecoderSettings(config.decoderThreads, config.hotwords())
        val lease = mutex.withLock {
            val shared = decoder?.takeIf { decoderSettings == settings } ?: run {
                onPreparing(null)
                load {
                    decoder?.retire()
                    decoder = null
                    decoderSettings = null
                    SharedEngine(factory.decoder(store.directory(LocalModels.PARAKEET), settings)) { it.release() }
                }.also {
                    decoder = it
                    decoderSettings = settings
                }
            }
            shared.lease()
        }
        val segmenter = try {
            segmenter()
        } catch (e: Throwable) {
            withContext(engineDispatcher) { lease.close() }
            throw e
        }
        return RecognitionEngines(lease.engine, segmenter, settings.hotwords, lease)
    }

    override suspend fun segmenter(): SpeechSegmenter {
        install(listOf(LocalModels.SILERO_VAD)) {}
        return load { factory.segmenter(store.directory(LocalModels.SILERO_VAD), segmenterSettings) }
    }

    override suspend fun synthesizer(): Lease<SpeechSynthesizer> = mutex.withLock {
        val shared = synthesizer ?: run {
            if (store.missing(LocalModels.TEXT_TO_SPEECH).isNotEmpty()) throw VoiceException(VoiceError.ModelMissing)
            load { SharedEngine(factory.synthesizer(store.directory(LocalModels.SUPERTONIC), config.synthesisThreads)) { it.release() } }
                .also { synthesizer = it }
        }
        shared.lease()
    }

    override suspend fun prepareAll(onProgress: (Float?) -> Unit) {
        install(LocalModels.ALL, onProgress)
        val recognition = recognition(onProgress)
        val synthesis = synthesizer()
        withContext(engineDispatcher) {
            recognition.close()
            synthesis.close()
        }
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
            decoder?.retire()
            synthesizer?.retire()
        }
        decoder = null
        decoderSettings = null
        synthesizer = null
    }
}
