package app.regie.voice.elevenlabs

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.Pcm16
import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.SpeechRequest
import app.regie.voice.api.SpeechVoice
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ElevenLabsTextToSpeechTest {
    private val server = MockWebServer()

    @BeforeEach fun start() = server.start()

    @AfterEach fun stop() = server.close()

    private fun config(key: String? = "test-key", voice: String? = "voice123") =
        ElevenLabsConfig(apiKey = { key }, voiceId = { voice }, baseUrl = server.url("/").toString())

    private val pcm = ShortArray(22_050) { (it % 200 * 100 - 10_000).toShort() } // one second

    @Test
    fun `asks Flash for PCM and streams it back in frames`() {
        // Odd chunk sizes on the wire: samples must still be put back together exactly.
        server.enqueue(
            MockResponse.Builder().body(Buffer().write(Pcm16.toLittleEndian(pcm))).throttleBody(3_001, 1, TimeUnit.MILLISECONDS).build(),
        )

        val frames = runBlocking { withTimeout(10_000) { ElevenLabsTextToSpeech(config()).synthesize("Bonjour USER", SpeechRequest("fr")).toList() } }

        assertTrue(frames.size > 1, "streamed in several frames")
        assertTrue(frames.all { it.format == AudioFormat(22_050) })
        assertContentEquals(pcm, frames.flatMap { it.samples.asList() }.toShortArray())
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/v1/text-to-speech/voice123/stream", request.url.encodedPath)
        assertEquals("pcm_22050", request.url.queryParameter("output_format"))
        assertEquals("test-key", request.headers["xi-api-key"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("Bonjour USER", body["text"]!!.jsonPrimitive.content)
        assertEquals("eleven_flash_v2_5", body["model_id"]!!.jsonPrimitive.content)
        assertEquals("fr", body["language_code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `interrupting the speech cancels the request`() {
        val canceled = CountDownLatch(1)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) = canceled.countDown()
        }).build()
        // Ten seconds of audio, sent slowly.
        server.enqueue(
            MockResponse.Builder().body(Buffer().write(ByteArray(441_000))).throttleBody(4_410, 100, TimeUnit.MILLISECONDS).build(),
        )

        val firstFrames = runBlocking { withTimeout(10_000) { ElevenLabsTextToSpeech(config(), client).synthesize("Un long texte").take(2).toList() } }

        assertEquals(2, firstFrames.size)
        assertTrue(canceled.await(5, TimeUnit.SECONDS), "the HTTP call is cancelled")
    }

    @Test
    fun `HTTP errors become typed voice errors`() {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":{"status":"invalid_api_key","message":"Invalid API key"}}""").build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":{"status":"quota_exceeded","message":"Quota"}}""").build())
        server.enqueue(MockResponse.Builder().code(429).body("""{"detail":{"status":"system_busy"}}""").build())
        server.enqueue(MockResponse.Builder().code(422).body("""{"detail":{"status":"voice_not_found","message":"Voice not found"}}""").build())

        val errors = List(4) {
            assertFailsWith<VoiceException> { runBlocking { ElevenLabsTextToSpeech(config()).synthesize("x").first() } }.error
        }

        assertEquals(
            listOf(VoiceError.KeyRejected, VoiceError.QuotaExceeded, VoiceError.RateLimited, VoiceError.Provider("voice_not_found", "Voice not found")),
            errors,
        )
    }

    @Test
    fun `missing settings and network are reported`() {
        val noKey = assertFailsWith<VoiceException> { runBlocking { ElevenLabsTextToSpeech(config(key = null)).synthesize("x").first() } }
        val noVoice = assertFailsWith<VoiceException> { runBlocking { ElevenLabsTextToSpeech(config(voice = "")).synthesize("x").first() } }
        val url = server.url("/").toString()
        server.close()
        val offline = assertFailsWith<VoiceException> {
            runBlocking { ElevenLabsTextToSpeech(ElevenLabsConfig({ "k" }, { "v" }, baseUrl = url)).synthesize("x").first() }
        }

        assertEquals(VoiceError.KeyMissing, noKey.error)
        assertEquals(VoiceError.NotConfigured("voice_id"), noVoice.error)
        assertEquals(VoiceError.Offline, offline.error)
    }

    @Test
    fun `lists the voices so that the user can pick one`() {
        server.enqueue(
            MockResponse.Builder().body(
                """{"voices":[
                  {"voice_id":"abc","name":"Claire","labels":{"language":"fr","accent":"parisian"}},
                  {"voice_id":"def","name":"Rachel","labels":{},"verified_languages":[{"language":"en"}]},
                  {"name":"sans id"}
                ]}""",
            ).build(),
        )

        val voices = runBlocking { ElevenLabsTextToSpeech(config()).voices() }

        assertEquals(listOf(SpeechVoice("abc", "Claire", "fr"), SpeechVoice("def", "Rachel", "en")), voices)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/voices", request.url.encodedPath)
        assertEquals("test-key", request.headers["xi-api-key"])
    }

    @Test
    fun `the provider is ready only with a key`() {
        val withKey = runBlocking { ElevenLabsVoiceProvider(config()).prepare().toList() }
        val withoutKey = runBlocking { ElevenLabsVoiceProvider(config(key = null)).prepare().toList() }

        assertEquals(listOf(ProviderStatus.Ready), withKey)
        assertEquals(listOf(ProviderStatus.Unavailable(VoiceError.KeyMissing)), withoutKey)
    }
}
