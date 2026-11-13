package app.regie.voicelab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.voice.api.DictationState
import app.regie.voice.elevenlabs.ScribeModel
import java.util.Locale

private val Background = Color(0xFF0E1116)
private val Panel = Color(0xFF171B22)
private val Muted = Color(0xFF8B95A5)
private val Bright = Color(0xFFECEFF4)
private val Accent = Color(0xFF7AA2F7)
private val Danger = Color(0xFFF7768E)

private const val SAMPLE_TEXT = "La session Claude sur regie attend ta validation : elle veut lancer npm run check dans le dossier bridge."

@Composable
fun VoiceLabScreen(lab: VoiceLab) {
    val ui by lab.ui.collectAsState()
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Panel)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Background).padding(16.dp)) {
            // Upper screen: 832 dp wide; lower screen (to type the key): 538 dp.
            val dictationShare = if (maxWidth >= 700.dp) 0.58f else 0.45f
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val guided = ui.guided
                if (guided != null) GuidedPanel(ui, guided, Modifier.weight(dictationShare)) else DictationPanel(ui, Modifier.weight(dictationShare))
                SettingsPanel(ui, lab, Modifier.weight(1f - dictationShare))
            }
        }
    }
}

@Composable
private fun DictationPanel(ui: UiState, modifier: Modifier) {
    val providerName = if (ui.provider == "local") "Local (sherpa-onnx)" else "ElevenLabs"
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Maintenir L2 pour dicter · $providerName", color = Muted, fontSize = 18.sp)
        val (label, text, final) = describe(ui.dictation)
        Text(label, color = if (ui.dictation is DictationState.Failed) Danger else Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .border(1.dp, Panel, RoundedCornerShape(12.dp))
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(text, color = if (final) Bright else Muted, fontSize = 28.sp, lineHeight = 36.sp)
        }
        ui.measure?.let { m ->
            val parts = buildList {
                add("Latence ${m.latencyMs?.let { "$it ms" } ?: "—"}")
                add("Audio ${"%.1f".format(Locale.FRANCE, m.audioMs / 1000.0)} s")
                m.realTimeFactor?.let { add("Temps réel ×${"%.2f".format(Locale.FRANCE, it)}") }
            }
            Text(parts.joinToString("  ·  "), color = Bright, fontSize = 20.sp)
        }
        ui.bench?.let { b ->
            Text("Banc ${b.provider} ${b.done}/${b.total}", color = Accent, fontSize = 16.sp)
            Text(b.line, color = Muted, fontSize = 14.sp, maxLines = 2)
        }
        Refusal(ui)
    }
}

/** The last adb command refused, if any: it must be seen without logcat. */
@Composable
private fun Refusal(ui: UiState) {
    ui.refusal?.let { Text(it, color = Danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2) }
}

