package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.regie.R
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.overview.ControlState
import app.regie.overview.QuitStep
import app.regie.ui.components.ApprovalBanner
import app.regie.ui.components.BarAction
import app.regie.ui.components.MicButton
import app.regie.ui.components.Notice
import app.regie.ui.components.StateMessage
import app.regie.ui.components.SessionRow
import app.regie.ui.components.bottomDivider
import app.regie.ui.components.topDivider
import app.regie.ui.components.tappable
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

/** Ce que l'écran du bas peut demander, au doigt comme à la manette. */
interface ControlCallbacks {
    fun onButton(button: Button)
    fun onTrigger(trigger: Trigger, held: Boolean)
    fun onOpenApproval()

    /** « Quitter », au toucher : le PRD ne lui donne pas de bouton de manette. */
    fun onQuit()
    fun onConfirmQuit()
    fun onCancelQuit()

    /** Ouvrir les réglages d'accessibilité, où l'utilisateur active le mode console (ticket 25). */
    fun onEnableConsole() {}
}

/**
 * Écran du haut : la vue d'ensemble, une liste dense d'une ligne par session. En mode un seul écran,
 * la zone du bas devient un panneau en bas. [scrollVelocity] : le stick droit fait défiler la liste.
 */
@Composable
fun OverviewTopScreen(
    overview: Overview,
    control: ControlState,
    singleScreen: Boolean,
    callbacks: ControlCallbacks,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f),
) {
    val now by rememberNow()
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Column(Modifier.fillMaxSize()) {
            OverviewHeader(overview)
            SessionList(overview, control, now, compact = singleScreen, scrollVelocity, modifier = Modifier.weight(1f))
            if (singleScreen) {
                Notices(overview, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp).fillMaxWidth())
                CompactControlPanel(overview, control, callbacks)
            }
        }
        if (singleScreen) control.quit?.let { QuitPrompt(it, callbacks) }
    }
}

/** Écran du bas : la première validation en attente, le micro et les boutons utiles. */
@Composable
fun OverviewBottomScreen(overview: Overview, control: ControlState, callbacks: ControlCallbacks, consoleOff: Boolean = false) {
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        BottomContent(overview, control, callbacks, consoleOff)
        control.quit?.let { QuitPrompt(it, callbacks) }
    }
}

@Composable
private fun BottomContent(overview: Overview, control: ControlState, callbacks: ControlCallbacks, consoleOff: Boolean) {
    Column(Modifier.fillMaxSize()) {
        // La première validation en attente d'abord, en haut de l'écran (PRD, parcours 1).
        overview.firstPendingApproval?.let {
            ApprovalBanner(
                pending = it,
                onOpen = callbacks::onOpenApproval,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp).fillMaxWidth(),
            )
        }
        Notices(overview, Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp).fillMaxWidth())
        // La permission spéciale est un geste de l'utilisateur : l'avis explique et ouvre les réglages.
        if (consoleOff) Box(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp).fillMaxWidth().testTag("console-notice").tappable(callbacks::onEnableConsole)) {
            Notice(stringResource(R.string.console_notice_title), stringResource(R.string.console_notice_hint), Palette.textMuted)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            MicButton(
                active = control.heldTriggers.isNotEmpty(),
                onPress = { callbacks.onTrigger(Trigger.L2, held = true) },
                onRelease = { callbacks.onTrigger(Trigger.L2, held = false) },
            )
            BasicText(micTitle(control), style = sans(18.sp, weight = 600, color = micTitleColor(control)))
            BasicText(
                text = stringResource(R.string.mic_r2_examples),
                style = sans(13.sp, color = Palette.textMuted, lineHeight = 19.5.sp).copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        ButtonBar(callbacks, Modifier.fillMaxWidth().height(50.dp).topDivider().padding(horizontal = 16.dp), withQuitSpacer = true)
    }
}

