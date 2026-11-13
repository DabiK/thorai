package app.regie.voice.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Replays a WAV file (PCM 16-bit mono) as an [AudioSource], cut into frames of [frameDurationMs].
 *
 * With [realtime], frames are paced like a microphone would deliver them; otherwise they are
 * emitted as fast as they are collected. Used by tests and by the trial app to replay recordings.
 */
class WavAudioSource(
    wav: ByteArray,
    private val frameDurationMs: Int = DEFAULT_FRAME_MS,
    private val realtime: Boolean = false,
) : AudioSource {
    private val pcm: ShortArray
    override val format: AudioFormat

    init {
        require(frameDurationMs > 0) { "frameDurationMs must be positive" }
        val parsed = Wav.parse(wav)
        pcm = parsed.samples
        format = parsed.format
    }

    /** Every sample of the file. */
    val samples: ShortArray get() = pcm.copyOf()

    override fun frames(): Flow<AudioFrame> = flow {
        val step = maxOf(1, format.samplesFor(frameDurationMs))
        var start = 0
        while (start < pcm.size) {
            val end = minOf(pcm.size, start + step)
            val frame = AudioFrame(pcm.copyOfRange(start, end), format)
            if (realtime && start > 0) delay(frame.durationMs)
            emit(frame)
            start = end
        }
    }

    companion object {
        const val DEFAULT_FRAME_MS = 100

        fun fromFile(file: File, frameDurationMs: Int = DEFAULT_FRAME_MS, realtime: Boolean = false) =
            WavAudioSource(file.readBytes(), frameDurationMs, realtime)
    }
}

/** Minimal RIFF/WAVE reader and writer for PCM 16-bit mono. */
object Wav {
    class Clip(val format: AudioFormat, val samples: ShortArray)

    private const val FORMAT_PCM = 1
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    fun parse(bytes: ByteArray): Clip {
        require(bytes.size >= 12 && tag(bytes, 0) == "RIFF" && tag(bytes, 8) == "WAVE") { "Not a RIFF/WAVE file" }
        var format: AudioFormat? = null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = tag(bytes, offset)
            val size = int32(bytes, offset + 4)
            val body = offset + 8
            require(id == "data" || (size >= 0 && body + size <= bytes.size)) { "Truncated WAV chunk '$id'" }
            when (id) {
                "fmt " -> format = parseFormat(bytes, body)
                "data" -> {
                    val fmt = requireNotNull(format) { "WAV data chunk before fmt chunk" }
                    // Streamed WAV files may declare an unknown (or too large) size: read what is there.
                    val declared = if (size < 0 || body + size > bytes.size) bytes.size - body else size
                    return Clip(fmt, Pcm16.fromLittleEndian(bytes, body, declared and 1.inv()))
                }
            }
            offset = body + size + (size and 1)
        }
        throw IllegalArgumentException("WAV file has no data chunk")
    }

    private fun parseFormat(bytes: ByteArray, at: Int): AudioFormat {
        val audioFormat = int16(bytes, at)
        val channels = int16(bytes, at + 2)
        val sampleRate = int32(bytes, at + 4)
        val bitsPerSample = int16(bytes, at + 14)
        require(audioFormat == FORMAT_PCM || audioFormat == FORMAT_EXTENSIBLE) { "WAV must be PCM, got format $audioFormat" }
        require(channels == 1) { "WAV must be mono, got $channels channels" }
        require(bitsPerSample == 16) { "WAV must be 16-bit, got $bitsPerSample bits" }
        return AudioFormat(sampleRate)
    }

    private fun tag(b: ByteArray, at: Int) = String(b, at, 4, Charsets.US_ASCII)

    private fun int16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun int32(b: ByteArray, at: Int) = int16(b, at) or (int16(b, at + 2) shl 16)

    /** Encodes [samples] as a WAV file. Handy to build test fixtures and to save recordings. */
    fun encode(samples: ShortArray, format: AudioFormat): ByteArray {
        val data = Pcm16.toLittleEndian(samples)
        val out = java.io.ByteArrayOutputStream(44 + data.size)
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun i32(v: Int) { i16(v and 0xFFFF); i16((v shr 16) and 0xFFFF) }
        ascii("RIFF"); i32(36 + data.size); ascii("WAVE")
        ascii("fmt "); i32(16); i16(FORMAT_PCM); i16(1); i32(format.sampleRateHz)
        i32(format.sampleRateHz * 2); i16(2); i16(16)
        ascii("data"); i32(data.size); out.write(data)
        return out.toByteArray()
    }
}
