package app.regie.game.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/**
 * Boîte de dialogue du jeu : un cartouche au nom de l'agent, à cheval sur le cadre, un portrait
 * facultatif à gauche, et la flèche « suite » qui clignote en bas à droite quand [showNext].
 */
@Composable
fun DialogBox(
    modifier: Modifier = Modifier,
    speaker: String? = null,
    speakerColor: Color = Game.colors.line,
    portrait: (@Composable () -> Unit)? = null,
    showNext: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = Game.metrics
    // Le cartouche (64 px) dépasse de moitié au-dessus du cadre.
    val overlap = if (speaker != null) metrics.dp(34) else metrics.dp(0)
    Box(modifier) {
        PixelFrame(
            Modifier.fillMaxWidth().padding(top = overlap),
            thick = true,
            contentPadding = PaddingValues(start = metrics.dp(48), end = metrics.dp(80), top = metrics.dp(48), bottom = metrics.dp(40)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(40))) {
                portrait?.let { Box(Modifier.background(Game.colors.paperDeep), contentAlignment = Alignment.TopCenter) { it() } }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(14)), content = content)
            }
        }
        if (showNext) {
            PixelArrow(
                ArrowDirection.DOWN,
                Modifier.align(Alignment.BottomEnd).padding(end = metrics.dp(40), bottom = metrics.dp(28)),
                color = Game.colors.ink,
                blink = true,
            )
        }
        speaker?.let {
            BasicText(
                it,
                style = GameText.display(44, Game.colors.light),
                maxLines = 1,
                modifier = Modifier.padding(start = metrics.dp(56))
                    .background(speakerColor, PixelShape.notched(metrics.pixelPx))
                    .padding(horizontal = metrics.dp(26), vertical = metrics.dp(10)),
            )
        }
    }
}

/** Réplique dans une boîte de dialogue. */
@Composable
fun DialogText(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = GameText.body(44))
}

/** Une option d'un menu de choix, avec sa touche. */
@Immutable
data class Choice(val label: String, val key: String? = null, val detail: String? = null, val enabled: Boolean = true)

/** Menu de choix : un curseur-flèche clignote devant l'option choisie, chaque option porte sa touche. */
@Composable
fun ChoiceMenu(choices: List<Choice>, selected: Int, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(
        modifier,
        thick = true,
        contentPadding = PaddingValues(horizontal = metrics.dp(26), vertical = metrics.dp(22)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
            choices.forEachIndexed { index, choice ->
                val on = index == selected
                Box(
                    Modifier.fillMaxWidth()
                        .semantics { this.selected = on }
                        .then(if (on) Modifier.background(colors.paperDeep) else Modifier),
                ) {
                    if (on) {
                        PixelArrow(
                            ArrowDirection.RIGHT,
                            Modifier.align(Alignment.CenterStart).padding(start = metrics.dp(14)),
                            color = colors.focus,
                            blink = true,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = metrics.dp(52), end = metrics.dp(16), top = metrics.dp(10), bottom = metrics.dp(10)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.dp(24)),
                    ) {
                        val ink = if (choice.enabled) colors.ink else colors.muted
                        Column(Modifier.weight(1f)) {
                            BasicText(choice.label, style = GameText.body(36, ink, lineHeightPercent = 110))
                            choice.detail?.let { BasicText(it, style = GameText.body(24, colors.muted, lineHeightPercent = 110)) }
                        }
                        choice.key?.let { KeyChip(it) } ?: Box(Modifier.width(metrics.dp(48)))
                    }
                }
            }
        }
    }
}