@Composable
private fun OverviewHeader(overview: Overview) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).bottomDivider().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(stringResource(R.string.app_name), style = sans(16.sp, weight = 650, letterSpacing = (-0.01).em))
        val count = overview.activeCount
        BasicText(
            pluralStringResource(R.plurals.overview_session_count, count, count),
            style = sans(13.sp, color = Palette.textMuted),
        )
        BridgeIndicator(overview.bridge)
        Spacer(Modifier.weight(1f))
        BasicText(agentVersions(overview.agents), style = mono(11.sp, color = Palette.textSubtle))
    }
}

/** L'état du pont dans l'en-tête, seulement quand il n'est pas connecté. */
@Composable
private fun BridgeIndicator(bridge: BridgeStatus) {
    val (label, color) = when (bridge) {
        BridgeStatus.Connected -> return
        BridgeStatus.Connecting -> stringResource(R.string.bridge_connecting) to Palette.textMuted
        is BridgeStatus.Unreachable -> stringResource(R.string.bridge_unreachable) to Palette.waiting
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        BasicText(label, style = sans(13.sp, color = color))
    }
}

/**
 * « claude 2.1.322 · codex 0.167.2 ». Une version hors de la plage testée est en ambre, avec
 * l'avertissement ; une version que le pont n'a pas lue n'est pas affichée.
 */
@Composable
private fun agentVersions(agents: List<AgentVersion>): AnnotatedString {
    val untested = stringResource(R.string.version_untested)
    return buildAnnotatedString {
        agents.forEachIndexed { index, entry ->
            if (index > 0) append(" · ")
            val label = listOfNotNull(entry.agent.label, entry.version).joinToString(" ")
            if (entry.isTested) {
                append(label)
            } else {
                withStyle(SpanStyle(color = Palette.waiting)) { append("$label ($untested)") }
            }
        }
    }
}

/** Ce que l'utilisateur doit savoir et peut faire : pont injoignable, accès des agents à Android. */
@Composable
private fun Notices(overview: Overview, modifier: Modifier) {
    val bridge = overview.bridge as? BridgeStatus.Unreachable
    if (bridge == null && !overview.androidAccessMissing) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bridge?.let { Notice(stringResource(R.string.bridge_unreachable), stringResource(hintText(it.hint)), Palette.waiting) }
        if (overview.androidAccessMissing) {
            Notice(stringResource(R.string.android_access_title), stringResource(R.string.android_access_hint), Palette.textMuted)
        }
    }
}

private fun hintText(hint: BridgeHint) = when (hint) {
    BridgeHint.START_EXTERNAL -> R.string.bridge_hint_start_external
    BridgeHint.GRANT_RUN_COMMAND -> R.string.bridge_hint_grant_run_command
    BridgeHint.TERMUX_MISSING -> R.string.bridge_hint_termux_missing
    BridgeHint.TOKEN_MISSING -> R.string.bridge_hint_token_missing
    BridgeHint.TOKEN_REFUSED -> R.string.bridge_hint_token_refused
    BridgeHint.RESTARTING -> R.string.bridge_hint_restarting
}

/** Confirmation de « Quitter » (A confirme, B annule, au doigt aussi), puis l'arrêt en cours. */
@Composable
private fun QuitPrompt(step: QuitStep, callbacks: ControlCallbacks) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.background.copy(alpha = 0.82f))
            // Le voile avale les touchers : rien d'autre ne réagit pendant la confirmation.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .background(Palette.surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                is QuitStep.Confirm -> {
                    BasicText(
                        pluralStringResource(R.plurals.quit_busy, step.busy, step.busy),
                        style = sans(15.sp, weight = 600, lineHeight = 20.sp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        BarAction("A", stringResource(R.string.quit_confirm), callbacks::onConfirmQuit)
                        BarAction("B", stringResource(R.string.quit_cancel), callbacks::onCancelQuit)
                    }
                }
                QuitStep.Stopping -> BasicText(stringResource(R.string.quit_stopping), style = sans(15.sp, weight = 600))
            }
        }
    }
}

/** Ticks d'une seconde, pour que l'ancienneté avance à l'écran. */
@Composable
internal fun rememberNow(): State<Instant> = produceState(Clock.System.now()) {
    while (true) {
        delay(1_000)
        value = Clock.System.now()
    }
}

