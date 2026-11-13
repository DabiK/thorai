package app.regie.voicelab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.AudioSink
import app.regie.voice.api.AudioSource
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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

/**
 * Plays PCM frames with an `AudioTrack` in streaming mode. Writes go in slices of 50 ms, so that
 * cancelling the caller cuts the voice within about 50 ms.
 */
class SpeakerSink : AudioSink {
    override suspend fun play(audio: Flow<AudioFrame>) {
        var track: AudioTrack? = null
        var written = 0L
        var completed = false
        try {
            audio.collect { frame ->
                val current = track ?: open(frame.format.sampleRateHz).also {
                    track = it
                    it.play()
                }
                val slice = frame.format.samplesFor(SLICE_MS)
                var offset = 0
                while (offset < frame.samples.size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(slice, frame.samples.size - offset)
                    val n = withContext(Dispatchers.IO) { current.write(frame.samples, offset, count, AudioTrack.WRITE_BLOCKING) }
                    if (n < 0) throw VoiceException(VoiceError.Provider("speaker", "AudioTrack.write returned $n"))
                    offset += n
                    written += n
                }
            }
            // Let the end of the buffer play before returning.
            track?.let { current ->
                current.stop()
                while (current.playbackHeadPosition < written && current.playState != AudioTrack.PLAYSTATE_STOPPED) delay(20)
            }
            completed = true
        } finally {
            withContext(NonCancellable) {
                track?.let {
                    if (!completed) {
                        it.pause()
                        it.flush()
                    }
                    it.release()
                }
            }
        }
    }

    private fun open(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AndroidAudioFormat.CHANNEL_OUT_MONO, AndroidAudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AndroidAudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate / 5 * 2)) // about 200 ms
            .build()
    }

    private companion object {
        const val SLICE_MS = 50
    }
}
