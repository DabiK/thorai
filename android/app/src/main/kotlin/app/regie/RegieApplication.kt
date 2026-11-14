package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Process
import android.util.Log
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
import app.regie.domain.usecase.OpenApproval
import app.regie.domain.usecase.OpenSession
import app.regie.domain.usecase.PauseAll
import app.regie.domain.usecase.Quit
import app.regie.domain.usecase.SendMessage
import app.regie.domain.usecase.SpeakSummary
import app.regie.domain.usecase.StartDictation
import app.regie.domain.usecase.StartNewSession
import app.regie.domain.usecase.StartUp
import app.regie.domain.usecase.StartUpReport
import app.regie.domain.usecase.StartVoiceCommand
import app.regie.domain.usecase.StopDictation
import app.regie.domain.usecase.StopVoiceCommand
import app.regie.gateway.bridge.BridgeAgentGateway
import app.regie.gateway.bridge.ReplayAgentGateway
import app.regie.input.InputRouter
import app.regie.overview.OverviewAction
import app.regie.overview.OverviewController
import app.regie.overview.OverviewGrid
import app.regie.session.Screen
import app.regie.session.SessionAction
import app.regie.session.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val startDictation = StartDictation()
    private val stopDictation = StopDictation()
    private val startVoiceCommand = StartVoiceCommand()
    private val stopVoiceCommand = StopVoiceCommand()
    private val openApproval = OpenApproval()
    private val startNewSession = StartNewSession()
    private val speakSummary = SpeakSummary()
    private val pauseAll = PauseAll()

    val controller = OverviewController(overview, ::execute)
    val sessionController = SessionController(live, overview, current = { (_screen.value as? Screen.Session)?.id }, ::execute)

    /** La manette va à l'écran affiché. */
    val inputRouter = InputRouter { input ->
        when (_screen.value) {
            Screen.Overview -> controller.handle(input)
            is Screen.Session -> sessionController.handle(input)
        }
    }
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

    /**
     * Branche la passerelle, une fois pour le processus. [replay] choisit la passerelle factice, qui
     * rejoue les enregistrements du pont (en debug : `--ez fake_gateway true`).
     */
    fun start(replay: Boolean) {
        if (gateway != null) return
        replaying = replay
        val launcher = TermuxBridgeLauncher(app)
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
            StartUp(chosen, WirelessDebuggingAccess(app), launcher).also { startUp = it }.report
        }
        gateway = chosen
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
                if (screen is Screen.Session) openSession(screen.id).collect { _live.value = it }
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
            is SessionAction.Open -> navigate(Screen.Session(action.id))
            SessionAction.Back -> navigate(Screen.Overview)
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
            is SessionAction.SpeakSummary -> speakSummary(action.id)
            is SessionAction.StartDictation -> startDictation(action.id)
            SessionAction.StopDictation -> stopDictation()
            SessionAction.StartVoiceCommand -> startVoiceCommand()
            SessionAction.StopVoiceCommand -> stopVoiceCommand()
        }
    }

    /** Change d'écran : les gâchettes tenues sont relâchées, rien de la session précédente ne reste. */
    private fun navigate(to: Screen) {
        if (_screen.value == to) return
        controller.releaseTriggers()
        sessionController.releaseTriggers()
        sessionController.reset()
        _screen.value = to
    }

    private fun dispatch(action: OverviewAction) = when (action) {
        is OverviewAction.StartDictation -> startDictation(action.target)
        OverviewAction.StopDictation -> stopDictation()
        OverviewAction.StartVoiceCommand -> startVoiceCommand()
        OverviewAction.StopVoiceCommand -> stopVoiceCommand()
        is OverviewAction.OpenSession -> navigate(Screen.Session(action.id))
        is OverviewAction.OpenApproval -> openApproval(action.id)
        OverviewAction.NewSession -> startNewSession()
        is OverviewAction.SpeakSummary -> speakSummary(action.target)
        OverviewAction.PauseAll -> pauseAll()
        OverviewAction.Quit -> quitNow()
    }

    private fun quitNow() {
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
