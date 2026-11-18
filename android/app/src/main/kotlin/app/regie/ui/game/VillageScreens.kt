package app.regie.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.regie.R
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.theme.GameTheme
import app.regie.game.ui.village.Carnet
import app.regie.game.ui.village.CarnetApproval
import app.regie.game.ui.village.CarnetBottomScreen
import app.regie.game.ui.village.CarnetCallbacks
import app.regie.game.ui.village.CarnetHeader
import app.regie.game.ui.village.CarnetKey
import app.regie.game.ui.village.CarnetMic
import app.regie.game.ui.village.CarnetNotice
import app.regie.game.ui.village.CarnetQuit
import app.regie.game.ui.village.CarnetQuitPrompt
import app.regie.game.ui.village.CarnetRow
import app.regie.game.ui.village.CarnetTone
import app.regie.game.ui.village.CarnetTrigger
import app.regie.game.ui.village.CarnetVersion
import app.regie.game.ui.village.CompactCarnet
import app.regie.game.ui.village.VillageTopScreen
import app.regie.game.ui.village.DefaultVillageLayer
import app.regie.game.ui.chantier.constructionLayer
import app.regie.game.engine.village.Construction
import app.regie.game.ui.village.focusedHouse
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.overview.ControlState
import app.regie.overview.QuitStep
import app.regie.ui.components.agentName
import app.regie.ui.formatAge
import app.regie.ui.screens.ControlCallbacks
import app.regie.ui.screens.rememberNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/*
 * Branchement du hameau (ticket 45) : les écrans du jeu reçoivent la vue d'ensemble, l'état de la
 * manette et le coursier, et rendent les mêmes informations et actions que OverviewScreens, avec les
 * textes de l'app. OverviewScreens part au ticket 51.
 */

/** Les rappels de la vue d'ensemble, vus par le carnet. */
fun ControlCallbacks.carnet(onGoTo: (SessionId) -> Unit, onSetup: () -> Unit, onVoice: () -> Unit): CarnetCallbacks = object : CarnetCallbacks {
    override fun onKey(key: CarnetKey) = onButton(Button.valueOf(key.name))
    override fun onTrigger(trigger: CarnetTrigger, held: Boolean) = this@carnet.onTrigger(Trigger.valueOf(trigger.name), held)
    override fun onGoTo(session: SessionId) = onGoTo(session)
    override fun onSetup() = onSetup()
    override fun onVoiceSettings() = onVoice()
    override fun onQuit() = this@carnet.onQuit()
    override fun onConfirmQuit() = this@carnet.onConfirmQuit()
    override fun onCancelQuit() = this@carnet.onCancelQuit()
}

/**
 * L'écran du haut de la vue d'ensemble : le hameau et son coursier. En mode un seul écran, le carnet
 * compact passe sous le hameau, et la confirmation de « Quitter » par-dessus.
 */
@Composable
fun GameOverviewTop(
    scene: VillageSceneState?,
    overview: Overview,
    control: ControlState,
    singleScreen: Boolean,
    setupLeft: Boolean,
    callbacks: CarnetCallbacks,
    onEnterHouse: (SessionId) -> Unit,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f),
    construction: Construction? = null,
) {
    GameTheme {
        val assets = rememberGameAssets()
        val carnet = rememberCarnet(overview, control, setupLeft)
        val focused = rememberFocus(scene, active = true, overview, control)
        VillageTopScreen(
            scene,
            assets,
            onEnterHouse,
            // Un chantier en cours (ticket 48) : son panneau, ses étapes et la nouvelle rue, par-dessus le hameau.
            layer = if (construction == null) DefaultVillageLayer else remember(construction) { constructionLayer(construction, panel = true) },
            carnet = if (!singleScreen) null else ({ CompactCarnet(carnet, focused.house, focused.atDoor, assets, callbacks, scrollVelocity = scrollVelocity) }),
            prompt = if (!singleScreen) null else carnet.quit?.let { quit -> { CarnetQuitPrompt(quit, callbacks) } },
        )
    }
}

