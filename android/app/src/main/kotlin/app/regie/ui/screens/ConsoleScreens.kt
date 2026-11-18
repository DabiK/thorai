package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import app.regie.domain.model.ApprovalDecision
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.R
import androidx.compose.ui.res.stringResource
import app.regie.domain.model.FloatingCard
import app.regie.domain.model.FloatingKind
import app.regie.input.Button
import app.regie.overlay.OverlayContent
import app.regie.ui.components.AgentTag
import app.regie.ui.components.ButtonChip
import app.regie.ui.components.StatusDot
import app.regie.ui.components.statusColor
import app.regie.ui.components.statusLabel
import app.regie.ui.theme.Palette
import app.regie.ui.theme.sans

/**
 * Ce que Régie pose par-dessus l'écran du haut quand une autre appli l'occupe (ticket 25). Rien ne s'y
 * touche : l'écran du haut n'est pas tactile, la manette passe par le service d'accessibilité.
 */
@Composable
fun ConsoleOverlayScreen(content: OverlayContent, onButton: (Button) -> Unit, transcript: ThreadScroll, command: ThreadScroll) {
    when (content) {
        is OverlayContent.Dictation -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content.replyingTo?.let {
                BasicText("${stringResource(R.string.card_replying_to)}\u00A0: $it", style = sans(13.sp, color = Palette.textMuted), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(620.dp).background(Palette.surface, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
            }
            Panel(Modifier.width(620.dp).height(270.dp)) {
                DictationBottomScreen(content.review, onButton, {}, {}, compact = true, session = content.session, transcript = transcript)
            }
        }
        is OverlayContent.Command -> Panel(Modifier.width(620.dp).height(300.dp)) {
            VoiceCommandScreen(content.review, bottom = true, single = false, onButton, {}, {}, command)
        }
        is OverlayContent.Card -> FloatingCardView(content.card, content.keys)
        is OverlayContent.Bubble -> Row(
            Modifier.widthIn(max = 460.dp).background(Palette.surface, RoundedCornerShape(18.dp))
                .border(1.dp, Palette.divider, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("console-bubble"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(content.session.status)
            BasicText(statusLabel(content.session.status), style = sans(12.sp, color = statusColor(content.session.status)), maxLines = 1)
            content.lastSentence?.let {
                BasicText(it, style = sans(12.sp, color = Palette.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Palette.divider, RoundedCornerShape(16.dp)).background(Palette.background)) {
        content()
    }
}

/**
 * Le message volant : l'agent, son projet, sa phrase, et les touches utiles. Une question ou une
 * validation se signale en ambre. Les touches n'apparaissent qu'une fois que la carte les prend
 * ([keys]) ; une validation ne propose que les choix que l'agent offre.
 */
@Composable
fun FloatingCardView(card: FloatingCard, keys: Boolean = true) {
    Column(
        Modifier.width(460.dp).background(Palette.surface, RoundedCornerShape(16.dp))
            .border(1.dp, if (card.kind == FloatingKind.MESSAGE) Palette.divider else Palette.waiting.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp).testTag("floating-card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentTag(card.agent)
            BasicText(card.project, style = sans(13.sp, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            when (card.kind) {
                FloatingKind.QUESTION -> BasicText(stringResource(R.string.card_awaiting_answer), style = sans(12.sp, color = Palette.waiting))
                FloatingKind.APPROVAL -> BasicText(stringResource(R.string.status_awaiting_approval), style = sans(12.sp, color = Palette.waiting))
                FloatingKind.MESSAGE -> Unit
            }
        }
        if (card.text.isNotEmpty()) BasicText(card.text,
            style = if (card.kind == FloatingKind.APPROVAL) app.regie.ui.theme.mono(14.sp, lineHeight = 20.sp) else sans(15.sp, lineHeight = 21.sp),
            maxLines = 6, overflow = TextOverflow.Ellipsis)
        val approval = card.approval
        Row(Modifier.alpha(if (keys) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (approval?.optionFor(ApprovalDecision.ONCE) != null) ButtonChip("A", stringResource(R.string.card_allow_once))
            ButtonChip("B", stringResource(R.string.card_close))
            if (approval?.optionFor(ApprovalDecision.REJECT) != null) ButtonChip("L2", stringResource(R.string.card_refuse_dictating))
            else if (approval == null) ButtonChip("L2", stringResource(R.string.card_answer))
        }
    }
}
