package app.regie.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.regie.voice.api.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

class ForegroundMicrophone(private val context: Context) : AudioSource {
    override val format = AudioFormat.SPEECH
    override fun frames() = flow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw VoiceException(VoiceError.NotConfigured("RECORD_AUDIO"))
        }
        val service = Intent(context, MicrophoneService::class.java)
        try {
            context.startForegroundService(service)
            emitAll(MicrophoneSource(context).frames())
        } finally {
            context.stopService(service)
        }
    }
}
