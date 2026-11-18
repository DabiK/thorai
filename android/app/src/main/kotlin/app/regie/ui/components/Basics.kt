package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.regie.R
import app.regie.domain.model.Agent
import app.regie.domain.model.SessionStatus
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans
import java.util.Locale

/** Contour de focus de la maquette : trait de 2 dp, à 2 dp du bord, sans changer la mise en page. */
fun Modifier.focusOutline(visible: Boolean, cornerRadius: Dp): Modifier =
    if (!visible) {
        this
    } else {
        drawWithContent {
            drawContent()
            val stroke = 2.dp.toPx()
            val inset = 2.dp.toPx() + stroke / 2
            drawRoundRect(
                color = Palette.text,
                topLeft = Offset(-inset, -inset),
                size = Size(size.width + 2 * inset, size.height + 2 * inset),
                cornerRadius = CornerRadius(cornerRadius.toPx() + inset),
                style = Stroke(stroke),
            )
        }
    }

@Composable
fun agentName(agent: Agent): String = agent.label

/** Étiquette d'agent : « CLAUDE », « CODEX ». */
@Composable
fun AgentTag(agent: Agent) {
    BasicText(
        text = agentName(agent).uppercase(Locale.ROOT),
        style = mono(10.5.sp, weight = 500, color = Palette.tagText, letterSpacing = 0.06.em),
        modifier = Modifier
            .background(Palette.tagBackground, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

fun statusColor(status: SessionStatus): Color = when (status) {
    SessionStatus.WORKING -> Palette.running
    SessionStatus.AWAITING_APPROVAL -> Palette.waiting
    SessionStatus.IDLE -> Palette.done
    SessionStatus.STOPPED -> Palette.textMuted
    SessionStatus.FAILED -> Palette.failed
}

@Composable
fun statusLabel(status: SessionStatus): String = stringResource(
    when (status) {
        SessionStatus.WORKING -> R.string.status_working
        SessionStatus.AWAITING_APPROVAL -> R.string.status_awaiting_approval
        SessionStatus.IDLE -> R.string.status_idle
        SessionStatus.STOPPED -> R.string.status_stopped
        SessionStatus.FAILED -> R.string.status_failed
    },
)

/** Point d'état : plein, creux pour une session arrêtée. */
@Composable
fun StatusDot(status: SessionStatus) {
    val dot = Modifier.size(7.dp)
    val color = statusColor(status)
    Box(if (status == SessionStatus.STOPPED) dot.border(1.5.dp, color, CircleShape) else dot.background(color, CircleShape))
}

/** Pastille d'état : point coloré et libellé. Arrêtée, le point est creux. */
@Composable
fun StatusPill(status: SessionStatus) {
    val color = statusColor(status)
    val label = statusLabel(status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(status)
        BasicText(label, style = sans(12.sp, color = color))
    }
}

/** Puce ronde d'un bouton de la manette (« A », « X », « Y ») suivie de l'action. */
@Composable
fun ButtonChip(button: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        KeyChip(button)
        BasicText(label, style = sans(12.5.sp, color = Palette.footerText), maxLines = 1)
    }
}

/** Une seule puce : ronde pour A/B/X/Y, rectangulaire pour les autres touches. */
@Composable
fun KeyChip(key: String) {
    val round = key in listOf("A", "B", "X", "Y")
    Box(
        Modifier.height(22.dp).widthIn(min = 22.dp)
            .background(Palette.chipBackground, if (round) CircleShape else RoundedCornerShape(6.dp))
            .padding(horizontal = if (round) 0.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(key, style = sans(11.sp, weight = 600), maxLines = 1)
    }
}
