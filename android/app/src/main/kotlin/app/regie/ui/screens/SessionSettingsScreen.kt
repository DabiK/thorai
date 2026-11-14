package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*

@Composable
fun SessionSettingsScreen(state: SessionSettingsReview, onButton: (Button) -> Unit, onSelect: (Int) -> Unit, compact: Boolean = false) {
    val list = rememberLazyListState()
    LaunchedEffect(state.selected) { list.animateScrollToItem(state.selected) }
    Column(Modifier.fillMaxSize().background(Palette.background).padding(if (compact) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
        BasicText("Options · ${state.session?.project.orEmpty()}", style = sans(18.sp, 600))
        if (!compact) BasicText("Mode : ${approvalModeLabel(state.session?.approvalMode.orEmpty())}", style = sans(13.sp, color = Palette.textMuted))
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.rows) { index, row ->
                val value = if (index == state.selected) state.value else state.replacements[row.id] ?: row.current
                Row(Modifier.fillMaxWidth().focusOutline(index == state.selected, 10.dp)
                    .background(Palette.surface, RoundedCornerShape(10.dp)).clickable(enabled = !state.busy) { onSelect(index) }
                    .heightIn(min = if (compact) 36.dp else 44.dp).padding(if (compact) 8.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(row.label, style = sans(13.sp, color = Palette.textMuted), modifier = Modifier.width(105.dp))
                    BasicText(row.choices.firstOrNull { it.value == value }?.name ?: value ?: if (row.id == "resume") "A pour reprendre" else "Non communiqué", style = sans(14.sp, 600), modifier = Modifier.weight(1f))
                }
            }
        }
        if (!compact && state.session?.configOptions.isNullOrEmpty()) BasicText("Modèle et réflexion non communiqués. Reprends la session pour les actualiser.", style = sans(12.sp, color = Palette.textMuted))
        if (state.row?.id == "approvalMode") BasicText(approvalModeHelp(state.value.orEmpty()), style = sans(12.sp, color = Palette.textMuted))
        if (state.replacements.isNotEmpty()) BasicText("${state.replacements.size} choix préparés. Applique-les pour reprendre.", style = sans(12.sp, color = Palette.textMuted))
        if (state.notice.isNotBlank() || state.session?.active == false) BasicText(state.notice.ifEmpty { "Choisis les remplacements, puis applique-les." }, style = sans(12.sp, color = Palette.textMuted), maxLines = if (compact) 2 else 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BarAction("↑", "Choisir avant") { onButton(Button.UP) }
            BarAction("↓", "Choisir après") { onButton(Button.DOWN) }
            BarAction("←", "Changer") { onButton(Button.LEFT) }
            BarAction("→", "Changer") { onButton(Button.RIGHT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ButtonChip("A", if (state.busy) "Application…" else "Appliquer",
                Modifier.alpha(if (state.canApply) 1f else 0.4f).clickable(enabled = state.canApply) { onButton(Button.A) }.padding(6.dp))
            ButtonChip("B", "Fermer",
                Modifier.alpha(if (state.busy) 0.4f else 1f).clickable(enabled = !state.busy) { onButton(Button.B) }.padding(6.dp))
            if (state.canInterrupt) BarAction("Start", "Interrompre") { onButton(Button.START) }
        }
    }
}
