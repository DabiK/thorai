package app.regie.voice.elevenlabs

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Realtime transcription models of ElevenLabs Scribe. */
enum class ScribeModel(val id: String) {
    /** Scribe v3 Live, released on 10 November 2026: the default. */
    V3_LIVE("scribe_v3_live"),

    /** Scribe v2 Realtime, kept as an option. */
    V2_REALTIME("scribe_v2_realtime"),
}

/**
 * Settings of the ElevenLabs adapter. The key and the voice are read at each use through
 * [apiKey] and [voiceId], so a change in the app settings applies without rebuilding anything.
 * The key is entered by the user in the app; it never comes from the repository.
 */
class ElevenLabsConfig(
    val apiKey: () -> String?,
    /** Synthesis voice; no default voice is safe (ElevenLabs default voices expire). */
    val voiceId: () -> String? = { null },
    val sttModel: ScribeModel = ScribeModel.V3_LIVE,
    val ttsModel: String = "eleven_flash_v2_5",
    /** Sample rate of synthesized PCM: one of 8000, 16000, 22050, 24000, 32000, 44100 (Pro), 48000. */
    val ttsSampleRateHz: Int = 22_050,
    /** How long to wait for the final text once speech has ended. */
    val finalTimeout: Duration = 10.seconds,
    /** Injected by tests to talk to a local server. */
    val baseUrl: String = "https://api.elevenlabs.io",
)
