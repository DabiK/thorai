package app.regie.game.ui.setup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupChecks
import app.regie.domain.model.SetupPermission
import app.regie.domain.model.SetupReview
import app.regie.domain.model.SetupStep
import app.regie.domain.model.VoiceModel
import app.regie.domain.model.VoiceModelFailure
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.WorkDots
import app.regie.game.ui.menu.PaperScreen
import app.regie.game.ui.menu.TapHint
import app.regie.game.ui.menu.TapHintBar
import app.regie.game.ui.menu.tapped
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Le parcours d'accueil (ticket 28, ADR 23) dans le style du jeu (ticket 53). Mêmes étapes, mêmes
 * vérifications, mêmes actions : seule la présentation change.
 *
 * - En haut, l'arrivée au hameau : le coursier marche de panneau en panneau, un par étape
 *   ([ArrivalScene]), et l'explication de l'étape s'affiche dans un grand cadre sur la scène, avec ses
 *   commandes à recopier. Le stick droit la fait défiler (l'écran du haut n'est pas tactile).
 * - En bas, le carnet : l'avancement, ce qui est vérifié, l'action de A en grand, puis B, X, Y dans
 *   la barre des touches. Tout se touche aussi ; « Reprendre plus tard » se touche seulement.
 * - Sur un seul écran : la route en bandeau, l'explication à gauche, l'état et l'action à droite.
 *
 * [onAction] reçoit l'action de la touche ; la manette passe par l'app, comme avant.
 */
@Composable
fun GameSetupScreen(
    review: SetupReview,
    bottom: Boolean,
    singleScreen: Boolean,
    appId: String,
    assets: GameAssets,
    onAction: (SetupAction?) -> Unit,
    modifier: Modifier = Modifier,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f),
) {
    Box(modifier.fillMaxSize().background(Game.colors.bar).testTag("setup")) {
        when {
            singleScreen -> SingleSetup(review, appId, assets, onAction, scrollVelocity)
            bottom -> SetupBottom(review, onAction)
            else -> SetupTop(review, appId, assets, scrollVelocity)
        }
    }
}

/** L'écran du haut : la route du hameau, l'explication de l'étape posée dessus. */
@Composable
private fun SetupTop(review: SetupReview, appId: String, assets: GameAssets, scrollVelocity: StateFlow<Float>) {
    val metrics = Game.metrics
    // Avec des commandes, tout doit tenir pendant qu'on tape en bas : le cadre prend plus de place.
    val dense = commands(review.step, appId) != null
    val panel = if (dense) DENSE_PANEL_PX else PANEL_PX
    BoxWithConstraints(Modifier.fillMaxSize().testTag("game-setup-top")) {
        val total = constraints.maxHeight
        ArrivalScene(review, assets, Modifier.fillMaxSize(), clearPx = total - panel - PANEL_MARGIN_PX)
        Explanation(
            review, appId, compact = false, scrollVelocity,
            Modifier.align(Alignment.BottomCenter).padding(start = metrics.dp(72), end = metrics.dp(72), bottom = metrics.dp(PANEL_MARGIN_PX))
                .fillMaxWidth().height(metrics.dp(panel)),
        )
    }
}

/** L'écran du bas : l'avancement, l'état vérifié, l'action de A, puis B, X et Y. */
@Composable
private fun SetupBottom(review: SetupReview, onAction: (SetupAction?) -> Unit) {
    PaperScreen(
        stepTitle(review.step),
        secondaryHints(review, onAction),
        Modifier.testTag("game-setup-bottom"),
        headEnd = { Later(review, onAction) },
    ) {
        Progress(review)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Game.metrics.dp(14)),
        ) {
            StepState(review, compact = false)
        }
        PrimaryAction(review, onAction)
    }
}

