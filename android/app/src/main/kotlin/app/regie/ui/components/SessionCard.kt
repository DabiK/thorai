package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.R
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Progress
import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import app.regie.ui.formatAge
import app.regie.ui.formatDiffStat
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans

private val CardShape = RoundedCornerShape(14.dp)

/**
 * Carte d'une session dans la vue d'ensemble. Chaque bloc n'apparaît que si la
 * session expose l'information. [compact] sert au mode un seul écran.
 */
@Composable
fun SessionCard(session: Session, focused: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val awaiting = session.status == SessionStatus.AWAITING_APPROVAL
    Column(
        modifier = modifier
            .focusOutline(focused, cornerRadius = 14.dp)
            .clip(CardShape)
            .background(Palette.surface)
            .then(if (awaiting) Modifier.border(1.dp, Palette.waiting.copy(alpha = 0.5f), CardShape) else Modifier)
            .padding(horizontal = 16.dp, vertical = if (compact) 11.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp),
    ) {
        CardHeader(session)
        BasicText(
            text = session.task,
            style = sans(15.sp, weight = 600, lineHeight = 19.5.sp),
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        CardBody(session)
        Spacer(Modifier.weight(1f))
        CardFooter(session)
    }
}

@Composable
private fun CardHeader(session: Session) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgentTag(session.agent)
        BasicText(session.project, style = sans(13.sp, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // La branche prend la place libre et s'abrège seule, la pastille reste calée à droite.
        Box(Modifier.weight(1f)) {
            session.branch?.let {
                BasicText(it, style = mono(11.sp, color = Palette.textSubtle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        StatusPill(session.status)
    }
}

@Composable
private fun CardBody(session: Session) {
    val muted = sans(12.sp, color = Palette.textMuted, lineHeight = 17.4.sp)
    val progress = session.progress
    if (session.status == SessionStatus.RUNNING && progress != null) {
        StepBar(progress)
        BasicText(
            text = progress.currentStep
                ?.let { stringResource(R.string.card_step_with_label, progress.step, progress.total, it) }
                ?: stringResource(R.string.card_step, progress.step, progress.total),
            style = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    session.pendingApproval?.let { approval ->
        BasicText(
            text = stringResource(
                when (approval.kind) {
                    ApprovalKind.COMMAND -> R.string.card_wants_command
                    ApprovalKind.FILE_CHANGE -> R.string.card_wants_file_change
                },
            ),
            style = muted,
        )
        BasicText(
            text = approval.subject,
            style = mono(12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.background, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
    session.question?.let {
        BasicText(stringResource(R.string.card_question, it), style = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    session.summary?.let {
        BasicText(it, style = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Barre d'étapes : faites en bleu, en cours en bleu sourd, à venir en gris. */
@Composable
private fun StepBar(progress: Progress) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(progress.total) { index ->
            val step = index + 1
            val color = when {
                step < progress.step -> Palette.running
                step == progress.step -> Palette.stepCurrent
                else -> Palette.stepPending
            }
            Box(Modifier.weight(1f).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun CardFooter(session: Session) {
    val subtle = mono(11.5.sp, color = Palette.textSubtle)
    val lastAction = session.lastAction
    val diffStat = session.diffStat
    val progress = session.progress
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            lastAction != null -> {
                BasicText("›", style = subtle)
                BasicText(lastAction.command, style = mono(11.5.sp, color = Palette.footerText), maxLines = 1)
            }
            diffStat != null -> BasicText(formatDiffStat(diffStat), style = subtle)
            progress != null -> BasicText(stringResource(R.string.card_step_short, progress.step, progress.total), style = subtle)
        }
        // Le résultat de la dernière action prend la place libre ; l'ancienneté reste calée à droite.
        Box(Modifier.weight(1f)) {
            lastAction?.outcome?.let { BasicText(it, style = subtle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        BasicText(formatAge(session.sinceLastActivity), style = subtle)
    }
}
