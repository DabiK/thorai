package app.regie.voice.fake

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.SpeechActivity
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import app.regie.voice.api.VoiceProviderId
import app.regie.voice.api.VoiceProviders
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.WavAudioSource
import app.regie.voice.api.startDictation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeVoiceTest {
    private fun wav(name: String) = WavAudioSource(
        requireNotNull(javaClass.getResourceAsStream("/wav/$name")).use { it.readBytes() },
    )

    @Test
    fun `replays partials then the final text while reading a recording`() = runTest {
        val stt = FakeSpeechToText("Mets à jour le README")
        val request = DictationRequest(vocabulary = Vocabulary.of("README"))

        val states = stt.transcribe(wav("consigne-17.wav").frames(), request).toList()

        assertEquals(
            listOf(
                DictationState.Connecting,
                DictationState.Listening(""),
                DictationState.Listening("Mets"),
                DictationState.Listening("Mets à"),
                DictationState.Listening("Mets à jour"),
                DictationState.Listening("Mets à jour le"),
                DictationState.Finalizing("Mets à jour le"),
                DictationState.Final("Mets à jour le README"),
            ),
            states,
        )
        assertEquals(listOf(request), stt.requests)
    }

    @Test
    fun `scripts are used one per dictation, in a loop`() = runTest {
        val stt = FakeSpeechToText("un", "deux")
        val source = wav("consigne-13.wav")

        val finals = List(3) { stt.transcribe(source.frames(), DictationRequest()).toList().last() }

        assertEquals(listOf("un", "deux", "un").map { DictationState.Final(it) }, finals)
    }

    @Test
    fun `simulates errors at connection or in the middle of the dictation`() = runTest {
        val stt = FakeSpeechToText(
            listOf(
                FakeDictationScript.failing(VoiceError.KeyRejected),
                FakeDictationScript.failing(VoiceError.Offline, afterFrames = 5),
            ),
        )
        val source = wav("consigne-13.wav")

        val refused = stt.transcribe(source.frames(), DictationRequest()).toList()
        val dropped = stt.transcribe(source.frames(), DictationRequest()).toList()

        assertEquals(listOf(DictationState.Connecting, DictationState.Failed(VoiceError.KeyRejected)), refused)
        assertEquals(DictationState.Failed(VoiceError.Offline), dropped.last())
    }

    @Test
    fun `drives a dictation from start to finish`() = runTest {
        val dictation = FakeSpeechToText("Lance Codex").startDictation(backgroundScope, wav("consigne-02.wav"))
        dictation.finish()

        assertEquals(DictationState.Final("Lance Codex"), dictation.await())
    }

    @Test
    fun `synthesis gives audio proportional to the text and remembers it`() = runTest {
        val tts = FakeTextToSpeech(format = AudioFormat.SPEECH, msPerCharacter = 50)

        val frames = tts.synthesize("Bonjour").toList()

        assertEquals(350, frames.sumOf { it.durationMs })
        assertEquals(listOf("Bonjour"), tts.spoken)
    }

    @Test
    fun `synthesis is interrupted by cancelling the playback`() = runTest {
        val tts = FakeTextToSpeech(realtime = true, msPerCharacter = 100)
        val sink = RecordingAudioSink()

        val playing = launch { sink.play(tts.synthesize("Une phrase assez longue")) }
        kotlinx.coroutines.delay(250)
        playing.cancel()

        assertEquals(3, sink.played.size)
        assertEquals(250, currentTime)
    }

    @Test
    fun `synthesis errors are thrown as VoiceException`() = runTest {
        val tts = FakeTextToSpeech(error = VoiceError.QuotaExceeded)

        val e = assertFailsWith<VoiceException> { tts.synthesize("Bonjour").first() }

        assertEquals(VoiceError.QuotaExceeded, e.error)
    }

    @Test
    fun `speech detection hears the recording and not the silence`() = runTest {
        val vad = FakeVoiceActivityDetector()
        val silence = AudioFrame(ShortArray(1_600), AudioFormat.SPEECH)

        val activity = wav("consigne-13.wav").frames().toList().map { vad.process(it) }
        val afterwards = List(5) { vad.process(silence) }

        assertTrue(SpeechActivity.SPEECH in activity)
        assertEquals(SpeechActivity.SILENCE, afterwards.last(), "silence after the hangover")
        vad.reset()
        assertEquals(SpeechActivity.SILENCE, vad.process(silence))
    }

    @Test
    fun `the fake provider registers like any other`() = runTest {
        val providers = VoiceProviders(listOf(FakeVoiceProvider()))

        val provider = providers[VoiceProviderId("fake")]

        assertEquals(ProviderStatus.Ready, provider.prepare().take(1).toList().single())
    }
}
