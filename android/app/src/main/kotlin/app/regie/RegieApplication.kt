package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Process
import android.util.Log
import app.regie.console.TermuxBridgeLauncher
import app.regie.console.WirelessDebuggingAccess
import app.regie.display.DisplayMonitor
import app.regie.domain.model.BridgeConnection
import app.regie.domain.model.Overview
import app.regie.domain.model.UnreachableReason
import app.regie.domain.port.AgentGateway
import app.regie.domain.usecase.ObserveOverview
import app.regie.domain.usecase.OpenApproval
import app.regie.domain.usecase.OpenSession
import app.regie.domain.usecase.PauseAll
import app.regie.domain.usecase.Quit
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Racine de composition, unique pour le processus : les deux activités partagent
 * la vue d'ensemble, le contrôleur, le routeur d'entrées et le mode d'écran.
 * C'est le seul endroit qui choisit la passerelle vers les agents (ADR 06).
 */
class AppContainer(private val app: Application, displayManager: DisplayManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _overview = MutableStateFlow(Overview.EMPTY)
    val overview: StateFlow<Overview> = _overview.asStateFlow()

    private var gateway: AgentGateway? = null
    private var startUp: StartUp? = null
    private var quit: Quit? = null
    private var startUpJob: Job? = null

    private val startDictation = StartDictation()
    private val stopDictation = StopDictation()
    private val startVoiceCommand = StartVoiceCommand()
    private val stopVoiceCommand = StopVoiceCommand()
    private val openSession = OpenSession()
    private val openApproval = OpenApproval()
    private val startNewSession = StartNewSession()
    private val speakSummary = SpeakSummary()
    private val pauseAll = PauseAll()

    val controller = OverviewController(overview, ::execute)
    val inputRouter = InputRouter(controller::handle)
    val displays = DisplayMonitor(displayManager)

    /** L'écran du haut existe : l'écran du bas se ferme quand il disparaît. */
    val topAlive = MutableStateFlow(false)

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
        Log.i(TAG, "passerelle : ${if (replay) "factice (enregistrements du pont)" else "pont"}")

        scope.launch { chosen.maintainConnection() }
        scope.launch { ObserveOverview(chosen, reports)().collect { _overview.value = it } }
        // Le pont s'est tu en cours de route : Régie le relance (StartUp espace les tentatives).
        scope.launch {
            chosen.connection.collect {
                if (it == BridgeConnection.Unreachable(UnreachableReason.NOT_RESPONDING)) runStartUp()
            }
        }
    }

    /** Au lancement et à chaque retour au premier plan : débogage sans fil, puis pont (StartUp). */
    fun runStartUp() {
        val useCase = startUp ?: return
        if (startUpJob?.isActive == true || _closing.value) return
        startUpJob = scope.launch {
            val report = useCase()
            Log.i(TAG, "lancement : débogage sans fil ${report.wirelessDebugging}, pont ${report.lastLaunch ?: "déjà là"}")
        }
    }

    /** Le jeton du pont, copié par `thor/bridge/install.sh` dans le stockage privé de l'app. Jamais journalisé. */
    private fun readToken(): String? =
        File(app.filesDir, TOKEN_FILE).takeIf { it.canRead() }?.readText()?.trim()?.ifEmpty { null }

    private fun execute(action: OverviewAction) {
        Log.i(TAG, "action : $action")
        dispatch(action)
    }

    private fun dispatch(action: OverviewAction) = when (action) {
        is OverviewAction.StartDictation -> startDictation(action.target)
        OverviewAction.StopDictation -> stopDictation()
        OverviewAction.StartVoiceCommand -> startVoiceCommand()
        OverviewAction.StopVoiceCommand -> stopVoiceCommand()
        is OverviewAction.OpenSession -> openSession(action.id)
        is OverviewAction.OpenApproval -> openApproval(action.id)
        OverviewAction.NewSession -> startNewSession()
        is OverviewAction.SpeakSummary -> speakSummary(action.target)
        OverviewAction.PauseAll -> pauseAll()
        OverviewAction.Quit -> quitNow()
    }

    private fun quitNow() {
        scope.launch {
            startUpJob?.cancel()
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
