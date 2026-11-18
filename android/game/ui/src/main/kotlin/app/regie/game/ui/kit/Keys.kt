package app.regie.game.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/** L'ordre des touches dans un groupe d'actions (`docs/textes.md`). */
object KeyOrder {
    val ORDER = listOf("A", "B", "X", "Y", "L1", "R1", "L2", "R2", "Start")

    /** Les touches octogonales ; les autres sont rectangulaires. */
    val ROUND = setOf("A", "B", "X", "Y")

    /** Rang d'une touche ; la croix et les sticks, qui servent à se déplacer, passent en tête. */
    fun rank(key: String): Int = ORDER.indexOf(key)

    /** Les aides triées par leur première touche, l'ordre d'origine départageant les égalités. */
    fun sort(hints: List<KeyHintSpec>): List<KeyHintSpec> = hints.sortedBy { hint -> hint.keys.minOf { rank(it) } }
}

/** Une aide de la barre : une ou plusieurs touches, puis l'action. */
@Immutable
data class KeyHintSpec(val keys: List<String>, val label: String) {
    constructor(key: String, label: String) : this(listOf(key), label)
}

@Immutable
data class KeyChipColors(val background: Color, val ink: Color)

/** Sur le papier, la touche est en vert profond pour se détacher ; la barre des touches la remet en papier. */
private val LocalKeyChipColors = compositionLocalOf<KeyChipColors?> { null }

/** Une touche de la manette : 48 px de haut, octogonale pour A, B, X et Y, rectangle cranté sinon. */
@Composable
fun KeyChip(key: String, modifier: Modifier = Modifier, colors: KeyChipColors? = null) {
    val metrics = Game.metrics
    val chip = colors ?: LocalKeyChipColors.current ?: KeyChipColors(Game.colors.line, Game.colors.paper)
    val round = key in KeyOrder.ROUND
    val shape = if (round) PixelShape.octagon(metrics.pixelPx) else PixelShape.notched(metrics.pixelPx)
    val sized = if (round) modifier.width(metrics.dp(48)) else modifier.widthIn(min = metrics.dp(48))
    Box(
        sized.height(metrics.dp(48)).background(chip.background, shape).padding(horizontal = metrics.dp(if (round) 0 else 12)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(key, style = GameText.body(26, chip.ink), maxLines = 1)
    }
}

/** Les touches d'une aide, suivies de l'action. */
@Composable
fun KeyHint(hint: KeyHintSpec, modifier: Modifier = Modifier, color: Color = Game.colors.ink, textSizePx: Int = 28) {
    val metrics = Game.metrics
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
        hint.keys.forEach { KeyChip(it) }
        BasicText(hint.label, style = GameText.body(textSizePx, color), maxLines = 1)
    }
}

/** La barre des touches en bas de l'écran : 96 px, fond vert profond, aides dans l'ordre des touches. */
@Composable
fun KeyHintBar(hints: List<KeyHintSpec>, modifier: Modifier = Modifier) {
    val colors = Game.colors
    val metrics = Game.metrics
    CompositionLocalProvider(LocalKeyChipColors provides KeyChipColors(colors.keyBackground, colors.keyInk)) {
        Row(
            modifier.fillMaxWidth().height(metrics.dp(96)).background(colors.bar).padding(horizontal = metrics.dp(40)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(36)),
        ) {
            KeyOrder.sort(hints).forEach { KeyHint(it, color = colors.barInk) }
        }
    }
}
