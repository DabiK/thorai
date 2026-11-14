package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.domain.usecase.DiffReview
import app.regie.domain.usecase.DiffExplanation
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DiffScreen(review: DiffReview, bottom: Boolean, single: Boolean, velocity: StateFlow<Float>,
    onButton: (Button) -> Unit, onFile: (Int) -> Unit, onHeld: (Boolean) -> Unit,
    steps: Flow<Int> = emptyFlow()) {
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        if (!bottom) {
            Row(Modifier.fillMaxWidth().height(44.dp).bottomDivider().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("Régie › ${review.project} › Diff", style = sans(13.sp, color = Palette.textMuted))
                BasicText(review.file?.path.orEmpty(), style = mono(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Counts(review.file?.changes)
                BasicText(if (review.files.isEmpty()) "0 / 0" else "${review.fileIndex + 1} / ${review.files.size}", style = mono(11.sp, color = Palette.textSubtle))
            }
            Box(Modifier.weight(1f)) {
                val explanation = review.explanation
                if (explanation != null) Explanation(explanation, velocity, steps)
                else DiffLines(review, velocity)
            }
        }
        if (bottom || single) {
            Column(if (bottom) Modifier.weight(1f).padding(16.dp) else Modifier.fillMaxWidth().topDivider().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (review.explanation == null) {
                    if (bottom) BasicText("${review.files.size} fichiers · éditions suivies", style = sans(13.sp, color = Palette.textMuted))
                    val list = rememberLazyListState()
                    LaunchedEffect(review.fileIndex) { if (review.files.isNotEmpty()) list.scrollToItem(review.fileIndex) }
                    if (bottom) LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(review.files.size) { index -> FileButton(review, index, Modifier.fillMaxWidth()) { onFile(index) } }
                    } else LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(review.files.size) { index -> FileButton(review, index, Modifier.widthIn(min = 180.dp, max = 320.dp)) { onFile(index) } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (review.selected != null) MicButton(false, { onHeld(true) }, { onHeld(false) }, size = 48.dp, halo = 3.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText(review.selected?.let { "Ligne ${it.line.number}" + (if (it.line.kind == DiffKind.REMOVED) " supprimée" else "") +
                                (if (it.block.fileCoordinates) "" else " de l’extrait") } ?: "Aucune ligne à commenter", style = sans(13.sp))
                            BasicText("L2 : commenter · ↑↓ : ligne · ←→ : fichier", style = sans(11.sp, color = Palette.textMuted))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (review.selected != null) BarAction("Y", "Explique-moi ce bloc") { onButton(Button.Y) }
                        BarAction("B", "Retour") { onButton(Button.B) }
                    }
                    if (bottom) BasicText("Stick droit : défiler · L1/R1 : sessions / validation", style = sans(12.sp, color = Palette.textSubtle))
                } else {
                    val explanation = requireNotNull(review.explanation)
                    BasicText(explanation.label, style = mono(12.sp))
                    BasicText(explanationStatus(explanation), style = sans(13.sp, color = Palette.textMuted))
                    if (bottom) Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (!explanation.sending && (explanation.delivery == null || explanation.delivery?.canRetry == true))
                            BarAction("A", "Demander") { onButton(Button.A) }
                        BarAction("B", "Revenir au diff") { onButton(Button.B) }
                    }
                }
            }
        }
    }
}

@Composable private fun Counts(changes: LineChanges?) {
    if (changes == null) BasicText("+/− ?", style = mono(12.sp, color = Palette.textSubtle))
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("+${changes.added}", style = mono(12.sp, color = Palette.added))
        BasicText("−${changes.removed}", style = mono(12.sp, color = Palette.removed))
    }
}

