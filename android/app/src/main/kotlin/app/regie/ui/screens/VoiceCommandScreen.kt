package app.regie.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.CommandPhase
import app.regie.domain.model.VoiceCommandReview
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VoiceCommandScreen(state: VoiceCommandReview, bottom: Boolean, single: Boolean,
    onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onSettings: () -> Unit, scrolling: ThreadScroll) {
    val held by rememberUpdatedState(onHeld)
    val scroll = rememberScrollState()
    LaunchedEffect(scrolling) { scrolling.steps.collect { scroll.scrollTo(scroll.value + it * 150) } }
    LaunchedEffect(scrolling.velocity) {
        while (true) { delay(16); if (scrolling.velocity.value != 0f) scroll.scrollTo(scroll.value + (scrolling.velocity.value * 20).toInt()) }
    }
    LaunchedEffect(state.phase) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        Row(Modifier.fillMaxWidth().bottomDivider().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("ThorAI › Ordre vocal · R2", style = sans(13.sp, 600, Palette.mic))
            if (bottom || single) BasicText("Régler la voix", style = sans(12.sp, color = Palette.textMuted), modifier = Modifier.tappable(onSettings))
        }
        if (!bottom) Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText("« ${state.text.ifEmpty { "…" }} »", style = sans(25.sp, lineHeight = 35.sp))
            BasicText(if (state.phase == CommandPhase.REVIEW) "Action proposée" else "Ordre vocal", style = sans(12.sp, color = Palette.textSubtle))
            BasicText(state.notice, style = sans(17.sp, lineHeight = 24.sp))
        }
        if (bottom || single) {
            Column(Modifier.then(if (bottom) Modifier.weight(1f) else Modifier).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bottom) Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(state.notice, style = sans(18.sp, 600, lineHeight = 25.sp))
                }
                if (state.phase == CommandPhase.RECORDING || state.phase == CommandPhase.FINALIZING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        MicButton(state.phase == CommandPhase.RECORDING, { onHeld(true) }, { onHeld(false) }, size = 48.dp, halo = 4.dp)
                        BasicText("${state.durationMs / 1000} s · niveau ${(state.level * 100).toInt()} %\nRelâche R2 quand tu as fini", style = mono(12.sp, color = Palette.textMuted))
                    }
                }
                Row(Modifier.fillMaxWidth().topDivider().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (state.canConfirm) BarAction("A", "Confirmer") { onButton(Button.A) }
                    if (state.phase != CommandPhase.RUNNING) BarAction("B", if (state.phase == CommandPhase.DONE) "Revenir" else "Annuler") { onButton(Button.B) }
                    if (state.phase in setOf(CommandPhase.REVIEW, CommandPhase.UNKNOWN, CommandPhase.DONE)) ButtonChip("R2", "Redire", modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            held(true)
                            try { tryAwaitRelease() } finally { held(false) }
                        })
                    })
                }
            }
        }
    }
}
