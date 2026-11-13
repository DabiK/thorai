package app.regie.voice.api

enum class SpeechActivity { SPEECH, SILENCE }

/** On-device speech detection port: tells, frame after frame, whether someone is speaking. */
interface VoiceActivityDetector {
    /** Format the detector expects. */
    val format: AudioFormat

    /** Feeds one frame and returns the activity after it. Detectors keep state between frames. */
    fun process(frame: AudioFrame): SpeechActivity

    /** Forgets the past frames, before a new utterance. */
    fun reset()
}
