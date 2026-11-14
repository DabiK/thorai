package app.regie.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.domain.usecase.ApprovalReview
import app.regie.input.Button
import app.regie.ui.components.AgentTag
import app.regie.ui.components.BarAction
import app.regie.ui.components.tappable
import app.regie.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ApprovalTopScreen(
    review: ApprovalReview,
    overview: Overview,
    singleScreen: Boolean,
    steps: SharedFlow<Int>,
    velocity: StateFlow<Float>,
    onButton: (Button) -> Unit,
    onSelect: (ApprovalId) -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(review.request?.id) { scroll.scrollTo(0) }
    LaunchedEffect(steps) { steps.collect { scroll.scrollTo(scroll.value + it * 150) } }
    LaunchedEffect(velocity) {
        while (true) { delay(16); if (velocity.value != 0f) scroll.scrollTo(scroll.value + (velocity.value * 20).toInt()) }
    }
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Régie  ›  ", style = sans(13.sp, color = Palette.textMuted))
            overview.sessions.firstOrNull { it.id == review.request?.sessionId }?.let {
                AgentTag(it.agent)
                BasicText(it.project, style = sans(13.sp, 600), modifier = Modifier.padding(start = 10.dp))
            }
            Spacer(Modifier.weight(1f))
            BasicText("${review.queue.size} en attente", style = sans(13.sp, color = Palette.waiting))
        }
        Column(Modifier.weight(1f)) {
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 30.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val request = review.request
                if (request == null) {
                    BasicText("Cette demande n’est plus en attente.", style = sans(16.sp))
                } else {
                    BasicText(when (request.kind) {
                        ApprovalKind.COMMAND -> "Exécuter une commande"
                        ApprovalKind.FILE_CHANGE -> "Modifier un fichier"
                        ApprovalKind.OTHER -> "Autoriser une action"
                    }, style = sans(22.sp, 600))
                    CodeBlock(request.subject ?: "Action non précisée par l’agent")
                    request.workingDirectory?.let { BasicText("dans $it", style = mono(11.5.sp, color = Palette.textSubtle)) }
                    Detail("Pourquoi", request.reason ?: "L’agent n’a pas donné de justification.")
                    Detail("Ce que ça touche", request.paths.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "Cibles non précisées par l’agent.")
                    request.plan?.let { Detail("Plan proposé", it) }
                    request.changes.forEach { change ->
                        Detail("Fichier", change.path)
                        change.before?.let { BasicText("Avant", style = sans(12.sp, color = Palette.textMuted)); CodeBlock(it) }
                        BasicText("Après", style = sans(12.sp, color = Palette.done))
                        CodeBlock(change.after)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(max = 80.dp).verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Aussi en attente", style = sans(12.sp, color = Palette.textMuted))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    review.queue.filter { it.id != review.request?.id }.forEach { request ->
                        BasicText("${overview.sessions.firstOrNull { it.id == request.sessionId }?.project ?: "Session"} · ${request.subject ?: "Action"}",
                            style = mono(11.5.sp), maxLines = 2, modifier = Modifier.tappable { onSelect(request.id) })
                    }
                    if (review.queue.none { it.id != review.request?.id }) BasicText("Aucune autre demande", style = sans(12.sp, color = Palette.textSubtle))
                }
            }
        }

        if (singleScreen) ApprovalBottomScreen(review, onButton, compact = true)
    }
}

@Composable
private fun Detail(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BasicText(title, style = sans(12.sp, 600, Palette.textMuted))
        BasicText(text, style = sans(14.sp, lineHeight = 20.sp))
    }
}

@Composable
private fun CodeBlock(text: String) {
    BasicText(text, style = mono(16.sp, lineHeight = 22.sp), modifier = Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(12.dp)).border(1.dp, Palette.waiting.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(16.dp))
}

@Composable
fun ApprovalBottomScreen(review: ApprovalReview, onButton: (Button) -> Unit, compact: Boolean = false) {
    Column(Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).background(Palette.background).padding(if (compact) 12.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BarAction("L1", "Régie") { onButton(Button.L1) }
            BarAction("R1", "Demande suivante") { onButton(Button.R1) }
        }
        BasicText(approvalNotice(review), style = sans(if (compact) 12.sp else 17.sp, 500, Palette.waiting))
        if (compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecisionButton("A", "Autoriser une fois", review.canAnswer(ApprovalDecision.ONCE), Modifier.weight(1f)) { onButton(Button.A) }
                if (review.request?.optionFor(ApprovalDecision.SESSION) != null) {
                    DecisionButton("X", "Cette session", review.canAnswer(ApprovalDecision.SESSION), Modifier.weight(1f)) { onButton(Button.X) }
                }
                DecisionButton("B", "Refuser", review.canAnswer(ApprovalDecision.REJECT), Modifier.weight(1f)) { onButton(Button.B) }
            }
        } else {
            DecisionButton("A", "Autoriser une fois", review.canAnswer(ApprovalDecision.ONCE), Modifier.fillMaxWidth(), primary = true) { onButton(Button.A) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (review.request?.optionFor(ApprovalDecision.SESSION) != null) {
                    DecisionButton("X", "Toujours dans cette session", review.canAnswer(ApprovalDecision.SESSION), Modifier.weight(1f)) { onButton(Button.X) }
                }
                DecisionButton("B", "Refuser", review.canAnswer(ApprovalDecision.REJECT), Modifier.weight(1f)) { onButton(Button.B) }
            }
        }
        if (!compact) {
            if (review.request?.optionFor(ApprovalDecision.SESSION) == null) {
                BasicText("L’agent ne propose pas d’autorisation limitée à cette session.", style = sans(12.sp, color = Palette.textSubtle))
            }
            Spacer(Modifier.weight(1f))
            BasicText("L2 maintenu : refuser avec une consigne, puis A pour confirmer.", style = sans(13.sp, color = Palette.textMuted))
            BasicText("Une décision concerne uniquement la demande affichée en haut.", style = sans(13.sp, color = Palette.textMuted))
        }
    }
}

@Composable
internal fun DecisionButton(key: String?, label: String, enabled: Boolean, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Row(modifier.height(if (primary) 68.dp else 48.dp).background(if (primary && enabled) Palette.text else Palette.surface, RoundedCornerShape(14.dp)).then(if (enabled) Modifier.tappable(onClick) else Modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (key != null) BasicText(key, style = mono(14.sp, 600, if (primary && enabled) Palette.background else if (enabled) Palette.text else Palette.textSubtle))
        BasicText(label, style = sans(if (primary) 17.sp else 13.sp, weight = if (primary) 600 else 400, color = if (primary && enabled) Palette.background else if (enabled) Palette.text else Palette.textSubtle))
    }
}

internal fun approvalNotice(review: ApprovalReview, shortcutsEnabled: Boolean = true): String = when {
    review.sending -> "Réponse en cours…"
    review.answer?.reply == ApprovalReply.ACCEPTED -> if (shortcutsEnabled && review.queue.any { it.id != review.request?.id }) "Réponse reçue. R1 ouvre la demande suivante." else "Réponse reçue."
    review.answer?.reply == ApprovalReply.UNKNOWN -> "Réponse non confirmée. Vérifie le fil de la session avant de poursuivre."
    review.answer?.reply == ApprovalReply.NOT_SENT -> "Réponse non envoyée. Tu peux réessayer."
    review.answer?.reply == ApprovalReply.UNAVAILABLE || !review.pending -> "Cette demande n’est plus disponible."
    else -> "L’agent attend ta décision"
}
