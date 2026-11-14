package app.regie

import app.regie.domain.model.*
import app.regie.commandmodel.MistralCommandModel
import app.regie.domain.usecase.ReviewVoiceCommand
import app.regie.domain.usecase.InterpretVoiceCommand
import app.regie.domain.usecase.ExecuteVoiceCommand
import app.regie.domain.usecase.AnswerApproval
import app.regie.domain.usecase.DeliverDictation
import app.regie.session.sessionId
import app.regie.domain.usecase.ReviewDiff
import app.regie.domain.usecase.DiffReview
import app.regie.domain.usecase.ReviewDictation
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import app.regie.voice.*
import app.regie.voice.api.WavAudioSource
import app.regie.voice.local.LocalVoiceProvider
import app.regie.voice.local.LocalConfig
import app.regie.voice.elevenlabs.ElevenLabsVoiceProvider
import app.regie.voice.elevenlabs.ElevenLabsConfig
import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Process
import android.util.Log
import app.regie.console.ExternalBridge
import app.regie.console.TermuxBridgeLauncher
import app.regie.console.WirelessDebuggingAccess
import app.regie.display.DisplayMonitor
import app.regie.domain.model.Delivery
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Overview
import app.regie.domain.port.AgentGateway
import app.regie.domain.usecase.DictatedMessages
import app.regie.domain.usecase.InterruptSession
import app.regie.domain.usecase.KeepBridgeUp
import app.regie.domain.usecase.ObserveOverview
import app.regie.domain.usecase.ReviewApprovals
import app.regie.domain.usecase.ApprovalReview
import app.regie.domain.usecase.NotifySessions
import app.regie.console.AndroidSessionNotifications
import app.regie.domain.usecase.NotifyApprovals
import app.regie.domain.model.ApprovalId
import app.regie.approval.ApprovalController
import app.regie.console.AndroidApprovalNotifications
import app.regie.domain.usecase.OpenSession
import app.regie.domain.usecase.Quit
import app.regie.domain.usecase.SendMessage
import app.regie.domain.usecase.ReviewNewSession
import app.regie.domain.usecase.StartUp
import app.regie.domain.usecase.StartUpReport
import app.regie.gateway.bridge.BridgeAgentGateway
import app.regie.gateway.bridge.ReplayAgentGateway
import app.regie.input.InputRouter
import app.regie.overview.OverviewAction
import app.regie.overview.OverviewController
import app.regie.overview.OverviewGrid
import app.regie.session.Screen
import app.regie.domain.usecase.ReviewSessionSettings
import app.regie.domain.model.SessionSettingsReview
import app.regie.session.SessionAction
import app.regie.session.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RegieApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, getSystemService(DisplayManager::class.java))
    }
}

/**
 * Racine de composition, unique pour le processus : les deux activités partagent l'écran affiché
 * (vue d'ensemble ou session), la vue d'ensemble, la session ouverte, les contrôleurs, le routeur
 * d'entrées et le mode d'écran. C'est le seul endroit qui choisit la passerelle vers les agents (ADR 06).
 */