/**
 * La liste des sessions, dans l'ordre de la vue d'ensemble. La ligne qui a le focus reste visible :
 * la liste ne défile que si elle sort de l'écran, d'une ligne à la fois quand la croix descend.
 */
@Composable
private fun SessionList(overview: Overview, control: ControlState, now: Instant, compact: Boolean, velocity: StateFlow<Float>, modifier: Modifier) {
    val sessions = overview.sessions
    if (sessions.isEmpty() && overview.bridge == BridgeStatus.Connected) {
        StateMessage("Aucune session. Crée une session avec X.", modifier)
        return
    }
    val focus = control.focusIn(sessions.map { it.id })
    val list = rememberLazyListState()
    val speed = with(LocalDensity.current) { STICK_SPEED.toPx() }
    LaunchedEffect(focus, sessions.size) {
        if (sessions.isEmpty()) return@LaunchedEffect
        val layout = list.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == focus }
        when {
            item == null -> list.animateScrollToItem(focus)
            item.offset < layout.viewportStartOffset -> list.animateScrollBy((item.offset - layout.viewportStartOffset).toFloat())
            item.offset + item.size > layout.viewportEndOffset - layout.afterContentPadding ->
                list.animateScrollBy((item.offset + item.size - layout.viewportEndOffset + layout.afterContentPadding).toFloat())
        }
    }
    LaunchedEffect(list) {
        velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                list.scrollBy(value * speed * (frame - last) / 1e9f)
                last = frame
            }
        }
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth().testTag("session-list"),
    ) {
        itemsIndexed(sessions, key = { _, session -> session.id.value }) { index, session ->
            SessionRow(session = session, now = now, focused = index == focus, compact = compact)
        }
    }
}

private val STICK_SPEED = 900.dp

/** Panneau du mode un seul écran : micro, validation en attente et boutons, sur une ligne. */
@Composable
private fun CompactControlPanel(overview: Overview, control: ControlState, callbacks: ControlCallbacks) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).topDivider().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MicButton(
            active = control.heldTriggers.isNotEmpty(),
            onPress = { callbacks.onTrigger(Trigger.L2, held = true) },
            onRelease = { callbacks.onTrigger(Trigger.L2, held = false) },
            size = 40.dp,
            halo = 4.dp,
        )
        val pending = overview.firstPendingApproval
        when {
            control.heldTriggers.isNotEmpty() || pending == null ->
                BasicText(
                    text = micTitle(control),
                    style = sans(14.sp, weight = 600, color = micTitleColor(control)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            else -> ApprovalBanner(pending, callbacks::onOpenApproval, Modifier.weight(1f), compact = true)
        }
        ButtonBar(callbacks, Modifier, withQuitSpacer = false)
    }
}

@Composable
private fun ButtonBar(callbacks: ControlCallbacks, modifier: Modifier, withQuitSpacer: Boolean) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BarAction("A", stringResource(R.string.action_open)) { callbacks.onButton(Button.A) }
        BarAction("X", stringResource(R.string.action_new_session)) { callbacks.onButton(Button.X) }
        BarAction("Y", stringResource(R.string.action_console_session)) { callbacks.onButton(Button.Y) }
        if (withQuitSpacer) Spacer(Modifier.weight(1f))
        QuitAction(callbacks::onQuit)
    }
}

/** « Quitter », tactile seulement : un simple libellé, sans puce de bouton. */
@Composable
private fun QuitAction(onClick: () -> Unit) {
    BasicText(
        text = stringResource(R.string.action_quit),
        style = sans(12.5.sp, color = Palette.textMuted),
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun micTitle(control: ControlState): String = stringResource(
    when {
        Trigger.L2 in control.heldTriggers -> R.string.mic_l2_held
        Trigger.R2 in control.heldTriggers -> R.string.mic_r2_held
        else -> R.string.mic_hold_l2
    },
)

private fun micTitleColor(control: ControlState) = micTitleColor(control.heldTriggers)

internal fun micTitleColor(held: Set<Trigger>) = if (held.isEmpty()) Palette.text else Palette.mic

