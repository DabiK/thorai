package app.regie.domain.usecase

import app.regie.domain.model.*
import app.regie.domain.port.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReviewVoiceCommand(
    private val capture: DictationCapture,
    private val interpret: InterpretVoiceCommand,
    private val execute: suspend (VoiceCommand) -> String,
    private val scope: CoroutineScope,
    private val onLaunch: ((NewSessionDraft, String) -> Unit)? = null,
    private val loadProjects: (suspend () -> List<String>)? = null,
) {
    private val mutable = MutableStateFlow(VoiceCommandReview())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var held = false
    private var context: VoiceCommandContext? = null

    fun start(snapshot: VoiceCommandContext) {
        if (state.value.visible) return
        context = snapshot
        held = true
        mutable.value = VoiceCommandReview(CommandPhase.RECORDING, notice = "Relâche R2 quand tu as fini")
        capture.start(snapshot.sessions.flatMap { listOfNotNull(it.title, it.project, it.agent.name) } + snapshot.projects)
        job = scope.launch { capture.state.collect(::applyCapture) }
    }

    private fun applyCapture(event: CaptureState) {
        if (state.value.phase !in setOf(CommandPhase.RECORDING, CommandPhase.FINALIZING)) return
        mutable.value = state.value.copy(text = event.text, level = event.level, durationMs = event.durationMs,
            notice = event.error ?: event.notice ?: if (held) "Relâche R2 quand tu as fini" else "Transcription…")
        if (held) return
        if (event.error != null) {
            mutable.value = state.value.copy(phase = CommandPhase.UNKNOWN, command = null)
        } else if (event.complete) {
            mutable.value = state.value.copy(phase = CommandPhase.INTERPRETING, notice = "Interprétation…", level = 0f)
            val snapshot = context ?: return
            job?.cancel()
            job = scope.launch {
                // A first R2 can start before the startup catalogue arrives. Capture must not wait.
                val context = if (loadProjects != null && launchPhrase(event.text) != null) {
                    val projects = try { loadProjects() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                    snapshot.copy(projects = projects)
                } else snapshot
                val result = interpret(event.text, context)
                val launch = result.command as? VoiceCommand.Launch
                if (launch != null && onLaunch != null) {
                    mutable.value = VoiceCommandReview()
                    onLaunch(launch.draft, event.text)
                    return@launch
                }
                // Opening a conversation or the overview changes no agent state (ticket 26).
                if (result.command is VoiceCommand.Navigate || result.command == VoiceCommand.ShowOverview) {
                    mutable.value = state.value.copy(phase = CommandPhase.RUNNING, command = result.command)
                    try {
                        execute(result.command)
                        mutable.value = VoiceCommandReview()
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {
                        mutable.value = state.value.copy(phase = CommandPhase.DONE, notice = "Ouverture impossible. Réessaie depuis la régie.")
                    }
                    return@launch
                }
                mutable.value = state.value.copy(phase = if (result.command == null) CommandPhase.UNKNOWN else CommandPhase.REVIEW,
                    command = result.command, notice = result.description)
            }
        }
    }

    fun finish() {
        if (state.value.phase != CommandPhase.RECORDING) return
        held = false
        mutable.value = state.value.copy(phase = CommandPhase.FINALIZING, level = 0f)
        capture.finish()
        applyCapture(capture.state.value)
    }

    fun confirm() {
        val current = state.value
        if (!current.canConfirm) return
        val command = current.command ?: return
        mutable.value = current.copy(phase = CommandPhase.RUNNING)
        job?.cancel()
        job = scope.launch {
            val result = try { execute(command) } catch (e: CancellationException) { throw e } catch (_: Exception) {
                "Résultat incertain. Vérifie la session. Aucun renvoi automatique."
            }
            mutable.value = current.copy(phase = CommandPhase.DONE, notice = result)
        }
    }

    fun discard() {
        if (state.value.phase == CommandPhase.RUNNING) return
        job?.cancel(); capture.cancel(); held = false; context = null
        mutable.value = VoiceCommandReview()
    }
}
