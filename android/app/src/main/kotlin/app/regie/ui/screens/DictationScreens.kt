package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*

@Composable
fun DictationTopScreen(state: DictationReview, session: Session?, single: Boolean, onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onSettings: () -> Unit, scrolling: ThreadScroll) {
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(scrolling) { scrolling.steps.collect { scroll.scrollTo(scroll.value + it * 150) } }
    LaunchedEffect(scrolling.velocity) {
        while (true) { delay(16); if (scrolling.velocity.value != 0f) scroll.scrollTo(scroll.value + (scrolling.velocity.value * 20).toInt()) }
    }
    LaunchedEffect(state.selectedWord, state.phase, layout) {
        if (state.phase == DictationPhase.CORRECT) state.words.getOrNull(state.selectedWord)?.let { word ->
            layout?.let { result -> scroll.scrollTo(result.getLineTop(result.getLineForOffset(word.range.first)).toInt()) }
        }
    }
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        Row(Modifier.fillMaxWidth().height(44.dp).bottomDivider().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(when (state.target) { is DictationTarget.Rejection -> "Refuser avec consigne"; is DictationTarget.DiffComment -> "Commentaire"; else -> "Message pour" }, style = sans(13.sp, color = Palette.textMuted))
            session?.let { AgentTag(it.agent); BasicText(it.project, style = sans(13.sp, 600)); BasicText(it.branch.orEmpty(), style = mono(11.sp, color = Palette.textSubtle), maxLines = 1, modifier = Modifier.weight(1f)) }
            BasicText(duration(state.durationMs), style = mono(12.sp, color = Palette.mic))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (state.phase == DictationPhase.RECORDING) BasicText("Transcription", style = sans(12.sp, color = Palette.textSubtle))
            val text = buildAnnotatedString {
                append(state.text.ifEmpty { "…" })
                recognizedTerms(state.text, state.vocabulary).forEach {
                    addStyle(SpanStyle(color = Palette.running, background = Palette.activeRow), it.range.first, it.range.last + 1)
                }
                if (state.phase == DictationPhase.CORRECT) state.words.getOrNull(state.selectedWord)?.let {
                    addStyle(SpanStyle(color = Palette.background, background = Palette.waiting), it.range.first, it.range.last + 1)
                }
            }
            BasicText(text, style = sans(25.sp, lineHeight = 37.sp), onTextLayout = { layout = it })
            (state.target as? DictationTarget.DiffComment)?.let { BasicText(it.location, style = mono(12.sp, color = Palette.textMuted)) }
        }
        if (single) DictationBottomScreen(state, onButton, onHeld, onSettings, compact = true)
    }
}

@Composable
fun DictationBottomScreen(state: DictationReview, onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onSettings: () -> Unit, compact: Boolean = false) {
    Column(Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).background(Palette.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("Dictée · français", style = sans(13.sp, 600, Palette.mic))
            BasicText(duration(state.durationMs), style = mono(12.sp))
            BasicText("Régler la voix", style = sans(12.sp, color = Palette.textMuted), modifier = Modifier.tappable(onSettings))
        }
        Column(Modifier.then(if (compact) Modifier else Modifier.weight(1f)).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    DictationLevel(state, compact = true)
                    MicButton(state.phase == DictationPhase.RECORDING, { onHeld(true) }, { onHeld(false) }, size = 58.dp, halo = 4.dp)
                }
                Spacer(Modifier.height(12.dp))
            } else {
                DictationLevel(state, compact = false)
                Spacer(Modifier.height(20.dp))
                MicButton(state.phase == DictationPhase.RECORDING, { onHeld(true) }, { onHeld(false) }, size = 58.dp, halo = 4.dp)
                Spacer(Modifier.height(16.dp))
            }
            BasicText(state.notice ?: when (state.phase) {
                DictationPhase.RECORDING -> "Relâche L2 pour relire"
                DictationPhase.FINALIZING -> "Transcription…"
                DictationPhase.SENDING -> "Envoi…"
                else -> "Relis avant d’envoyer"
            }, style = sans(if (compact) 13.sp else 18.sp, 600), modifier = Modifier.fillMaxWidth())
        }
        if (state.phase == DictationPhase.CORRECT) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BarAction("←", "Choisir avant") { onButton(Button.LEFT) }; BarAction("→", "Choisir après") { onButton(Button.RIGHT) }
        }
        Row(Modifier.fillMaxWidth().topDivider().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.editable) BarAction("A", "Envoyer") { onButton(Button.A) }
            BarAction("B", if (state.phase == DictationPhase.SENT) "Revenir" else "Effacer") { onButton(Button.B) }
            if (state.editable) {
                BarAction("X", "Corriger un mot") { onButton(Button.X) }
                BarAction("Y", "Compléter") { onButton(Button.Y) }
            }
        }
    }
}

