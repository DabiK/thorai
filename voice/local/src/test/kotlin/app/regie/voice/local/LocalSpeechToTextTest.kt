package app.regie.voice.local

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.Wav
import app.regie.voice.api.WavAudioSource
import app.regie.voice.api.startDictation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSpeechToTextTest {
    private val config = LocalConfig(partialIntervalMs = 700)

    private fun wav(name: String) = WavAudioSource(TestWavs.bytes(name), frameDurationMs = 100)

    /**
     * What the fake decoder gives for [samples]: one decode from each cut to the end of the next
     * utterance, the first one from the very start of the audio.
     */
    private fun expectedFinal(samples: ShortArray): String {
        val segmenter = FakeSegmenter()
        segmenter.accept(SegmentedTranscriber.toFloat(samples))
        segmenter.flush()
        var cut = 0L
        return segmenter.drain().joinToString(" ") { span -> "[${span.end - cut}]".also { cut = span.end } }
    }

    @Test
    fun `a recording gives growing partials, then the final text of its utterances`() = runTest {
        val engines = FakeEngineSource()
        val source = wav("consigne-02.wav")

        val states = LocalSpeechToText(engines, config).transcribe(source.frames(), DictationRequest()).toList()

        assertEquals(DictationState.Listening(""), states.first())
        val partials = states.filterIsInstance<DictationState.Listening>().map { it.partial }.filter { it.isNotEmpty() }
        assertTrue(partials.isNotEmpty(), "no partial in $states")
        assertIs<DictationState.Finalizing>(states[states.size - 2])
        val final = assertIs<DictationState.Final>(states.last())
        assertEquals(expectedFinal(source.samples), final.text)
        assertTrue(final.text.isNotEmpty())
        assertTrue(engines.segmenters.single().released, "segmenter not released")
    }

    @Test
    fun `the utterance in progress is decoded again every 700 ms of speech`() = runTest {
        val engines = FakeEngineSource()

        LocalSpeechToText(engines, config).transcribe(wav("consigne-13.wav").frames(), DictationRequest()).toList()

        val interval = AudioFormat.SPEECH.samplesFor(700)
        // One utterance: each provisional decode covers one more interval than the previous one,
        // then the final decode covers the whole utterance.
        val sizes = engines.decoder.calls.map { it.first }
        val provisional = sizes.dropLast(1)
        assertTrue(provisional.size >= 2, "decodes: $sizes")
        assertEquals(List(provisional.size - 1) { interval }, provisional.zipWithNext { a, b -> b - a })
        assertTrue(sizes.last() >= provisional.last(), "decodes: $sizes")
    }

    @Test
    fun `releasing the button in the middle of a sentence keeps its last words`() = runTest {
        val engines = FakeEngineSource()
        val full = wav("consigne-17.wav").samples
        // Cut while speaking: the segmenter has an utterance in progress.
        val cut = full.copyOf(AudioFormat.SPEECH.samplesFor(1_500))
        val audio = WavAudioSource(Wav.encode(cut, AudioFormat.SPEECH), frameDurationMs = 100)

        val final = LocalSpeechToText(engines, config).transcribe(audio.frames(), DictationRequest()).toList().last()

        assertEquals(DictationState.Final(expectedFinal(cut)), final)
        assertTrue(assertIs<DictationState.Final>(final).text.isNotEmpty())
    }

    @Test
    fun `the final text covers every sample once, words before the segmenter triggers included`() = runTest {
        // A quiet start, below the threshold of the segmenter, then the sentence.
        val quiet = ShortArray(8_000) { if (it % 2 == 0) 200 else -200 }
        val samples = quiet + wav("consigne-02.wav").samples
        val audio = WavAudioSource(Wav.encode(samples, AudioFormat.SPEECH), frameDurationMs = 100)

        val final = LocalSpeechToText(FakeEngineSource(), config).transcribe(audio.frames(), DictationRequest()).toList().last()

        val decoded = Regex("""\[(\d+)]""").findAll(assertIs<DictationState.Final>(final).text).map { it.groupValues[1].toInt() }.toList()
        assertEquals(samples.size, decoded.sum())
    }

    @Test
    fun `a word too short for the segmenter is still decoded`() = runTest {
        val word = flowOf(AudioFrame(ShortArray(8_000) { if (it % 2 == 0) 200 else -200 }, AudioFormat.SPEECH))

        val final = LocalSpeechToText(FakeEngineSource(), config).transcribe(word, DictationRequest()).toList().last()

        assertEquals(DictationState.Final("[8000]"), final)
    }

    @Test
    fun `silence after the last utterance is not decoded`() = runTest {
        val engines = FakeEngineSource()
        val samples = wav("consigne-17.wav").samples + ShortArray(32_000)
        val audio = WavAudioSource(Wav.encode(samples, AudioFormat.SPEECH), frameDurationMs = 100)

        LocalSpeechToText(engines, config).transcribe(audio.frames(), DictationRequest()).toList()

        assertTrue(engines.decoder.calls.none { it.first > wav("consigne-17.wav").samples.size + 16_000 }, "${engines.decoder.calls}")
    }

    @Test
    fun `silence gives an empty final text`() = runTest {
        val silence = flowOf(AudioFrame(ShortArray(16_000), AudioFormat.SPEECH))

        val states = LocalSpeechToText(FakeEngineSource(), config).transcribe(silence, DictationRequest()).toList()

        assertEquals(listOf(DictationState.Listening(""), DictationState.Finalizing(""), DictationState.Final("")), states)
    }

    @Test
    fun `audio said while the models are prepared is not lost`() = runTest {
        val source = wav("consigne-02.wav")
        val allRead = CompletableDeferred<Unit>()
        val audio: Flow<AudioFrame> = flow {
            source.frames().collect { emit(it) }
            allRead.complete(Unit)
        }
        val engines = FakeEngineSource(prepare = { progress ->
            progress(0f)
            progress(0.5f)
            allRead.await() // the whole recording is read before the models are ready
            progress(null)
        })

        val states = LocalSpeechToText(engines, config).transcribe(audio, DictationRequest()).toList()

        assertEquals(
            listOf(DictationState.PreparingModel(0f), DictationState.PreparingModel(0.5f), DictationState.PreparingModel(null)),
            states.take(3),
        )
        assertEquals(DictationState.Final(expectedFinal(source.samples)), states.last())
    }

    @Test
    fun `a failed download fails the dictation`() = runTest {
        val engines = FakeEngineSource(prepare = { throw VoiceException(VoiceError.Offline) })

        val states = LocalSpeechToText(engines, config).transcribe(wav("consigne-02.wav").frames(), DictationRequest()).toList()

        assertEquals(listOf<DictationState>(DictationState.Failed(VoiceError.Offline)), states)
    }

    @Test
    fun `a decoder crash fails the dictation and releases the segmenter`() = runTest {
        val engines = FakeEngineSource(decoder = FakeDecoder(failAfter = 0))

        val last = LocalSpeechToText(engines, config).transcribe(wav("consigne-02.wav").frames(), DictationRequest()).toList().last()

        assertEquals(DictationState.Failed(VoiceError.Provider("local", "decoder crashed")), last)
        assertTrue(engines.segmenters.single().released)
        assertEquals(0, engines.openLeases, "the decoder is given back")
    }

    @Test
    fun `an unexpected error while preparing is reported as a failure, not thrown`() = runTest {
        val engines = FakeEngineSource(prepare = { throw IllegalStateException("cannot rename x.part") })

        val states = LocalSpeechToText(engines, config).transcribe(wav("consigne-02.wav").frames(), DictationRequest()).toList()

        assertEquals(listOf<DictationState>(DictationState.Failed(VoiceError.Provider("local", "cannot rename x.part"))), states)
    }

    @Test
    fun `a finished dictation gives its decoder back`() = runTest {
        val engines = FakeEngineSource()

        LocalSpeechToText(engines, config).transcribe(wav("consigne-02.wav").frames(), DictationRequest()).toList()

        assertEquals(0, engines.openLeases)
    }

    @Test
    fun `audio at another sample rate is refused`() = runTest {
        val audio = flowOf(AudioFrame(ShortArray(4_410), AudioFormat(44_100)))

        val last = LocalSpeechToText(FakeEngineSource(), config).transcribe(audio, DictationRequest()).toList().last()

        assertEquals(VoiceError.Provider("audio_format", "expected 16 kHz, got 44100 Hz"), assertIs<DictationState.Failed>(last).error)
    }

    @Test
    fun `cancelling the dictation releases the segmenter`() = runTest {
        val engines = FakeEngineSource()

        LocalSpeechToText(engines, config).transcribe(wav("consigne-02.wav").frames(), DictationRequest()).take(2).toList()

        assertTrue(engines.segmenters.single().released)
        assertEquals(0, engines.openLeases)
    }

    @Test
    fun `the vocabulary is passed as hotwords only when they are enabled`() = runTest {
        val request = DictationRequest(vocabulary = Vocabulary.of("AgentGateway", "a/b", "SessionEvent", "AgentGateway"))
        val with = FakeEngineSource(hotwords = true)
        val without = FakeEngineSource(hotwords = false)

        LocalSpeechToText(with, config).transcribe(wav("consigne-02.wav").frames(), request).toList()
        LocalSpeechToText(without, config).transcribe(wav("consigne-02.wav").frames(), request).toList()

        assertEquals(setOf("AgentGateway/SessionEvent"), with.decoder.calls.map { it.second }.toSet())
        assertEquals(setOf<String?>(null), without.decoder.calls.map { it.second }.toSet())
        assertNull(LocalSpeechToText.hotwords(Vocabulary.EMPTY))
    }

    @Test
    fun `the statistics of the last dictation give its real-time factor`() = runTest {
        val source = wav("consigne-13.wav")
        val stt = LocalSpeechToText(FakeEngineSource(), config)

        stt.transcribe(source.frames(), DictationRequest()).toList()

        val stats = requireNotNull(stt.lastStats)
        assertEquals(source.samples.size.toLong(), stats.audioSamples)
        assertTrue(stats.segments >= 1)
        assertTrue(requireNotNull(stats.realTimeFactor) >= 0.0)
    }

    @Test
    fun `it drives a push-to-talk dictation`() = runTest {
        val dictation = LocalSpeechToText(FakeEngineSource(), config).startDictation(backgroundScope, wav("consigne-17.wav"))

        val final = dictation.await()

        assertEquals(DictationState.Final(expectedFinal(wav("consigne-17.wav").samples)), final)
    }
}
