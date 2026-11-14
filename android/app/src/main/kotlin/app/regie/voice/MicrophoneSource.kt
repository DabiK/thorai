package app.regie.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.AudioSource
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import android.media.AudioFormat as AndroidAudioFormat

/** The microphone, 16 kHz mono, tuned for speech recognition. Collecting records, cancelling stops. */
class MicrophoneSource(private val context: Context) : AudioSource {
    override val format: AudioFormat = AudioFormat.SPEECH

    override fun frames(): Flow<AudioFrame> = flow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw VoiceException(VoiceError.NotConfigured("RECORD_AUDIO"))
        }
        val rate = format.sampleRateHz
        val minBuffer = AudioRecord.getMinBufferSize(rate, AndroidAudioFormat.CHANNEL_IN_MONO, AndroidAudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AndroidAudioFormat.CHANNEL_IN_MONO,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, rate * 2), // one second of margin
            )
        } catch (e: SecurityException) {
            throw VoiceException(VoiceError.NotConfigured("RECORD_AUDIO"), e)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw VoiceException(VoiceError.Provider("microphone", "AudioRecord not initialized"))
        }
        try {
            record.startRecording()
            val buffer = ShortArray(format.samplesFor(FRAME_MS))
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) throw VoiceException(VoiceError.Provider("microphone", "AudioRecord.read returned $n"))
                if (n > 0) emit(AudioFrame(buffer.copyOf(n), format))
            }
        } finally {
            record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val FRAME_MS = 100
    }
}
