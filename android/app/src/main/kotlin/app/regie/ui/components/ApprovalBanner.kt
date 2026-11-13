package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import app.regie.domain.model.PendingApproval
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.sans

private val BannerShape = RoundedCornerShape(12.dp)

/** Bandeau de la première validation en attente. R1 ou un toucher l'ouvre. */
@Composable
fun ApprovalBanner(pending: PendingApproval, onOpen: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier
            .clip(BannerShape)
            .background(Palette.bannerBackground)
            .border(1.dp, Palette.waiting.copy(alpha = 0.45f), BannerShape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).background(Palette.waiting, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                text = stringResource(R.string.banner_awaiting, agentName(pending.session.agent)),
                style = sans(13.sp, weight = 600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = "${pending.session.project} · ${pending.approval.subject}",
                style = mono(11.sp, color = Palette.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyChip("R1")
            BasicText(stringResource(R.string.banner_see), style = sans(12.5.sp))
        }
    }
}
