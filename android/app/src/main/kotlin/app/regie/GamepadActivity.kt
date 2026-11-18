package app.regie

import android.os.Bundle
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.display.ScreenMode
import app.regie.ui.components.tappable
import app.regie.ui.screens.DictationTopScreen
import app.regie.ui.screens.DictationBottomScreen
import app.regie.ui.theme.Palette
import app.regie.ui.theme.sans
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.InputRouter
import app.regie.input.Trigger
import app.regie.input.TriggerAxes
import app.regie.overview.ControlState
import app.regie.domain.model.Overview
import app.regie.session.Screen
import app.regie.session.SessionNotice
import app.regie.ui.screens.ControlCallbacks
import app.regie.ui.screens.SessionCallbacks
import app.regie.ui.screens.ThreadScroll
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.launch
import app.regie.game.ui.theme.GameTheme
import kotlin.math.max

/**
 * Base des deux activités : la manette passe par le routeur d'entrées unique, avant
 * le système de focus de Compose et avant les raccourcis système (A → DPAD_CENTER…),
 * qui ne s'appliquent qu'aux touches non consommées.
 */
abstract class GamepadActivity : ComponentActivity() {

    protected val container: AppContainer get() = (application as RegieApplication).container

    protected val callbacks = object : ControlCallbacks {
        override fun onButton(button: Button) = container.handleInput(GamepadInput.Pressed(button))

        override fun onTrigger(trigger: Trigger, held: Boolean) = container.touchTrigger(trigger, held)

        override fun onOpenApproval() = container.controller.openPendingApproval()

        override fun onQuit() = container.controller.requestQuit()

        override fun onConfirmQuit() = container.controller.confirmQuit()

        override fun onCancelQuit() = container.controller.cancelQuit()

        // Accessibilité s'ouvre sur l'écran qui l'a demandée : celui du bas est tactile.
        override fun onEnableConsole() = startActivity(
            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle(),
        )
    }

    protected val sessionCallbacks = object : SessionCallbacks {
        override fun onOverview() = container.openOverview()
        override fun onOptions() = container.openSessionSettings()
        override fun onSettingRow(row: String) = container.openSessionSettings(row)
        override fun onButton(button: Button) = container.handleInput(GamepadInput.Pressed(button))

        override fun onTrigger(trigger: Trigger, held: Boolean) = container.touchTrigger(trigger, held)

        override fun onDraftChange(text: String) = container.sessionController.onDraftChange(text)

        override fun onSend() = container.sessionController.send()

        override fun onNoticeSeen(notice: SessionNotice) = container.sessionController.dismissNotice(notice)
    }

    protected val threadScroll by lazy {
        ThreadScroll(container.sessionController.scrollSteps, container.sessionController.scrollVelocity)
    }

