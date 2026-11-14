package app.regie.voice

import app.regie.domain.port.CaptureState
import app.regie.domain.port.DictationCapture
import app.regie.voice.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Translates the voice API to the application's capture port; providers are supplied by composition. */
class VoiceCapture(
    private val speech: () -> SpeechToText,
    private val source: () -> AudioSource,
    private val scope: CoroutineScope,
) : DictationCapture {
    private val mutable = MutableStateFlow(CaptureState())
    override val state = mutable.asStateFlow()
    private var dictation: Dictation? = null
    private var observer: Job? = null

    override fun start(vocabulary: List<String>) {
        cancel()
        mutable.value = CaptureState(recording = true, notice = "Préparation de la voix…")
        val audio = source()
        val measured = object : AudioSource {
            override val format = audio.format
            override fun frames() = audio.frames().onEach { frame ->
                val energy = frame.samples.sumOf { it.toDouble() * it } / frame.samples.size.coerceAtLeast(1)
                mutable.update { it.copy(level = (sqrt(energy) / 32768).toFloat().coerceIn(0f, 1f), durationMs = it.durationMs + frame.durationMs) }
            }
        }
        val active = speech().startDictation(scope, measured, DictationRequest("fr", Vocabulary(vocabulary)))
        dictation = active
        observer = scope.launch {
            active.state.collect { event ->
                mutable.update { before -> when (event) {
                    DictationState.Connecting -> before.copy(notice = "Préparation de la voix…")
                    is DictationState.PreparingModel -> before.copy(notice = "Modèle local : ${event.progress?.let { "${(it * 100).toInt()} %" } ?: "chargement…"}")
                    is DictationState.Listening -> before.copy(text = event.partial, notice = null)
                    is DictationState.Finalizing -> before.copy(text = event.partial, recording = false, notice = "Transcription…")
                    is DictationState.Final -> before.copy(text = event.text, recording = false, complete = true, level = 0f, notice = null)
                    is DictationState.Failed -> before.copy(recording = false, level = 0f, error = message(event.error))
                    DictationState.Cancelled -> before.copy(recording = false, level = 0f)
                } }
                // A terminal event ends ownership of audio even if a provider is still unwinding.
                if (event.isTerminal) active.cancel()
            }
        }
    }
    override fun finish() {
        mutable.update { it.copy(recording = false, level = 0f) }
        dictation?.finish()
    }
    override fun cancel() {
        observer?.cancel()
        dictation?.cancel()
        dictation = null
        mutable.update { it.copy(recording = false, level = 0f) }
    }
}

private fun message(error: VoiceError): String = when (error) {
    VoiceError.KeyMissing -> "Saisis ta clé ElevenLabs dans les réglages voix."
    VoiceError.KeyRejected -> "Clé ElevenLabs refusée. Vérifie les réglages voix."
    VoiceError.Offline -> "Connexion voix interrompue. Réessaie ou choisis la voix locale."
    VoiceError.QuotaExceeded -> "Quota ElevenLabs épuisé. La voix locale reste disponible."
    VoiceError.RateLimited -> "Trop de demandes. Réessaie dans un instant."
    VoiceError.ModelMissing -> "Le modèle local doit être téléchargé."
    is VoiceError.NotConfigured -> "Autorise le microphone dans les réglages voix."
    is VoiceError.Provider -> "La transcription a échoué. Réessaie avec L2."
}
