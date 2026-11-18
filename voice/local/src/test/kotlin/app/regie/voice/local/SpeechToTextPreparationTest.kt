package app.regie.voice.local

import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechToTextPreparationTest {
    @TempDir
    lateinit var root: File

    private fun installed(store: ModelStore, vararg specs: ModelSpec) = specs.forEach { spec ->
        store.directory(spec).mkdirs(); File(store.directory(spec), ModelStore.MARKER).writeText("")
    }

    @Test
    fun `transcription alone is ready without the synthesis model, sizes leave synthesis out`() {
        val store = ModelStore(root, OkHttpClient())
        val provider = LocalVoiceProvider(store, FakeEngineSource(), LocalConfig())
        val expected = LocalModels.SPEECH_TO_TEXT.sumOf { it.downloadBytes }
        assertFalse(provider.speechToTextInstalled)
        assertEquals(expected, provider.speechToTextDownloadBytes)
        assertTrue(provider.speechToTextFreeBytes > expected + LocalModels.PARAKEET.installedBytes)

        installed(store, *LocalModels.SPEECH_TO_TEXT.toTypedArray())
        assertTrue(provider.speechToTextInstalled)
        assertFalse(provider.modelsInstalled)
        assertEquals(0, provider.speechToTextDownloadBytes)
        assertEquals(0, provider.speechToTextFreeBytes)
    }

    @Test
    fun `preparing the transcription reports progress, gives the engines back and ends ready`() = runTest {
        val engines = FakeEngineSource(prepare = { progress -> progress(0.5f); progress(null) })
        val provider = LocalVoiceProvider(ModelStore(root, OkHttpClient()), engines, LocalConfig())
        val statuses = provider.prepareSpeechToText().toList()
        assertEquals(listOf(ProviderStatus.Downloading(0.5f), ProviderStatus.Downloading(null), ProviderStatus.Ready), statuses)
        assertEquals(0, engines.openLeases)
    }

    @Test
    fun `a failed download ends unavailable with its voice error`() = runTest {
        val engines = FakeEngineSource(prepare = { throw VoiceException(VoiceError.Offline) })
        val provider = LocalVoiceProvider(ModelStore(root, OkHttpClient()), engines, LocalConfig())
        assertEquals(listOf<ProviderStatus>(ProviderStatus.Unavailable(VoiceError.Offline)), provider.prepareSpeechToText().toList())
    }
}
