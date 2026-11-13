package app.regie.voice.local

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.SpeechActivity
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import app.regie.voice.api.WavAudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalTextToSpeechTest {
    @Test
    fun `each sentence is emitted as a frame at the model rate`() = runTest {
        val engines = FakeEngineSource()

        val frames = LocalTextToSpeech(engines, LocalConfig(speaker = { 3 })).synthesize("Bonjour. La session est prête. Fin.").toList()

        assertEquals(3, frames.size)
        assertTrue(frames.all { it.format == AudioFormat(22_050) && it.samples.size == 2_205 })
        assertEquals(
            listOf(Triple("Bonjour.", "fr", 3), Triple("La session est prête.", "fr", 3), Triple("Fin.", "fr", 3)),
            engines.synthesizer.generated,
        )
    }

    @Test
    fun `stopping the collection stops the engine`() = runTest {
        // The engine runs on its own thread, as on the device.
        val engines = FakeEngineSource(synthesizer = FakeSynthesizer(sentenceMillis = 50), engineDispatcher = Dispatchers.Default)

        LocalTextToSpeech(engines, LocalConfig()).synthesize("Un. Deux. Trois. Quatre. Cinq.").first()

        assertTrue(engines.synthesizer.chunksAccepted < 5, "generated ${engines.synthesizer.chunksAccepted} sentences")
    }

    @Test
    fun `cancelling does not wait for the sentence being generated`() = runTest {
        // Each sentence takes 2 s to generate: cancelling must not wait for it.
        val engines = FakeEngineSource(synthesizer = FakeSynthesizer(sentenceMillis = 2_000), engineDispatcher = Dispatchers.Default)

        val start = System.nanoTime()
        withTimeoutOrNull(100) { LocalTextToSpeech(engines, LocalConfig()).synthesize("Un. Deux.").toList() }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(elapsedMs < 1_000, "cancelling took $elapsedMs ms")
    }

    @Test
    fun `an engine failure is reported as a voice error`() = runTest {
        val broken = object : SpeechSynthesizer by FakeSynthesizer() {
            override fun generate(text: String, language: String, speaker: Int, speed: Float, onAudio: (FloatArray) -> Boolean) =
                throw IllegalStateException("native error")
        }
        val engines = object : EngineSource by FakeEngineSource() {
            override suspend fun synthesizer(): SpeechSynthesizer = broken
        }

        val error = assertFailsWith<VoiceException> { LocalTextToSpeech(engines, LocalConfig()).synthesize("Bonjour.").toList() }

        assertEquals(VoiceError.Provider("local_tts", "native error"), error.error)
    }

    @Test
    fun `a missing model is reported`() = runTest {
        val engines = FakeEngineSource(synthesizerMissing = VoiceException(VoiceError.ModelMissing))

        val error = assertFailsWith<VoiceException> { LocalTextToSpeech(engines, LocalConfig()).synthesize("Bonjour.").toList() }

        assertEquals(VoiceError.ModelMissing, error.error)
    }

    @Test
    fun `text is cut into sentences, without breaking file names`() {
        assertEquals(
            listOf("Bonjour !", "Ouvre AgentGateway.kt et lance npm run check.", "Prêt ?", "Oui : vas-y…", "Fin"),
            LocalTextToSpeech.sentences(" Bonjour ! Ouvre AgentGateway.kt et lance npm run check. Prêt ? Oui : vas-y… Fin "),
        )
    }

    @Test
    fun `samples are converted to 16-bit PCM and clipped`() {
        assertContentEquals(
            shortArrayOf(0, Short.MAX_VALUE, (-Short.MAX_VALUE).toShort(), Short.MAX_VALUE, 16_383),
            LocalTextToSpeech.toPcm(floatArrayOf(0f, 1f, -1f, 3f, 0.5f)),
        )
    }

    @Test
    fun `the ten Supertonic voices are offered by speaker number`() = runTest {
        assertEquals((0..9).map { "$it" }, LocalTextToSpeech(FakeEngineSource(), LocalConfig()).voices().map { it.id })
    }
}

class LocalVoiceActivityDetectorTest {
    @Test
    fun `speech is detected in a recording, then silence after it`() {
        val segmenter = FakeSegmenter()
        val detector = LocalVoiceActivityDetector(segmenter)
        val source = WavAudioSource(TestWavs.bytes("consigne-02.wav"), frameDurationMs = 100)
        val frames = source.samples.toList().chunked(1_600) { app.regie.voice.api.AudioFrame(it.toShortArray(), AudioFormat.SPEECH) }
        val silence = app.regie.voice.api.AudioFrame(ShortArray(16_000), AudioFormat.SPEECH)

        val activity = frames.map(detector::process)
        val after = detector.process(silence)

        assertTrue(SpeechActivity.SPEECH in activity)
        assertEquals(SpeechActivity.SILENCE, after)
        detector.close()
        assertTrue(segmenter.released)
    }
}
