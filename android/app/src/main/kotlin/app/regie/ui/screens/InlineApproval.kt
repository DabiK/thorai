package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.domain.usecase.ApprovalReview
import app.regie.ui.components.tappable
import app.regie.ui.components.BarAction
import app.regie.ui.theme.*

@Composable
fun InlineApproval(review: ApprovalReview, onDecision: (ApprovalId, ApprovalDecision) -> Unit, onNext: () -> Unit, details: Boolean = true, shortcutsEnabled: Boolean = true, onDismiss: () -> Unit = {}) {
    val request = review.request ?: return
    Column(Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(12.dp))
        .border(1.dp, Palette.waiting.copy(alpha = .4f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(approvalNotice(review, shortcutsEnabled), style = sans(12.sp, 600, Palette.waiting))
        if (details && (review.pending || review.sending)) {
            BasicText(request.subject ?: "Action non précisée", style = mono(13.sp, lineHeight = 18.sp))
            request.workingDirectory?.let { BasicText(it, style = mono(10.sp, color = Palette.textSubtle)) }
            BasicText("Pourquoi", style = sans(11.sp, color = Palette.textMuted))
            BasicText(request.reason ?: "Justification non communiquée.", style = sans(13.sp, lineHeight = 18.sp))
            val otherPaths = request.paths.filterNot { path -> request.changes.any { it.path == path } }
            if (otherPaths.isNotEmpty()) BasicText(otherPaths.joinToString(" · "), style = mono(11.sp, color = Palette.textMuted))
            request.plan?.let { BasicText(it, style = sans(13.sp)) }
            request.changes.forEach { change ->
                BasicText(change.path, style = mono(11.sp))
                change.before?.let { BasicText("− $it", style = mono(12.sp, color = Palette.failed)) }
                BasicText("+ ${change.after}", style = mono(12.sp, color = Palette.done))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecisionButton("A".takeIf { shortcutsEnabled }, "Autoriser une fois", review.canAnswer(ApprovalDecision.ONCE), Modifier.weight(1f)) { onDecision(request.id, ApprovalDecision.ONCE) }
            DecisionButton("B".takeIf { shortcutsEnabled }, "Refuser", review.canAnswer(ApprovalDecision.REJECT), Modifier.weight(1f)) { onDecision(request.id, ApprovalDecision.REJECT) }
            if (request.optionFor(ApprovalDecision.SESSION) != null) DecisionButton("X".takeIf { shortcutsEnabled }, "Autoriser pour la session", review.canAnswer(ApprovalDecision.SESSION), Modifier.weight(1f)) { onDecision(request.id, ApprovalDecision.SESSION) }
        }
        if (!review.pending && !review.sending && review.queue.isEmpty()) InlineApprovalAction("←", "Fermer la validation", shortcutsEnabled, onDismiss)
        if (review.queue.any { it.id != request.id }) InlineApprovalAction("R1", "Voir la suivante (${review.queue.count { it.id != request.id }})", shortcutsEnabled, onNext)
    }
}

/** Options own the gamepad; the card keeps explicit touch actions. */
@Composable
private fun InlineApprovalAction(key: String, label: String, shortcutsEnabled: Boolean, onClick: () -> Unit) {
    if (shortcutsEnabled) BarAction(key, label, onClick)
    else BasicText(label, style = sans(13.sp), modifier = Modifier.tappable(onClick).padding(8.dp))
}
