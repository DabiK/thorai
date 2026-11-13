package app.regie.voice.local

import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalRuntimeTest {
    @TempDir
    lateinit var root: File

    private val factory = FakeFactory()
    private var hotwords = false

    /** A store whose models are already installed: no network in these tests. */
    private fun store(vararg installed: ModelSpec) = ModelStore(root, OkHttpClient()).also { store ->
        installed.forEach { spec -> store.directory(spec).mkdirs(); File(store.directory(spec), ModelStore.MARKER).writeText("") }
    }

    private fun runtime(store: ModelStore) =
        LocalRuntime(store, factory, LocalConfig(hotwords = { hotwords }), Dispatchers.Unconfined)

    @Test
    fun `the decoder is loaded once, each dictation gets its own segmenter`() = runBlocking {
        val runtime = runtime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()))

        val first = runtime.recognition {}
        val second = runtime.recognition {}

        assertSame(first.decoder, second.decoder)
        assertEquals(1, factory.decoders.size)
        assertEquals(2, factory.segmenters.size)
    }

    @Test
    fun `changing the hotwords setting reloads the decoder`() = runBlocking {
        val runtime = runtime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()))

        runtime.recognition {}.close()
        hotwords = true
        val engines = runtime.recognition {}

        assertTrue(engines.hotwords)
        assertEquals(listOf(false, true), factory.decoders.map { it.second.hotwords })
        assertTrue(factory.decoders.first().first.released)
    }

    @Test
    fun `a decoder replaced by a new setting is freed only once its dictations end`() = runBlocking {
        val runtime = runtime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()))
        val running = runtime.recognition {}
        val old = factory.decoders.single().first

        hotwords = true
        val next = runtime.recognition {}

        assertFalse(old.released, "the decoder of the dictation in progress was freed")
        running.close()
        assertTrue(old.released)
        assertFalse(factory.decoders.last().first.released)
        next.close()
        assertFalse(factory.decoders.last().first.released, "the current decoder stays loaded")
    }

    @Test
    fun `release waits for the dictations and the speech in progress`() = runBlocking {
        val runtime = runtime(store(*LocalModels.ALL.toTypedArray()))
        val dictation = runtime.recognition {}
        val speech = runtime.synthesizer()

        runtime.release()

        assertFalse(factory.decoders.single().first.released)
        assertFalse(factory.synthesizers.single().released)
        dictation.close()
        speech.close()
        assertTrue(factory.decoders.single().first.released)
        assertTrue(factory.synthesizers.single().released)
        assertTrue(factory.segmenters.single().released)
        runtime.recognition {}
        assertEquals(2, factory.decoders.size, "loaded again after release")
    }

    @Test
    fun `closing a dictation twice gives its decoder back once`() = runBlocking {
        val runtime = runtime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()))
        val first = runtime.recognition {}
        runtime.recognition {}

        first.close()
        first.close()
        runtime.release()

        assertFalse(factory.decoders.single().first.released, "the second dictation still uses it")
    }

    @Test
    fun `loading reports a preparing state without progress`() = runBlocking {
        val runtime = runtime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()))
        val reported = mutableListOf<Float?>()

        runtime.recognition { reported += it }
        runtime.recognition { reported += it }

        assertEquals(listOf<Float?>(null), reported)
    }

    @Test
    fun `synthesis needs its model to be installed`() = runBlocking {
        val runtime = runtime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()))

        val error = assertFailsWith<VoiceException> { runtime.synthesizer() }

        assertEquals(VoiceError.ModelMissing, error.error)
        assertTrue(factory.synthesizers.isEmpty())
    }

    @Test
    fun `release frees the engines, which load again on next use`() = runBlocking {
        val runtime = runtime(store(*LocalModels.ALL.toTypedArray()))
        runtime.recognition {}.close()
        runtime.synthesizer().close()

        runtime.release()
        runtime.recognition {}

        assertTrue(factory.decoders.first().first.released)
        assertTrue(factory.synthesizers.single().released)
        assertEquals(2, factory.decoders.size)
    }

    @Test
    fun `a decoder loaded while its dictation is cancelled is kept, then freed on release`() = runBlocking {
        lateinit var dictation: Job
        // The dictation is cancelled while the native decoder loads, as when L2 is released early.
        val cancelling = object : EngineFactory by factory {
            override fun decoder(modelDir: File, settings: DecoderSettings): SegmentDecoder =
                factory.decoder(modelDir, settings).also { dictation.cancel() }
        }
        val runtime = LocalRuntime(
            store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()), cancelling, LocalConfig(hotwords = { hotwords }), Dispatchers.Unconfined,
        )

        dictation = launch { runtime.recognition {}.close() }
        dictation.join()

        assertTrue(dictation.isCancelled)
        runtime.recognition {}.close()
        assertEquals(1, factory.decoders.size, "the loaded decoder is reused, not loaded twice")
        runtime.release()
        assertTrue(factory.decoders.single().first.released, "the decoder leaked")
    }

    @Test
    fun `a synthesizer loaded while its speech is cancelled is kept, then freed on release`() = runBlocking {
        lateinit var speech: Job
        val cancelling = object : EngineFactory by factory {
            override fun synthesizer(modelDir: File, threads: Int): SpeechSynthesizer =
                factory.synthesizer(modelDir, threads).also { speech.cancel() }
        }
        val runtime = LocalRuntime(store(*LocalModels.ALL.toTypedArray()), cancelling, LocalConfig(), Dispatchers.Unconfined)

        speech = launch { runtime.synthesizer().close() }
        speech.join()

        assertTrue(speech.isCancelled)
        runtime.synthesizer().close()
        assertEquals(1, factory.synthesizers.size, "the loaded synthesizer is reused, not loaded twice")
        runtime.release()
        assertTrue(factory.synthesizers.single().released, "the synthesizer leaked")
    }

    @Test
    fun `a segmenter created while its dictation is cancelled is freed`() = runBlocking {
        lateinit var dictation: Job
        val cancelling = object : EngineFactory by factory {
            override fun segmenter(modelDir: File, settings: SegmenterSettings): SpeechSegmenter =
                factory.segmenter(modelDir, settings).also { dictation.cancel() }
        }
        val runtime = LocalRuntime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()), cancelling, LocalConfig(), Dispatchers.Unconfined)

        dictation = launch { runtime.recognition {}.close() }
        dictation.join()

        assertTrue(dictation.isCancelled)
        assertTrue(factory.segmenters.single().released, "the segmenter leaked")
        runtime.release()
        assertTrue(factory.decoders.single().first.released, "the decoder lease was not given back")
    }

    @Test
    fun `a preparation cancelled while the synthesizer loads gives the decoder back`() = runBlocking {
        lateinit var preparation: Job
        val cancelling = object : EngineFactory by factory {
            override fun synthesizer(modelDir: File, threads: Int): SpeechSynthesizer =
                factory.synthesizer(modelDir, threads).also { preparation.cancel() }
        }
        val runtime = LocalRuntime(store(*LocalModels.ALL.toTypedArray()), cancelling, LocalConfig(), Dispatchers.Unconfined)

        preparation = launch { runtime.prepareAll {} }
        preparation.join()
        runtime.release()

        assertTrue(preparation.isCancelled)
        assertTrue(factory.decoders.single().first.released, "the decoder leaked")
        assertTrue(factory.synthesizers.single().released, "the synthesizer leaked")
    }

    @Test
    fun `an engine that cannot load is reported as a provider error`() = runBlocking {
        val broken = object : EngineFactory by factory {
            override fun decoder(modelDir: File, settings: DecoderSettings): SegmentDecoder = throw IllegalArgumentException("bad model")
        }
        val runtime = LocalRuntime(store(*LocalModels.SPEECH_TO_TEXT.toTypedArray()), broken, LocalConfig(), Dispatchers.Unconfined)

        val error = assertFailsWith<VoiceException> { runtime.recognition {} }

        assertEquals(VoiceError.Provider("model_load", "bad model"), error.error)
    }
}

class LocalVoiceProviderTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `prepare reports the download, then ready`() = runTest {
        val engines = FakeEngineSource(prepare = { progress -> progress(0f); progress(0.5f); progress(1f) })
        val provider = LocalVoiceProvider(ModelStore(root, OkHttpClient()), engines, LocalConfig())

        val statuses = provider.prepare().toList()

        assertEquals(
            listOf(ProviderStatus.Downloading(0f), ProviderStatus.Downloading(0.5f), ProviderStatus.Downloading(1f), ProviderStatus.Ready),
            statuses,
        )
    }

    @Test
    fun `prepare reports a failure as unavailable`() = runTest {
        val engines = FakeEngineSource(prepare = { throw VoiceException(VoiceError.Offline) })
        val provider = LocalVoiceProvider(ModelStore(root, OkHttpClient()), engines, LocalConfig())

        assertEquals(listOf<ProviderStatus>(ProviderStatus.Unavailable(VoiceError.Offline)), provider.prepare().toList())
    }

    @Test
    fun `models are reported missing until installed`() {
        val provider = LocalVoiceProvider(root)

        assertFalse(provider.modelsInstalled)
        assertEquals(LocalModels.ALL.sumOf { it.downloadBytes }, provider.remainingDownloadBytes)
        assertEquals("local", provider.id.value)
    }
}
