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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*

@Composable
fun SessionSettingsScreen(state: SessionSettingsReview, onButton: (Button) -> Unit, onSelect: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(state.selected) { list.animateScrollToItem(state.selected) }
    Column(Modifier.fillMaxSize().background(Palette.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Options · ${state.session?.project.orEmpty()}", style = sans(18.sp, 600))
        BasicText("En cours : ${approvalModeLabel(state.session?.approvalMode.orEmpty())}", style = sans(13.sp, color = Palette.textMuted))
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.rows) { index, row ->
                val value = if (index == state.selected) state.value else row.current
                Row(Modifier.fillMaxWidth().focusOutline(index == state.selected, 10.dp)
                    .background(Palette.surface, RoundedCornerShape(10.dp)).clickable(enabled = !state.busy) { onSelect(index) }
                    .heightIn(min = 44.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(row.label, style = sans(13.sp, color = Palette.textMuted), modifier = Modifier.width(105.dp))
                    BasicText(row.choices.firstOrNull { it.value == value }?.name ?: value ?: if (row.id == "resume") "A pour reprendre" else "Non communiqué", style = sans(14.sp, 600), modifier = Modifier.weight(1f))
                }
            }
        }
        if (state.session?.configOptions.isNullOrEmpty()) BasicText("Modèle et réflexion : non exposés. Reprends une ancienne session pour les actualiser.", style = sans(12.sp, color = Palette.textMuted))
        if (state.row?.id == "approvalMode") BasicText(approvalModeHelp(state.value.orEmpty()), style = sans(12.sp, color = Palette.textMuted))
        BasicText(state.notice.ifEmpty { "↑ ↓ choisir · ← → modifier · A appliquer" }, style = sans(12.sp, color = Palette.textMuted), maxLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BarAction("↑", "Précédent") { onButton(Button.UP) }
            BarAction("↓", "Suivant") { onButton(Button.DOWN) }
            BarAction("←", "Changer") { onButton(Button.LEFT) }
            BarAction("→", "Changer") { onButton(Button.RIGHT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(if (state.busy) "Application…" else "A · Appliquer", style = sans(15.sp, 600, if (state.canApply) Palette.text else Palette.textSubtle),
                modifier = Modifier.clickable(enabled = state.canApply) { onButton(Button.A) }.padding(12.dp))
            BasicText("B · Fermer", style = sans(14.sp), modifier = Modifier.clickable(enabled = !state.busy) { onButton(Button.B) }.padding(12.dp))
        }
    }
}
