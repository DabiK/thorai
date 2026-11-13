package app.regie

import android.app.Application
import android.hardware.display.DisplayManager
import android.util.Log
import app.regie.display.DisplayMonitor
import app.regie.domain.fake.FakeAgentGateway
import app.regie.domain.model.Overview
import app.regie.domain.port.AgentGateway
import app.regie.domain.usecase.ObserveOverview
import app.regie.domain.usecase.OpenApproval
import app.regie.domain.usecase.OpenSession
import app.regie.domain.usecase.PauseAll
import app.regie.domain.usecase.SpeakSummary
import app.regie.domain.usecase.StartDictation
import app.regie.domain.usecase.StartNewSession
import app.regie.domain.usecase.StartVoiceCommand
import app.regie.domain.usecase.StopDictation
import app.regie.domain.usecase.StopVoiceCommand
import app.regie.input.InputRouter
import app.regie.overview.OverviewAction
import app.regie.overview.OverviewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RegieApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(getSystemService(DisplayManager::class.java))
    }
}

/**
 * Racine de composition, unique pour le processus : les deux activités partagent
 * la vue d'ensemble, le contrôleur, le routeur d'entrées et le mode d'écran.
 */
class AppContainer(displayManager: DisplayManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Le ticket 05 remplacera la passerelle factice par l'adapter du pont.
    private val gateway: AgentGateway = FakeAgentGateway()

    val overview: StateFlow<Overview> = ObserveOverview(gateway)().stateIn(scope, SharingStarted.Eagerly, Overview.EMPTY)

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
    }

    private companion object {
        const val TAG = "Regie"
    }
}