/** Un seul écran : la route en bandeau, l'explication à gauche, l'état et les actions à droite. */
@Composable
private fun SingleSetup(review: SetupReview, appId: String, assets: GameAssets, onAction: (SetupAction?) -> Unit, scrollVelocity: StateFlow<Float>) {
    val metrics = Game.metrics
    Box(Modifier.fillMaxSize().testTag("game-setup-single")) {
        ArrivalScene(review, assets, Modifier.fillMaxSize(), clearPx = SINGLE_BAND_PX)
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(metrics.dp(SINGLE_BAND_PX)))
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(start = metrics.dp(40), end = metrics.dp(40), bottom = metrics.dp(24)),
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(28)),
            ) {
                Explanation(review, appId, compact = true, scrollVelocity, Modifier.weight(1.3f).fillMaxHeight())
                PixelFrame(
                    Modifier.weight(1f).fillMaxHeight(),
                    thick = true,
                    colors = FrameDefaults.paper(),
                    contentPadding = PaddingValues(horizontal = metrics.dp(28), vertical = metrics.dp(22)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Progress(review, Modifier.weight(1f), counterText = false)
                            Later(review, onAction)
                        }
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(metrics.dp(10)),
                        ) {
                            StepState(review, compact = true)
                        }
                        PrimaryAction(review, onAction, compact = true)
                    }
                }
            }
            TapHintBar(secondaryHints(review, onAction))
        }
    }
}

/**
 * L'explication d'une étape, dans un grand cadre : où on en est, le titre, deux phrases, la marche à
 * suivre, puis les commandes (des données, telles quelles) et la note. Avec des commandes, une phrase
 * suffit et les commandes passent à droite.
 */
