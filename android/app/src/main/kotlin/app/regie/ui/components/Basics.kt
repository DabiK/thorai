package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun agentName(agent: Agent): String = stringResource(
    when (agent) {
        Agent.CLAUDE -> R.string.agent_claude
        Agent.CODEX -> R.string.agent_codex
    },
)

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

/** Pastille d'état : point coloré et libellé. Arrêtée, le point est creux. */
@Composable
fun StatusPill(status: SessionStatus) {
    val color = statusColor(status)
    val label = stringResource(
        when (status) {
            SessionStatus.WORKING -> R.string.status_working
            SessionStatus.AWAITING_APPROVAL -> R.string.status_awaiting_approval
            SessionStatus.IDLE -> R.string.status_idle
            SessionStatus.STOPPED -> R.string.status_stopped
            SessionStatus.FAILED -> R.string.status_failed
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val dot = Modifier.size(7.dp)
        Box(
            if (status == SessionStatus.STOPPED) {
                dot.border(1.5.dp, color, CircleShape)
            } else {
                dot.background(color, CircleShape)
            },
        )
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
        Box(
            modifier = Modifier.size(20.dp).background(Palette.chipBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(button, style = sans(11.sp, weight = 600))
        }
        BasicText(label, style = sans(12.5.sp, color = Palette.footerText), maxLines = 1)
    }
}

/** Puce rectangulaire d'une touche d'épaule (« R1 »). */
@Composable
fun KeyChip(key: String) {
    BasicText(
        text = key,
        style = sans(10.5.sp, weight = 600),
        modifier = Modifier
            .border(1.dp, Palette.keyBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
