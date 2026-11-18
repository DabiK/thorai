package app.regie

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.console.TermuxBridgeLauncher
import app.regie.display.ScreenMode
import app.regie.input.GamepadInput
import app.regie.ui.screens.ApprovalTopScreen
import app.regie.ui.screens.OverviewTopScreen
import app.regie.ui.screens.SessionTopScreen
import kotlinx.coroutines.launch

/** Écran du haut (display 0). Il lance l'écran du bas quand celui-ci est allumé. */
class TopActivity : GamepadActivity() {

    /** Cette instance s'est ouverte sur le mauvais écran et a passé la main à une autre. */
    private var relaunched = false

    /** La permission RUN_COMMAND est demandée : le lancement attend la réponse. */
    private var askingPermission = false

    private val runCommandPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        askingPermission = false
        container.runStartUp()
        askNotificationPermission()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun askNotificationPermission() {
        if (!container.replaying && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotification(intent: Intent) {
        intent.getStringExtra(EXTRA_SESSION)?.let { container.openSession(app.regie.domain.model.SessionId(it)) }
        intent.getStringExtra(EXTRA_APPROVAL)?.let { container.openApproval(app.regie.domain.model.ApprovalId(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.displays.topScreenTarget(display.displayId)?.let { target ->
            relaunchOn(target)
            return
        }
        applyDebugExtras(intent)
        container.start(replay = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_FAKE_GATEWAY, false))
        container.topAlive.value = true
        // Premier lancement : le parcours d'accueil demande lui-même chaque permission, expliquée (ticket 28).
        container.offerSetup()
        if (!container.setupPending) {
            askRunCommandPermission()
            if (!askingPermission) askNotificationPermission()
        }
        // Vue jeu (ticket 49) : l'écran titre, au premier lancement du processus. Le parcours d'accueil
        // passe devant tant qu'il est ouvert ; une notification ouverte écarte le titre.
        container.showTitle()
        openNotification(intent)

        setScreen(
            approval = {
                val review by container.approvalReview.collectAsStateWithLifecycle()
                val overview by container.overview.collectAsStateWithLifecycle()
                val mode by container.displays.mode.collectAsStateWithLifecycle()
                ApprovalTopScreen(review, overview, mode == ScreenMode.Single,
                    container.approvalController.scrollSteps, container.approvalController.scrollVelocity,
                    onButton = { container.handleInput(GamepadInput.Pressed(it)) },
                    onSelect = container::selectApproval)
            },
            overview = { overview, control ->
                val mode by container.displays.mode.collectAsStateWithLifecycle()
                OverviewTopScreen(overview, control, singleScreen = mode == ScreenMode.Single, callbacks = callbacks,
                    scrollVelocity = container.controller.scrollVelocity)
            },
            session = { shown, overview ->
                val mode by container.displays.mode.collectAsStateWithLifecycle()
                val live by container.live.collectAsStateWithLifecycle()
                val control by container.sessionController.state.collectAsStateWithLifecycle()
                val draft by container.sessionController.draft
                val approval by container.inlineApproval.collectAsStateWithLifecycle()
                val options by container.sessionSettings.collectAsStateWithLifecycle()
                val dictation by container.dictation.state.collectAsStateWithLifecycle()
                val panel: (@androidx.compose.runtime.Composable () -> Unit)? = if (options.visible) ({
                    app.regie.ui.screens.SessionSettingsScreen(options, { container.handleInput(GamepadInput.Pressed(it)) }, container::selectSessionSetting, compact = true)
                }) else null
                // Un seul écran : la dictée prend le panneau du bas, le fil reste au-dessus.
                val dictationPanel: (@androidx.compose.runtime.Composable () -> Unit)? = if (mode == ScreenMode.Single && dictation.visible) ({
                    app.regie.ui.screens.DictationBottomScreen(dictation, { container.handleInput(GamepadInput.Pressed(it)) },
                        { container.touchTrigger(app.regie.input.Trigger.L2, it) }, container::openVoiceSettings, compact = true,
                        session = overview.sessions.firstOrNull { it.id == dictation.target?.sessionId }, transcript = dictationScroll)
                }) else null
                SessionTopScreen(live, gone = overview.neighboursOf(shown.id) == null, control, draft, singleScreen = mode == ScreenMode.Single, scroll = threadScroll, callbacks = sessionCallbacks, approval = approval, onDecision = container::answerInline, onNextApproval = container::nextInlineApproval, onDismissApproval = container::dismissInlineApproval, options = panel, dictation = dictationPanel, dictating = dictation.visible)
            },
        )

        lifecycleScope.launch {
            // Repris à chaque retour au premier plan et à chaque changement d'écran.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.displays.mode.collect { mode ->
                    if (mode is ScreenMode.Dual && !container.bottomStarted.value) launchBottom(mode.bottomDisplayId)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.topStarted.value = true
        // À chaque retour au premier plan : le pont a pu s'arrêter, la console redémarrer.
        if (!relaunched && !askingPermission) container.runStartUp()
    }

    /**
     * Sans RUN_COMMAND, Régie ne peut pas démarrer le pont : la permission (de Termux) est demandée
     * au premier lancement de chaque processus. Refusée, l'écran donne la marche à suivre.
     */
    private fun askRunCommandPermission() {
        if (container.replaying || !container.hasTermux) return
        if (checkSelfPermission(TermuxBridgeLauncher.PERMISSION) == PackageManager.PERMISSION_GRANTED) return
        askingPermission = true
        runCommandPermission.launch(TermuxBridgeLauncher.PERMISSION)
    }

    override fun onStop() {
        container.topStarted.value = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugExtras(intent)
        openNotification(intent)
        // Régie rouverte depuis le lanceur alors que son écran du bas avait disparu : il revient.
        val mode = container.displays.mode.value
        if (mode is ScreenMode.Dual && !container.bottomStarted.value) launchBottom(mode.bottomDisplayId)
    }

    override fun onDestroy() {
        if (isFinishing && !relaunched) container.topAlive.value = false
        super.onDestroy()
    }

    /**
     * Ouverte sur un autre écran (lanceur de l'écran du bas, `am start --display 4`) :
     * on se ferme d'abord, pour que `singleTask` ne nous retrouve pas, puis on se relance.
     */
    private fun relaunchOn(displayId: Int) {
        relaunched = true
        finish()
        startActivity(
            Intent(intent).setClass(this, TopActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
        )
    }

    private fun launchBottom(displayId: Int) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        startActivity(
            Intent(this, BottomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            options.toBundle(),
        )
    }

    /** En debug seulement : `--ez force_single_screen true` force le mode un seul écran. */
    private fun applyDebugExtras(intent: Intent) {
        if (BuildConfig.DEBUG && intent.hasExtra("voice_wav")) {
            val name = intent.getStringExtra("voice_wav").orEmpty()
            container.debugVoiceWav = if (name.matches(Regex("[A-Za-z0-9_-]+\\.wav"))) java.io.File(filesDir, name).takeIf { it.isFile } else null
        }
        if (BuildConfig.DEBUG && intent.hasExtra(EXTRA_FORCE_SINGLE_SCREEN)) {
            container.displays.forceSingleScreen(intent.getBooleanExtra(EXTRA_FORCE_SINGLE_SCREEN, false))
        }
        // Captures du parcours d'accueil sur la console, sans accorder de permission à la place de l'utilisateur.
        if (BuildConfig.DEBUG && intent.hasExtra(EXTRA_SETUP_STEP)) {
            app.regie.domain.model.SetupStep.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_SETUP_STEP) }?.let(container::openSetupAt)
        }
    }

    companion object {
        const val EXTRA_SESSION = "session_id"
        const val EXTRA_APPROVAL = "approval_id"
        const val EXTRA_FORCE_SINGLE_SCREEN = "force_single_screen"

        /** En debug : `--es setup_step VOICE` ouvre le parcours d'accueil sur cette étape. */
        const val EXTRA_SETUP_STEP = "setup_step"

        /** En debug : `--ez fake_gateway true` au premier lancement du processus branche la passerelle factice. */
        const val EXTRA_FAKE_GATEWAY = "fake_gateway"
    }
}
