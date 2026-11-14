package app.regie.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.regie.voice.api.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ForegroundMicrophone(
    private val context: Context,
    private val microphone: AudioSource = MicrophoneSource(context),
) : AudioSource {
    override val format = AudioFormat.SPEECH
    override fun frames() = flow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw VoiceException(VoiceError.NotConfigured("RECORD_AUDIO"))
        }
        microphoneOwnership.withLock {
            val service = Intent(context, MicrophoneService::class.java)
            try {
                context.startForegroundService(service)
                emitAll(microphone.frames())
            } finally {
                context.stopService(service)
            }
        }
    }

    private companion object {
        // Every dictation creates a new source, but Android has one microphone service.
        // Keep ownership until AudioRecord's IO flow has closed and stopService has run.
        val microphoneOwnership = Mutex()
    }
}
