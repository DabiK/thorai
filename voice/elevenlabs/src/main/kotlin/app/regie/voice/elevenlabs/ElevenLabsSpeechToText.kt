package app.regie.voice.elevenlabs

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.AudioFrame
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.SpeechToText
import app.regie.voice.api.VoiceError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Realtime transcription with ElevenLabs Scribe, over a WebSocket. */
class ElevenLabsSpeechToText(
    private val config: ElevenLabsConfig,
    private val client: OkHttpClient = ElevenLabsVoiceProvider.defaultClient(),
) : SpeechToText {

    override fun transcribe(audio: Flow<AudioFrame>, request: DictationRequest): Flow<DictationState> = channelFlow {
        val key = config.apiKey()?.takeIf { it.isNotBlank() }
        if (key == null) {
            send(DictationState.Failed(VoiceError.KeyMissing))
            return@channelFlow
        }
        send(DictationState.Connecting)

        val events = Channel<SocketEvent>(Channel.UNLIMITED)
        val socket = client.newWebSocket(
            Request.Builder().url(ScribeProtocol.url(config, request)).header("xi-api-key", key).build(),
            Listener(events),
        )
        // Audio is read at once: OkHttp queues it until the socket opens, so nothing said while
        // connecting is lost.
        val sender = launch {
            try {
                audio.collect { socket.send(ScribeProtocol.audioChunk(it)) }
                socket.send(ScribeProtocol.commit(AudioFormat.SPEECH.sampleRateHz))
                events.send(SocketEvent.SpeechEnded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.send(SocketEvent.Failed(VoiceError.Provider("audio_source", e.message)))
            }
        }

        var partial = ""
        var speechEnded = false
        var timeout: Job? = null
        var terminal: DictationState? = null
        try {
            while (terminal == null) {
                when (val event = events.receive()) {
                    SocketEvent.SpeechEnded -> {
                        speechEnded = true
                        send(DictationState.Finalizing(partial))
                        timeout = launch {
                            delay(config.finalTimeout)
                            events.send(SocketEvent.Failed(VoiceError.Provider("timeout", "no final text after ${config.finalTimeout}")))
                        }
                    }
                    is SocketEvent.Text -> when (val message = ScribeProtocol.parse(event.text)) {
                        ScribeProtocol.Message.SessionStarted -> if (!speechEnded) send(DictationState.Listening(partial))
                        is ScribeProtocol.Message.Partial -> {
                            partial = message.text
                            send(if (speechEnded) DictationState.Finalizing(partial) else DictationState.Listening(partial))
                        }
                        is ScribeProtocol.Message.Committed -> terminal = DictationState.Final(message.text)
                        is ScribeProtocol.Message.Error -> terminal = DictationState.Failed(ScribeProtocol.error(message.type, message.message))
                        is ScribeProtocol.Message.Other -> Unit
                    }
                    is SocketEvent.Failed -> terminal = DictationState.Failed(event.error)
                }
            }
            send(terminal)
        } finally {
            timeout?.cancel()
            sender.cancel()
            if (terminal is DictationState.Final) socket.close(NORMAL_CLOSURE, null) else socket.cancel()
        }
    }

    private sealed interface SocketEvent {
        data class Text(val text: String) : SocketEvent

        data object SpeechEnded : SocketEvent

        data class Failed(val error: VoiceError) : SocketEvent
    }

    private class Listener(private val events: SendChannel<SocketEvent>) : WebSocketListener() {
        @Volatile private var opened = false

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            events.trySend(SocketEvent.Text(text))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            events.trySend(SocketEvent.Failed(VoiceError.Provider("closed", "server closed the session: $code $reason".trim())))
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = if (!opened && response != null) {
                ElevenLabsErrors.http(response.code, runCatching { response.peekBody(4_096).string() }.getOrNull())
            } else {
                VoiceError.Offline
            }
            events.trySend(SocketEvent.Failed(error))
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
    }
}
