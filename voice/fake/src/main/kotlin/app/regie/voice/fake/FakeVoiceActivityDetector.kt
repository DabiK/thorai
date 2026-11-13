package app.regie.voice.fake

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.SpeechActivity
import app.regie.voice.api.VoiceActivityDetector
import kotlin.math.sqrt

/**
 * Energy-based speech detection: a frame whose RMS level exceeds [threshold] is speech, and
 * speech lasts [hangoverFrames] more frames to bridge short pauses. Deterministic, for tests;
 * the real detector (Silero) comes with the local provider.
 */
class FakeVoiceActivityDetector(
    private val threshold: Double = 500.0,
    private val hangoverFrames: Int = 3,
    override val format: AudioFormat = AudioFormat.SPEECH,
) : VoiceActivityDetector {
    private var quietFrames = Int.MAX_VALUE

    override fun process(frame: AudioFrame): SpeechActivity {
        quietFrames = if (rms(frame.samples) > threshold) 0 else quietFrames.saturatingIncrement()
        return if (quietFrames <= hangoverFrames) SpeechActivity.SPEECH else SpeechActivity.SILENCE
    }

    override fun reset() {
        quietFrames = Int.MAX_VALUE
    }

    private fun Int.saturatingIncrement() = if (this == Int.MAX_VALUE) this else this + 1

    private fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size)
    }
}
