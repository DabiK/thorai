package app.regie.voice.local

import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import app.regie.voice.api.VoiceProvider
import app.regie.voice.api.VoiceProviderId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Free, offline voice with sherpa-onnx: Parakeet TDT 0.6B v3 + Silero for transcription,
 * Supertonic 3 for synthesis. Models live in [modelsDir] (app storage), downloaded on first use.
 */
class LocalVoiceProvider internal constructor(
    private val store: ModelStore,
    private val runtime: EngineSource,
    config: LocalConfig,
) : VoiceProvider {

    constructor(
        modelsDir: File,
        config: LocalConfig = LocalConfig(),
        client: OkHttpClient = defaultClient(),
    ) : this(ModelStore(modelsDir, client), config)

    private constructor(store: ModelStore, config: LocalConfig) : this(
        store,
        LocalRuntime(store, SherpaOnnxEngines, config, Dispatchers.Default.limitedParallelism(1, "sherpa-onnx")),
        config,
    )

    override val id = ID
    override val displayName = "Local (sherpa-onnx)"
    override val speechToText = LocalSpeechToText(runtime, config)
    override val textToSpeech = LocalTextToSpeech(runtime, config)

    /** Whether every model is installed; then [prepare] only loads them. */
    val modelsInstalled: Boolean get() = store.missing(LocalModels.ALL).isEmpty()

    /** Bytes left to download before the provider can be used. */
    val remainingDownloadBytes: Long get() = store.remainingDownloadBytes(LocalModels.ALL)

    /** Whether the transcription models (VAD and Parakeet) are installed: all a dictation needs. */
    val speechToTextInstalled: Boolean get() = store.missing(LocalModels.SPEECH_TO_TEXT).isEmpty()

    /** Bytes left to download before a dictation can start, without the synthesis model. */
    val speechToTextDownloadBytes: Long get() = store.remainingDownloadBytes(LocalModels.SPEECH_TO_TEXT)

    /** Free space the transcription models need to be downloaded and installed. */
    val speechToTextFreeBytes: Long get() = store.requiredFreeBytes(LocalModels.SPEECH_TO_TEXT)

    /**
     * Downloads the transcription models only, then loads them, so that the first dictation starts at
     * once (first launch of the app, ticket 28). Synthesis stays for [prepare].
     */
    fun prepareSpeechToText(): Flow<ProviderStatus> = channelFlow {
        try {
            val engines = runtime.recognition { progress -> trySend(ProviderStatus.Downloading(progress)) }
            withContext(NonCancellable + runtime.engineDispatcher) { engines.close() }
            send(ProviderStatus.Ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            send(ProviderStatus.Unavailable(e.error))
        } catch (e: Exception) {
            send(ProviderStatus.Unavailable(VoiceError.Provider("local", e.message)))
        }
    }

    /** Downloads the missing models (reporting [ProviderStatus.Downloading]), then loads them. */
    override fun prepare(): Flow<ProviderStatus> = channelFlow {
        try {
            runtime.prepareAll { progress -> trySend(ProviderStatus.Downloading(progress)) }
            send(ProviderStatus.Ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoiceException) {
            send(ProviderStatus.Unavailable(e.error))
        } catch (e: Exception) {
            send(ProviderStatus.Unavailable(VoiceError.Provider("local", e.message)))
        }
    }

    /** A Silero speech detector, installing its model if needed. Close it after use. */
    suspend fun voiceActivityDetector(): LocalVoiceActivityDetector = LocalVoiceActivityDetector(runtime.segmenter())

    /** Frees the loaded models; they are loaded again on next use. */
    suspend fun release() {
        runtime.release()
    }

    companion object {
        val ID = VoiceProviderId("local")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