/** The guided reading: the sentence to read in large, what is heard, the result of the last one. */
@Composable
private fun GuidedPanel(ui: UiState, guided: GuidedUi, modifier: Modifier) {
    val providerName = if (guided.provider == "local") "Local" else "ElevenLabs"
    val prompt = guided.prompt
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val position = if (prompt != null) "${guided.read + 1}/${guided.total} · B : recommencer" else "${guided.read}/${guided.total} lues"
        Text("Lecture guidée · $providerName · $position", color = Muted, fontSize = 16.sp, maxLines = 1)
        // The sentence comes first: it keeps its whole height, the details below give way.
        Box(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(14.dp)) {
            Text(prompt?.text ?: "Terminé", color = Bright, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
        }
        if (prompt != null) Text("Maintiens L2 et lis la phrase", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        val dictation = ui.dictation
        if (dictation != null && !dictation.isTerminal) {
            val (label, text, _) = describe(dictation)
            Text("$label : $text", color = Muted, fontSize = 16.sp, maxLines = 2)
        }
        guided.last?.let { Text("Précédente : ${VoiceLab.describe(it)}", color = Bright, fontSize = 15.sp, maxLines = 2) }
        guided.status?.let { Text(it, color = Accent, fontSize = 15.sp, maxLines = 2) }
        Refusal(ui)
    }
}

/** Label, text, and whether the text is final. */
private fun describe(state: DictationState?): Triple<String, String, Boolean> = when (state) {
    null -> Triple("Prêt", "…", false)
    is DictationState.PreparingModel -> Triple(
        state.progress?.let { "Téléchargement du modèle ${(it * 100).toInt()} %" } ?: "Chargement du modèle…",
        "",
        false,
    )
    DictationState.Connecting -> Triple("Connexion…", "", false)
    is DictationState.Listening -> Triple("J'écoute", state.partial.ifEmpty { "Parlez…" }, false)
    is DictationState.Finalizing -> Triple("Texte final…", state.partial, false)
    is DictationState.Final -> Triple("Texte final", state.text.ifEmpty { "(rien entendu)" }, true)
    is DictationState.Failed -> Triple("Échec", VoiceLab.describe(state.error), true)
    DictationState.Cancelled -> Triple("Annulée", "", true)
}

@Composable
private fun SettingsPanel(ui: UiState, lab: VoiceLab, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Section("Fournisseur")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("ElevenLabs", ui.provider == "elevenlabs") { lab.selectProvider("elevenlabs") }
            Choice("Local", ui.provider == "local") { lab.selectProvider("local") }
        }
        if (ui.provider == "local") LocalSettings(ui.local, lab) else ElevenLabsSettings(ui.elevenLabs, lab)

        Spacer(Modifier.height(8.dp))
        Section("Lecture guidée du corpus")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("ElevenLabs", ui.guided?.provider == "elevenlabs") { lab.startGuided("elevenlabs") }
            Choice("Local", ui.guided?.provider == "local") { lab.startGuided("local") }
            if (ui.guided != null) OutlinedButton(onClick = lab::stopGuided) { Text("Arrêter", fontSize = 18.sp) }
        }

        Spacer(Modifier.height(8.dp))
        Section("Lecture à voix haute")
        var text by remember { mutableStateOf(SAMPLE_TEXT) }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { lab.speak(text) }) { Text("Lire", fontSize = 18.sp) }
            OutlinedButton(onClick = lab::stopSpeaking, enabled = ui.speaking) { Text("Couper (L2/R2)", fontSize = 18.sp) }
        }
        ui.speechInfo?.let { Text(it, color = Muted, fontSize = 16.sp) }
    }
}

@Composable
private fun LocalSettings(local: LocalUi, lab: VoiceLab) {
    Text(
        if (local.installed) "Modèles installés" else "Modèles à télécharger : environ ${local.remainingMb} Mo",
        color = Bright,
        fontSize = 18.sp,
    )
    if (!local.installed || local.downloading) {
        Button(onClick = lab::downloadLocal, enabled = !local.downloading) { Text("Télécharger les modèles", fontSize = 18.sp) }
    }
    local.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
    local.status?.let { Text(it, color = Muted, fontSize = 16.sp) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Termes favorisés", color = Bright, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        Choice("Non", !local.hotwords) { lab.setHotwords(false) }
        Choice("Oui", local.hotwords) { lab.setHotwords(true) }
    }
}

@Composable
private fun ElevenLabsSettings(eleven: ElevenLabsUi, lab: VoiceLab) {
    if (eleven.keySaved) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Clé enregistrée (chiffrée)", color = Bright, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
            OutlinedButton(onClick = lab::deleteKey) { Text("Effacer") }
        }
    } else {
        var key by remember { mutableStateOf("") }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Clé ElevenLabs") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { lab.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Enregistrer la clé", fontSize = 18.sp) }
    }
    Text("Modèle Scribe", color = Muted, fontSize = 16.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("v3 Live", eleven.scribe == ScribeModel.V3_LIVE) { lab.selectScribe(ScribeModel.V3_LIVE) }
        Choice("v2 Realtime", eleven.scribe == ScribeModel.V2_REALTIME) { lab.selectScribe(ScribeModel.V2_REALTIME) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voix : ${eleven.voiceName ?: "à choisir"}", color = Bright, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
        OutlinedButton(onClick = lab::loadVoices, enabled = eleven.keySaved) { Text("Charger les voix") }
    }
    eleven.voicesStatus?.let { Text(it, color = Muted, fontSize = 16.sp) }
    for (voice in eleven.voices.take(MAX_VOICES)) {
        OutlinedButton(onClick = { lab.selectVoice(voice) }, modifier = Modifier.fillMaxWidth()) {
            Text(voice.name + (voice.language?.let { " ($it)" } ?: ""))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(label, fontSize = 18.sp, softWrap = false) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, fontSize = 18.sp, color = Bright, softWrap = false) }
    }
}

private const val MAX_VOICES = 30
