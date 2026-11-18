package app.regie.game.ui.house

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.KeyChipColors
import app.regie.game.ui.kit.KeyOrder
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/**
 * Une action de la barre des touches : ses touches, son libellé, et ce qu'un doigt y fait.
 * [onHold] remplace [onTap] pour une gâchette maintenue (L2 tenu au doigt).
 */
@Immutable
data class PadAction(
    val keys: List<String>,
    val label: String,
    val enabled: Boolean = true,
    val onTap: (() -> Unit)? = null,
    val onHold: ((Boolean) -> Unit)? = null,
) {
    constructor(key: String, label: String, enabled: Boolean = true, onTap: (() -> Unit)? = null) :
        this(listOf(key), label, enabled, onTap)
}

/**
 * La barre des touches du bas, comme `KeyHintBar`, mais chaque aide se touche aussi : l'écran du bas
 * est tactile. Les aides suivent l'ordre de `docs/textes.md`, la croix en tête.
 */
@Composable
fun PadActionBar(actions: List<PadAction>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = Game.colors
    val metrics = Game.metrics
    Row(
        modifier.fillMaxWidth().height(metrics.dp(96)).background(colors.bar).padding(horizontal = metrics.dp(if (compact) 24 else 40)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(if (compact) 24 else 36)),
    ) {
        actions.sortedBy { action -> action.keys.minOf { KeyOrder.rank(it) } }.forEach { action ->
            PadActionHint(action, KeyChipColors(colors.keyBackground, colors.keyInk), colors.barInk, if (compact) 26 else 28)
        }
    }
}

/** Une aide touchable : ses touches puis son libellé ; pâle quand l'action n'est pas possible. */
@Composable
fun PadActionHint(action: PadAction, chip: KeyChipColors? = null, ink: androidx.compose.ui.graphics.Color = Game.colors.ink, textSizePx: Int = 28) {
    val metrics = Game.metrics
    val hold by rememberUpdatedState(action.onHold)
    val touch = when {
        !action.enabled -> Modifier
        action.onHold != null -> Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                hold?.invoke(true)
                try {
                    tryAwaitRelease()
                } finally {
                    hold?.invoke(false)
                }
            })
        }
        action.onTap != null -> Modifier.clickable(onClick = action.onTap)
        else -> Modifier
    }
    Row(
        touch.alpha(if (action.enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        action.keys.forEach { KeyChip(it, colors = chip) }
        BasicText(action.label, style = GameText.body(textSizePx, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
