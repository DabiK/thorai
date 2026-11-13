package app.regie.voice.elevenlabs

import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.SpeechToText
import app.regie.voice.api.TextToSpeech
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceProvider
import app.regie.voice.api.VoiceProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** ElevenLabs: Scribe realtime for transcription, Flash for synthesis. */
class ElevenLabsVoiceProvider(
    private val config: ElevenLabsConfig,
    client: OkHttpClient = defaultClient(),
) : VoiceProvider {
    override val id = ID
    override val displayName = "ElevenLabs"
    override val speechToText: SpeechToText = ElevenLabsSpeechToText(config, client)
    override val textToSpeech: TextToSpeech = ElevenLabsTextToSpeech(config, client)

    override fun prepare(): Flow<ProviderStatus> = flow {
        emit(if (config.apiKey().isNullOrBlank()) ProviderStatus.Unavailable(VoiceError.KeyMissing) else ProviderStatus.Ready)
    }

    companion object {
        val ID = VoiceProviderId("elevenlabs")

        /** Pings keep the dictation socket alive and detect a dead network. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}
