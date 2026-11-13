package app.regie.voicelab

import app.regie.voice.api.VoiceError
import app.regie.voice.api.Vocabulary
import app.regie.voice.fake.FakeDictationScript
import app.regie.voice.fake.FakeSpeechToText
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringTest {
    @Test
    fun `text is compared in lower case, without punctuation`() {
        assertEquals("ouvre le fichier agentgateway kt", Scoring.normalize("Ouvre le fichier AgentGateway.kt !"))
        assertEquals(0.0, Scoring.wordErrorRate("Crée une branche ticket/05.", "crée une branche ticket 05"))
    }

    @Test
    fun `word error rate counts substitutions, deletions and insertions`() {
        assertEquals(0.25, Scoring.wordErrorRate("lance npm run check", "lance NPM run chèque"))
        assertEquals(0.5, Scoring.wordErrorRate("lance npm run check", "lance run"))
        assertEquals(1.0, Scoring.wordErrorRate("lance npm", "lance npm run check"))
        assertEquals(1.0, Scoring.wordErrorRate("", "bonjour"))
    }

    @Test
    fun `terms of the expected sentence are found or missed`() {
        val terms = listOf("AgentGateway", "sendDictation", "Termux", "build.gradle.kts")
        val expected = "Ouvre le fichier AgentGateway.kt et ajoute une méthode sendDictation, puis build.gradle.kts."

        val score = Scoring.terms(expected, "Ouvre le fichier agent gateway.kt et ajoute une méthode sendDictation puis build gradle kts", terms)

        assertEquals(listOf("sendDictation", "build.gradle.kts"), score.found)
        assertEquals(listOf("AgentGateway"), score.missed)
        assertEquals(3, score.expected)
    }

    @Test
    fun `statistics give mean, median and max`() {
        assertEquals(Stat(2.5, 2.5, 4.0), Stat.of(listOf(4.0, 1.0, 3.0, 2.0)))
        assertEquals(Stat(2.0, 2.0, 3.0), Stat.of(listOf(3.0, 1.0, 2.0)))
        assertNull(Stat.of(emptyList()))
    }
}

class BenchTest {
    @TempDir
    lateinit var dir: File

    private val sentences = """
        # comment
        02|Lance npm run check dans le dossier bridge et dis-moi si ça passe.
        13|Remplace le Thread.sleep par un delay dans la coroutine.
        17|Mets à jour le README avec la commande gradlew check.
    """.trimIndent()

    private fun copyWav(name: String, as_: String = name) =
        File(dir, as_).writeBytes(requireNotNull(javaClass.getResourceAsStream("/wav/$name")).use { it.readBytes() })

    @Test
    fun `recordings are matched to their sentence by their number`() {
        copyWav("consigne-02.wav")
        copyWav("consigne-13.wav", "13.wav")
        copyWav("consigne-17.wav", "x-99.wav")
        File(dir, "notes.txt").writeText("ignored")

        val cases = Bench.cases(dir, Bench.parseSentences(sentences))

        assertEquals(listOf("13", "02", "99"), cases.map { it.id })
        assertEquals("Remplace le Thread.sleep par un delay dans la coroutine.", cases[0].expected)
        assertNull(cases[2].expected)
        assertEquals(listOf("02"), Bench.cases(dir, Bench.parseSentences(sentences), only = setOf(2)).map { it.id })
    }

    @Test
    fun `each recording is dictated and scored`() = runTest {
        copyWav("consigne-02.wav")
        copyWav("consigne-17.wav")
        val stt = FakeSpeechToText(
            "Lance npm run check dans le dossier bridge et dis-moi si ça passe.",
            "Mets à jour le readme avec la commande gradle w check.",
        )
        val terms = Vocabulary.of("npm run check", "README", "gradlew check", "Termux")
        val progress = mutableListOf<Bench.Progress>()

        val items = Bench(terms, realtime = false).run(stt, Bench.cases(dir, Bench.parseSentences(sentences)), { 0.25 }) { p, _ -> progress += p }

        assertEquals(listOf("02", "17"), items.map { it.case.id })
        assertEquals(0.0, items[0].wer)
        assertEquals(listOf("npm run check"), items[0].terms?.found)
        assertEquals(listOf("README"), items[1].terms?.found)
        assertEquals(listOf("gradlew check"), items[1].terms?.missed)
        assertTrue(items.all { it.latencyMs != null && it.audioMs > 1_000 && it.error == null })
        assertEquals(0.25, items[0].realTimeFactor)
        assertEquals(listOf(0, 1, 1, 2), progress.map { it.done })
        // The vocabulary of the bench is the one sent to the provider.
        assertEquals(terms, stt.requests.first().vocabulary)
    }

    @Test
    fun `a failed dictation is reported, the bench goes on`() = runTest {
        copyWav("consigne-02.wav")
        copyWav("consigne-13.wav")
        val stt = FakeSpeechToText(
            listOf(FakeDictationScript.failing(VoiceError.KeyMissing), FakeDictationScript.saying("Remplace le Thread.sleep par un delay dans la coroutine.")),
        )

        val items = Bench(Vocabulary.EMPTY, realtime = false).run(stt, Bench.cases(dir, Bench.parseSentences(sentences)))

        assertEquals("KeyMissing", items[0].error)
        assertEquals(0.0, items[1].wer)
    }

