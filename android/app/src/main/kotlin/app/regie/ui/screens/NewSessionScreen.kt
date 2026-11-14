package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*

@Composable
fun NewSessionScreen(state: NewSessionForm, bottom: Boolean, single: Boolean, onButton: (Button) -> Unit,
    onHeld: (Boolean) -> Unit, onField: (NewSessionField) -> Unit, onInstruction: (String) -> Unit, onBranch: (String) -> Unit) {
    val list = rememberLazyListState()
    val held by rememberUpdatedState(onHeld)
    val editable by rememberUpdatedState(state.editable)
    LaunchedEffect(state.field) {
        val index = state.fields.indexOf(state.field) + 1
        if (index !in list.layoutInfo.visibleItemsInfo.filter { it.offset + it.size <= list.layoutInfo.viewportEndOffset }.map { it.index }) list.scrollToItem(index)
    }
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        Row(Modifier.fillMaxWidth().bottomDivider().padding(horizontal = 20.dp, vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("Régie › Nouvelle session", style = sans(13.sp, 600))
            BasicText(if (state.heard.isEmpty()) "fiche vierge · X" else "ordre vocal · R2", style = sans(12.sp, color = Palette.textSubtle))
        }
        if (!bottom) LazyColumn(Modifier.weight(1f).padding(horizontal = 28.dp), state = list,
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.heard.isNotEmpty()) BasicText("« ${state.heard} »", style = sans(14.sp, color = Palette.textMuted, lineHeight = 20.sp), maxLines = 2)
                    BasicText("Ce que j’ai compris", style = sans(12.sp, color = Palette.textSubtle))
                }
            }
            items(state.fields) { field ->
                Row(Modifier.fillMaxWidth().focusOutline(state.field == field, 10.dp)
                    .background(Palette.surface, RoundedCornerShape(10.dp)).clickable(enabled = state.editable) { onField(field) }
                    .heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BasicText(field.label, style = sans(12.5.sp, color = Palette.textSubtle), modifier = Modifier.width(76.dp))
                    Column(Modifier.weight(1f)) {
                        when (field) {
                            NewSessionField.AGENT -> Segments(listOf("Claude", "Codex"), state.agent?.let { if (it == Agent.CLAUDE) "Claude" else "Codex" })
                            NewSessionField.PROJECT -> {
                                BasicText(state.project ?: "À choisir", style = sans(14.sp, 600))
                                state.project?.let { BasicText("~/projects/$it", style = mono(11.sp, color = Palette.textSubtle)) }
                            }
                            NewSessionField.BRANCH -> BasicText(state.branchLabel, style = mono(13.sp))
                            NewSessionField.BASE -> BasicText(state.base.ifEmpty { "À choisir" }, style = mono(13.sp))
                            NewSessionField.VALIDATION -> Segments(listOf("Plan d’abord", "Modifs auto", "Tout valider"), state.modeLabel)
                            NewSessionField.INSTRUCTION -> BasicText(if (state.capturing || state.finalizing) state.partial.ifEmpty { "…" } else state.instruction.ifEmpty { "Aucune · L2 pour dicter" }, style = sans(14.sp, lineHeight = 20.sp))
                        }
                    }
                    if (state.field == field) BasicText(if (field == NewSessionField.INSTRUCTION) "L2" else "‹ ›", style = sans(12.sp, color = Palette.textMuted))
                }
            }
        }
        if (bottom || single) Column(Modifier.then(if (bottom) Modifier.weight(1f) else Modifier).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(if (single) 8.dp else 12.dp)) {
            if (bottom) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("Vérifie et lance", style = sans(18.sp, 600))
                BasicText("Croix ↑ ↓ pour choisir une ligne, ← → pour la changer. R2 pour redire l’ordre complet.", style = sans(13.sp, color = Palette.textMuted, lineHeight = 18.sp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BarAction("↑", "Précédent") { onButton(Button.UP) }
                    BasicText(state.field.label, style = sans(13.sp, 600))
                    BarAction("↓", "Suivant") { onButton(Button.DOWN) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BarAction("←", "Changer") { onButton(Button.LEFT) }
                    BarAction("→", "Changer") { onButton(Button.RIGHT) }
                }
                if (state.field == NewSessionField.BRANCH && state.branchChoice == BranchChoice.NEW) {
                    BasicText("Nom facultatif · sinon l’agent le choisit", style = sans(12.sp, color = Palette.textMuted))
                    BasicTextField(state.branch, onBranch, enabled = state.editable, singleLine = true, textStyle = mono(14.sp),
                        modifier = Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(8.dp)).padding(12.dp).testTag("branch-name"))
                }
                if (state.field == NewSessionField.INSTRUCTION && !state.capturing && !state.finalizing) BasicTextField(state.instruction, onInstruction,
                    enabled = state.editable, textStyle = sans(14.sp), maxLines = 3,
                    modifier = Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(8.dp)).padding(10.dp).testTag("instruction"))
            }
            if (state.capturing || state.finalizing) BasicText(
                if (state.capturing) "L2 · ${(state.level * 100).toInt()} % · ${state.partial.ifBlank { "Écoute…" }}" else "Transcription…",
                style = sans(13.sp, color = Palette.mic), maxLines = 2)
            BasicText(if (state.loading) "Chargement des projets…" else state.notice.ifEmpty { state.problem ?: "Rien ne démarre avant ton A." }, style = sans(12.sp, color = Palette.textMuted), maxLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).background(if (state.canLaunch) Palette.text else Palette.textSubtle, RoundedCornerShape(12.dp))
                    .clickable(enabled = state.canLaunch) { onButton(Button.A) }.padding(horizontal = 16.dp, vertical = if (single) 12.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("A", style = sans(12.sp, 600, Palette.text), modifier = Modifier.background(Palette.background, RoundedCornerShape(20.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
                    BasicText(if (state.sending) "Lancement…" else "Lancer la session", style = sans(if (single) 14.sp else 17.sp, 600, Palette.background))
                }
                if (single) BarAction("B", "Annuler") { onButton(Button.B) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ButtonChip("L2", "Redicter la consigne", modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (editable) {
                            held(true)
                            try { tryAwaitRelease() } finally { held(false) }
                        }
                    })
                })
                if (!single && !state.sending) BarAction("B", "Annuler") { onButton(Button.B) }
            }
        }
    }
}

@Composable
private fun Segments(values: List<String>, selected: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        values.forEach { value ->
            BasicText(value, style = sans(12.5.sp, if (value == selected) 600 else 400, if (value == selected) Palette.background else Palette.textMuted),
                modifier = Modifier.background(if (value == selected) Palette.text else Palette.background, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}