/**
 * L'écran du bas de la vue d'ensemble : le carnet. [villageShown] : l'écran du haut montre le hameau ;
 * sinon (une autre appli l'occupe), la ligne choisie est celle de la vue d'ensemble classique.
 */
@Composable
fun GameOverviewBottom(
    scene: VillageSceneState?,
    villageShown: Boolean,
    overview: Overview,
    control: ControlState,
    setupLeft: Boolean,
    callbacks: CarnetCallbacks,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f),
) {
    GameTheme {
        val assets = rememberGameAssets()
        val carnet = rememberCarnet(overview, control, setupLeft)
        val focused = rememberFocus(scene, villageShown, overview, control)
        CarnetBottomScreen(carnet, focused.house, focused.atDoor, assets, callbacks, scrollVelocity = scrollVelocity)
    }
}

private class Focus(val house: SessionId?, val atDoor: Boolean)

/** La maison choisie : celle du coursier quand le hameau est visible, sinon la ligne qui a le focus. */
@Composable
private fun rememberFocus(scene: VillageSceneState?, active: Boolean, overview: Overview, control: ControlState): Focus {
    if (scene == null || !active) {
        return Focus(overview.sessions.getOrNull(control.focusIn(overview.sessions.map { it.id }))?.id, atDoor = false)
    }
    // Recalculée quand le coursier change de maison, pas à chaque pixel de sa marche.
    val house by remember(scene) { derivedStateOf { scene.focusedHouse() } }
    val atDoor by remember(scene) { derivedStateOf { scene.facingDoor != null } }
    return Focus(house, atDoor)
}

/** Le carnet de la vue d'ensemble, rédigé avec les textes de l'app. */
@Composable
fun rememberCarnet(overview: Overview, control: ControlState, setupLeft: Boolean): Carnet {
    val now by rememberNow()
    val bridge = overview.bridge
    val unreachable = (bridge as? BridgeStatus.Unreachable)?.let { stringResource(R.string.bridge_unreachable) to stringResource(hintText(it.hint)) }
    val untested = stringResource(R.string.version_untested)
    val header = CarnetHeader(
        count = pluralStringResource(R.plurals.overview_session_count, overview.activeCount, overview.activeCount),
        bridge = when (bridge) {
            BridgeStatus.Connected -> null
            BridgeStatus.Connecting -> stringResource(R.string.bridge_connecting)
            is BridgeStatus.Unreachable -> stringResource(R.string.bridge_unreachable)
        },
        bridgeTone = if (bridge is BridgeStatus.Unreachable) CarnetTone.WAIT else CarnetTone.MUTED,
        versions = overview.agents.map { versionOf(it, untested) },
    )
    val approval = overview.firstPendingApproval?.let { pending ->
        CarnetApproval(
            session = pending.session.id,
            inhabitant = InhabitantKind.of(pending.session.agent),
            title = stringResource(R.string.banner_awaiting, agentName(pending.session.agent)),
            where = listOfNotNull(pending.session.project, pending.session.title).joinToString(" · "),
            subject = pending.approval.subject,
        )
    }
    val rows = overview.sessions.map { rowOf(it, now) }
    val empty = if (overview.sessions.isNotEmpty()) null else when (bridge) {
        BridgeStatus.Connected -> stringResource(app.regie.game.ui.R.string.hameau_empty)
        BridgeStatus.Connecting -> stringResource(R.string.bridge_connecting)
        is BridgeStatus.Unreachable -> unreachable?.let { (title, hint) -> "$title. $hint" }
    }
    val held = control.heldTriggers
    val mic = CarnetMic(
        title = stringResource(
            when {
                Trigger.L2 in held -> R.string.mic_l2_held
                Trigger.R2 in held -> R.string.mic_r2_held
                else -> R.string.mic_hold_l2
            },
        ),
        held = held.map { CarnetTrigger.valueOf(it.name) }.toSet(),
    )
    val quit = when (val step = control.quit) {
        null -> null
        is QuitStep.Confirm -> CarnetQuit.Confirm(
            pluralStringResource(R.plurals.quit_busy, step.busy, step.busy),
            stringResource(R.string.quit_confirm),
            stringResource(R.string.quit_cancel),
        )
        QuitStep.Stopping -> CarnetQuit.Stopping(stringResource(R.string.quit_stopping))
    }
    return Carnet(
        header = header,
        mic = mic,
        notices = listOfNotNull(unreachable?.let { (title, hint) -> CarnetNotice(title, hint) }),
        approval = approval,
        rows = rows,
        empty = empty,
        setupLeft = setupLeft,
        quit = quit,
    )
}

