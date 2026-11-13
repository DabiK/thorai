package app.regie.voicelab

import android.content.Context
import android.os.Build
import android.util.Log
import app.regie.voice.api.Dictation
import app.regie.voice.api.DictationRequest
import app.regie.voice.api.DictationState
import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.SpeechVoice
import app.regie.voice.api.VoiceError
import app.regie.voice.api.VoiceException
import app.regie.voice.api.VoiceProvider
import app.regie.voice.api.Vocabulary
import app.regie.voice.api.startDictation
import app.regie.voice.elevenlabs.ElevenLabsConfig
import app.regie.voice.elevenlabs.ElevenLabsVoiceProvider
import app.regie.voice.elevenlabs.ScribeModel
import app.regie.voice.local.LocalConfig
import app.regie.voice.local.LocalModels
import app.regie.voice.local.LocalVoiceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Measures of the last dictation. */
data class Measure(val provider: String, val latencyMs: Long?, val audioMs: Long, val realTimeFactor: Double?)

data class LocalUi(
    val installed: Boolean,
    val remainingMb: Long,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val status: String? = null,
    val hotwords: Boolean = false,
)

data class ElevenLabsUi(
    val keySaved: Boolean,
    val scribe: ScribeModel,
    val voiceName: String?,
    val voices: List<SpeechVoice> = emptyList(),
    val voicesStatus: String? = null,
)

data class BenchUi(val provider: String, val done: Int, val total: Int, val line: String, val running: Boolean)

/** Guided reading: [read] sentences done out of [total], [prompt] to read now (null once finished). */
data class GuidedUi(
    val provider: String,
    val read: Int,
    val total: Int,
    val prompt: Prompt?,
    val last: BenchItem?,
    val status: String?,
)

data class UiState(
    val provider: String,
    val dictation: DictationState? = null,
    val measure: Measure? = null,
    val speaking: Boolean = false,
    val speechInfo: String? = null,
    val local: LocalUi,
    val elevenLabs: ElevenLabsUi,
    val bench: BenchUi? = null,
    val guided: GuidedUi? = null,
)

/**
 * Composition root and state of the trial app: it builds the providers, runs dictations, speech,
 * downloads and benches, and exposes what the screen shows. The provider is read from the
 * settings at each use, so switching applies to the next dictation without restarting.
 */
class VoiceLab(private val context: Context, private val scope: CoroutineScope) {
    private val settings = Settings(context)
    private val secrets = SecretStore(context)
    private val sink = SpeakerSink()

    val local = LocalVoiceProvider(File(context.filesDir, "voice-models"), LocalConfig(hotwords = { settings.hotwords }))
    private var elevenLabs = buildElevenLabs()

    /** Terms to favour: `voice/corpus/termes.txt`, packaged as an asset. */
    private val vocabulary = Vocabulary.parse(asset("termes.txt"))

    private val mutableUi = MutableStateFlow(
        UiState(
            provider = settings.provider,
            local = localUi(),
            elevenLabs = ElevenLabsUi(secrets.contains(SecretStore.ELEVENLABS_KEY), settings.scribeModel, settings.voiceName),
        ),
    )
    val ui: StateFlow<UiState> = mutableUi.asStateFlow()

    private var dictation: Dictation? = null
    private var releasedAt = 0L
    private var speech: Job? = null
    private var download: Job? = null
    private var bench: Job? = null
    private var guided: GuidedReading? = null
    private var guidedStartedAt = ""

    /** Whether the guided reading is on: B then reads the last sentence again. */
    val guidedActive: Boolean get() = guided != null

    init {
        preloadLocal()
    }

    /** Loads the installed local models in the background, so that the first dictation is fast. */
    private fun preloadLocal() {
        if (settings.provider == LocalVoiceProvider.ID.value && local.modelsInstalled) downloadLocal()
    }

    private fun buildElevenLabs() = ElevenLabsVoiceProvider(
        ElevenLabsConfig(
            apiKey = { secrets.read(SecretStore.ELEVENLABS_KEY) },
            voiceId = { settings.voiceId },
            sttModel = settings.scribeModel,
        ),
    )

