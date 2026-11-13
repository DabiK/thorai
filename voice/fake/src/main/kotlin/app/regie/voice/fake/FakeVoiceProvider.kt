package app.regie.voice.fake

import app.regie.voice.api.AudioFrame
import app.regie.voice.api.AudioSink
import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.SpeechToText
import app.regie.voice.api.TextToSpeech
import app.regie.voice.api.VoiceProvider
import app.regie.voice.api.VoiceProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A provider made of fakes, for the app tests and for demos without network. */
class FakeVoiceProvider(
    override val speechToText: SpeechToText = FakeSpeechToText("Lance npm run check dans le dossier bridge"),
    override val textToSpeech: TextToSpeech = FakeTextToSpeech(),
    private val status: ProviderStatus = ProviderStatus.Ready,
    override val id: VoiceProviderId = ID,
) : VoiceProvider {
    override val displayName = "Voix factice"

    override fun prepare(): Flow<ProviderStatus> = flowOf(status)

    companion object {
        val ID = VoiceProviderId("fake")
    }
}

/** A sink that keeps what it is asked to play, instead of playing it. */
class RecordingAudioSink : AudioSink {
    private val frames = mutableListOf<AudioFrame>()

    val played: List<AudioFrame> get() = synchronized(frames) { frames.toList() }

    override suspend fun play(audio: Flow<AudioFrame>) {
        audio.collect { synchronized(frames) { frames += it } }
    }
}
