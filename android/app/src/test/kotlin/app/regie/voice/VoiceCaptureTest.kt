package app.regie.voice

import app.regie.voice.api.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceCaptureTest {
    @Test fun `audio level and elapsed time accompany partials then release produces final text`() = runTest {
        val source = object : AudioSource {
            override val format = AudioFormat.SPEECH
            override fun frames() = flow {
                while (true) { emit(AudioFrame(ShortArray(1600) { 8000 }, format)); delay(100) }
            }
        }
        val stt = object : SpeechToText {
            override fun transcribe(audio: kotlinx.coroutines.flow.Flow<AudioFrame>, request: DictationRequest) = flow {
                assertEquals(listOf("PassageCheck.ts"), request.vocabulary.terms)
                audio.collect { emit(DictationState.Listening("Dans PassageCheck.ts")) }
                emit(DictationState.Final("Dans PassageCheck.ts ajoute un test"))
            }
        }
        val capture = VoiceCapture({ stt }, { source }, backgroundScope)
        capture.start(listOf("PassageCheck.ts"))
        advanceTimeBy(250); runCurrent()
        assertEquals("Dans PassageCheck.ts", capture.state.value.text)
        assertTrue(capture.state.value.level > 0f)
        assertEquals(300L, capture.state.value.durationMs)
        capture.finish(); runCurrent()
        assertTrue(capture.state.value.complete)
        assertEquals("Dans PassageCheck.ts ajoute un test", capture.state.value.text)
    }
    @Test fun `provider failure releases audio even when the provider has not closed its flow`() = runTest {
        var capturing = false
        val source = object : AudioSource {
            override val format = AudioFormat.SPEECH
            override fun frames() = flow {
                capturing = true
                try { emit(AudioFrame(shortArrayOf(1), format)); kotlinx.coroutines.awaitCancellation() }
                finally { capturing = false }
            }
        }
        val failed = object : SpeechToText {
            override fun transcribe(audio: kotlinx.coroutines.flow.Flow<AudioFrame>, request: DictationRequest) = kotlinx.coroutines.flow.channelFlow {
                launch { audio.collect { send(DictationState.Failed(VoiceError.Offline)) } }
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val capture = VoiceCapture({ failed }, { source }, backgroundScope)
        capture.start(emptyList()); runCurrent()
        assertNotNull(capture.state.value.error)
        assertFalse(capturing)
    }

}