    @Test
    fun `the report gives items and summary in JSON and CSV`() = runTest {
        copyWav("consigne-02.wav")
        copyWav("consigne-13.wav")
        val stt = FakeSpeechToText("Lance npm run check, dans le dossier bridge.", "Remplace le Thread.sleep par un delay dans la coroutine.")
        val items = Bench(Vocabulary.of("Thread.sleep"), realtime = false).run(stt, Bench.cases(dir, Bench.parseSentences(sentences)))

        val report = BenchReport("fake", mapOf("model" to "test"), "20261113-220000", items)
        val json = report.toJson()
        val csv = report.toCsv().lines()

        assertEquals(2, json["items"]!!.jsonArray.size)
        val summary = json["summary"]!!.jsonObject
        assertEquals(2, summary["count"]!!.jsonPrimitive.int)
        assertEquals(1, summary["termsFound"]!!.jsonPrimitive.int)
        assertTrue(summary["wer"]!!.jsonObject.containsKey("median"))
        assertTrue(csv[0].startsWith("id,wav,attendu,obtenu,latence_ms"))
        // A text with a comma is quoted.
        assertTrue(csv[1].contains("\"Lance npm run check, dans le dossier bridge.\""), csv[1])
        assertTrue(csv.any { it.startsWith("médiane,") })
    }
}

class TriggersTest {
    private val events = mutableListOf<Pair<Triggers.Trigger, Boolean>>()
    private val triggers = Triggers { trigger, pressed -> events += trigger to pressed }

    @Test
    fun `the key and the axis of one press make one press and one release`() {
        triggers.onKey(Triggers.Trigger.L2, true)
        triggers.onAxis(Triggers.Trigger.L2, 0.3f)
        triggers.onAxis(Triggers.Trigger.L2, 1f)
        triggers.onKey(Triggers.Trigger.L2, false)
        triggers.onAxis(Triggers.Trigger.L2, 0.4f)
        triggers.onAxis(Triggers.Trigger.L2, 0f)

        assertEquals(listOf(Triggers.Trigger.L2 to true, Triggers.Trigger.L2 to false), events)
    }

    @Test
    fun `the axis alone works, with a hysteresis`() {
        triggers.onAxis(Triggers.Trigger.R2, 0.6f)
        triggers.onAxis(Triggers.Trigger.R2, 0.3f) // still held
        triggers.onAxis(Triggers.Trigger.R2, 0.1f)
        triggers.onAxis(Triggers.Trigger.R2, 0.4f) // not enough to press again

        assertEquals(listOf(Triggers.Trigger.R2 to true, Triggers.Trigger.R2 to false), events)
    }

    @Test
    fun `L2 and R2 are independent`() {
        triggers.onKey(Triggers.Trigger.L2, true)
        triggers.onKey(Triggers.Trigger.R2, true)
        triggers.onKey(Triggers.Trigger.R2, false)

        assertEquals(listOf(Triggers.Trigger.L2 to true, Triggers.Trigger.R2 to true, Triggers.Trigger.R2 to false), events)
    }
}

class CommandTest {
    private fun parse(vararg extras: Pair<String, Any>): List<Command> {
        val map = extras.toMap()
        return Command.parse(string = { map[it] as? String }, boolean = { map[it] as? Boolean })
    }

    @Test
    fun `settings come before actions`() {
        assertEquals(
            listOf(Command.UseProvider("local"), Command.Speak("Bonjour")),
            parse("speak" to "Bonjour", "provider" to "local"),
        )
    }

    @Test
    fun `a bench can be limited to some sentences`() {
        assertEquals(listOf(Command.RunBench("local", setOf(2, 13), null)), parse("bench" to "local", "only" to "02,13"))
        assertEquals(listOf(Command.RunBench("elevenlabs", null, null)), parse("bench" to "elevenlabs"))
    }

    @Test
    fun `a bench can replay the recordings of the guided reading`() {
        assertEquals(listOf(Command.RunBench("elevenlabs", null, "user")), parse("bench" to "elevenlabs", "corpus" to "user"))
        // Only a plain folder name under corpus/: no way out of it.
        assertEquals(listOf(Command.RunBench("local", null, null)), parse("bench" to "local", "corpus" to "../../secrets"))
    }

    @Test
    fun `the guided reading starts or stops`() {
        assertEquals(listOf(Command.StartGuided("local")), parse("guided" to "local"))
        assertEquals(listOf(Command.UseHotwords(false), Command.StopGuided), parse("guided" to "stop", "hotwords" to false))
    }

    @Test
    fun `commands are only read from the adb entry, that only the shell may start`() {
        val extras = mapOf("bench" to "elevenlabs", "speak" to "Bonjour")

        val fromShell = Command.parse(Command.ADB_ENTRY, string = { extras[it] }, boolean = { null })
        val fromAnotherApp = Command.parse("app.regie.voicelab.MainActivity", string = { extras[it] }, boolean = { null })

        assertEquals(2, fromShell.size)
        assertEquals(emptyList(), fromAnotherApp)
        assertEquals(emptyList(), Command.parse(null, string = { extras[it] }, boolean = { null }))
    }

    @Test
    fun `download, scribe and hotwords`() {
        assertEquals(
            listOf(Command.UseScribe("scribe_v2_realtime"), Command.UseHotwords(true), Command.DownloadLocal),
            parse("download" to "local", "scribe" to "scribe_v2_realtime", "hotwords" to true),
        )
        assertEquals(emptyList(), parse("download" to "everything", "speak" to " "))
    }
}
