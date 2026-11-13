package app.regie.voice.elevenlabs

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.Pcm16
import app.regie.voice.api.VoiceError
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.WavAudioSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ElevenLabsSpeechToTextTest {
    private val server = MockWebServer()

    @BeforeEach fun start() = server.start()

    @AfterEach fun stop() = server.close()

    private fun wav(name: String, realtime: Boolean = false) = WavAudioSource(
        requireNotNull(javaClass.getResourceAsStream("/wav/$name")).use { it.readBytes() },
        realtime = realtime,
    )

    private fun config(key: String? = "test-key", model: ScribeModel = ScribeModel.V3_LIVE) =
        ElevenLabsConfig(apiKey = { key }, sttModel = model, baseUrl = server.url("/").toString(), finalTimeout = 2_000.milliseconds)

    private fun upgrade(scribe: ScribeServer) = server.enqueue(MockResponse.Builder().webSocketUpgrade(scribe).build())

    private fun transcribe(source: WavAudioSource, config: ElevenLabsConfig = config(), request: DictationRequest = DictationRequest()) =
        runBlocking { withTimeout(10_000) { ElevenLabsSpeechToText(config).transcribe(source.frames(), request).toList() } }

    @Test
    fun `streams a recording, shows partials then the committed text`() {
        val scribe = ScribeServer.fromFixture("dictee-consigne-17.json")
        upgrade(scribe)

        // Paced like a microphone, so that the session starts while the user is still speaking.
        val states = transcribe(wav("consigne-17.wav", realtime = true))

        assertEquals(DictationState.Connecting, states.first())
        assertEquals(DictationState.Listening(""), states[1])
        val partials = states.filterIsInstance<DictationState.Listening>().map { it.partial }.distinct()
        assertEquals(
            listOf("", "Mets", "Mets à jour", "Mets à jour le README", "Mets à jour le README avec",
                "Mets à jour le README avec la commande", "Mets à jour le README avec la commande gradlew",
                "Mets à jour le README avec la commande gradlew check"),
            partials,
        )
        assertTrue(states.any { it is DictationState.Finalizing })
        assertEquals(DictationState.Final("Mets à jour le README avec la commande gradlew check."), states.last())
    }

    @Test
    fun `sends the whole recording as base64 chunks, then an empty commit`() {
        val scribe = ScribeServer.fromFixture("dictee-consigne-17.json")
        upgrade(scribe)
        val source = wav("consigne-17.wav")

        transcribe(source)

        val chunks = scribe.received.toList()
        val audio = chunks.dropLast(1)
        assertTrue(audio.all { it.string("message_type") == "input_audio_chunk" && !it["commit"]!!.jsonPrimitive.boolean })
        assertTrue(audio.all { it["sample_rate"]!!.jsonPrimitive.int == 16_000 })
        val sent = audio.flatMap { Base64.getDecoder().decode(it.string("audio_base_64")).asList() }.toByteArray()
        assertContentEquals(Pcm16.toLittleEndian(source.samples), sent)
        val commit = chunks.last()
        assertTrue(commit["commit"]!!.jsonPrimitive.boolean)
        assertEquals("", commit.string("audio_base_64"))
    }

    @Test
    fun `connects with the key header and the dictation parameters`() {
        upgrade(ScribeServer.fromFixture("dictee-consigne-17.json"))
        val longTerm = "kotlinx-coroutines-test" // 23 characters: dropped
        val terms = listOf("AgentGateway", " ", longTerm, "agentgateway", "Claude Code") + (1..60).map { "terme$it" }

        transcribe(wav("consigne-13.wav"), request = DictationRequest("fr", Vocabulary(terms)))

        val handshake = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/speech-to-text/realtime", handshake.url.encodedPath)
        assertEquals("test-key", handshake.headers["xi-api-key"])
        assertEquals("scribe_v3_live", handshake.url.queryParameter("model_id"))
        assertEquals("pcm_16000", handshake.url.queryParameter("audio_format"))
        assertEquals("fr", handshake.url.queryParameter("language_code"))
        assertEquals("manual", handshake.url.queryParameter("commit_strategy"))
        val keyterms = handshake.url.queryParameterValues("keyterms")
        assertEquals(50, keyterms.size)
        assertEquals(listOf("AgentGateway", "Claude Code", "terme1"), keyterms.take(3))
        assertTrue(keyterms.all { it != null && it.length <= 20 })
        assertTrue("keyterms=AgentGateway&keyterms=Claude%20Code" in handshake.url.encodedQuery!!)
    }

    @Test
    fun `Scribe v2 Realtime can be chosen`() {
        upgrade(ScribeServer.fromFixture("dictee-consigne-17.json"))

        transcribe(wav("consigne-13.wav"), config(model = ScribeModel.V2_REALTIME))

        assertEquals("scribe_v2_realtime", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("model_id"))
    }

    @Test
    fun `each Scribe error is mapped to a typed state`() {
        val expected = mapOf(
            "auth_error" to VoiceError.KeyRejected,
            "quota_exceeded" to VoiceError.QuotaExceeded,
            "rate_limited" to VoiceError.RateLimited,
            "commit_throttled" to VoiceError.RateLimited,
        )
        for (error in ScribeServer.errors()) {
            val type = error.string("message_type")!!
            upgrade(ScribeServer(onOpen = listOf(Json.parseToJsonElement("""{"message_type":"session_started","session_id":"s"}""")), onAudio = listOf(error)))

            val last = transcribe(wav("consigne-13.wav")).last()

            assertEquals(DictationState.Failed(expected[type] ?: VoiceError.Provider(type, error.string("error"))), last, type)
        }
    }

    @Test
    fun `a refused key at the handshake is reported as such`() {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":{"status":"invalid_api_key","message":"Invalid API key"}}""").build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":{"status":"quota_exceeded","message":"Quota exceeded"}}""").build())
        server.enqueue(MockResponse.Builder().code(429).body("""{"detail":{"status":"too_many_concurrent_requests"}}""").build())

        val outcomes = List(3) { transcribe(wav("consigne-13.wav")).last() }

        assertEquals(
            listOf(VoiceError.KeyRejected, VoiceError.QuotaExceeded, VoiceError.RateLimited).map { DictationState.Failed(it) },
            outcomes,
        )
    }

    @Test
    fun `a network cut during the dictation means offline`() {
        upgrade(ScribeServer(cutAfterChunks = 5))

        assertEquals(DictationState.Failed(VoiceError.Offline), transcribe(wav("consigne-13.wav")).last())
    }

    @Test
    fun `no server at all means offline`() {
        val url = server.url("/").toString()
        server.close()

        val states = transcribe(wav("consigne-13.wav"), ElevenLabsConfig(apiKey = { "k" }, baseUrl = url))

        assertEquals(DictationState.Failed(VoiceError.Offline), states.last())
    }

    @Test
    fun `without a key nothing is sent`() {
        val states = transcribe(wav("consigne-13.wav"), config(key = " "))

        assertEquals(listOf(DictationState.Failed(VoiceError.KeyMissing)), states)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no committed text in time is a failure, not a hang`() {
        upgrade(ScribeServer(onOpen = listOf(Json.parseToJsonElement("""{"message_type":"session_started","session_id":"s"}"""))))

        val last = transcribe(wav("consigne-13.wav"))

        assertEquals("timeout", ((last.last() as DictationState.Failed).error as VoiceError.Provider).code)
    }

    @Test
    fun `cancelling the dictation closes the socket`() {
        val scribe = ScribeServer.fromFixture("dictee-consigne-17.json")
        upgrade(scribe)
        val endless = WavAudioSource(
            app.regie.voice.api.Wav.encode(ShortArray(16_000 * 60), AudioFormat.SPEECH),
            realtime = true,
        )

        runBlocking {
            withTimeout(10_000) {
                ElevenLabsSpeechToText(config()).transcribe(endless.frames(), DictationRequest())
                    .first { it is DictationState.Listening && it.partial == "Mets" }
            }
        }

        assertTrue(scribe.clientGone.await(5, TimeUnit.SECONDS), "the server sees the client leave")
    }

    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content
}