@Composable
private fun Explanation(review: SetupReview, appId: String, compact: Boolean, scrollVelocity: StateFlow<Float>, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val step = review.step
    val text = explanation(step)
    val commands = commands(step, appId)
    val dense = commands != null
    val scroll = rememberScrollState()
    StickScroll(scroll, scrollVelocity)
    PixelFrame(
        modifier.testTag("setup-explanation"),
        thick = true,
        contentPadding = PaddingValues(horizontal = metrics.dp(if (compact) 32 else 48), vertical = metrics.dp(if (compact) 24 else 32)),
    ) {
        val body = if (compact) 28 else if (dense) 30 else 34
        Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(metrics.dp(if (compact) 10 else 14))) {
            BasicText(counter(step), style = GameText.body(24, colors.muted))
            BasicText(stepTitle(step), style = GameText.display(if (compact) 56 else 64))
            val row = @Composable { textModifier: Modifier ->
                Column(textModifier, verticalArrangement = Arrangement.spacedBy(metrics.dp(if (compact) 10 else 14))) {
                    // Serré : la première phrase seulement, la marche à suivre dit le reste.
                    text.lead.take(if (dense) 1 else text.lead.size).forEachIndexed { index, sentence ->
                        BasicText(sentence, style = GameText.body(body, if (index == 0) colors.ink else colors.muted, weight = if (index == 0) 600 else 400))
                    }
                    text.steps.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                            BasicText("${index + 1}", style = GameText.code(body + 2, colors.focus), modifier = Modifier.width(metrics.dp(24)))
                            BasicText(item, style = GameText.body(body - 4))
                        }
                    }
                    // La note d'une étape à commandes passe en bas, sous l'état : les commandes gardent la place.
                    if (!dense) text.note?.let { BasicText(it, style = GameText.body(body - 6, colors.muted)) }
                }
            }
            if (commands != null && !compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(40))) {
                    row(Modifier.weight(0.9f))
                    CommandBlock(commands, Modifier.weight(1.2f))
                }
            } else {
                row(Modifier)
                commands?.let { CommandBlock(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Des commandes : des données, montrées telles quelles, à recopier ou à coller (Y les copie). */
@Composable
private fun CommandBlock(text: String, modifier: Modifier) {
    val metrics = Game.metrics
    PixelFrame(
        modifier,
        colors = FrameDefaults.filled(Game.colors.paperDeep),
        contentPadding = PaddingValues(horizontal = metrics.dp(24), vertical = metrics.dp(16)),
    ) {
        BasicText(text, style = GameText.code(30), modifier = Modifier.testTag("setup-commands"))
    }
}

/** Où on en est : « Étape 3 sur 9 » et une case par étape, comme sur la route. */
@Composable
private fun Progress(review: SetupReview, modifier: Modifier = Modifier, counterText: Boolean = true) {
    val metrics = Game.metrics
    Row(
        modifier.semantics { contentDescription = "setup-progress" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(20)),
    ) {
        if (counterText) BasicText(counter(review.step), Modifier.weight(1f), style = GameText.body(26, Game.colors.muted), maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.art(2))) {
            SetupStep.entries.forEach { step ->
                MarkBox(
                    when {
                        step == review.step -> StateMark.CURRENT
                        review.checks.isDone(step) && step != SetupStep.FINISH -> StateMark.DONE
                        else -> StateMark.TODO
                    },
                    Modifier.semantics { contentDescription = "setup-pip-${step.name.lowercase()}" },
                )
            }
        }
    }
}

/** « Reprendre plus tard » : refermer sans tout faire, au doigt. Le menu Start reprend où on en est. */
@Composable
private fun Later(review: SetupReview, onAction: (SetupAction?) -> Unit) {
    if (review.keys.b == SetupAction.CLOSE || review.keys.y == SetupAction.CLOSE) return
    val metrics = Game.metrics
    BasicText(
        stringResource(R.string.game_setup_later),
        Modifier.tapped { onAction(SetupAction.CLOSE) }.padding(horizontal = metrics.dp(12), vertical = metrics.dp(10)).testTag("setup-later"),
        style = GameText.body(26, Game.colors.muted),
        maxLines = 1,
    )
}

/** L'action de A, en grand ; sans action, ce que l'étape attend, et rien ne se passe. */
@Composable
private fun PrimaryAction(review: SetupReview, onAction: (SetupAction?) -> Unit, compact: Boolean = false) {
    val metrics = Game.metrics
    val colors = Game.colors
    val action = review.keys.a
    PixelFrame(
        Modifier.fillMaxWidth().testTag("setup-primary").semantics(mergeDescendants = true) {}
            .then(if (action != null) Modifier.tapped { onAction(action) } else Modifier),
        thick = true,
        colors = if (action != null) FrameDefaults.focus() else FrameDefaults.panel(),
        contentPadding = PaddingValues(horizontal = metrics.dp(28), vertical = metrics.dp(if (compact) 14 else 20)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(20))) {
            if (action != null) {
                KeyChip("A")
                BasicText(actionLabel(action, review), style = GameText.body(if (compact) 30 else 36, weight = 600), maxLines = 2)
            } else {
                WorkDots(colors.muted)
                BasicText(waitingLabel(review.step), style = GameText.body(if (compact) 30 else 36, colors.muted), maxLines = 1)
            }
        }
    }
}

/** B, X et Y, dans la barre des touches, chacune touchable. */
@Composable
private fun secondaryHints(review: SetupReview, onAction: (SetupAction?) -> Unit): List<TapHint> {
    val keys = review.keys
    return listOf("B" to keys.b, "X" to keys.x, "Y" to keys.y).mapNotNull { (key, action) ->
        action?.let { TapHint(key, actionLabel(it, review)) { onAction(it) } }
    }
}

/** Les cases du jeu, comme le journal de quête : verte prête, ambre en attente, vide à faire. */
private enum class StateMark { DONE, CURRENT, WAITING, TODO, NONE }

@Composable
private fun MarkBox(mark: StateMark, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val clock = Game.clock
    val box = modifier.size(metrics.art(5))
    when (mark) {
        StateMark.DONE -> Box(box.background(colors.work))
        StateMark.CURRENT -> Box(box.graphicsLayer { alpha = Blink.pulse(clock.value) }.background(colors.wait))
        StateMark.WAITING -> Box(box.background(colors.wait))
        StateMark.TODO -> Box(
            box.drawBehind {
                val p = metrics.pixelPx.toFloat()
                drawRect(colors.idle, Offset(p / 2, p / 2), Size(size.width - p, size.height - p), style = Stroke(p))
            },
        )
        StateMark.NONE -> Box(box)
    }
}

/** L'état vérifié de l'étape, ligne par ligne, avec les avis utiles. */
@Composable
private fun StepState(review: SetupReview, compact: Boolean) {
    val checks = review.checks
    when (review.step) {
        SetupStep.WELCOME -> {
            BasicText(stringResource(R.string.game_setup_need), style = GameText.body(24, Game.colors.muted))
            StateLine(StateMark.NONE, stringResource(R.string.game_setup_need_termux), stringResource(R.string.game_setup_need_termux_value), compact)
            StateLine(StateMark.NONE, stringResource(R.string.game_setup_need_network), stringResource(R.string.game_setup_need_network_value), compact)
            StateLine(StateMark.NONE, stringResource(R.string.game_setup_need_account), stringResource(R.string.game_setup_need_account_value), compact)
        }
        SetupStep.TERMUX -> {
            StateLine(if (checks.termux) StateMark.DONE else StateMark.WAITING, stringResource(R.string.game_setup_termux),
                stringResource(if (checks.termux) R.string.game_setup_installed else R.string.game_setup_missing), compact)
            val bridge = checks.bridge == BridgeStatus.Connected
            StateLine(if (bridge) StateMark.DONE else StateMark.TODO, stringResource(R.string.game_setup_debian),
                stringResource(if (bridge) R.string.game_setup_debian_ready else R.string.game_setup_debian_later), compact)
            if (!checks.termux) Hint(stringResource(R.string.game_setup_note_termux))
            if (review.copied) Hint(stringResource(R.string.game_setup_copied), Game.colors.focus)
        }
        SetupStep.BRIDGE -> {
            StateLine(permissionMark(checks, SetupPermission.RUN_COMMAND, checks.runCommand), stringResource(R.string.game_setup_run_command),
                permissionText(checks, SetupPermission.RUN_COMMAND, checks.runCommand, R.string.game_setup_granted_plural), compact)
            val (mark, value) = when (val bridge = checks.bridge) {
                BridgeStatus.Connected -> StateMark.DONE to R.string.game_setup_bridge_reachable
                BridgeStatus.Connecting -> (if (checks.runCommand) StateMark.WAITING else StateMark.TODO) to R.string.game_setup_bridge_connecting
                is BridgeStatus.Unreachable -> (if (bridge.hint == BridgeHint.RESTARTING) StateMark.WAITING else StateMark.TODO) to R.string.game_setup_bridge_unreachable
            }
            StateLine(mark, stringResource(R.string.game_setup_bridge), stringResource(value), compact)
            (checks.bridge as? BridgeStatus.Unreachable)?.takeIf { checks.runCommand }?.let { Hint(bridgeHint(it.hint)) }
        }
        SetupStep.VOICE -> {
            StateLine(permissionMark(checks, SetupPermission.MICROPHONE, checks.microphone), stringResource(R.string.game_setup_microphone),
                permissionText(checks, SetupPermission.MICROPHONE, checks.microphone, R.string.game_setup_granted), compact)
            VoiceModelState(checks.voiceModel, compact)
        }
        SetupStep.NOTIFICATIONS -> StateLine(
            permissionMark(checks, SetupPermission.NOTIFICATIONS, checks.notifications), stringResource(R.string.game_setup_notifications),
            permissionText(checks, SetupPermission.NOTIFICATIONS, checks.notifications, R.string.game_setup_granted_plural), compact,
        )
        SetupStep.CONSOLE_MODE -> StateLine(
            if (checks.consoleMode) StateMark.DONE else StateMark.TODO, stringResource(R.string.game_setup_console),
            stringResource(if (checks.consoleMode) R.string.game_setup_enabled else R.string.game_setup_disabled), compact,
        )
        SetupStep.ANDROID_ACCESS -> {
            StateLine(if (checks.androidAccess) StateMark.DONE else StateMark.TODO, stringResource(R.string.game_setup_android),
                stringResource(if (checks.androidAccess) R.string.game_setup_granted_feminine else R.string.game_setup_to_grant), compact)
            StateLine(if (checks.wirelessDebugging) StateMark.DONE else StateMark.TODO, stringResource(R.string.game_setup_wireless),
                stringResource(if (checks.wirelessDebugging) R.string.game_setup_enabled else R.string.game_setup_wireless_off), compact)
            if (!checks.androidAccess) Hint(stringResource(R.string.game_setup_note_android))
            if (review.copied) Hint(stringResource(R.string.game_setup_copied), Game.colors.focus)
        }
        SetupStep.AGENTS -> {
            val agents = checks.agents
            when {
                checks.bridge != BridgeStatus.Connected -> Hint(stringResource(R.string.game_setup_agents_need_bridge), Game.colors.alertLine)
                agents == null -> Hint(stringResource(R.string.game_setup_agents_reading))
                agents.isEmpty() -> Hint(stringResource(R.string.game_setup_agents_none))
                else -> agents.forEach { agent ->
                    StateLine(
                        when (agent.connected) { true -> StateMark.DONE; null -> StateMark.WAITING; false -> StateMark.TODO },
                        agent.agent.label,
                        stringResource(when (agent.connected) { true -> R.string.game_agents_connected; false -> R.string.game_agents_disconnected; null -> R.string.game_agents_unknown }),
                        compact,
                    )
                }
            }
        }
        SetupStep.FINISH -> {
            // Le bilan tient en une ligne ; seules les étapes restantes sont détaillées.
            val steps = SetupStep.entries.filter { it != SetupStep.WELCOME && it != SetupStep.FINISH }
            val left = steps.filterNot(checks::isDone)
            StateLine(if (left.isEmpty()) StateMark.DONE else StateMark.NONE, stringResource(R.string.game_setup_ready_steps),
                stringResource(R.string.game_setup_ready_count, steps.size - left.size, steps.size), compact)
            left.forEach { step ->
                StateLine(StateMark.TODO, shortLabel(step), stringResource(if (step.optional) R.string.game_setup_skipped else R.string.game_setup_sign_todo), compact = true)
            }
        }
    }
}

@Composable
private fun VoiceModelState(model: VoiceModel, compact: Boolean) {
    val label = stringResource(R.string.game_setup_voice_model)
    when (model) {
        VoiceModel.Installed -> StateLine(StateMark.DONE, label, stringResource(R.string.game_setup_installed), compact)
        VoiceModel.NotNeeded -> StateLine(StateMark.DONE, stringResource(R.string.game_setup_voice), stringResource(R.string.game_setup_voice_cloud), compact)
        is VoiceModel.Missing -> {
            StateLine(StateMark.TODO, label, stringResource(R.string.game_setup_voice_to_download, megabytes(model.downloadBytes)), compact)
            Hint(stringResource(R.string.game_setup_voice_space, gigabytes(model.freeBytes)))
        }
        is VoiceModel.Downloading -> {
            StateLine(StateMark.WAITING, label, model.progress?.let { stringResource(R.string.game_setup_voice_percent, (it * 100).toInt()) }
                ?: stringResource(R.string.game_setup_voice_loading), compact)
            ProgressBar(model.progress)
            Hint(stringResource(R.string.game_setup_voice_continues))
        }
        is VoiceModel.Failed -> {
            StateLine(StateMark.WAITING, label, stringResource(R.string.game_setup_voice_interrupted), compact)
            Hint(
                stringResource(
                    when (model.failure) {
                        VoiceModelFailure.OFFLINE -> R.string.game_setup_voice_offline
                        VoiceModelFailure.STORAGE -> R.string.game_setup_voice_storage
                        VoiceModelFailure.OTHER -> R.string.game_setup_voice_other
                    },
                ),
                Game.colors.alertLine,
            )
        }
    }
}

/** La barre du téléchargement, en pixels ; sans pourcentage (chargement du modèle), les points du travail. */
@Composable
private fun ProgressBar(progress: Float?) {
    val metrics = Game.metrics
    val colors = Game.colors
    Box(Modifier.padding(start = metrics.dp(MARK_INDENT_PX)).fillMaxWidth().testTag("voice-progress")) {
        if (progress == null) {
            WorkDots(colors.work)
        } else {
            PixelFrame(Modifier.fillMaxWidth(), colors = FrameDefaults.filled(colors.paperDeep)) {
                Box(
                    Modifier.fillMaxWidth().height(metrics.art(3)).drawBehind {
                        drawRect(colors.work, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
                    },
                )
            }
        }
    }
}

@Composable
private fun StateLine(mark: StateMark, label: String, value: String, compact: Boolean) {
    val metrics = Game.metrics
    val colors = Game.colors
    val size = if (compact) 28 else 32
    Row(
        Modifier.fillMaxWidth().heightIn(min = metrics.dp(if (compact) 44 else 56)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(20)),
    ) {
        MarkBox(mark)
        BasicText(label, Modifier.weight(1f), style = GameText.body(size, weight = 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(
            value,
            style = GameText.body(
                size,
                when (mark) {
                    StateMark.DONE -> colors.focus
                    StateMark.WAITING, StateMark.CURRENT -> colors.alertLine
                    StateMark.TODO, StateMark.NONE -> colors.muted
                },
                weight = if (mark == StateMark.DONE) 600 else 400,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Hint(text: String, color: Color = Game.colors.muted) {
    BasicText(text, Modifier.padding(start = Game.metrics.dp(MARK_INDENT_PX)), style = GameText.body(26, color))
}

private fun permissionMark(checks: SetupChecks, permission: SetupPermission, granted: Boolean) = when {
    granted -> StateMark.DONE
    permission in checks.refused -> StateMark.WAITING
    else -> StateMark.TODO
}

@Composable
private fun permissionText(checks: SetupChecks, permission: SetupPermission, granted: Boolean, yes: Int): String = stringResource(
    when {
        granted -> yes
        permission in checks.refused -> R.string.game_setup_refused
        else -> R.string.game_setup_to_grant
    },
)

/** Le stick droit fait défiler [scroll] à la vitesse demandée, tant qu'il est poussé. */
@Composable
private fun StickScroll(scroll: ScrollState, velocity: StateFlow<Float>) {
    // Les mesures du jeu sont en pixels de l'écran : la vitesse aussi.
    val speed = STICK_PX_PER_SECOND.toFloat()
    LaunchedEffect(scroll, velocity) {
        velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                scroll.scrollBy(value * speed * (now - last) / 1e9f)
                last = now
            }
        }
    }
}

/** La hauteur du cadre de l'explication, en pixels de l'écran du haut (1080). */
private const val PANEL_PX = 500

/** Avec des commandes à recopier. */
private const val DENSE_PANEL_PX = 620

/** Sous le cadre, jusqu'au bord de l'écran. */
private const val PANEL_MARGIN_PX = 40

/** Le bandeau de la route, sur un seul écran. */
private const val SINGLE_BAND_PX = 280

/** Le retrait d'un avis, aligné sur le texte d'une ligne d'état (case et espace). */
private const val MARK_INDENT_PX = 40

/** La vitesse du défilement au stick, en pixels de l'écran par seconde. */
private const val STICK_PX_PER_SECOND = 1400
