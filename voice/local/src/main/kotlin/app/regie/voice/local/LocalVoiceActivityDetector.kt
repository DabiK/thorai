package app.regie.voice.local

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.SpeechActivity
import app.regie.voice.api.VoiceActivityDetector

/**
 * On-device speech detection with Silero. Get one from [LocalVoiceProvider.voiceActivityDetector]
 * and [close] it when done: it holds a native model.
 */
class LocalVoiceActivityDetector internal constructor(
    private val segmenter: SpeechSegmenter,
) : VoiceActivityDetector, AutoCloseable {
    override val format: AudioFormat = AudioFormat.SPEECH

    override fun process(frame: AudioFrame): SpeechActivity {
        require(frame.format == format) { "Silero expects 16 kHz, got ${frame.format.sampleRateHz} Hz" }
        segmenter.accept(SegmentedTranscriber.toFloat(frame.samples))
        // Only the activity matters here: finished utterances are dropped.
        segmenter.drain()
        return if (segmenter.isSpeechDetected()) SpeechActivity.SPEECH else SpeechActivity.SILENCE
    }

    override fun reset() = segmenter.reset()

    override fun close() = segmenter.release()
}