class AppContainer(private val app: Application, displayManager: DisplayManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _overview = MutableStateFlow(Overview.EMPTY)
    val overview: StateFlow<Overview> = _overview.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Overview)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** La session affichée, `null` sur la vue d'ensemble, pendant l'ouverture, ou si elle a disparu. */
    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private var gateway: AgentGateway? = null
    private var startUp: StartUp? = null
    private var quit: Quit? = null
    private var sendMessage: SendMessage? = null
    private var interruptSession: InterruptSession? = null
    private val dictated = DictatedMessages()
    private var startUpJob: Job? = null
    private var keepBridgeUpJob: Job? = null

    private val voicePreferences = VoicePreferences(app)
    val voiceSettings = voicePreferences.state
    private var settingsReview: ReviewSessionSettings? = null
    val sessionSettings = MutableStateFlow(SessionSettingsReview())
    fun selectSessionSetting(index: Int) { settingsReview?.select(index) }
    fun openSessionSettings() { (screen.value as? Screen.Session)?.let { settingsReview?.open(it.id) } }
    val settingsOpen = MutableStateFlow(false)
    private val localVoice = LocalVoiceProvider(File(app.filesDir, "voice-models"), LocalConfig(hotwords = { true }))
    private val cloudVoice = ElevenLabsVoiceProvider(ElevenLabsConfig(apiKey = voicePreferences::apiKey))
    /** Debug-only WAV replay uses the real provider and the same review/delivery path. */
    var debugVoiceWav: File? = null
    private val capture = VoiceCapture(
        { if (voiceSettings.value.provider == DictationProvider.LOCAL) localVoice.speechToText else cloudVoice.speechToText },
        { debugVoiceWav?.let { WavAudioSource.fromFile(it, realtime = true) } ?: ForegroundMicrophone(app) }, scope,
    )
    private var deliverDictation: DeliverDictation? = null
    val dictation = ReviewDictation(capture, { target, text ->
        deliverDictation?.invoke(target, text) ?: DictationDelivery("Pont indisponible", target)
    }, scope)
    private var executeVoiceCommand: ExecuteVoiceCommand? = null
    private var projects: List<String> = emptyList()
    val voiceCommand = ReviewVoiceCommand(capture, InterpretVoiceCommand(MistralCommandModel(voicePreferences::commandKey)),
        { command -> executeVoiceCommand?.invoke(command) ?: "Pont indisponible" }, scope, onLaunch = { draft, text -> openNewSession(draft, text) }, loadProjects = { gateway?.projects().orEmpty() })
    val commandScroll = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val commandVelocity = MutableStateFlow(0f)

    private fun startVoiceCommand() {
        if (dictation.state.value.visible || settingsOpen.value || controller.state.value.quit != null) return
        val shown = _screen.value
        val current = when (shown) {
            is Screen.Session -> shown.id
            is Screen.Diff -> shown.id
            Screen.Approval -> approvalReview.value.request?.sessionId
            Screen.Overview -> controller.state.value.focusedId
        }
        val approval = when (shown) {
            Screen.Approval -> approvalReview.value.request
            Screen.Overview -> overview.value.firstPendingApproval?.approval
            is Screen.Session, is Screen.Diff -> null
        }
        voiceCommand.start(VoiceCommandContext(overview.value.sessions.toList(), current, approval, projects))
    }
    private fun stopVoiceCommand() = voiceCommand.finish()
    fun saveCommandKey(key: String) = voicePreferences.saveCommandKey(key)

    val dictationScroll = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val dictationVelocity = MutableStateFlow(0f)
    private val vocabularies = mutableMapOf<SessionId, List<String>>()
    fun selectVoice(provider: DictationProvider) = voicePreferences.select(provider)
    fun saveVoiceKey(key: String) = voicePreferences.saveKey(key)
    fun openVoiceSettings() { voiceCommand.finish(); dictation.finish(); settingsOpen.value = true }
    fun closeVoiceSettings() { settingsOpen.value = false }
    fun beginDictation(target: DictationTarget) {
        if (settingsOpen.value || voiceCommand.state.value.visible) return
        dictation.start(target, vocabularies[target.sessionId].orEmpty())
    }
    private fun startDictation(id: SessionId?) { id?.let { beginDictation(DictationTarget.Message(it)) } }
    private fun stopDictation() = dictation.finish()

    private var touchDictation: Trigger? = null
    /** The window owns the release even when the microphone changes screens. */
    fun touchTrigger(trigger: Trigger, held: Boolean) {
        if (held) { touchDictation = trigger; handleInput(GamepadInput.TriggerDown(trigger)) }
    }
    fun releaseTouchDictation() {
        touchDictation?.let { trigger -> touchDictation = null; handleInput(GamepadInput.TriggerUp(trigger)) }
    }

    fun handleInput(input: GamepadInput) {
        // A release belongs to the physical stick even while an overlay owns the inputs.
        if (input is GamepadInput.Scroll && input.velocity == 0f) {
            diffVelocity.value = 0f
            newSessionVelocity.value = 0f
        }
        if (sessionSettings.value.visible) {
            if (input is GamepadInput.Pressed) when (input.button) {
                Button.UP -> settingsReview?.move(-1)
                Button.DOWN -> settingsReview?.move(1)
                Button.LEFT -> settingsReview?.change(-1)
                Button.RIGHT -> settingsReview?.change(1)
                Button.A -> settingsReview?.apply()
                Button.B -> settingsReview?.close()
                else -> Unit
            }
            return
        }
        if (settingsOpen.value) {
            if (input == GamepadInput.Pressed(Button.B)) closeVoiceSettings()
            return
        }
        if (voiceCommand.state.value.visible) {
            when (input) {
                is GamepadInput.TriggerUp -> if (input.trigger == Trigger.R2) voiceCommand.finish()
                is GamepadInput.TriggerDown -> if (input.trigger == Trigger.R2 && voiceCommand.state.value.phase in setOf(CommandPhase.REVIEW, CommandPhase.UNKNOWN, CommandPhase.DONE)) {
                    voiceCommand.discard(); startVoiceCommand()
                }
                is GamepadInput.Pressed -> when (input.button) {
                    Button.A -> voiceCommand.confirm()
                    Button.B -> { voiceCommand.discard(); commandVelocity.value = 0f }
                    Button.UP -> commandScroll.tryEmit(-1)
                    Button.DOWN -> commandScroll.tryEmit(1)
                    else -> Unit
                }
                is GamepadInput.Scroll -> commandVelocity.value = input.velocity
            }
            return
        }
        if (newSession.value.visible) { handleNewSession(input); return }
        if (dictation.state.value.visible) {
            when (input) {
                is GamepadInput.TriggerDown -> if (input.trigger == Trigger.L2) dictation.record()
                is GamepadInput.TriggerUp -> if (input.trigger == Trigger.L2) dictation.finish()
                is GamepadInput.Pressed -> when (input.button) {
                    Button.A -> dictation.send()
                    Button.B -> { dictation.discard(); dictationVelocity.value = 0f; controller.releaseTriggers(); sessionController.releaseTriggers() }
                    Button.Y -> dictation.append()
                    Button.X -> dictation.correct()
                    Button.LEFT -> dictation.moveWord(-1)
                    Button.RIGHT -> dictation.moveWord(1)
                    Button.UP -> if (dictation.state.value.phase == DictationPhase.CORRECT) dictation.moveWord(-1) else { dictationScroll.tryEmit(-1) }
                    Button.DOWN -> if (dictation.state.value.phase == DictationPhase.CORRECT) dictation.moveWord(1) else { dictationScroll.tryEmit(1) }
                    else -> Unit
                }
                is GamepadInput.Scroll -> dictationVelocity.value = input.velocity
            }
            return
        }
        if (input is GamepadInput.TriggerDown && input.trigger == Trigger.R2) { startVoiceCommand(); return }
        if (input is GamepadInput.TriggerUp && input.trigger == Trigger.R2) { stopVoiceCommand(); return }
        if (input is GamepadInput.TriggerDown && input.trigger == Trigger.L2) {
            when (val screen = _screen.value) {
                is Screen.Session -> startDictation(screen.id)
                is Screen.Diff -> if (diffReview.value.explanation == null) reviewDiff?.commentTarget()?.let(::beginDictation)
                Screen.Overview -> controller.handle(input)
                Screen.Approval -> approvalReview.value.takeIf { it.canAnswer(ApprovalDecision.REJECT) }?.request?.let {
                    beginDictation(DictationTarget.Rejection(it.sessionId, it.id))
                }
            }
            return
        }
        when (_screen.value) {
            Screen.Overview -> controller.handle(input)
            Screen.Approval -> approvalController.handle(input)
            is Screen.Session -> sessionController.handle(input)
            is Screen.Diff -> handleDiffInput(input)
        }
    }
    private var reviewDiff: ReviewDiff? = null
    val diffReview = MutableStateFlow(DiffReview())
    val diffVelocity = MutableStateFlow(0f)
    val diffSteps = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 16)
    fun selectDiffFile(index: Int) { reviewDiff?.selectFile(index) }
    private fun handleDiffInput(input: GamepadInput) {
        val review = reviewDiff ?: return
        when (input) {
            is GamepadInput.Scroll -> diffVelocity.value = input.velocity
            is GamepadInput.TriggerDown, is GamepadInput.TriggerUp -> Unit
            is GamepadInput.Pressed -> {
                if (review.state.value.explanation != null) {
                    when (input.button) {
                        Button.A -> review.confirmExplanation()
                        Button.B -> { review.dismissExplanation(); diffVelocity.value = 0f }
                        Button.UP -> diffSteps.tryEmit(-1)
                        Button.DOWN -> diffSteps.tryEmit(1)
                        Button.R1, Button.L1, Button.START -> sessionController.handle(input)
                        else -> Unit
                    }
                } else when (input.button) {
                    Button.UP -> review.moveLine(-1)
                    Button.DOWN -> review.moveLine(1)
                    Button.LEFT -> review.moveFile(-1)
                    Button.RIGHT -> review.moveFile(1)
                    Button.Y -> { diffVelocity.value = 0f; review.explain() }
                    Button.B -> _screen.value.sessionId?.let { openSession(it) }
                    Button.R1, Button.L1, Button.START -> sessionController.handle(input)
                    else -> Unit
                }
            }
        }
    }
    private var reviewApprovals: ReviewApprovals? = null
    val approvalReview = MutableStateFlow(ApprovalReview())
    val approvalController = ApprovalController(
        answer = { decision -> scope.launch { reviewApprovals?.answer(decision) } },
        next = { reviewApprovals?.next() },
        back = { navigate(Screen.Overview) },
    )

    fun openApproval(id: ApprovalId) {
        reviewApprovals?.open(id)
        navigate(Screen.Approval)
    }

    fun openSession(id: SessionId, summary: Boolean = false) {
        navigate(Screen.Session(id))
        if (summary) sessionController.openSummary()
    }

    fun selectApproval(id: ApprovalId) { reviewApprovals?.open(id) }

    private var reviewNewSession: ReviewNewSession? = null
    val newSession = MutableStateFlow(NewSessionForm())
    val newSessionVelocity = MutableStateFlow(0f)
    fun openNewSession(draft: NewSessionDraft? = null, text: String = "") {
        newSessionVelocity.value = 0f
        reviewNewSession?.open(draft, text)
    }
    fun selectNewSessionField(field: NewSessionField) { reviewNewSession?.select(field) }
    fun editNewInstruction(text: String) { reviewNewSession?.editInstruction(text) }
    fun editNewProjectName(text: String) { reviewNewSession?.editProjectName(text) }
    fun editNewBranch(text: String) { reviewNewSession?.editBranch(text) }

    private fun handleNewSession(input: GamepadInput) {
        if (input is GamepadInput.Scroll) { newSessionVelocity.value = input.velocity; return }
        val review = reviewNewSession ?: return
        when (input) {
            is GamepadInput.TriggerDown -> when (input.trigger) {
                Trigger.L2 -> review.record()
                Trigger.R2 -> if (review.state.value.editable) { newSessionVelocity.value = 0f; startVoiceCommand() }
            }
            is GamepadInput.TriggerUp -> if (input.trigger == Trigger.L2) review.finish()
            is GamepadInput.Pressed -> when (input.button) {
                Button.A -> { newSessionVelocity.value = 0f; review.launch() }
                Button.B -> { newSessionVelocity.value = 0f; review.back() }
                Button.UP -> review.move(-1)
                Button.DOWN -> review.move(1)
                Button.LEFT -> review.change(-1)
                Button.RIGHT -> review.change(1)
                else -> Unit
            }
            is GamepadInput.Scroll -> Unit
        }
    }

    val controller = OverviewController(overview, ::execute)
    val sessionController = SessionController(live, overview, current = { _screen.value.sessionId }, ::execute)

    /** La manette va à l'écran affiché. */
    val inputRouter = InputRouter(::handleInput)
    val displays = DisplayMonitor(displayManager)

    /** L'écran du haut existe : l'écran du bas se ferme quand il disparaît. */
    val topAlive = MutableStateFlow(false)

    /** L'écran du haut est visible. */
    val topStarted = MutableStateFlow(false)

    /** L'écran du bas est visible : l'écran du haut ne le relance pas. */
    val bottomStarted = MutableStateFlow(false)

    /** « Quitter » est allé au bout : les deux écrans se ferment, puis le processus. */
    private val _closing = MutableStateFlow(false)
    val closing: StateFlow<Boolean> = _closing.asStateFlow()

    /** La passerelle est la passerelle factice : rien à démarrer sur la console. */
    var replaying = false
        private set

    /** Termux présent : transport local de la Thor ; absent : pont externe du banc. */
    val hasTermux = TermuxBridgeLauncher.isInstalled(app)

    /**
     * Branche la passerelle, une fois pour le processus. [replay] choisit la passerelle factice, qui
     * rejoue les enregistrements du pont (en debug : `--ez fake_gateway true`).
     */
    fun start(replay: Boolean) {
        if (gateway != null) return
        replaying = replay
        val external = ExternalBridge()
        val launcher = if (hasTermux) TermuxBridgeLauncher(app) else external
        val androidAccess = if (hasTermux) WirelessDebuggingAccess(app) else external
        val chosen: AgentGateway = if (replay) {
            ReplayAgentGateway()
        } else {
            BridgeAgentGateway(
                token = ::readToken,
                historyCount = OverviewGrid.VISIBLE_CARDS,
                // Trace de connexion : jamais le jeton ni le contenu des messages.
                log = { Log.i(TAG, it) },
            )
        }
        val reports: StateFlow<StartUpReport?> = if (replay) {
            MutableStateFlow(null)
        } else {
            StartUp(chosen, androidAccess, launcher).also { startUp = it }.report
        }
        gateway = chosen
        settingsReview = ReviewSessionSettings(chosen, scope).also { review -> scope.launch { review.state.collect { sessionSettings.value = it } } }
        reviewNewSession = ReviewNewSession(chosen, capture, scope) { openSession(it) }.also { review ->
            scope.launch { review.state.collect { newSession.value = it } }
        }
        reviewDiff = ReviewDiff(chosen, scope).also { review -> scope.launch { review.state.collect { diffReview.value = it } } }
        val answer = AnswerApproval(chosen)
        executeVoiceCommand = ExecuteVoiceCommand(chosen, answer) { openSession(it) }
        scope.launch { chosen.connection.collectLatest { connection ->
            if (connection == BridgeConnection.Connected) projects = chosen.projects()
        } }

        val review = ReviewApprovals(chosen, scope, answer)
        deliverDictation = DeliverDictation(SendMessage(chosen, dictated), answer)
        reviewApprovals = review
        scope.launch { review.state.collect { approvalReview.value = it } }
        NotifyApprovals(chosen, topStarted, AndroidApprovalNotifications(app), scope)
        NotifySessions(chosen, topStarted, AndroidSessionNotifications(app), scope)
        quit = Quit(chosen, launcher)
        sendMessage = SendMessage(chosen, dictated)
        interruptSession = InterruptSession(chosen)
        val openSession = OpenSession(chosen, dictated)
        Log.i(TAG, "passerelle : ${if (replay) "factice (enregistrements du pont)" else "pont"}")

        scope.launch { chosen.maintainConnection() }
        scope.launch {
            ObserveOverview(chosen, reports)().collect {
                _overview.value = it
                // Dans la même passe : jamais une image où le focus pointe une autre session.
                controller.syncFocus()
            }
        }
        // La session affichée : son fil est chargé à l'ouverture, puis suivi en direct.
        scope.launch {
            _screen.collectLatest { screen ->
                _live.value = null
                screen.sessionId?.let { id -> openSession(id).collect { _live.value = it } }
            }
        }
        // Preload vocabulary while browsing: pressing L2 never waits for the network.
        scope.launch {
            chosen.sessions.map { sessions -> sessions.map { Triple(it.id, it.branch, it.modifiedFiles) } }.distinctUntilChanged().collectLatest { sessions ->
                for ((id, branch, files) in sessions) {
                    val terms = chosen.vocabulary(id)
                    vocabularies[id] = (listOfNotNull(branch) + files.map { it.substringAfterLast('/') } + terms).distinct().take(300)
                }
            }
        }
        // Le pont s'est tu en cours de route : Régie le relance, à chaque échéance, tant qu'un de
        // ses écrans est visible (Android refuse de démarrer un service depuis l'arrière-plan).
        startUp?.let { useCase ->
            keepBridgeUpJob = scope.launch {
                KeepBridgeUp(chosen, useCase, canStart = { !_closing.value && (topStarted.value || bottomStarted.value) }).run()
            }
        }
    }

    /** Au lancement et à chaque retour au premier plan : débogage sans fil, puis pont (StartUp). */
    fun runStartUp() {
        val useCase = startUp ?: return
        if (startUpJob?.isActive == true || _closing.value) return
        startUpJob = scope.launch {
            val report = useCase()
            val bridge = when {
                report.launch != null -> "démarrage ${report.launch}"
                report.bridgeUp -> "déjà là"
                else -> "démarrage déjà demandé il y a moins de 30 s"
            }
            Log.i(TAG, "lancement : débogage sans fil ${report.wirelessDebugging}, pont $bridge")
        }
    }

    /** Le jeton du pont, copié par `thor/bridge/install.sh` dans le stockage privé de l'app. Jamais journalisé. */
    private fun readToken(): String? =
        File(app.filesDir, TOKEN_FILE).takeIf { it.canRead() }?.readText()?.trim()?.ifEmpty { null }

    private fun execute(action: OverviewAction) {
        Log.i(TAG, "action : $action")
        dispatch(action)
    }

    private fun execute(action: SessionAction) {
        // Jamais le texte d'un message dans le journal.
        Log.i(TAG, "action : ${if (action is SessionAction.Send) "Send(${action.id})" else action}")
        when (action) {
            is SessionAction.OpenSettings -> settingsReview?.open(action.id)
            is SessionAction.OpenDiff -> navigate(Screen.Diff(action.id))
            is SessionAction.Open -> navigate(Screen.Session(action.id))
            SessionAction.Back -> navigate(Screen.Overview)
            is SessionAction.OpenApproval -> openApproval(action.id)
            is SessionAction.Send -> scope.launch {
                val delivery = sendMessage?.invoke(action.id, action.text) ?: Delivery.NOT_SENT
                Log.i(TAG, "message : $delivery")
                sessionController.onDelivered(action.id, action.text, delivery)
            }
            is SessionAction.Interrupt -> scope.launch {
                val shown = _live.value?.takeIf { it.session.id == action.id } ?: return@launch
                val heard = interruptSession?.invoke(shown) ?: return@launch
                Log.i(TAG, "interruption : ${if (heard) "envoyée" else "refusée"}")
                sessionController.onInterrupted(action.id, heard)
            }
            is SessionAction.ShowSummary -> sessionController.openSummary()
            is SessionAction.StartDictation -> startDictation(action.id)
            SessionAction.StopDictation -> stopDictation()
            SessionAction.StartVoiceCommand -> startVoiceCommand()
            SessionAction.StopVoiceCommand -> stopVoiceCommand()
        }
    }

    /** Change d'écran : les gâchettes tenues sont relâchées, rien de la session précédente ne reste. */
    private fun navigate(to: Screen) {
        if (_screen.value == to) return
        settingsReview?.close()
        controller.releaseTriggers()
        sessionController.releaseTriggers()
        sessionController.reset()
        approvalController.scrollVelocity.value = 0f
        diffVelocity.value = 0f
        reviewDiff?.close()
        if (to is Screen.Diff) reviewDiff?.open(to.id)
        _screen.value = to
    }

    private fun dispatch(action: OverviewAction) = when (action) {
        is OverviewAction.StartDictation -> startDictation(action.target)
        OverviewAction.StopDictation -> stopDictation()
        OverviewAction.StartVoiceCommand -> startVoiceCommand()
        OverviewAction.StopVoiceCommand -> stopVoiceCommand()
        is OverviewAction.OpenSession -> navigate(Screen.Session(action.id))
        is OverviewAction.OpenApproval -> openApproval(action.id)
        OverviewAction.NewSession -> openNewSession()
        is OverviewAction.ShowSummary -> action.target?.let { openSession(it, summary = true) }
        OverviewAction.PauseAll -> { scope.launch { executeVoiceCommand?.invoke(VoiceCommand.Pause(overview.value.sessions.map { it.id })) }; Unit }
        OverviewAction.Quit -> quitNow()
    }

    private fun quitNow() {
        voiceCommand.discard()
        dictation.discard()
        scope.launch {
            startUpJob?.cancel()
            keepBridgeUpJob?.cancel()
            val outcome = quit?.invoke()
            Log.i(TAG, "quitter : $outcome")
            _closing.value = true
            // Le temps que les deux activités se ferment, puis le processus.
            delay(CLOSE_DELAY_MS)
            Process.killProcess(Process.myPid())
        }
    }

    private companion object {
        const val TAG = "Regie"
        const val TOKEN_FILE = "bridge-token"
        const val CLOSE_DELAY_MS = 400L
    }
}