    private fun provider(id: String = settings.provider): VoiceProvider = if (id == LocalVoiceProvider.ID.value) local else elevenLabs

    private fun localUi() = LocalUi(
        installed = local.modelsInstalled,
        remainingMb = local.remainingDownloadBytes / 1_000_000,
        hotwords = settings.hotwords,
    )

    // --- Settings ---

    fun selectProvider(id: String) {
        if (id != LocalVoiceProvider.ID.value && id != ElevenLabsVoiceProvider.ID.value) {
            Log.w(TAG, "unknown provider '$id'")
            return
        }
        settings.provider = id
        mutableUi.update { it.copy(provider = id) }
        Log.i(TAG, "provider: $id")
        preloadLocal()
    }

    fun selectScribe(model: ScribeModel) {
        settings.scribeModel = model
        elevenLabs = buildElevenLabs()
        mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(scribe = model)) }
        Log.i(TAG, "scribe model: ${model.id}")
    }

    fun setHotwords(enabled: Boolean) {
        settings.hotwords = enabled
        mutableUi.update { it.copy(local = it.local.copy(hotwords = enabled)) }
        Log.i(TAG, "local hotwords: $enabled")
    }

    /** Stores the key typed by the user, encrypted. It is never logged. */
    fun saveKey(key: String) {
        if (key.isBlank()) return
        secrets.write(SecretStore.ELEVENLABS_KEY, key.trim())
        mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(keySaved = true)) }
    }

    fun deleteKey() {
        secrets.delete(SecretStore.ELEVENLABS_KEY)
        mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(keySaved = false)) }
    }

    fun loadVoices() {
        scope.launch {
            mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(voicesStatus = "Chargement des voix…")) }
            try {
                val voices = elevenLabs.textToSpeech.voices()
                mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(voices = voices, voicesStatus = "${voices.size} voix")) }
            } catch (e: VoiceException) {
                mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(voicesStatus = describe(e.error))) }
            }
        }
    }

    fun selectVoice(voice: SpeechVoice) {
        settings.voiceId = voice.id
        settings.voiceName = voice.name
        mutableUi.update { it.copy(elevenLabs = it.elevenLabs.copy(voiceName = voice.name)) }
    }

    // --- Dictation: hold L2 ---

    fun startDictation() {
        if (dictation?.state?.value?.isTerminal == false) return
        val reading = guided
        if (reading != null && reading.finished) return
        stopSpeaking()
        val provider = provider(reading?.provider ?: settings.provider)
        val prompt = reading?.current
        val microphone = CapturingSource(MicrophoneSource(context))
        val current = provider.speechToText.startDictation(scope, microphone, DictationRequest(language = "fr", vocabulary = vocabulary))
        dictation = current
        releasedAt = 0L
        mutableUi.update { it.copy(dictation = DictationState.Connecting, measure = null) }
        Log.i(TAG, "dictation started with ${provider.id}")
        scope.launch {
            val terminal = current.state.onEach { state -> mutableUi.update { it.copy(dictation = state) } }.first { it.isTerminal }
            val latencyMs = if (terminal is DictationState.Final && releasedAt > 0) (System.nanoTime() - releasedAt) / 1_000_000 else null
            val rtf = if (provider === local && terminal is DictationState.Final) local.speechToText.lastStats?.realTimeFactor else null
            val measure = Measure(provider.displayName, latencyMs, microphone.millis, rtf)
            mutableUi.update { it.copy(measure = measure) }
            Log.i(TAG, "dictation ended: ${terminal.javaClass.simpleName}, latency=${latencyMs}ms audio=${measure.audioMs}ms rtf=$rtf")
            // Cancelled by B: the same sentence is read again.
            if (reading != null && reading === guided && reading.current == prompt && terminal !is DictationState.Cancelled) {
                recordGuided(reading, provider, microphone, terminal, latencyMs, rtf)
            }
        }
    }

    /** End of speech (L2 released): latency is measured from here. */
    fun finishDictation() {
        val current = dictation ?: return
        if (current.state.value.isTerminal) return
        releasedAt = System.nanoTime()
        current.finish()
    }

    // --- Speech ---

    fun speak(text: String) {
        stopSpeaking()
        if (text.isBlank()) return
        val provider = provider()
        speech = scope.launch {
            val start = System.nanoTime()
            var firstAudio: Long? = null
            mutableUi.update { it.copy(speaking = true, speechInfo = "Lecture avec ${provider.displayName}…") }
            try {
                sink.play(
                    provider.textToSpeech.synthesize(text).onEach {
                        if (firstAudio == null) firstAudio = (System.nanoTime() - start) / 1_000_000
                    },
                )
                val info = "Lu avec ${provider.displayName}, premier son en ${firstAudio ?: "?"} ms"
                mutableUi.update { it.copy(speechInfo = info) }
                Log.i(TAG, "speech done: first audio ${firstAudio}ms, total ${(System.nanoTime() - start) / 1_000_000}ms")
            } catch (e: CancellationException) {
                mutableUi.update { it.copy(speechInfo = "Voix coupée") }
                Log.i(TAG, "speech cut after ${(System.nanoTime() - start) / 1_000_000}ms")
                throw e
            } catch (e: VoiceException) {
                mutableUi.update { it.copy(speechInfo = describe(e.error)) }
                Log.w(TAG, "speech failed: ${e.error}")
            } finally {
                mutableUi.update { it.copy(speaking = false) }
            }
        }
    }

    fun stopSpeaking() {
        speech?.cancel()
        speech = null
    }

    // --- Local models ---

    fun downloadLocal() {
        if (download?.isActive == true) return
        download = scope.launch {
            mutableUi.update { it.copy(local = it.local.copy(downloading = true, status = "Préparation…")) }
            val last = prepareLocal()
            mutableUi.update {
                it.copy(local = localUi().copy(status = if (last is ProviderStatus.Unavailable) describe(last.error) else "Modèles prêts"))
            }
        }
    }

    /** Downloads and loads the local models, reporting progress on screen and in logcat. */
    private suspend fun prepareLocal(): ProviderStatus {
        var logged = -1
        return local.prepare().onEach { status ->
            val line = when (status) {
                is ProviderStatus.Downloading -> status.progress?.let { "Téléchargement ${(it * 100).toInt()} %" } ?: "Chargement des modèles…"
                ProviderStatus.Ready -> "Modèles prêts"
                is ProviderStatus.Unavailable -> describe(status.error)
            }
            mutableUi.update { it.copy(local = it.local.copy(progress = (status as? ProviderStatus.Downloading)?.progress, status = line)) }
            val percent = ((status as? ProviderStatus.Downloading)?.progress?.times(100))?.toInt()
            if (percent == null || percent / 5 != logged) {
                logged = percent?.div(5) ?: logged
                Log.i(TAG, "local models: $line")
            }
        }.last()
    }

    // --- Guided reading: the user reads the corpus, holding L2 ---

    /** Starts reading the corpus from its first sentence, dictating with [providerId]. */
    fun startGuided(providerId: String) {
        if (providerId != LocalVoiceProvider.ID.value && providerId != ElevenLabsVoiceProvider.ID.value) {
            Log.w(TAG, "unknown provider '$providerId'")
            return
        }
        val dir = corpusDir(USER_CORPUS)
        val reading = GuidedReading(providerId, GuidedReading.prompts(sentences(dir)), dir, vocabulary)
        guided = reading
        guidedStartedAt = timestamp()
        mutableUi.update { it.copy(dictation = null, measure = null) }
        showGuided(reading, null, null)
        Log.i(TAG, "guided: ${reading.total} sentences with $providerId, recordings in $dir")
        if (providerId == LocalVoiceProvider.ID.value && local.modelsInstalled) downloadLocal()
    }

    fun stopGuided() {
        guided = null
        dictation?.cancel()
        mutableUi.update { it.copy(guided = null) }
        Log.i(TAG, "guided: stopped")
    }

    /** B: drops the dictation in progress, or goes back to the last sentence read; it is read again. */
    fun redoGuided() {
        val reading = guided ?: return
        val current = dictation
        if (current != null && !current.state.value.isTerminal) {
            current.cancel()
            showGuided(reading, null, "Dictée annulée : relis la phrase")
        } else if (reading.redo()) {
            showGuided(reading, null, "Relis la phrase")
        }
        Log.i(TAG, "guided: read ${reading.current?.label} again")
    }

    private fun recordGuided(
        reading: GuidedReading,
        provider: VoiceProvider,
        capture: CapturingSource,
        terminal: DictationState,
        latencyMs: Long?,
        rtf: Double?,
    ) {
        val item = reading.record(capture.samples(), capture.format, terminal, latencyMs, rtf)
        Log.i(TAG, "guided: ${describe(item)}")
        var status: String? = null
        if (reading.finished) {
            val report = reading.report(benchSettings(provider) + ("source" to "microphone"), guidedStartedAt)
            val name = "guided-${reading.provider}-$guidedStartedAt"
            writeReport(filesRoot(), name, report)
            status = "Fini : $name.json, latence médiane ${report.latency?.median?.toLong()} ms, " +
                "WER moyen ${report.wer?.mean?.let { "%.3f".format(Locale.ROOT, it) }}, termes ${report.termsFound}/${report.termsExpected}"
            Log.i(TAG, "guided: $status")
        }
        showGuided(reading, item, status)
    }

    private fun showGuided(reading: GuidedReading, last: BenchItem?, status: String?) {
        mutableUi.update {
            it.copy(guided = GuidedUi(reading.provider, reading.results.size, reading.total, reading.current, last, status))
        }
    }

    // --- Bench, driven by adb ---

    fun runBench(providerId: String, only: Set<Int>?, corpusName: String? = null) {
        if (bench?.isActive == true) {
            Log.w(TAG, "bench already running")
            return
        }
        bench = scope.launch(Dispatchers.Default) {
            val provider = provider(providerId)
            fun show(line: String, done: Int = 0, total: Int = 0, running: Boolean = true) {
                mutableUi.update { it.copy(bench = BenchUi(providerId, done, total, line, running)) }
                Log.i(TAG, "bench: $line")
            }
            val root = filesRoot()
            val corpus = corpusDir(corpusName)
            val terms = corpusFile(corpus, "termes.txt")?.let(Vocabulary::parse) ?: vocabulary
            val cases = Bench.cases(corpus, sentences(corpus), only)
            if (cases.isEmpty()) return@launch show("aucun WAV dans $corpus", running = false)

            if (provider === local) {
                val status = prepareLocal()
                if (status !is ProviderStatus.Ready) return@launch show("modèles locaux indisponibles", running = false)
            } else if (provider.prepare().last() is ProviderStatus.Unavailable) {
                return@launch show("ElevenLabs indisponible : clé absente", running = false)
            }

            val startedAt = timestamp()
            show("${cases.size} enregistrements, ${provider.displayName}", 0, cases.size)
            val items = Bench(terms).run(
                provider.speechToText,
                cases,
                realTimeFactor = { if (provider === local) local.speechToText.lastStats?.realTimeFactor else null },
            ) { progress, item ->
                val line = if (item == null) {
                    "${progress.done + 1}/${progress.total} ${progress.current}"
                } else {
                    describe(item)
                }
                show(line, progress.done, progress.total)
            }
            val settingsUsed = benchSettings(provider) + mapOf("realtime" to "true", "corpus" to (corpusName ?: "."))
            val report = BenchReport(providerId, settingsUsed, startedAt, items)
            val name = "bench-$providerId-" + (corpusName?.let { "$it-" } ?: "") + startedAt
            writeReport(root, name, report)
            show(
                "fini : $name.json, latence médiane ${report.latency?.median?.toLong()} ms, " +
                    "WER moyen ${report.wer?.mean?.let { "%.3f".format(Locale.ROOT, it) }}, termes ${report.termsFound}/${report.termsExpected}",
                items.size,
                items.size,
                running = false,
            )
        }
    }

    private fun benchSettings(provider: VoiceProvider): Map<String, String> = buildMap {
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        if (provider === local) {
            put("model", LocalModels.PARAKEET.name)
            put("hotwords", settings.hotwords.toString())
        } else {
            put("model", settings.scribeModel.id)
        }
    }

    // --- adb commands ---

    fun handle(commands: List<Command>) {
        for (command in commands) {
            Log.i(TAG, "command: ${command.javaClass.simpleName}")
            when (command) {
                is Command.UseProvider -> selectProvider(command.provider)
                is Command.UseScribe -> ScribeModel.entries.firstOrNull { it.id == command.model }?.let(::selectScribe)
                    ?: Log.w(TAG, "unknown scribe model '${command.model}'")
                is Command.UseHotwords -> setHotwords(command.enabled)
                Command.DownloadLocal -> downloadLocal()
                is Command.RunBench -> runBench(command.provider, command.only, command.corpus)
                is Command.StartGuided -> startGuided(command.provider)
                Command.StopGuided -> stopGuided()
                is Command.Speak -> speak(command.text)
            }
        }
    }

    private fun asset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    private fun filesRoot(): File = requireNotNull(context.getExternalFilesDir(null)) { "no external files dir" }

    /** `files/corpus/`, or its sub-folder [name] (`user`: the recordings of the guided reading). */
    private fun corpusDir(name: String?): File = File(filesRoot(), "corpus").let { if (name == null) it else File(it, name) }

    /** [name] from [dir], else from `files/corpus/`; null if neither has it (the asset is then used). */
    private fun corpusFile(dir: File, name: String): String? =
        listOf(File(dir, name), File(corpusDir(null), name)).firstOrNull { it.isFile }?.readText()

    private fun sentences(dir: File): Map<Int, String> = Bench.parseSentences(corpusFile(dir, "consignes.txt") ?: asset("consignes.txt"))

    private fun writeReport(root: File, name: String, report: BenchReport) {
        File(root, "$name.json").writeText(PRETTY.encodeToString(JsonObject.serializer(), report.toJson()))
        File(root, "$name.csv").writeText(report.toCsv())
    }

    companion object {
        private const val TAG = "VoiceLab"

        /** Sub-folder of `files/corpus/` where the guided reading saves its recordings. */
        const val USER_CORPUS = "user"
        private val PRETTY = Json { prettyPrint = true }

        private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

        /** One result, as shown on screen and in logcat. */
        fun describe(item: BenchItem): String =
            "${item.case.id} latence=${item.latencyMs}ms wer=${item.wer?.let { "%.2f".format(Locale.ROOT, it) }} " +
                "termes=${item.terms?.found?.size ?: 0}/${item.terms?.expected ?: 0} obtenu=\"${item.obtained ?: item.error}\""

        /** What to tell the user, in French. */
        fun describe(error: VoiceError): String = when (error) {
            VoiceError.Offline -> "Hors ligne : vérifier le réseau"
            VoiceError.KeyMissing -> "Clé ElevenLabs absente : la saisir dans les réglages"
            VoiceError.KeyRejected -> "Clé ElevenLabs refusée"
            VoiceError.QuotaExceeded -> "Quota ElevenLabs épuisé"
            VoiceError.RateLimited -> "Trop de requêtes : réessayer dans un moment"
            VoiceError.ModelMissing -> "Modèles locaux absents : les télécharger"
            is VoiceError.NotConfigured -> when (error.setting) {
                "voice_id" -> "Choisir une voix ElevenLabs dans les réglages"
                "RECORD_AUDIO" -> "Autoriser le micro"
                else -> "Réglage manquant : ${error.setting}"
            }
            is VoiceError.Provider -> "Erreur ${error.code}" + (error.message?.let { " : $it" } ?: "")
        }
    }
}
