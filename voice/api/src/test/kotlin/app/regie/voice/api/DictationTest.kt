package app.regie.voice.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DictationTest {
    /** A microphone that never stops by itself, like the real one. */
    private class EndlessMicrophone : AudioSource {
        override val format = AudioFormat.SPEECH
        var stopped = false

        override fun frames(): Flow<AudioFrame> = flow {
            try {
                while (true) {
                    emit(AudioFrame(ShortArray(1_600), format))
                    delay(100)
                }
            } finally {
                stopped = true
            }
        }
    }

    /** Counts the frames it receives and returns their number as the final text. */
    private class CountingSpeechToText : SpeechToText {
        var request: DictationRequest? = null

        override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest) = flow {
            this@CountingSpeechToText.request = request
            emit(DictationState.Listening(""))
            var count = 0
            audio.collect {
                count++
                emit(DictationState.Listening("$count"))
            }
            emit(DictationState.Finalizing("$count"))
            emit(DictationState.Final("$count frames"))
        }
    }

    @Test
    fun `finish ends the audio capture and gives the final text`() = runTest {
        val mic = EndlessMicrophone()
        val stt = CountingSpeechToText()
        val request = DictationRequest(language = "fr", vocabulary = Vocabulary.of("AgentGateway"))

        val dictation = stt.startDictation(backgroundScope, mic, request)
        delay(450)
        assertEquals(DictationState.Listening("5"), dictation.state.value)
        dictation.finish()

        assertEquals(DictationState.Final("5 frames"), dictation.await())
        runCurrent()
        assertTrue(mic.stopped, "the microphone is released")
        assertEquals(request, stt.request)
    }

    @Test
    fun `cancel drops the dictation and releases the microphone`() = runTest {
        val mic = EndlessMicrophone()
        val dictation = CountingSpeechToText().startDictation(backgroundScope, mic)
        delay(250)

        dictation.cancel()
        runCurrent()

        assertEquals(DictationState.Cancelled, dictation.state.value)
        assertTrue(mic.stopped)
    }

    @Test
    fun `a failure stays the final state, even if cancel comes after`() = runTest {
        val failing = object : SpeechToText {
            override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest) = flow {
                emit(DictationState.Connecting)
                emit(DictationState.Failed(VoiceError.KeyRejected))
            }
        }
        val dictation = failing.startDictation(backgroundScope, EndlessMicrophone())

        assertEquals(DictationState.Failed(VoiceError.KeyRejected), dictation.await())
        dictation.cancel()
        assertEquals(DictationState.Failed(VoiceError.KeyRejected), dictation.state.value)
    }

    @Test
    fun `an adapter that throws is reported as a failure`() = runTest {
        val throwing = object : SpeechToText {
            override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest): Flow<DictationState> = flow {
                throw VoiceException(VoiceError.Offline)
            }
        }
        val dictation = throwing.startDictation(backgroundScope, EndlessMicrophone())

        assertEquals(DictationState.Failed(VoiceError.Offline), dictation.await())
    }

    @Test
    fun `until stops a never-ending flow when the signal is raised`() = runTest {
        val stop = MutableStateFlow(false)
        val started = CompletableDeferred<Unit>()
        val endless = flow {
            emit(AudioFrame(ShortArray(1), AudioFormat.SPEECH))
            started.complete(Unit)
            awaitCancellation()
        }

        val collected = backgroundScope.async { endless.until(stop).toList() }
        started.await()
        stop.value = true

        assertEquals(1, collected.await().size)
    }
}
