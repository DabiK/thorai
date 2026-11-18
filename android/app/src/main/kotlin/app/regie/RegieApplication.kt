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
import app.regie.console.AndroidSetup
import app.regie.console.ConsoleBridge
import app.regie.display.DisplayMonitor
import app.regie.domain.model.Delivery
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Overview
import app.regie.domain.port.AgentGateway
import app.regie.domain.port.DictationCapture
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
import app.regie.domain.usecase.ConsoleLaunch
import app.regie.domain.usecase.ConsoleSession
import app.regie.domain.usecase.FloatingMessages
import app.regie.input.ConsoleKeys
import app.regie.overlay.OverlayContent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import app.regie.gateway.bridge.BridgeAgentGateway
import app.regie.gateway.bridge.ReplayAgentGateway
import app.regie.input.InputRouter
import app.regie.overview.OverviewAction
import app.regie.overview.OverviewController
import app.regie.overview.OverviewList
import app.regie.session.Screen
import app.regie.domain.usecase.ReviewAgentAuthentication
import app.regie.domain.usecase.ReviewSetup
import app.regie.display.ScreenMode
import app.regie.voice.api.ProviderStatus
import app.regie.voice.api.VoiceError
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
import kotlinx.coroutines.flow.update
import app.regie.courier.CourierNotifications
import app.regie.game.ui.courier.CourierTiming
import app.regie.game.engine.courier.Courier
import app.regie.game.engine.menu.GameMenu
import app.regie.game.engine.menu.MenuEntry
import app.regie.game.engine.menu.MenuKey
import app.regie.game.engine.menu.MenuOutcome
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

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
class AppContainer(
    private val app: Application,
    displayManager: DisplayManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    captureOverride: DictationCapture? = null,
    private val gatewayFactory: (() -> AgentGateway)? = null,
) {

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
    private var authenticationReview: ReviewAgentAuthentication? = null
    val agentAuthentication = MutableStateFlow(AgentAuthenticationReview())
    fun openAgentAuthentication() {
        if (dictation.state.value.visible || voiceCommand.state.value.visible || newSession.value.visible || controller.state.value.quit != null) return
        controller.releaseTriggers(); sessionController.releaseTriggers()
        authenticationReview?.open()
    }
    fun selectAuthenticationAgent(index: Int) { authenticationReview?.select(index) }
    fun submitAuthenticationKey(agent: Agent, key: String) { authenticationReview?.submitKey(key, agent) }
    var authenticationKeyAction: (() -> Unit)? = null
    fun refreshAuthentication() { authenticationReview?.refresh() }
    private var settingsReview: ReviewSessionSettings? = null

    // --- Vue jeu : écran titre, menu de pause et courrier (ticket 49) ---

    /** Le menu du jeu ouvert sur le hameau : le titre au lancement, la pause avec Start. */
    val gameMenu = MutableStateFlow(GameMenu.CLOSED)
    private var titleShown = false

    /** Le courrier : les derniers événements qui demandent l'attention, d'après le domaine. */
    val courier = MutableStateFlow(Courier())

    /** Au premier lancement du processus seulement : l'écran titre et son menu principal. */
    fun showTitle() {
        if (titleShown) return
        titleShown = true
        if (_screen.value == Screen.Overview) gameMenu.value = GameMenu.title()
    }

    /** Un toucher sur une entrée : elle devient l'entrée choisie, puis A. */
    fun chooseMenuEntry(index: Int) {
        gameMenu.update { it.copy(selected = index.coerceIn(0, (it.entries.size - 1).coerceAtLeast(0))) }
        pressMenu(MenuKey.A)
    }

    /** Un toucher sur une lettre : elle devient la lettre choisie, puis A. */
    fun openCourierLetter(index: Int) {
        gameMenu.update { it.copy(letter = index) }
        pressMenu(MenuKey.A)
    }

    fun pressMenu(key: MenuKey) {
        val step = gameMenu.value.press(key, courier.value.letters.size)
        gameMenu.value = step.menu
        when (val outcome = step.outcome) {
            is MenuOutcome.Choose -> when (outcome.entry) {
                MenuEntry.CONTINUE, MenuEntry.COURIER -> Unit
                MenuEntry.NEW_SESSION -> openNewSession()
                MenuEntry.AGENTS -> openAgentAuthentication()
                MenuEntry.SETTINGS -> openVoiceSettings()
                MenuEntry.PAUSE_ALL -> dispatch(OverviewAction.PauseAll)
                MenuEntry.QUIT -> controller.requestQuit()
            }
            is MenuOutcome.OpenLetter -> openLetter(outcome.index)
            MenuOutcome.MarkAllRead -> courier.update { it.markAllRead() }
            null -> Unit
        }
    }

    /**
     * La validation que l'oiseau apporte en ce moment ([CourierTiming.SHOWN] après son arrivée), si elle
     * attend encore : R1 l'ouvre. Quand le hameau sera branché (ticket 45), le coursier y marchera.
     */
    private fun openArrivingLetter(): Boolean {
        val letter = courier.value.latest ?: return false
        val pending = letter.approval?.takeIf { id -> gateway?.approvals?.value.orEmpty().any { it.id == id } } ?: return false
        if (kotlin.time.Clock.System.now() - letter.receivedAt >= CourierTiming.SHOWN) return false
        courier.update { it.read(letter.key) }
        openApproval(pending)
        return true
    }

    /** Une lettre ouverte : sa validation si elle attend encore, sinon sa session. */
    private fun openLetter(index: Int) {
        val letter = courier.value.letters.getOrNull(index) ?: return
        courier.update { it.read(letter.key) }
        val approval = letter.approval?.takeIf { id -> gateway?.approvals?.value.orEmpty().any { it.id == id } }
        if (approval != null) openApproval(approval) else openSession(letter.sessionId)
    }

    private fun menuKey(button: Button): MenuKey? = when (button) {
        Button.UP -> MenuKey.UP
        Button.DOWN -> MenuKey.DOWN
        Button.LEFT -> MenuKey.LEFT
        Button.RIGHT -> MenuKey.RIGHT
        Button.A -> MenuKey.A
        Button.B -> MenuKey.B
        Button.X -> MenuKey.X
        Button.Y -> MenuKey.Y
        Button.START -> MenuKey.START
        Button.L1, Button.R1 -> null
    }

    /**
     * Le menu du jeu a la manette sur le hameau. « Quitter » en confirmation passe d'abord ; L2 et
     * R2 restent valables (dicter, ordre vocal) ; le reste va au menu. Vrai si l'entrée est prise.
     */
    private fun handleGameMenu(input: GamepadInput): Boolean {
        if (!gameMenu.value.open || _screen.value != Screen.Overview) return false
        if (controller.state.value.quit != null) { controller.handle(input); return true }
        return when (input) {
            is GamepadInput.Pressed -> { menuKey(input.button)?.let(::pressMenu); true }
            is GamepadInput.Scroll -> true
            is GamepadInput.TriggerDown, is GamepadInput.TriggerUp -> false
        }
    }
    val sessionSettings = MutableStateFlow(SessionSettingsReview())
    fun selectSessionSetting(index: Int) { settingsReview?.select(index) }

    /**
     * Droite, ou un toucher sur une ligne de la table du bas ([row]) : la table passe en modification.
     * Jamais pendant une dictée ou un ordre : la table, cachée, capterait leurs touches.
     */
    fun openSessionSettings(row: String? = null) {
        if (dictation.state.value.visible || voiceCommand.state.value.visible) return
        (screen.value as? Screen.Session)?.let { settingsReview?.open(it.id, row) }
    }
    val settingsOpen = MutableStateFlow(false)
    private val localVoice = LocalVoiceProvider(File(app.filesDir, "voice-models"), LocalConfig(hotwords = { true }))
    private val cloudVoice = ElevenLabsVoiceProvider(ElevenLabsConfig(apiKey = voicePreferences::apiKey))
    /**
     * Debug-only WAV replay uses the real provider and the same review/delivery path. Single use:
     * the next capture takes it, and every later one listens to the microphone again.
     */
    var debugVoiceWav: File? = null
    private val capture: DictationCapture = captureOverride ?: VoiceCapture(
        { if (voiceSettings.value.provider == DictationProvider.LOCAL) localVoice.speechToText else cloudVoice.speechToText },
        { takeDebugVoiceWav()?.let { WavAudioSource.fromFile(it, realtime = true) } ?: ForegroundMicrophone(app) }, scope,
    )
    private var deliverDictation: DeliverDictation? = null
    internal fun takeDebugVoiceWav(): File? = debugVoiceWav.also { debugVoiceWav = null }

    // A message the agent received closes the dictation by itself; the session shows its receipt.
    val dictation = ReviewDictation(capture, { target, text ->
        deliverDictation?.invoke(target, text) ?: DictationDelivery("Pont indisponible", target)
    }, scope, shown = { id -> _screen.value == Screen.Session(id) }, onNothingHeard = { target ->
        // Nothing heard: nothing sent, the bottom goes back to the settings and says so in one line.
        releaseDictationInputs()
        sessionController.onNothingHeard(target.sessionId)
    }) { target, delivery ->
        // Same cleanup as B: the overview noted L2 held, and its release went to the dictation.
        releaseDictationInputs()
        closedByDelivery = kotlin.time.TimeSource.Monotonic.markNow()
        sessionController.onDictated(target.sessionId, delivery)
    }

    /** The dictation closed by itself: an A pressed twice must not reach a validation behind it. */
    private var closedByDelivery: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null

    private fun releaseDictationInputs() {
        dictationVelocity.value = 0f
        controller.releaseTriggers()
        sessionController.releaseTriggers()
    }
    // --- Parcours d'accueil (ticket 28) ---

    private val androidSetup = AndroidSetup(app, android.content.ComponentName(app, ConsoleService::class.java))
    val setup = ReviewSetup(androidSetup, ::performSetup)
    private var setupJob: Job? = null
    private var setupAgents: List<AgentAuthentication>? = null
    private val refusedPermissions = mutableSetOf<SetupPermission>()
    private val deniedOnce = mutableSetOf<SetupPermission>()
    private var voiceDownload: VoiceModel? = null
    private var voiceDownloadJob: Job? = null

    /** Le stick droit fait défiler l'explication de l'étape, sur l'écran du haut qui n'est pas tactile. */
    val setupVelocity = MutableStateFlow(0f)

    /** Une permission que l'activité visible doit demander : seule une activité peut afficher la demande. */
    val permissionRequests = kotlinx.coroutines.flow.MutableSharedFlow<SetupPermission>(extraBufferCapacity = 4)

    /** Le parcours n'a jamais été mené au bout ni refermé : il remplace les demandes du premier lancement. */
    val setupPending: Boolean get() = setup.pending

    /** Au lancement : la bienvenue, si le parcours n'a jamais été mené au bout. */
    fun offerSetup() {
        if (!setup.offerOnLaunch()) return
        authenticationReview?.dismissLaunchOffer()
        followSetup()
    }

    /** « Configurer », depuis l'accueil : la première étape qui n'est pas faite. */
    fun openSetup() {
        if (dictation.state.value.visible || voiceCommand.state.value.visible || newSession.value.visible || controller.state.value.quit != null) return
        controller.releaseTriggers(); sessionController.releaseTriggers()
        setup.update(readSetup())
        setup.open()
        followSetup()
    }

    /** En debug seulement (extra `setup_step`) : une étape précise, pour la capturer. */
    fun openSetupAt(step: SetupStep) {
        setup.update(readSetup())
        setup.open(at = step)
        followSetup()
    }

    /** Relit l'état de la console chaque seconde tant que le parcours est ouvert : les étapes passent seules au vert. */
    private fun followSetup() {
        if (setupJob?.isActive == true) return
        setupJob = scope.launch {
            var tick = 0
            var androidAccess = androidSetup.androidAccess()
            while (setup.state.value.visible) {
                val chosen = gateway
                if (tick % 3 == 0 && chosen != null && chosen.connection.value == BridgeConnection.Connected) {
                    setupAgents = try { chosen.authentication() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { setupAgents }
                }
                val checks = readSetup()
                // Association faite pendant que le pont tourne : regie-bridge-start relie les agents
                // à Android à chaque appel, même pont déjà démarré (thor/bridge/start.sh).
                if (checks.androidAccess && !androidAccess) Log.i(TAG, "accès Android : association vue, liaison ${consoleBridge.start()}")
                androidAccess = checks.androidAccess
                setup.update(checks)
                tick++
                delay(SETUP_POLL)
            }
        }
    }

    private fun readSetup() = SetupChecks(
        termux = androidSetup.termuxInstalled(),
        runCommand = androidSetup.granted(SetupPermission.RUN_COMMAND),
        bridge = overview.value.bridge,
        microphone = androidSetup.granted(SetupPermission.MICROPHONE),
        voiceModel = voiceModel(),
        notifications = androidSetup.granted(SetupPermission.NOTIFICATIONS),
        consoleMode = consoleService.value || androidSetup.consoleModeEnabled(),
        androidAccess = androidSetup.androidAccess(),
        wirelessDebugging = androidSetup.wirelessDebugging(),
        agents = setupAgents ?: gateway?.agents?.value?.takeIf { it.isNotEmpty() }?.map { AgentAuthentication(it.agent, it.connected) },
        refused = refusedPermissions.filterNot(androidSetup::granted).toSet(),
    )

    private fun voiceModel(): VoiceModel = when {
        voiceSettings.value.provider == DictationProvider.ELEVENLABS -> VoiceModel.NotNeeded
        localVoice.speechToTextInstalled -> VoiceModel.Installed
        else -> voiceDownload ?: VoiceModel.Missing(localVoice.speechToTextDownloadBytes, localVoice.speechToTextFreeBytes)
    }

    /** Transcription seule, chargée une fois : la première dictée démarre aussitôt. */
    private fun downloadVoice() {
        if (voiceDownloadJob?.isActive == true) return
        voiceDownload = VoiceModel.Downloading(0f)
        voiceDownloadJob = scope.launch {
            localVoice.prepareSpeechToText().collect { status ->
                voiceDownload = when (status) {
                    is ProviderStatus.Downloading -> VoiceModel.Downloading(status.progress)
                    ProviderStatus.Ready -> null
                    is ProviderStatus.Unavailable -> VoiceModel.Failed(when (val error = status.error) {
                        VoiceError.Offline -> VoiceModelFailure.OFFLINE
                        is VoiceError.Provider -> if (error.code == "storage_full" || error.code == "storage") VoiceModelFailure.STORAGE else VoiceModelFailure.OTHER
                        else -> VoiceModelFailure.OTHER
                    })
                }
                Log.i(TAG, "modèle de voix : ${status.javaClass.simpleName}")
                setup.update(readSetup())
            }
        }
    }

    /** L'écran tactile : celui du bas s'il est là, sinon le seul écran. */
    private fun touchDisplay(): Int = (displays.mode.value as? ScreenMode.Dual)?.bottomDisplayId ?: android.view.Display.DEFAULT_DISPLAY

    /** Les permissions se demandent depuis l'écran tactile, le bas s'il est affiché. */
    fun asksPermissions(bottom: Boolean): Boolean = bottom == (bottomStarted.value && displays.mode.value is ScreenMode.Dual)

    /**
     * Réponse à une demande. Refusée sans retour possible, seule la page des infos de l'appli l'accorde.
     * Un premier « non » laisse Android redemander ([askAgain]) ; une fenêtre fermée sans réponse ne
     * ressemble à un refus définitif qu'après un premier refus : on ne la confond pas avec lui.
     */
    fun onPermissionAnswer(permission: SetupPermission, granted: Boolean, askAgain: Boolean) {
        when {
            granted -> deniedOnce -= permission
            askAgain -> deniedOnce += permission
            permission in deniedOnce -> refusedPermissions += permission
        }
        Log.i(TAG, "permission $permission : ${if (granted) "accordée" else "refusée"}")
        if (granted && permission == SetupPermission.RUN_COMMAND) runStartUp()
        setup.update(readSetup())
    }

    private fun performSetup(action: SetupAction) {
        Log.i(TAG, "accueil : $action")
        when (action) {
            SetupAction.OPEN_TERMUX_RELEASES -> androidSetup.openTermuxReleases(touchDisplay())
            // Copiées, puis Termux s'ouvre sur l'écran tactile, où l'on colle et tape.
            SetupAction.COPY_INSTALL_COMMANDS -> { androidSetup.copy("Installation", SetupCommands.INSTALL); androidSetup.openTermux(touchDisplay()) }
            SetupAction.COPY_PAIRING_COMMANDS -> { androidSetup.copy("Association", SetupCommands.pairing(app.packageName)); androidSetup.openTermux(touchDisplay()) }
            SetupAction.GRANT_RUN_COMMAND -> permissionRequests.tryEmit(SetupPermission.RUN_COMMAND)
            SetupAction.GRANT_MICROPHONE -> permissionRequests.tryEmit(SetupPermission.MICROPHONE)
            SetupAction.GRANT_NOTIFICATIONS -> permissionRequests.tryEmit(SetupPermission.NOTIFICATIONS)
            SetupAction.RETRY_BRIDGE -> runStartUp()
            SetupAction.DOWNLOAD_VOICE -> downloadVoice()
            SetupAction.OPEN_ACCESSIBILITY -> androidSetup.openAccessibility(touchDisplay())
            SetupAction.OPEN_APP_INFO -> androidSetup.openAppInfo(touchDisplay())
            // En haut, à la croix : la fenêtre d'association y reste ouverte pendant qu'on tape dans Termux, en bas.
            SetupAction.OPEN_DEVELOPER_OPTIONS -> androidSetup.openDeveloperOptions(android.view.Display.DEFAULT_DISPLAY)
            SetupAction.OPEN_AGENTS -> openAgentAuthentication()
            SetupAction.CREATE_SESSION -> openNewSession()
            SetupAction.NEXT, SetupAction.BACK, SetupAction.SKIP, SetupAction.CLOSE -> Unit
        }
        setup.update(readSetup())
    }

    private var executeVoiceCommand: ExecuteVoiceCommand? = null
    private var projects: List<String> = emptyList()
    val voiceCommand = ReviewVoiceCommand(capture, InterpretVoiceCommand(MistralCommandModel(voicePreferences::commandKey)),
        { command -> executeVoiceCommand?.invoke(command) ?: "Pont indisponible" }, scope, onLaunch = { draft, text -> openNewSession(draft, text) }, loadProjects = { gateway?.projects().orEmpty() }, loadAgents = { gateway?.agents?.value.orEmpty().filter { it.available }.map { it.agent } })
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
            is Screen.Session -> inlineApproval.value.request?.takeIf { it.sessionId == shown.id }
            is Screen.Diff -> null
        }
        voiceCommand.start(VoiceCommandContext(overview.value.sessions.toList(), current, approval, projects, gateway?.agents?.value.orEmpty().filter { it.available }.map { it.agent }))
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
            sessionController.handle(input)
            controller.handle(input)
            diffVelocity.value = 0f
            newSessionVelocity.value = 0f
            setupVelocity.value = 0f
        }
        if (agentAuthentication.value.visible) {
            if (input is GamepadInput.Pressed) when (input.button) {
                Button.A -> if (agentAuthentication.value.current?.login?.method == "api_key" && agentAuthentication.value.current?.connected != true) authenticationKeyAction?.invoke() else authenticationReview?.confirm()
                Button.B -> authenticationReview?.close()
                Button.LEFT, Button.UP -> authenticationReview?.move(-1)
                Button.RIGHT, Button.DOWN -> authenticationReview?.move(1)
                Button.Y -> authenticationReview?.refresh()
                else -> Unit
            }
            return
        }
        if (setup.state.value.visible) {
            val keys = setup.state.value.keys
            if (input is GamepadInput.Scroll) setupVelocity.value = input.velocity
            if (input is GamepadInput.Pressed) when (input.button) {
                Button.A -> setup.press(keys.a)
                Button.B -> setup.press(keys.b)
                Button.X -> setup.press(keys.x)
                Button.Y -> setup.press(keys.y)
                else -> Unit
            }
            return
        }
        if (sessionSettings.value.visible) {
            if (input is GamepadInput.Pressed) when (input.button) {
                Button.UP -> settingsReview?.move(-1)
                Button.DOWN -> settingsReview?.move(1)
                Button.LEFT -> settingsReview?.change(-1)
                Button.RIGHT -> settingsReview?.change(1)
                Button.A -> settingsReview?.apply()
                Button.B -> settingsReview?.close()
                Button.START -> sessionController.handle(input)
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
                    Button.B -> { dictation.discard(); releaseDictationInputs() }
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
        if (input == GamepadInput.Pressed(Button.A) && closedByDelivery?.let { it.elapsedNow() < SECOND_A_IGNORED } == true) return
        if (handleGameMenu(input)) return
        // « R1 Voir » de l'oiseau messager : la validation de cette lettre-là, pas la première en attente.
        if (_screen.value == Screen.Overview && controller.state.value.quit == null && input == GamepadInput.Pressed(Button.R1) && openArrivingLetter()) return
        // Start sur le hameau : le menu de pause, qui garde « Tout mettre en pause » (ticket 49).
        if (_screen.value == Screen.Overview && controller.state.value.quit == null && input == GamepadInput.Pressed(Button.START)) { gameMenu.value = GameMenu.pause(); return }
        if (_screen.value == Screen.Overview && controller.state.value.quit == null && input == GamepadInput.Pressed(Button.B)) { openAgentAuthentication(); return }
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
            is Screen.Session -> {
                val review = inlineApproval.value
                val request = review.request?.takeIf { it.sessionId == _screen.value.sessionId }
                if (request != null && input is GamepadInput.Pressed && input.button in setOf(Button.A, Button.X, Button.B, Button.R1, Button.LEFT)) {
                    when (input.button) {
                        Button.A -> answerInline(request.id, ApprovalDecision.ONCE)
                        Button.X -> answerInline(request.id, ApprovalDecision.SESSION)
                        Button.B -> if (review.pending || review.sending) answerInline(request.id, ApprovalDecision.REJECT) else sessionController.handle(input)
                        Button.R1 -> nextInlineApproval()
                        Button.LEFT -> if (!review.pending && !review.sending) dismissInlineApproval() else sessionController.handle(input)
                        else -> Unit
                    }
                } else sessionController.handle(input)
            }
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
    private var inlineApprovals: ReviewApprovals? = null
    val inlineApproval = MutableStateFlow(ApprovalReview())
    fun answerInline(id: ApprovalId, decision: ApprovalDecision) {
        val review = inlineApprovals ?: return
        if (review.state.value.request?.id != id || review.state.value.request?.sessionId != _screen.value.sessionId) return
        scope.launch { review.answer(decision, expectedId = id) }
    }
    fun dismissInlineApproval() { inlineApprovals?.dismissResolved() }
    fun nextInlineApproval() { inlineApprovals?.next() }

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

    fun openOverview() { navigate(Screen.Overview) }

    fun openSession(id: SessionId) {
        navigate(Screen.Session(id))
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

    // --- Mode console (ticket 25, ADR 19) ---

    /** Les activités de Régie qui ont la manette (l'activité au premier plan du système), par instance. */
    private val gamepadOwners = mutableSetOf<Any>()

    fun gamepadFocus(owner: Any, focused: Boolean) {
        val changed = if (focused) gamepadOwners.add(owner) else gamepadOwners.remove(owner)
        if (changed) Log.i(TAG, "manette : ${owner.javaClass.simpleName} ${if (focused) "a la main" else "rend la main"}")
    }

    /** Le service d'accessibilité de Régie tourne : L2 marche depuis toute appli. */
    val consoleService = MutableStateFlow(false)

    private var floating: FloatingMessages? = null
    private var answerApproval: AnswerApproval? = null
    val floatingCard = MutableStateFlow<app.regie.domain.model.FloatingCard?>(null)

    /**
     * La carte qui prend A et B : une carte toute neuve les laisse un moment à l'appli en cours, pour
     * qu'un A destiné à Chrome n'autorise pas une validation qu'on n'a pas lue.
     */
    private val cardKeysFor = MutableStateFlow<String?>(null)

    /** La carte à laquelle répond la dictée en cours. */
    private var answeringKey: String? = null
    private val answeringCard = MutableStateFlow(false)
    private var consoleSessionJob: Job? = null

    /**
     * La manette vue par le service d'accessibilité, quand une autre appli a le focus. Rien n'est pris
     * tant que Régie n'est pas branchée, qu'elle se ferme ou qu'une de ses activités a la main.
     */
    val consoleKeys = ConsoleKeys(InputRouter(::handleConsoleInput),
        available = { gateway != null && !_closing.value && gamepadOwners.isEmpty() }, captured = ::consoleCaptured)

    /**
     * Ce que Régie pose par-dessus l'écran du haut quand une autre appli l'occupe, et que son écran
     * du bas n'est pas visible non plus (ADR 19).
     */
    val consoleOverlay: StateFlow<OverlayContent?> = combine(
        combine(topStarted, bottomStarted, ::Pair),
        combine(dictation.state, voiceCommand.state, ::Pair),
        combine(floatingCard, answeringCard, cardKeysFor, ::Triple),
        live, overview,
    ) { screens, voice, card, shown, current ->
        OverlayContent.of(screens.first, screens.second, voice.first, voice.second, card.first, card.second, shown, current.sessions,
            cardKeys = card.first != null && card.third == card.first?.key)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Les boutons qu'un panneau de Régie attend : le service les prend aux autres applis. */
    private fun consoleCaptured(): Set<Button> {
        val card = floatingCard.value?.takeIf { it.key == cardKeysFor.value }
        return when {
            // Parcours d'accueil ouvert : rien n'est pris à l'appli où l'on fait le geste (Termux, réglages).
            setup.state.value.visible -> emptySet()
            dictation.state.value.visible -> DICTATION_KEYS
            voiceCommand.state.value.visible -> setOf(Button.A, Button.B, Button.UP, Button.DOWN)
            // Opened by R2 « lance … » over another app: A launches, B cancels, the cross edits.
            newSession.value.visible -> setOf(Button.A, Button.B, Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT)
            card?.approval?.optionFor(ApprovalDecision.ONCE) != null -> setOf(Button.A, Button.B)
            card != null -> setOf(Button.B)
            else -> emptySet()
        }
    }

    /**
     * Une entrée prise à une autre appli. La carte volante d'abord : B la ferme, A autorise une fois sa
     * validation, L2 y répond (un refus avec consigne pour une validation). Sans carte, L2 dicte un
     * message à la session au premier plan de Régie, même si Régie montre un diff ou une validation :
     * on ne la regarde pas. Un panneau passif laissé ouvert dans Régie (réglages, connexion des
     * agents) se referme d'abord. Le reste suit le chemin habituel.
     */
    private fun handleConsoleInput(input: GamepadInput) {
        // Le parcours d'accueil ouvert (Termux ou les réglages en bas, par exemple) : L2 ne dicte rien.
        val busy = dictation.state.value.visible || voiceCommand.state.value.visible || newSession.value.visible ||
            controller.state.value.quit != null || setup.state.value.visible
        val card = floatingCard.value?.takeIf { !busy }
        val l2 = input is GamepadInput.TriggerDown && input.trigger == Trigger.L2
        if (card != null) when {
            input == GamepadInput.Pressed(Button.B) -> { floating?.dismiss(); return }
            input == GamepadInput.Pressed(Button.A) -> {
                // Same guard as inside Régie: a second A just after a dictation never authorizes.
                if (closedByDelivery?.let { it.elapsedNow() < SECOND_A_IGNORED } != true) card.approval?.let(::approveFromCard)
                return
            }
            l2 -> { closePassivePanels(); replyFromCard(card); return }
        }
        if (!busy && l2 && !topStarted.value) {
            closePassivePanels()
            consoleTarget()?.let { beginDictation(DictationTarget.Message(it)) }
            return
        }
        handleInput(input)
    }

    /** Les panneaux sans décision en cours : ils cèdent la place à la dictée lancée hors de Régie. */
    private fun closePassivePanels() {
        settingsOpen.value = false
        settingsReview?.close()
        authenticationReview?.close()
    }

    /** La session au premier plan de Régie : celle affichée, sinon celle qui a le focus dans la liste. */
    private fun consoleTarget(): SessionId? = when (val shown = _screen.value) {
        is Screen.Session -> shown.id
        is Screen.Diff -> shown.id
        Screen.Approval -> approvalReview.value.request?.sessionId
        Screen.Overview -> controller.state.value.focusedId
    }

    private fun replyFromCard(card: app.regie.domain.model.FloatingCard) {
        val target = card.approval?.takeIf { it.optionFor(ApprovalDecision.REJECT) != null }
            ?.let { DictationTarget.Rejection(it.sessionId, it.id) } ?: DictationTarget.Message(card.sessionId)
        val pinned = floating?.pin()
        beginDictation(target)
        if (dictation.state.value.visible) {
            answeringKey = pinned
            answeringCard.value = true
        } else floating?.release(pinned)
    }

    private fun approveFromCard(request: app.regie.domain.model.Approval) {
        val answer = answerApproval ?: return
        if (request.optionFor(ApprovalDecision.ONCE) == null) return
        scope.launch {
            val reply = answer(request.id, ApprovalDecision.ONCE).reply
            Log.i(TAG, "validation depuis la carte : $reply")
            if (reply == ApprovalReply.ACCEPTED) floating?.dismiss()
        }
    }

    /** Y sur la vue d'ensemble : la session console la plus récente, sinon sa fiche pré-remplie. */
    fun openConsoleSession() {
        val chosen = gateway ?: return
        if (consoleSessionJob?.isActive == true) return
        consoleSessionJob = scope.launch {
            // An empty catalogue means « not reachable », not « no folder »: keep the last known list.
            val catalog = try { chosen.projectCatalog().map { it.name } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
            val folders = catalog.ifEmpty { projects }
            when (val choice = ConsoleSession.choose(chosen.sessions.value, folders, chosen.agents.value.filter { it.available }.map { it.agent })) {
                is ConsoleLaunch.Resume -> openSession(choice.id)
                is ConsoleLaunch.Create -> openNewSession(choice.draft)
                ConsoleLaunch.Unavailable -> openNewSession()
            }
        }
    }

    /** « Quitter » est allé au bout : les deux écrans se ferment, puis le processus. */
    private val _closing = MutableStateFlow(false)
    val closing: StateFlow<Boolean> = _closing.asStateFlow()

    /** La passerelle est la passerelle factice : rien à démarrer sur la console. */
    var replaying = false
        private set

    /** Termux, relu à chaque demande : transport local de la Thor ; absent, pont externe du banc. */
    private val consoleBridge = ConsoleBridge(app)
    val hasTermux: Boolean get() = consoleBridge.hasTermux

    /**
     * Branche la passerelle, une fois pour le processus. [replay] choisit la passerelle factice, qui
     * rejoue les enregistrements du pont (en debug : `--ez fake_gateway true`).
     */
    fun start(replay: Boolean) {
        if (gateway != null) return
        replaying = replay
        val launcher = consoleBridge
        val androidAccess = consoleBridge
        val chosen: AgentGateway = gatewayFactory?.invoke() ?: if (replay) {
            ReplayAgentGateway()
        } else {
            BridgeAgentGateway(
                token = ::readToken,
                historyCount = OverviewList.PRELOADED,
                // Trace de connexion : jamais le jeton ni le contenu des messages.
                log = { Log.i(TAG, it) },
            )
        }
        val reports: StateFlow<StartUpReport?> = if (replay) {
            MutableStateFlow(null)
        } else {
            StartUp(chosen, androidAccess, launcher, pairing = launcher).also { startUp = it }.report
        }
        gateway = chosen
        authenticationReview = ReviewAgentAuthentication(chosen, scope).also { review ->
            scope.launch { review.state.collect { agentAuthentication.value = it } }
            // Le parcours d'accueil ouvert a son étape des comptes : pas de seconde offre par-dessus.
            scope.launch { chosen.connection.collect { if (it == BridgeConnection.Connected && !setup.state.value.visible) review.offerOnLaunch() } }
        }
        settingsReview = ReviewSessionSettings(chosen, scope).also { review -> scope.launch { review.state.collect { sessionSettings.value = it } } }
        reviewNewSession = ReviewNewSession(chosen, capture, scope) { openSession(it) }.also { review ->
            scope.launch { review.state.collect { newSession.value = it } }
        }
        reviewDiff = ReviewDiff(chosen, scope).also { review -> scope.launch { review.state.collect { diffReview.value = it } } }
        val answer = AnswerApproval(chosen)
        executeVoiceCommand = ExecuteVoiceCommand(chosen, answer, { openSession(it) }, { navigate(Screen.Overview) })
        answerApproval = answer
        // A card only when the conversation is visible nowhere: neither Régie screen is shown (ADR 19).
        val nowhere = combine(topStarted, bottomStarted) { top, bottom -> !top && !bottom }
            .stateIn(scope, SharingStarted.Eagerly, !topStarted.value && !bottomStarted.value)
        val floatingMessages = FloatingMessages(chosen, nowhere, scope)
        floating = floatingMessages
        scope.launch { floatingMessages.card.collect { floatingCard.value = it } }
        scope.launch {
            floatingCard.map { it?.key }.distinctUntilChanged().collectLatest { key ->
                cardKeysFor.value = null
                if (key != null) { delay(CARD_KEYS_DELAY); cardKeysFor.value = key }
            }
        }
        // The reply dictated from a card is over (sent, erased or refused): that card leaves with it.
        scope.launch {
            dictation.state.map { it.visible }.distinctUntilChanged().collect { visible ->
                if (!visible && answeringCard.value) {
                    answeringCard.value = false
                    floatingMessages.release(answeringKey)
                    answeringKey = null
                }
            }
        }
        scope.launch { chosen.connection.collectLatest { connection ->
            if (connection == BridgeConnection.Connected) projects = chosen.projects()
        } }

        val inline = ReviewApprovals(chosen, scope, answer)
        inlineApprovals = inline
        scope.launch { inline.state.collect { inlineApproval.value = it } }
        val review = ReviewApprovals(chosen, scope, answer)
        deliverDictation = DeliverDictation(SendMessage(chosen, dictated), answer)
        reviewApprovals = review
        scope.launch { review.state.collect { approvalReview.value = it } }
        NotifyApprovals(chosen, topStarted, AndroidApprovalNotifications(app), scope)
        NotifySessions(chosen, topStarted, AndroidSessionNotifications(app), scope)
        // Le courrier du jeu : mêmes cas d'usage, mais il reçoit tout, Régie visible ou non (ticket 49).
        val letters = CourierNotifications(courier, approvalText = { request ->
            val session = chosen.sessions.value.firstOrNull { it.id == request.sessionId }
            app.getString(R.string.banner_awaiting, session?.agent?.label ?: app.getString(R.string.courier_agent)) to
                listOfNotNull(session?.project, request.subject ?: app.getString(R.string.card_wants_other)).joinToString(" · ")
        })
        val neverShown = MutableStateFlow(false)
        NotifyApprovals(chosen, neverShown, letters, scope)
        NotifySessions(chosen, neverShown, letters, scope)
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
                screen.sessionId?.let { inline.followSession(it) }
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

    /** Le jeton du pont, créé par Régie ou copié en debug par `thor/bridge/install.sh` dans le stockage privé de l'app. Jamais journalisé. */
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
            is SessionAction.OpenSettings -> settingsReview?.open(action.id, action.row)
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
            is SessionAction.StartDictation -> startDictation(action.id)
            SessionAction.StopDictation -> stopDictation()
            SessionAction.StartVoiceCommand -> startVoiceCommand()
            SessionAction.StopVoiceCommand -> stopVoiceCommand()
        }
    }

    /** Change d'écran : les gâchettes tenues sont relâchées, rien de la session précédente ne reste. */
    private fun navigate(to: Screen) {
        if (_screen.value == to) return
        gameMenu.value = GameMenu.CLOSED
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
        OverviewAction.ConsoleSession -> openConsoleSession()
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
        val SECOND_A_IGNORED = 500.milliseconds
        val CARD_KEYS_DELAY = 1000.milliseconds
        val SETUP_POLL = 1000.milliseconds
        val DICTATION_KEYS = setOf(Button.A, Button.B, Button.X, Button.Y, Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT)
    }
}
