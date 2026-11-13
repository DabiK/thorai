package app.regie.voicelab

import app.regie.voice.api.AudioFormat
import app.regie.voice.api.DictationState
import app.regie.voice.api.VoiceError
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.Wav
import app.regie.voice.api.WavAudioSource
import app.regie.voice.fake.FakeSpeechToText
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidedReadingTest {
    @TempDir
    lateinit var root: File

    private val sentences = mapOf(
        13 to "Remplace le Thread.sleep par un delay dans la coroutine.",
        2 to "Lance npm run check dans le dossier bridge et dis-moi si ça passe.",
    )
    private val vocabulary = Vocabulary.of("npm run check", "Thread.sleep")
    private val user get() = File(root, "user")

    private fun recording(name: String) =
        WavAudioSource(requireNotNull(javaClass.getResourceAsStream("/wav/$name")).use { it.readBytes() }).samples

    private fun reading() = GuidedReading("local", GuidedReading.prompts(sentences), user, vocabulary)

    @Test
    fun `sentences are read in the order of their number`() {
        val reading = reading()

        assertEquals(Prompt(2, sentences.getValue(2)), reading.current)
        assertEquals(2, reading.total)
        assertEquals("02", reading.current?.label)
    }

    @Test
    fun `each reading is saved, scored, and the next sentence comes`() {
        val reading = reading()
        val audio = recording("consigne-02.wav")

        val item = reading.record(audio, AudioFormat.SPEECH, DictationState.Final("Lance npm run check dans le dossier bridge."), 420, 0.3)

        assertEquals("02", item.case.id)
        assertEquals(File(user, "02.wav"), item.case.wav)
        assertEquals(420, item.latencyMs)
        assertEquals(audio.size * 1000L / 16_000, item.audioMs)
        assertTrue(item.wer!! > 0.0)
        assertEquals(listOf("npm run check"), item.terms?.found)
        assertEquals(0.3, item.realTimeFactor)
        assertEquals(13, reading.current?.id)
    }

    @Test
    fun `the recordings can be replayed by the bench in another provider`() = runTest {
        val reading = reading()
        val audio = recording("consigne-02.wav")
        reading.record(audio, AudioFormat.SPEECH, DictationState.Final("x"), 100, null)

        val cases = Bench.cases(user, sentences)
        val stt = FakeSpeechToText("Lance npm run check dans le dossier bridge et dis-moi si ça passe.")
        val items = Bench(vocabulary, realtime = false).run(stt, cases)

        assertEquals(listOf("02"), cases.map { it.id })
        assertEquals(sentences[2], cases.single().expected)
        assertContentEquals(audio, WavAudioSource.fromFile(File(user, "02.wav")).samples)
        assertEquals(0.0, items.single().wer)
        assertEquals(audio.size * 1000L / 16_000, items.single().audioMs)
    }

    @Test
    fun `B goes back to the sentence just read`() {
        val reading = reading()
        assertFalse(reading.redo(), "nothing to read again yet")
        reading.record(ShortArray(16_000), AudioFormat.SPEECH, DictationState.Final("faux"), 100, null)

        assertTrue(reading.redo())

        assertEquals(2, reading.current?.id)
        assertTrue(reading.results.isEmpty())
    }

    @Test
    fun `a failed dictation keeps its recording and its error, the reading goes on`() {
        val reading = reading()

        val item = reading.record(ShortArray(8_000), AudioFormat.SPEECH, DictationState.Failed(VoiceError.Offline), null, null)

        assertEquals("Offline", item.error)
        assertNull(item.obtained)
        assertTrue(File(user, "02.wav").isFile)
        assertEquals(13, reading.current?.id)
    }

    @Test
    fun `once every sentence is read, the report has the bench format`() {
        val reading = reading()
        reading.record(recording("consigne-02.wav"), AudioFormat.SPEECH, DictationState.Final(sentences.getValue(2)), 300, null)
        reading.record(recording("consigne-13.wav"), AudioFormat.SPEECH, DictationState.Final(sentences.getValue(13)), 500, null)

        assertTrue(reading.finished)
        val json = reading.report(mapOf("source" to "microphone"), "20261113-220000").toJson()

        assertEquals("local", json["provider"]!!.jsonPrimitive.content)
        assertEquals(listOf("02.wav", "13.wav"), json["items"]!!.jsonArray.map { it.jsonObject["wav"]!!.jsonPrimitive.content })
        assertEquals(400.0, json["summary"]!!.jsonObject["latencyMs"]!!.jsonObject["mean"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun `the capture keeps what the provider received`() = runTest {
        val wav = Wav.encode(recording("consigne-17.wav"), AudioFormat.SPEECH)
        val source = CapturingSource(WavAudioSource(wav, frameDurationMs = 100))

        val frames = source.frames().toList()

        assertContentEquals(frames.flatMap { it.samples.toList() }.toShortArray(), source.samples())
        assertEquals(frames.sumOf { it.samples.size } * 1000L / 16_000, source.millis)
    }
}
