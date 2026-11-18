package app.regie.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.regie.domain.model.DictationProvider
import app.regie.domain.model.VoiceSettings
import app.regie.game.ui.R
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/** Les gestes des réglages de la voix : les mêmes que la vue classique, au doigt, et B pour revenir. */
class VoiceSettingsCallbacks(
    val select: (DictationProvider) -> Unit = {},
    val saveKey: (String) -> Unit = {},
    val permission: () -> Unit = {},
    val close: () -> Unit = {},
    val saveCommandKey: (String) -> Unit = {},
)

/**
 * Les réglages de la voix, sur l'écran du bas : le fournisseur de transcription, la clé ElevenLabs,
 * la clé Mistral des ordres ambigus et le microphone. Les clés sont saisies par l'utilisateur dans
 * des champs masqués et ne sont jamais réaffichées.
 */
@Composable
fun GameVoiceSettingsScreen(settings: VoiceSettings, modifier: Modifier = Modifier, callbacks: VoiceSettingsCallbacks = VoiceSettingsCallbacks()) {
    var key by remember { mutableStateOf("") }
    var commandKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val metrics = Game.metrics
    val colors = Game.colors
    PaperScreen(
        stringResource(R.string.game_voice_title),
        listOf(TapHint("B", stringResource(R.string.game_hint_back), callbacks.close)),
        modifier.imePadding().testTag("game-voice-settings"),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(metrics.dp(20))) {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(20))) {
                DictationProvider.entries.forEach { provider ->
                    val on = settings.provider == provider
                    PixelFrame(
                        Modifier.weight(1f).semantics { selected = on }.tapped { callbacks.select(provider) },
                        colors = if (on) FrameDefaults.focus() else FrameDefaults.panel(),
                        contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(16)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
                            Box(Modifier.size(metrics.art(4)).background(if (on) colors.focus else colors.idle))
                            BasicText(
                                stringResource(if (provider == DictationProvider.LOCAL) R.string.game_voice_local else R.string.game_voice_cloud),
                                style = GameText.body(30, weight = if (on) 600 else 400),
                            )
                        }
                    }
                }
            }
            BasicText(stringResource(if (settings.keyConfigured) R.string.game_voice_key_saved else R.string.game_voice_key_missing), style = GameText.body(26, colors.muted))
            SecretField(key) { key = it; saved = false }
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(36))) {
                TextAction(stringResource(if (saved) R.string.game_voice_saved else R.string.game_voice_save)) {
                    if (key.isNotBlank()) { callbacks.saveKey(key); key = ""; saved = true }
                }
                TextAction(stringResource(R.string.game_voice_erase), muted = true) { callbacks.saveKey(""); key = "" }
            }
            BasicText(
                stringResource(if (settings.commandKeyConfigured) R.string.game_voice_mistral_saved else R.string.game_voice_mistral_missing),
                style = GameText.body(26, colors.muted),
            )
            BasicText(stringResource(R.string.game_voice_mistral_help), style = GameText.body(24, colors.muted))
            SecretField(commandKey) { commandKey = it }
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(36))) {
                TextAction(stringResource(R.string.game_voice_mistral_save)) {
                    if (commandKey.isNotBlank()) { callbacks.saveCommandKey(commandKey); commandKey = "" }
                }
                TextAction(stringResource(R.string.game_voice_mistral_erase), muted = true) { callbacks.saveCommandKey(""); commandKey = "" }
            }
            TextAction(stringResource(R.string.game_voice_microphone), onTap = callbacks.permission)
            BasicText(stringResource(R.string.game_voice_help), style = GameText.body(24, colors.muted))
        }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit) {
    val metrics = Game.metrics
    PixelFrame(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(14))) {
        BasicTextField(
            value,
            onChange,
            Modifier.fillMaxWidth(),
            textStyle = GameText.code(40),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            cursorBrush = SolidColor(Game.colors.ink),
        )
    }
}

@Composable
private fun TextAction(label: String, muted: Boolean = false, onTap: () -> Unit) {
    BasicText(
        label,
        Modifier.tapped(onTap).padding(vertical = Game.metrics.dp(8)),
        style = GameText.body(30, if (muted) Game.colors.muted else Game.colors.focus, weight = 600),
    )
}
