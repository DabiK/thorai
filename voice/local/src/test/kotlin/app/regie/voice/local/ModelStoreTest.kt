package app.regie.voice.local

import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okio.Buffer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelStoreTest {
    @TempDir
    lateinit var root: File

    private val server = MockWebServer()
    private val client = OkHttpClient()

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.close()

    private fun spec(file: String, archive: Boolean, size: Long) =
        ModelSpec(name = file.substringBefore('.'), url = server.url("/models/$file").toString(), archive = archive, downloadBytes = size, installedBytes = size)

    /** A `.tar.bz2` like the sherpa-onnx releases: one top-level directory named like the model. */
    private fun tarBz2(entries: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            for ((name, content) in entries) {
                val entry = TarArchiveEntry(name)
                entry.size = content.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(content)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun body(bytes: ByteArray) = MockResponse.Builder().body(Buffer().write(bytes)).build()

    /** Serves [content], honouring `Range: bytes=n-` with a 206; records the Range headers. */
    private fun rangeServer(content: ByteArray, ranges: MutableList<String?>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.headers["Range"]
                ranges += range
                if (range == null) return body(content)
                val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                return MockResponse.Builder().code(206)
                    .setHeader("Content-Range", "bytes $from-${content.size - 1}/${content.size}")
                    .body(Buffer().write(content.copyOfRange(from, content.size))).build()
            }
        }
    }

    @Test
    fun `a single-file model is downloaded into its directory`() = runBlocking {
        val onnx = Random(1).nextBytes(10_000)
        server.enqueue(body(onnx))
        val store = ModelStore(root, client)
        val vad = spec("silero_vad.onnx", archive = false, size = 10_000)

        store.install(listOf(vad))

        assertTrue(store.isInstalled(vad))
        assertContentEquals(onnx, File(store.directory(vad), "silero_vad.onnx").readBytes())
        assertEquals(listOf("silero_vad"), root.list()!!.toList())
    }

    @Test
    fun `an archive is extracted into the model directory, then deleted`() = runBlocking {
        val archive = tarBz2(
            mapOf(
                "voice-model/fr.onnx" to Random(2).nextBytes(5_000),
                "voice-model/tokens.txt" to "a 1\nb 2\n".toByteArray(),
                "voice-model/data/fr_dict" to Random(3).nextBytes(700),
            ),
        )
        server.enqueue(body(archive))
        val store = ModelStore(root, client)
        val model = spec("voice-model.tar.bz2", archive = true, size = archive.size.toLong())

        store.install(listOf(model))

        val dir = store.directory(model)
        assertTrue(store.isInstalled(model))
        assertEquals("a 1\nb 2\n", File(dir, "tokens.txt").readText())
        assertEquals(700, File(dir, "data/fr_dict").length())
        // Only the installed model is left: no archive, no .part, no staging directory.
        assertEquals(listOf("voice-model"), root.list()!!.toList())
    }

    @Test
    fun `an interrupted download resumes where it stopped`() = runBlocking {
        val onnx = Random(4).nextBytes(200_000)
        val vad = spec("silero_vad.onnx", archive = false, size = onnx.size.toLong())
        // First attempt: the connection drops in the middle of the body.
        server.enqueue(MockResponse.Builder().body(Buffer().write(onnx)).onResponseBody(SocketEffect.CloseSocket()).build())
        val store = ModelStore(root, client)

        val error = assertFailsWith<VoiceException> { store.install(listOf(vad)) }

        assertEquals(VoiceError.Offline, error.error)
        val part = File(root, "silero_vad.onnx.part")
        val kept = part.length()
        assertTrue(kept in 1 until onnx.size, "kept $kept bytes")
        assertFalse(store.isInstalled(vad))

        val ranges = Collections.synchronizedList(mutableListOf<String?>())
        rangeServer(onnx, ranges)
        store.install(listOf(vad))

        assertEquals(listOf<String?>("bytes=$kept-"), ranges)
        assertContentEquals(onnx, File(store.directory(vad), "silero_vad.onnx").readBytes())
        assertFalse(part.exists())
    }

    @Test
    fun `a cancelled download keeps its part and resumes later`() = runBlocking {
        val onnx = Random(5).nextBytes(400_000)
        val vad = spec("silero_vad.onnx", archive = false, size = onnx.size.toLong())
        server.enqueue(MockResponse.Builder().body(Buffer().write(onnx)).throttleBody(20_000, 50, TimeUnit.MILLISECONDS).build())
        val store = ModelStore(root, client)
        val part = File(root, "silero_vad.onnx.part")

        val job = launch { store.install(listOf(vad)) }
        withTimeout(5_000) { while (part.length() == 0L) delay(10) }
        job.cancelAndJoin()

        assertFalse(store.isInstalled(vad))
        assertNull(store.progress.value)
        val ranges = Collections.synchronizedList(mutableListOf<String?>())
        rangeServer(onnx, ranges)
        store.install(listOf(vad))

        assertTrue(ranges.single()!!.startsWith("bytes="))
        assertContentEquals(onnx, File(store.directory(vad), "silero_vad.onnx").readBytes())
    }

    @Test
    fun `a server that ignores the range sends the whole file again`() = runBlocking {
        val onnx = Random(6).nextBytes(5_000)
        val vad = spec("silero_vad.onnx", archive = false, size = onnx.size.toLong())
        File(root, "silero_vad.onnx.part").writeBytes(onnx.copyOf(1_000))
        server.enqueue(body(onnx))

        ModelStore(root, client).install(listOf(vad))

        assertEquals("bytes=1000-", server.takeRequest().headers["Range"])
        assertContentEquals(onnx, File(root, "silero_vad/silero_vad.onnx").readBytes())
    }

    @Test
    fun `progress values are increasing and end at 1`() = runBlocking {
        val archive = tarBz2(mapOf("m/encoder.onnx" to Random(9).nextBytes(300_000)))
        server.enqueue(MockResponse.Builder().body(Buffer().write(archive)).throttleBody(50_000, 20, TimeUnit.MILLISECONDS).build())
        val store = ModelStore(root, client)
        val model = spec("m.tar.bz2", archive = true, size = archive.size.toLong())
        val values = Collections.synchronizedList(mutableListOf<Float>())

        val recorder = launch { store.progress.collect { it?.let(values::add) } }
        delay(20)
        store.install(listOf(model))
        recorder.cancelAndJoin()

        assertTrue(values.size > 2, "progress: $values")
        assertEquals(values.sorted(), values.toList())
        assertEquals(1f, values.last(), 0.01f)
    }

    @Test
    fun `nothing is downloaded without enough free space`() = runBlocking {
        val store = ModelStore(root, client, usableSpace = { 100_000_000 })
        val parakeet = spec("parakeet.tar.bz2", archive = true, size = 487_000_000)

        val error = assertFailsWith<VoiceException> { store.install(listOf(parakeet)) }

        assertEquals("storage_full", (error.error as VoiceError.Provider).code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an HTTP error is reported with its code`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())

        val error = assertFailsWith<VoiceException> {
            ModelStore(root, client).install(listOf(spec("silero_vad.onnx", archive = false, size = 10)))
        }

        assertEquals("download_http_404", (error.error as VoiceError.Provider).code)
    }

    @Test
    fun `an archive entry cannot escape the model directory`() = runBlocking {
        server.enqueue(body(tarBz2(mapOf("../../evil.txt" to "x".toByteArray()))))
        val model = spec("m.tar.bz2", archive = true, size = 1_000)

        assertFailsWith<VoiceException> { ModelStore(root, client).install(listOf(model)) }

        assertFalse(File(root.absoluteFile.parentFile!!.parentFile!!, "evil.txt").exists())
        assertFalse(File(root, "m.tar.bz2").exists(), "a bad archive is dropped, to be downloaded again")
    }

    @Test
    fun `concurrent installations download once`() = runBlocking {
        server.enqueue(body(Random(10).nextBytes(10_000)))
        val store = ModelStore(root, client)
        val vad = spec("silero_vad.onnx", archive = false, size = 10_000)

        listOf(async { store.install(listOf(vad)) }, async { store.install(listOf(vad)) }).awaitAll()

        assertEquals(1, server.requestCount)
        assertTrue(store.isInstalled(vad))
    }

    @Test
    fun `the real models come from the sherpa-onnx releases only`() {
        assertTrue(LocalModels.ALL.all { it.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/") })
        assertEquals(
            listOf("silero_vad.onnx", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2", "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"),
            LocalModels.ALL.map { it.fileName },
        )
    }
}