/** « claude 2.1.322 », avec l'avertissement d'une version hors de la plage testée. */
private fun versionOf(entry: AgentVersion, untested: String): CarnetVersion {
    val label = listOfNotNull(entry.agent.label, entry.version).joinToString(" ")
    return CarnetVersion(if (entry.isTested) label else "$label ($untested)", entry.isTested)
}

/** Une ligne : ce que montrait `SessionRow`, avec les mêmes textes. */
@Composable
private fun rowOf(session: Session, now: Instant): CarnetRow {
    val approval = session.pendingApproval
    val error = session.lastError.takeIf { session.status == SessionStatus.FAILED }
    val lastAction = session.lastAction
    val (detail, tone) = when {
        approval != null -> {
            val wants = stringResource(
                when (approval.kind) {
                    ApprovalKind.COMMAND -> R.string.card_wants_command
                    ApprovalKind.FILE_CHANGE -> R.string.card_wants_file_change
                    ApprovalKind.OTHER -> R.string.card_wants_other
                },
            )
            listOfNotNull(wants, approval.subject).joinToString(" ") to CarnetTone.WAIT
        }
        error != null -> error to CarnetTone.ERROR
        lastAction != null -> {
            val status = lastAction.status?.let { " · ${actionStatusLabel(it)}" }.orEmpty()
            "› ${lastAction.description}$status" to if (lastAction.status == ActionStatus.FAILED) CarnetTone.ERROR else CarnetTone.MUTED
        }
        else -> null to CarnetTone.MUTED
    }
    return CarnetRow(
        session = session.id,
        agent = session.agent,
        inhabitant = InhabitantKind.of(session.agent),
        project = session.project,
        title = session.title,
        branch = session.branch,
        state = TagState.of(session.status),
        stopped = session.status == SessionStatus.STOPPED,
        step = session.progress?.let { stringResource(R.string.card_step_short, it.step, it.total) },
        detail = detail,
        detailTone = tone,
        age = formatAge(now - session.lastActivityAt),
    )
}

@Composable
private fun actionStatusLabel(status: ActionStatus): String = stringResource(
    when (status) {
        ActionStatus.PENDING -> R.string.action_status_pending
        ActionStatus.IN_PROGRESS -> R.string.action_status_in_progress
        ActionStatus.COMPLETED -> R.string.action_status_completed
        ActionStatus.FAILED -> R.string.action_status_failed
        ActionStatus.INTERRUPTED -> R.string.action_status_interrupted
    },
)

private fun hintText(hint: BridgeHint) = when (hint) {
    BridgeHint.START_EXTERNAL -> R.string.bridge_hint_start_external
    BridgeHint.GRANT_RUN_COMMAND -> R.string.bridge_hint_grant_run_command
    BridgeHint.TERMUX_MISSING -> R.string.bridge_hint_termux_missing
    BridgeHint.TOKEN_MISSING -> R.string.bridge_hint_token_missing
    BridgeHint.TOKEN_REFUSED -> R.string.bridge_hint_token_refused
    BridgeHint.RESTARTING -> R.string.bridge_hint_restarting
}
