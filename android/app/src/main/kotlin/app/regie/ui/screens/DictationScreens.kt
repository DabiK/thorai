package app.regie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.components.*
import app.regie.ui.theme.*

/**
 * Dictée plein écran, en mode un seul écran hors d'une session : la transcription en grand, puis les
 * commandes compactes. Sur deux écrans, l'écran du haut ne change pas : tout est en bas.
 */
@Composable
fun DictationTopScreen(state: DictationReview, session: Session?, single: Boolean, onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onSettings: () -> Unit, scrolling: ThreadScroll) {
    Column(Modifier.fillMaxSize().background(Palette.background)) {
        Row(Modifier.fillMaxWidth().height(44.dp).bottomDivider().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TargetLine(state, session, Modifier.weight(1f))
            BasicText(duration(state.durationMs), style = mono(12.sp, color = Palette.mic))
        }
        Transcript(state, scrolling, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp))
        if (single) DictationBottomScreen(state, onButton, onHeld, onSettings, compact = true)
    }
}

/**
 * Commandes de la dictée. Avec [transcript], c'est l'écran du bas complet de la dictée : destinataire,
 * transcription en direct (25 sp), niveau du micro, puis A, B, X, Y. L'écran du haut garde son fil.
 */
@Composable
fun DictationBottomScreen(
    state: DictationReview,
    onButton: (Button) -> Unit,
    onHeld: (Boolean) -> Unit,
    onSettings: () -> Unit,
    compact: Boolean = false,
    session: Session? = null,
    transcript: ThreadScroll? = null,
) {
    val full = !compact || transcript != null
    Column(Modifier.then(if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).background(Palette.background)
        .padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (transcript != null) TargetLine(state, session, Modifier.weight(1f))
            else BasicText("Dictée · français", style = sans(13.sp, 600, Palette.mic), modifier = Modifier.weight(1f))
            BasicText(duration(state.durationMs), style = mono(12.sp, color = if (state.phase == DictationPhase.RECORDING) Palette.mic else Palette.textMuted))
            BasicText("Régler la voix", style = sans(12.sp, color = Palette.textMuted), modifier = Modifier.tappable(onSettings))
        }
        if (transcript != null) {
            Transcript(state, transcript, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MicButton(state.phase == DictationPhase.RECORDING, { onHeld(true) }, { onHeld(false) }, size = 44.dp, halo = 3.dp)
                DictationLevel(state, compact = true)
                BasicText(phaseText(state), style = sans(14.sp, 600), maxLines = 2, modifier = Modifier.weight(1f))
            }
        } else Column(Modifier.then(if (compact) Modifier else Modifier.weight(1f)).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
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
            BasicText(phaseText(state), style = sans(if (compact) 13.sp else 18.sp, 600), modifier = Modifier.fillMaxWidth())
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

private fun phaseText(state: DictationReview) = state.notice ?: when (state.phase) {
    DictationPhase.RECORDING -> "Relâche L2 pour relire"
    DictationPhase.FINALIZING -> "Transcription…"
    DictationPhase.SENDING -> "Envoi…"
    else -> "Relis avant d’envoyer"
}

/** « Message pour CODEX atelier main », ou la consigne de refus, ou le commentaire de diff. */
@Composable
private fun TargetLine(state: DictationReview, session: Session?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(when (state.target) { is DictationTarget.Rejection -> "Refuser avec consigne"; is DictationTarget.DiffComment -> "Commentaire"; else -> "Message pour" },
            style = sans(13.sp, color = Palette.textMuted), maxLines = 1)
        session?.let {
            AgentTag(it.agent)
            BasicText(it.project, style = sans(13.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            it.branch?.let { branch -> BasicText(branch, style = mono(11.sp, color = Palette.textSubtle), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * La transcription en direct, à 25 sp : les termes du projet reconnus en bleu, le mot à corriger en
 * ambre. La croix et le stick la font défiler ; en correction, elle suit le mot choisi.
 */
@Composable
private fun Transcript(state: DictationReview, scrolling: ThreadScroll, modifier: Modifier) {
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
    // Pendant l'enregistrement, la fin du texte reste visible.
    LaunchedEffect(state.text, state.phase) { if (state.phase == DictationPhase.RECORDING) scroll.scrollTo(scroll.maxValue) }
    Column(modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            // Rouge pendant l'écoute ; une fois L2 relâché, les niveaux restent visibles mais éteints.
            Box(Modifier.width(5.dp).height((4 + (levels.getOrNull(index) ?: 0f) * height * 2.5f).coerceAtMost(height).dp)
                .background(if (state.phase == DictationPhase.RECORDING) Palette.mic else Palette.keyBorder, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
fun VoiceSettingsScreen(settings: VoiceSettings, select: (DictationProvider) -> Unit, saveKey: (String) -> Unit, permission: () -> Unit, close: () -> Unit, saveCommandKey: (String) -> Unit = {}) {
    var commandKey by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Palette.background).imePadding()) {
        // Titre et retour toujours visibles : le reste défile sous eux.
        Row(Modifier.fillMaxWidth().bottomDivider().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Régler la voix", style = sans(20.sp, 650), modifier = Modifier.weight(1f))
            BarAction("B", "Revenir", size = 14.sp) { close() }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VoiceSection("Transcription")
            DictationProvider.entries.forEach { provider ->
                val chosen = settings.provider == provider
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).background(if (chosen) Palette.surface else Palette.background, RoundedCornerShape(10.dp))
                    .tappable { select(provider) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(18.dp).border(2.dp, if (chosen) Palette.text else Palette.keyBorder, CircleShape), contentAlignment = Alignment.Center) {
                        if (chosen) Box(Modifier.size(8.dp).background(Palette.text, CircleShape))
                    }
                    BasicText(if (provider == DictationProvider.LOCAL) "Voix locale · gratuite" else "ElevenLabs · en ligne", style = sans(16.sp, if (chosen) 600 else 400))
                }
            }
            BasicText("Voix locale : modèle téléchargé au premier usage. ElevenLabs reçoit l’audio et les termes du projet.", style = sans(13.sp, color = Palette.textMuted, lineHeight = 19.sp))
            BasicText("Autoriser le microphone", style = sans(15.sp, 500, Palette.running), modifier = Modifier.tappable(permission).padding(vertical = 6.dp))
            VoiceSection("Clé ElevenLabs : ${if (settings.keyConfigured) "enregistrée" else "à saisir"}")
            SecretField(key) { key = it; saved = false }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BasicText(if (saved) "Enregistrée" else "Enregistrer", style = sans(15.sp), modifier = Modifier.tappable { if (key.isNotBlank()) { saveKey(key); key = ""; saved = true } }.padding(vertical = 6.dp))
                BasicText("Effacer la clé", style = sans(15.sp, color = Palette.textMuted), modifier = Modifier.tappable { saveKey(""); key = "" }.padding(vertical = 6.dp))
            }
            VoiceSection("Ordres ambigus · Mistral : ${if (settings.commandKeyConfigured) "clé enregistrée" else "clé à saisir"}")
            BasicText("Facultatif et payant. Les ordres courants restent locaux. Mistral reçoit les phrases ambiguës. Les noms des sessions et projets sont transmis.", style = sans(13.sp, color = Palette.textMuted, lineHeight = 19.sp))
            SecretField(commandKey) { commandKey = it }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BasicText("Enregistrer Mistral", style = sans(15.sp), modifier = Modifier.tappable { if (commandKey.isNotBlank()) { saveCommandKey(commandKey); commandKey = "" } }.padding(vertical = 6.dp))
                BasicText("Effacer Mistral", style = sans(15.sp, color = Palette.textMuted), modifier = Modifier.tappable { saveCommandKey(""); commandKey = "" }.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun VoiceSection(title: String) {
    BasicText(title, style = sans(13.sp, 600, Palette.textSubtle), modifier = Modifier.padding(top = 6.dp))
}

/** Une clé saisie par l'utilisateur, masquée, jamais affichée en clair. */
@Composable
private fun SecretField(value: String, onChange: (String) -> Unit) {
    BasicTextField(value, onChange, textStyle = mono(16.sp), singleLine = true,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        cursorBrush = SolidColor(Palette.text),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Palette.surface, RoundedCornerShape(10.dp)).border(1.dp, Palette.divider, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 13.dp))
}

private fun duration(ms: Long) = "${ms / 60_000}:${(ms / 1000 % 60).toString().padStart(2, '0')}"

/** Exact identifier boundaries, so a branch named main does not highlight maintenant or mainframe. */
fun recognizedTerms(text: String, vocabulary: List<String>): List<MatchResult> = vocabulary
    .filter { it.isNotBlank() }
    .flatMap { Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(it) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE).findAll(text).toList() }
    .sortedBy { it.range.first }
