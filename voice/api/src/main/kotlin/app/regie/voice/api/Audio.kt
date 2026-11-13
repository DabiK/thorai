package app.regie.voice.api

import kotlinx.coroutines.flow.Flow

/**
 * Format of every audio frame exchanged by the voice ports: PCM, signed 16 bits, little-endian,
 * mono. Only the sample rate varies.
 */
data class AudioFormat(val sampleRateHz: Int) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive, got $sampleRateHz" }
    }

    val bytesPerSample: Int get() = 2

    fun samplesFor(durationMs: Int): Int = (sampleRateHz.toLong() * durationMs / 1000).toInt()

    companion object {
        /** Format expected by speech-to-text: 16 kHz mono. */
        val SPEECH = AudioFormat(16_000)
    }
}

/** A chunk of PCM 16-bit mono samples. */
class AudioFrame(val samples: ShortArray, val format: AudioFormat) {
    val durationMs: Long get() = samples.size * 1000L / format.sampleRateHz

    /** Samples as little-endian bytes, the wire format of most providers. */
    fun toLittleEndianBytes(): ByteArray = Pcm16.toLittleEndian(samples)

    override fun toString(): String = "AudioFrame(${samples.size} samples @ ${format.sampleRateHz} Hz)"
}

/**
 * Where audio comes from: the Android microphone, the replay of a WAV file, a test fixture.
 *
 * [frames] is cold: collecting it starts the capture, cancelling the collection stops it.
 */
interface AudioSource {
    val format: AudioFormat

    fun frames(): Flow<AudioFrame>
}

/** Where synthesized audio goes, for example an Android `AudioTrack`. */
interface AudioSink {
    /**
     * Plays [audio] until it completes, then returns. Cancelling the caller stops playback at
     * once: that is how speech is interrupted.
     */
    suspend fun play(audio: Flow<AudioFrame>)
}

/** Conversions between 16-bit samples and their little-endian byte encoding. */
object Pcm16 {
    fun toLittleEndian(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /** Decodes [length] bytes from [offset]; [length] must be even. */
    fun fromLittleEndian(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): ShortArray {
        require(length % 2 == 0) { "PCM 16-bit needs an even number of bytes, got $length" }
        val samples = ShortArray(length / 2)
        for (i in samples.indices) {
            val lo = bytes[offset + 2 * i].toInt() and 0xFF
            val hi = bytes[offset + 2 * i + 1].toInt()
            samples[i] = ((hi shl 8) or lo).toShort()
        }
        return samples
    }
}
