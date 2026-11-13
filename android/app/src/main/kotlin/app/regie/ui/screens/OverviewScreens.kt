package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.regie.R
import app.regie.domain.model.Overview
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.overview.ControlState
import app.regie.overview.OverviewGrid
import app.regie.ui.components.ApprovalBanner
import app.regie.ui.components.ButtonChip
import app.regie.ui.components.MicButton
import app.regie.ui.components.SessionCard
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans

/** Ce que l'écran du bas peut demander, au doigt comme à la manette. */
interface ControlCallbacks {
    fun onButton(button: Button)
    fun onTrigger(trigger: Trigger, held: Boolean)
    fun onOpenApproval()
}

/** Écran du haut : la vue d'ensemble. En mode un seul écran, la zone du bas devient un panneau en bas. */
@Composable
fun OverviewTopScreen(overview: Overview, control: ControlState, singleScreen: Boolean, callbacks: ControlCallbacks) {
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        OverviewHeader(overview)
        SessionGrid(overview, control, compact = singleScreen, modifier = Modifier.weight(1f))
        if (singleScreen) CompactControlPanel(overview, control, callbacks)
    }
}

/** Écran du bas : la première validation en attente, le micro et les boutons utiles. */
@Composable
fun OverviewBottomScreen(overview: Overview, control: ControlState, callbacks: ControlCallbacks) {
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        overview.firstPendingApproval?.let {
            ApprovalBanner(
                pending = it,
                onOpen = callbacks::onOpenApproval,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp).fillMaxWidth(),
            )
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
        ButtonBar(callbacks, Modifier.fillMaxWidth().height(50.dp).topDivider().padding(horizontal = 16.dp))
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
        val count = overview.sessions.size
        BasicText(
            pluralStringResource(R.plurals.overview_session_count, count, count),
            style = sans(13.sp, color = Palette.textMuted),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = overview.agentVersions.entries
                .sortedBy { it.key.ordinal }
                .joinToString(" · ") { (agent, version) -> "${agent.name.lowercase()} $version" },
            style = mono(11.sp, color = Palette.textSubtle),
        )
    }
}

@Composable
private fun SessionGrid(overview: Overview, control: ControlState, compact: Boolean, modifier: Modifier) {
    val sessions = overview.sessions
    val focus = control.focusIn(sessions.size.coerceAtMost(OverviewGrid.VISIBLE_CARDS))
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(OverviewGrid.ROWS) { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(OverviewGrid.COLUMNS) { column ->
                    val index = row * OverviewGrid.COLUMNS + column
                    val session = sessions.getOrNull(index)
                    if (session == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        SessionCard(
                            session = session,
                            focused = index == focus,
                            compact = compact,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

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
        ButtonBar(callbacks, Modifier)
    }
}

@Composable
private fun ButtonBar(callbacks: ControlCallbacks, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BarAction("A", stringResource(R.string.action_open)) { callbacks.onButton(Button.A) }
        BarAction("X", stringResource(R.string.action_new_session)) { callbacks.onButton(Button.X) }
        BarAction("Y", stringResource(R.string.action_speak_summary)) { callbacks.onButton(Button.Y) }
    }
}

@Composable
private fun BarAction(button: String, label: String, onClick: () -> Unit) {
    ButtonChip(
        button = button,
        label = label,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
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

private fun micTitleColor(control: ControlState) = if (control.heldTriggers.isEmpty()) Palette.text else Palette.mic

private fun Modifier.topDivider() = drawBehind {
    drawLine(Palette.divider, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
}

private fun Modifier.bottomDivider() = drawBehind {
    val y = size.height - 0.5f
    drawLine(Palette.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

