package app.regie.voice

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import app.regie.voice.api.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundMicrophoneTest {
    @Test fun `cancel then restart waits for old microphone closure before starting a new foreground service`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val services = RecordingServices(app)
        val releaseOldRead = CompletableDeferred<Unit>()
        var sources = 0
        val capture = VoiceCapture({ listening }, {
            val first = sources++ == 0
            ForegroundMicrophone(services, object : AudioSource {
                override val format = AudioFormat.SPEECH
                override fun frames() = flow {
                    try {
                        emit(AudioFrame(ShortArray(1600) { 8000 }, format))
                        awaitCancellation()
                    } finally {
                        // AudioRecord.read on IO may still be unwinding after B cancels.
                        if (first) withContext(NonCancellable) { releaseOldRead.await() }
                    }
                }
            })
        }, backgroundScope)

        capture.start(emptyList())
        runCurrent()
        assertTrue(services.running)
        capture.cancel()
        capture.start(emptyList())
        runCurrent()
        try {
            assertEquals(listOf("start"), services.events, "The previous read still owns the microphone")
            releaseOldRead.complete(Unit)
            runCurrent()
            assertEquals(listOf("start", "stop", "start"), services.events)
            assertTrue(services.running, "Closing the cancelled capture must not stop the new service")
            assertTrue(capture.state.value.level > 0f, "The replacement capture receives audio")
            capture.cancel()
            runCurrent()
            assertFalse(services.running)
            assertEquals(listOf("start", "stop", "start", "stop"), services.events)
        } finally {
            releaseOldRead.complete(Unit)
            capture.cancel()
            runCurrent()
        }
    }

    @Test fun `a restart cancelled while waiting never opens audio or stops its predecessor`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val services = RecordingServices(app)
        val releaseOldRead = CompletableDeferred<Unit>()
        var opened = 0
        val capture = VoiceCapture({ listening }, {
            ForegroundMicrophone(services, object : AudioSource {
                override val format = AudioFormat.SPEECH
                override fun frames() = flow<AudioFrame> {
                    opened++
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { releaseOldRead.await() } }
                }
            })
        }, backgroundScope)
        try {
            capture.start(emptyList())
            runCurrent()
            capture.cancel()
            capture.start(emptyList())
            runCurrent()
            capture.cancel()
            runCurrent()
            assertEquals(1, opened)
            assertEquals(listOf("start"), services.events)
            assertTrue(services.running)
            releaseOldRead.complete(Unit)
            runCurrent()
            assertEquals(listOf("start", "stop"), services.events)
            assertFalse(services.running)
        } finally {
            releaseOldRead.complete(Unit)
            capture.cancel()
            runCurrent()
        }
    }

    @Test fun `microphone failure releases ownership and the next dictation finishes normally`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val services = RecordingServices(app)
        var sources = 0
        val capture = VoiceCapture({ listening }, {
            val first = sources++ == 0
            ForegroundMicrophone(services, object : AudioSource {
                override val format = AudioFormat.SPEECH
                override fun frames() = flow {
                    if (first) throw VoiceException(VoiceError.Provider("microphone", "read failed"))
                    emit(AudioFrame(ShortArray(1600) { 8000 }, format))
                    awaitCancellation()
                }
            })
        }, backgroundScope)
        capture.start(emptyList())
        runCurrent()
        assertNotNull(capture.state.value.error)
        assertFalse(services.running)
        capture.start(emptyList())
        runCurrent()
        assertTrue(services.running)
        capture.finish()
        runCurrent()
        assertTrue(capture.state.value.complete)
        assertEquals("Dans PassageCheck.ts", capture.state.value.text)
        assertFalse(services.running)
        assertEquals(listOf("start", "stop", "start", "stop"), services.events)
    }

    private val listening = object : SpeechToText {
        override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest) = flow {
            audio.collect { emit(DictationState.Listening("Dans PassageCheck.ts")) }
            emit(DictationState.Final("Dans PassageCheck.ts"))
        }
    }

    // Android boundary only: no real microphone, service, provider, bridge or console.
    private class RecordingServices(context: Context) : ContextWrapper(context) {
        val events = mutableListOf<String>()
        var running = false
            private set

        override fun startForegroundService(service: Intent): ComponentName? {
            assertEquals(MicrophoneService::class.java.name, service.component?.className)
            events += "start"
            running = true
            return service.component
        }

        override fun stopService(service: Intent): Boolean {
            assertEquals(MicrophoneService::class.java.name, service.component?.className)
            events += "stop"
            running = false
            return true
        }
    }
}