    /** La transcription de la dictée : la croix et le stick la font défiler. */
    protected val dictationScroll by lazy { ThreadScroll(container.dictationScroll, container.dictationVelocity) }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.callback = GamepadWindowCallback(window.callback, container.inputRouter, container::releaseTouchDictation)
        // « Quitter » est allé au bout : les deux écrans se ferment, même en arrière-plan.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.closing.collect { if (it) finishAndRemoveTask() }
            }
        }
    }

    /**
     * L'activité au premier plan du système reçoit la manette. Tant qu'une des nôtres l'est, sa
     * fenêtre lit la manette ; sinon, le service d'accessibilité prend L2 et R2 (ticket 25).
     */
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        container.gamepadFocus(this, isTopResumedActivity)
    }

    override fun onDestroy() {
        container.gamepadFocus(this, false)
        super.onDestroy()
    }

    override fun onStop() {
        // Une autre appli a pris l'écran : un relâché de gâchette ne nous parviendrait plus.
        container.releaseTouchDictation()
        container.inputRouter.reset()
        super.onStop()
    }

    /**
     * L'écran affiché, le même sur les deux écrans de la console : la vue d'ensemble, ou une session.
     * [session] reçoit la vue d'ensemble pour les voisines (L1 / R1).
     */
    protected fun setScreen(
        overview: @Composable (Overview, ControlState) -> Unit,
        session: @Composable (Screen.Session, Overview) -> Unit,
        approval: @Composable () -> Unit,
    ) {
        setContent {
            RegieTheme {
                Box(Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding()) {
                    val authentication by container.agentAuthentication.collectAsStateWithLifecycle()
                    val screen by container.screen.collectAsStateWithLifecycle()
                    val current by container.overview.collectAsStateWithLifecycle()
                    val dictation by container.dictation.state.collectAsStateWithLifecycle()
                    val command by container.voiceCommand.state.collectAsStateWithLifecycle()
                    val newSession by container.newSession.collectAsStateWithLifecycle()
                    val sessionSettings by container.sessionSettings.collectAsStateWithLifecycle()
                    val settingsOpen by container.settingsOpen.collectAsStateWithLifecycle()
                    val settings by container.voiceSettings.collectAsStateWithLifecycle()
                    val mode by container.displays.mode.collectAsStateWithLifecycle()
                    val controlsHere = this@GamepadActivity is BottomActivity || mode == ScreenMode.Single
                    val button: (Button) -> Unit = { container.handleInput(GamepadInput.Pressed(it)) }
                    val held: (Boolean) -> Unit = { container.touchTrigger(Trigger.L2, it) }
                    val gameMenu by container.gameMenu.collectAsStateWithLifecycle()
                    val assets = app.regie.game.ui.atlas.rememberGameAssets()
                    if (authentication.visible) GameTheme {
                        // Vue jeu (ticket 49) : la connexion des agents, dans le menu.
                        app.regie.game.ui.menu.AgentsScreen(authentication, this@GamepadActivity is BottomActivity, mode == ScreenMode.Single,
                            gameMenu, current, assets, callbacks = app.regie.game.ui.menu.AgentsCallbacks(
                                select = container::selectAuthenticationAgent, confirm = { button(Button.A) }, back = { button(Button.B) },
                                refresh = container::refreshAuthentication, submitKey = container::submitAuthenticationKey,
                                bindKeyAction = { container.authenticationKeyAction = it }))
                    } else if (sessionSettings.visible && screen !is Screen.Session) {
                        app.regie.ui.screens.SessionSettingsScreen(sessionSettings, button, container::selectSessionSetting)
                    } else if (settingsOpen && controlsHere) GameTheme {
                        app.regie.game.ui.menu.GameVoiceSettingsScreen(settings, callbacks = app.regie.game.ui.menu.VoiceSettingsCallbacks(
                            container::selectVoice, container::saveVoiceKey, { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                            container::closeVoiceSettings, container::saveCommandKey))
                    } else if (command.visible) GameTheme {
                        app.regie.game.ui.console.GameVoiceCommandScreen(command, this@GamepadActivity is BottomActivity, mode == ScreenMode.Single, assets,
                            scroll = app.regie.game.ui.console.CommandScroll(container.commandScroll, container.commandVelocity),
                            callbacks = app.regie.game.ui.console.CommandCallbacks({ button(Button.A) }, { button(Button.B) },
                                { container.touchTrigger(Trigger.R2, it) }, container::openVoiceSettings))
                    } else if (newSession.visible) {
                        app.regie.ui.screens.NewSessionScreen(newSession, this@GamepadActivity is BottomActivity, mode == ScreenMode.Single,
                            button, held, container::selectNewSessionField, container::editNewInstruction, container::editNewBranch, container.newSessionVelocity, container::editNewProjectName)
                    } else if (dictation.visible && this@GamepadActivity is BottomActivity) {
                        // Deux écrans : la dictée et sa transcription en direct sont en bas ; le haut ne change pas.
                        DictationBottomScreen(dictation, button, held, container::openVoiceSettings,
                            session = current.sessions.firstOrNull { it.id == dictation.target?.sessionId }, transcript = dictationScroll)
                    } else if (dictation.visible && mode == ScreenMode.Single && screen !is Screen.Session) {
                        // Un seul écran, hors session : la dictée occupe l'écran. Dans une session, le fil reste.
                        DictationTopScreen(dictation, current.sessions.firstOrNull { it.id == dictation.target?.sessionId }, true, button, held, container::openVoiceSettings, dictationScroll)
                    } else if (gameMenu.open && screen == Screen.Overview) GameTheme {
                        // Vue jeu (ticket 49) : l'écran titre au lancement, le menu de pause avec Start.
                        GameMenuScreen(gameMenu, current, assets, this@GamepadActivity is BottomActivity, mode == ScreenMode.Single)
                    } else Column(Modifier.fillMaxSize()) {
                        // Dans une session, la table du bas règle la session et la dictée règle la voix.
                        if (controlsHere && screen !is Screen.Session) Row(Modifier.fillMaxWidth().background(Palette.background).padding(horizontal = 16.dp, vertical = 4.dp)) {
                            if (screen == Screen.Overview) {
                                app.regie.ui.components.BarAction("B", "Connexion des agents", container::openAgentAuthentication)
                            }
                            Spacer(Modifier.weight(1f))
                            BasicText("Régler la voix", style = sans(11.sp, color = Palette.textSubtle), modifier = Modifier.tappable(container::openVoiceSettings))
                        }
                        Box(Modifier.weight(1f)) {
                    when (val shown = screen) {
                        is Screen.Diff -> {
                            val review by container.diffReview.collectAsStateWithLifecycle()
                            app.regie.ui.screens.DiffScreen(review, this@GamepadActivity is BottomActivity, mode == ScreenMode.Single,
                                container.diffVelocity, button, container::selectDiffFile, held, container.diffSteps)
                        }
                        Screen.Approval -> approval()
                        Screen.Overview -> {
                            val control by container.controller.state.collectAsStateWithLifecycle()
                            overview(current, control)
                        }
                        is Screen.Session -> session(shown, current)
                    }
                        }
                    }
                    // L'oiseau messager (ticket 49) : en haut, quand une lettre arrive au courrier.
                    if (this@GamepadActivity !is BottomActivity) GameTheme {
                        val mail by container.courier.collectAsStateWithLifecycle()
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                            app.regie.game.ui.courier.CourierToast(mail.latest, { kotlin.time.Clock.System.now() }, assets,
                                action = app.regie.game.ui.kit.KeyHintSpec("R1", androidx.compose.ui.res.stringResource(R.string.banner_see))
                                    .takeIf { screen == Screen.Overview && !gameMenu.open })
                        }
                    }
                }
            }
        }
        // Sans vue focalisée, Android avale la première touche de navigation pour quitter
        // le mode tactile, avant même dispatchKeyEvent : la vue racine prend donc le focus.
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0)?.apply {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    /**
     * Le menu du jeu sur le hameau (ticket 49) : l'écran titre et son menu principal, ou le menu de
     * pause. Sur deux écrans, le haut montre le décor et le bas les choix ; sur un seul, tout est en haut.
     */
    @Composable
    private fun GameMenuScreen(
        menu: app.regie.game.engine.menu.GameMenu,
        overview: Overview,
        assets: app.regie.game.ui.atlas.GameAssets,
        bottom: Boolean,
        single: Boolean,
    ) {
        val control by container.controller.state.collectAsStateWithLifecycle()
        val mail by container.courier.collectAsStateWithLifecycle()
        val voice by container.voiceSettings.collectAsStateWithLifecycle()
        val quit = when (val step = control.quit) {
            is app.regie.overview.QuitStep.Confirm -> app.regie.game.ui.menu.QuitPrompt(step.busy)
            app.regie.overview.QuitStep.Stopping -> app.regie.game.ui.menu.QuitPrompt(0, stopping = true)
            null -> null
        }
        val callbacks = app.regie.game.ui.menu.MenuCallbacks(
            onChoose = container::chooseMenuEntry,
            onClose = { container.pressMenu(app.regie.game.engine.menu.MenuKey.B) },
            onConfirmQuit = container.controller::confirmQuit,
            onCancelQuit = container.controller::cancelQuit,
            onOpenLetter = container::openCourierLetter,
            onMarkAllRead = { container.pressMenu(app.regie.game.engine.menu.MenuKey.X) },
            onLeaveCourier = { container.pressMenu(app.regie.game.engine.menu.MenuKey.B) },
        )
        val now = kotlin.time.Clock.System.now()
        when {
            menu.kind == app.regie.game.engine.menu.MenuKind.TITLE && bottom ->
                app.regie.game.ui.menu.MainMenuScreen(menu, overview, quit = quit, callbacks = callbacks)
            menu.kind == app.regie.game.engine.menu.MenuKind.TITLE ->
                app.regie.game.ui.menu.TitleTopScreen(getString(R.string.app_name), menu, overview, assets, single, quit = quit, callbacks = callbacks)
            bottom -> app.regie.game.ui.menu.PauseMenuBottom(menu, overview, mail, now, assets, quit = quit, callbacks = callbacks)
            else -> app.regie.game.ui.menu.PauseMenuTop(menu, overview, mail, voice, now, assets, single, quit = quit, callbacks = callbacks)
        }
    }
}

