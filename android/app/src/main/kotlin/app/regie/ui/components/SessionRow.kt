package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.R
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import app.regie.ui.formatAge
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans
import kotlin.time.Instant

private val RowShape = RoundedCornerShape(10.dp)

/**
 * Une ligne de la vue d'ensemble : agent, projet et branche, titre, état et étape, demande,
 * erreur ou dernière action, ancienneté. Ce que le pont n'expose pas reste absent, jamais inventé.
 * [now] fait avancer l'ancienneté ; [compact] sert au mode un seul écran.
 */
@Composable
fun SessionRow(session: Session, now: Instant, focused: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusOutline(focused, cornerRadius = 10.dp)
            .background(if (focused) Palette.surface else Color.Transparent, RowShape)
            .then(if (focused) Modifier else Modifier.bottomDivider())
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(66.dp)) { AgentTag(session.agent) }
        Column(Modifier.weight(0.44f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    session.project,
                    style = sans(14.sp, weight = 600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                session.branch?.let {
                    BasicText(it, style = mono(11.sp, color = Palette.textSubtle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            session.title?.let {
                BasicText(it, style = sans(12.5.sp, color = Palette.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(0.56f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowStatus(session)
            RowDetail(session)
        }
        BasicText(
            formatAge(now - session.lastActivityAt),
            style = mono(11.5.sp, color = Palette.textSubtle).copy(textAlign = TextAlign.End),
            maxLines = 1,
            modifier = Modifier.width(46.dp),
        )
    }
}

/** « En cours · étape 2 sur 5 » : l'état en couleur, l'étape si l'agent a un plan. */
@Composable
private fun RowStatus(session: Session) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(session.status)
        val progress = session.progress
        val step = progress?.let { stringResource(R.string.card_step_short, it.step, it.total) }
        BasicText(
            buildAnnotatedString {
                append(statusLabel(session.status))
                step?.let { withStyle(SpanStyle(color = Palette.textSubtle)) { append(" · $it") } }
            },
            style = sans(12.5.sp, color = statusColor(session.status)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La demande en attente, sinon l'erreur, sinon la dernière action et où elle en est. */
@Composable
private fun RowDetail(session: Session) {
    val approval = session.pendingApproval
    val error = session.lastError.takeIf { session.status == SessionStatus.FAILED }
    val lastAction = session.lastAction
    val subtle = mono(11.5.sp, color = Palette.textSubtle)
    when {
        approval != null -> BasicText(
            buildAnnotatedString {
                append(
                    stringResource(
                        when (approval.kind) {
                            ApprovalKind.COMMAND -> R.string.card_wants_command
                            ApprovalKind.FILE_CHANGE -> R.string.card_wants_file_change
                            ApprovalKind.OTHER -> R.string.card_wants_other
                        },
                    ),
                )
                approval.subject?.let { withStyle(SpanStyle(color = Palette.text)) { append(" $it") } }
            },
            style = mono(11.5.sp, color = Palette.waiting),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        error != null -> BasicText(error, style = mono(11.5.sp, color = Palette.failed), maxLines = 1, overflow = TextOverflow.Ellipsis)
        lastAction != null -> BasicText(
            buildAnnotatedString {
                append("› ")
                withStyle(SpanStyle(color = Palette.footerText)) { append(lastAction.description) }
                lastAction.status?.let {
                    withStyle(SpanStyle(color = if (it == ActionStatus.FAILED) Palette.failed else Palette.textSubtle)) {
                        append(" · ${actionStatusLabel(it)}")
                    }
                }
            },
            style = subtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