@Composable
private fun DictationLevel(state: DictationReview, compact: Boolean) {
    // Actual recent PCM levels; no fabricated activity while the microphone is silent.
    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(state.durationMs, state.level) {
        if (state.durationMs == 0L) levels.clear()
        levels.add(state.level)
        if (levels.size > 32) levels.removeAt(0)
    }
    val height = if (compact) 32f else 72f
    Row(Modifier.height(height.dp).semantics {
        contentDescription = "Niveau du micro"
        progressBarRangeInfo = ProgressBarRangeInfo(state.level, 0f..1f)
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(32) { index ->
            Box(Modifier.width(5.dp).height((4 + (levels.getOrNull(index) ?: 0f) * height * 2.5f).coerceAtMost(height).dp).background(Palette.mic, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
fun VoiceSettingsScreen(settings: VoiceSettings, select: (DictationProvider) -> Unit, saveKey: (String) -> Unit, permission: () -> Unit, close: () -> Unit, saveCommandKey: (String) -> Unit = {}) {
    var commandKey by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Palette.background).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BasicText("Régler la voix", style = sans(22.sp, 600))
        DictationProvider.entries.forEach { provider ->
            BasicText("${if (settings.provider == provider) "●" else "○"}  ${if (provider == DictationProvider.LOCAL) "Voix locale · gratuite" else "ElevenLabs · en ligne"}", style = sans(16.sp), modifier = Modifier.tappable { select(provider) })
        }
        BasicText("Clé ElevenLabs : ${if (settings.keyConfigured) "enregistrée" else "à saisir"}", style = sans(13.sp, color = Palette.textMuted))
        BasicTextField(key, { key = it; saved = false }, textStyle = sans(16.sp), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            cursorBrush = SolidColor(Palette.text), modifier = Modifier.fillMaxWidth().background(Palette.surface).padding(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BasicText(if (saved) "Enregistrée" else "Enregistrer", style = sans(14.sp), modifier = Modifier.tappable { if (key.isNotBlank()) { saveKey(key); key = ""; saved = true } })
            BasicText("Effacer la clé", style = sans(14.sp, color = Palette.textMuted), modifier = Modifier.tappable { saveKey(""); key = "" })
        }
        BasicText("Ordres ambigus · Mistral : ${if (settings.commandKeyConfigured) "clé enregistrée" else "clé à saisir"}", style = sans(13.sp, color = Palette.textMuted))
        BasicText("Facultatif et payant. Les ordres courants restent locaux. Mistral reçoit les phrases ambiguës. Les noms des sessions et projets sont transmis.", style = sans(12.sp, color = Palette.textMuted, lineHeight = 17.sp))
        BasicTextField(commandKey, { commandKey = it }, textStyle = sans(16.sp), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().background(Palette.surface).padding(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BasicText("Enregistrer Mistral", style = sans(14.sp), modifier = Modifier.tappable { if (commandKey.isNotBlank()) { saveCommandKey(commandKey); commandKey = "" } })
            BasicText("Effacer Mistral", style = sans(14.sp, color = Palette.textMuted), modifier = Modifier.tappable { saveCommandKey(""); commandKey = "" })
        }
        BasicText("Autoriser le microphone", style = sans(15.sp, color = Palette.running), modifier = Modifier.tappable(permission))
        BasicText("Voix locale : modèle téléchargé au premier usage. ElevenLabs reçoit l’audio et les termes du projet.", style = sans(12.sp, color = Palette.textMuted, lineHeight = 17.sp))
        BarAction("B", "Revenir") { close() }
    }
}

private fun duration(ms: Long) = "${ms / 60_000}:${(ms / 1000 % 60).toString().padStart(2, '0')}"

/** Exact identifier boundaries, so a branch named main does not highlight maintenant or mainframe. */
fun recognizedTerms(text: String, vocabulary: List<String>): List<MatchResult> = vocabulary
    .filter { it.isNotBlank() }
    .flatMap { Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(it) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE).findAll(text).toList() }
    .sortedBy { it.range.first }