/**
 * Enveloppe le Window.Callback de l'activité : la fenêtre lui passe chaque touche et
 * chaque mouvement de manette avant la hiérarchie de vues, donc avant Compose.
 */
private class GamepadWindowCallback(
    private val wrapped: Window.Callback,
    private val router: InputRouter,
    private val touchEnd: () -> Unit,
) : Window.Callback by wrapped {

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = wrapped.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) touchEnd()
        return handled
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDown = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return wrapped.dispatchKeyEvent(event)
        }
        return router.onKey(event.keyCode, isDown, event.repeatCount) || wrapped.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            router.onAxes(
                TriggerAxes(
                    leftTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                    rightTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)),
                    rightStickY = event.getAxisValue(MotionEvent.AXIS_RZ),
                ),
            )
        }
        // La croix peut aussi arriver en axe (HAT) : on laisse Android la convertir en touches.
        return wrapped.dispatchGenericMotionEvent(event)
    }
}

/**
 * Retour système de l'écran du bas (sa barre de navigation, souvent juste après avoir fermé le
 * clavier) : il fermait l'écran du bas pour de bon, et Régie ne le relançait pas (ticket 25). Seul
 * « Quitter » le ferme (PRD) ; B et les boutons de Régie gardent la navigation. Le clavier, lui,
 * se ferme toujours d'abord : il passe avant l'activité.
 */
internal fun ComponentActivity.keepOpenOnSystemBack() {
    onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = Unit
    })
}