@Composable private fun FileButton(review: DiffReview, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val file = review.files[index]
    Row(modifier.heightIn(min = 48.dp).background(Palette.surface, RoundedCornerShape(12.dp))
        .then(if (index == review.fileIndex) Modifier.border(1.5.dp, Palette.text, RoundedCornerShape(12.dp)) else Modifier)
        .tappable(onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(file.path, style = mono(12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Counts(file.changes)
    }
}

@Composable private fun DiffLines(review: DiffReview, velocity: StateFlow<Float>) {
    val rows = review.rows
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            BasicText(when {
                review.loading -> "Chargement des éditions…"
                review.file != null -> "Diff non communiqué par l’agent pour ce fichier."
                else -> "Aucune édition suivie. Les fichiers écrits par une commande n’y figurent pas."
            }, style = sans(15.sp, color = Palette.textMuted))
        }
        return
    }
    val list = rememberLazyListState()
    val speed = with(LocalDensity.current) { 500.dp.toPx() }
    LaunchedEffect(review.file?.path, review.lineIndex) {
        val index = review.lineIndex
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == index } || visible.lastOrNull()?.index == index) list.scrollToItem(index)
    }
    LaunchedEffect(velocity, list) {
        velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) { val now = withFrameNanos { it }; list.scrollBy(value * speed * (now - last) / 1e9f); last = now }
        }
    }
    LazyColumn(state = list, contentPadding = PaddingValues(vertical = 10.dp), modifier = Modifier.fillMaxSize()) {
        items(rows.size) { index ->
            val row = rows[index]
            val line = row.line
            if (row.lineIndex == 0) BasicText("Édition ${review.file!!.blocks.indexOf(row.block) + 1} · " +
                (if (row.block.fileCoordinates) "lignes ancien / nouveau" else "extrait · position dans le fichier non communiquée"),
                style = mono(11.sp, color = Palette.textSubtle), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            val color = when (line.kind) { DiffKind.ADDED -> Palette.added; DiffKind.REMOVED -> Palette.removed; DiffKind.CONTEXT -> Palette.agentText }
            Row(Modifier.fillMaxWidth().background(if (line.kind == DiffKind.CONTEXT) Palette.background else color.copy(alpha = 0.10f))
                .then(if (index == review.lineIndex) Modifier.border(1.dp, Palette.text) else Modifier).padding(horizontal = 12.dp, vertical = 2.dp)) {
                BasicText("${line.oldLine ?: "·"}/${line.newLine ?: "·"}", style = mono(11.sp, color = Palette.textSubtle), modifier = Modifier.width(78.dp))
                BasicText(when (line.kind) { DiffKind.ADDED -> "+"; DiffKind.REMOVED -> "−"; DiffKind.CONTEXT -> " " }, style = mono(12.sp, color = color), modifier = Modifier.width(18.dp))
                BasicText(line.text.ifEmpty { " " }, style = mono(12.5.sp, color = color, lineHeight = 20.sp), modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun explanationStatus(value: DiffExplanation): String = when {
    value.sending -> "Demande en cours…"
    value.delivery == null -> "Relis le bloc. A demande une explication écrite à l’agent."
    value.delivery?.canRetry == true -> "Demande non livrée. A pour réessayer."
    value.delivery == Delivery.UNKNOWN -> "Envoi non confirmé. Vérifie le fil avant toute nouvelle demande."
    value.ended && value.reply.isBlank() -> "Tour terminé sans explication. Consulte le fil."
    value.reply.isNotBlank() -> "Réponse de l’agent · écrite uniquement"
    value.delivery == Delivery.QUEUED -> "Demande en attente de la fin du tour en cours."
    else -> "L’agent prépare son explication…"
}

@Composable private fun Explanation(explanation: DiffExplanation, velocity: StateFlow<Float>, steps: Flow<Int>) {
    val list = rememberLazyListState()
    val step = with(LocalDensity.current) { 120.dp.toPx() }
    LaunchedEffect(list, steps) { steps.collect { list.scrollBy(it * step) } }
    LaunchedEffect(list, velocity) { velocity.collectLatest { value ->
        if (value == 0f) return@collectLatest
        var last = withFrameNanos { it }
        while (true) { val now = withFrameNanos { it }; list.scrollBy(value * step * 4 * (now - last) / 1e9f); last = now }
    } }
    val text = if (explanation.delivery == null && !explanation.sending) explanation.prompt
        else explanation.reply.ifBlank { explanationStatus(explanation) }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { BasicText("Explique-moi ce bloc", style = sans(18.sp, weight = 600), modifier = Modifier.padding(bottom = 12.dp)) }
        items(text.lines().size) { index -> BasicText(text.lines()[index].ifEmpty { " " }, style = sans(15.sp, lineHeight = 22.sp)) }
    }
}
