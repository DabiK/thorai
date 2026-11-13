package app.regie.voice.api

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WavAudioSourceTest {
    @Test
    fun `reads a recording made by afconvert, skipping its extra chunks`() = runTest {
        val source = WavAudioSource(TestWavs.bytes("consigne-13.wav"))

        assertEquals(AudioFormat.SPEECH, source.format)
        val frames = source.frames().toList()
        val totalMs = frames.sumOf { it.durationMs }
        assertTrue(totalMs in 2_500..3_500, "duration was $totalMs ms")
        assertTrue(frames.dropLast(1).all { it.samples.size == 1_600 }, "100 ms frames at 16 kHz")
        assertTrue(frames.any { frame -> frame.samples.any { it.toInt() != 0 } }, "the recording is not silent")
    }

    @Test
    fun `frames put back together give every sample, the last frame being shorter`() = runTest {
        val samples = ShortArray(1_000) { (it * 31 - 7_000).toShort() }
        val source = WavAudioSource(Wav.encode(samples, AudioFormat.SPEECH), frameDurationMs = 20)

        val frames = source.frames().toList()

        assertEquals(listOf(320, 320, 320, 40), frames.map { it.samples.size })
        assertContentEquals(samples, frames.flatMap { it.samples.asList() }.toShortArray())
    }

    @Test
    fun `realtime replay paces frames like a microphone`() = runTest {
        val source = WavAudioSource(Wav.encode(ShortArray(16_000), AudioFormat.SPEECH), realtime = true)

        val frames = source.frames().toList()

        assertEquals(10, frames.size)
        assertEquals(900, currentTime, "first frame at once, then one every 100 ms")
    }

    @Test
    fun `refuses what is not PCM 16-bit mono`() {
        val stereo = Wav.encode(ShortArray(10), AudioFormat.SPEECH).also { it[22] = 2 }
        val eightBits = Wav.encode(ShortArray(10), AudioFormat.SPEECH).also { it[34] = 8 }

        assertFailsWith<IllegalArgumentException> { WavAudioSource(stereo) }
        assertFailsWith<IllegalArgumentException> { WavAudioSource(eightBits) }
        assertFailsWith<IllegalArgumentException> { WavAudioSource("not a wav file".toByteArray()) }
    }

    @Test
    fun `pcm little-endian round trip`() {
        val samples = shortArrayOf(0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE, 0x1234)

        val bytes = Pcm16.toLittleEndian(samples)

        assertEquals(0x34.toByte(), bytes[10])
        assertEquals(0x12.toByte(), bytes[11])
        assertContentEquals(samples, Pcm16.fromLittleEndian(bytes))
    }
}
