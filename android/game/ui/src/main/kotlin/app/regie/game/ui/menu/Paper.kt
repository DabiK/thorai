package app.regie.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.Choice
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.KeyChipColors
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.KeyOrder
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/** Une aide de la barre des touches, touchable sur l'écran du bas quand [onTap] est donné. */
@Immutable
data class TapHint(val spec: KeyHintSpec, val onHold: ((Boolean) -> Unit)? = null, val onTap: (() -> Unit)? = null) {
    constructor(key: String, label: String, onTap: (() -> Unit)? = null) : this(KeyHintSpec(key, label), onTap = onTap)
}

/** Un appui maintenu au doigt, comme une gâchette : vrai à l'appui, faux au relâché. */
fun Modifier.holdable(onHold: (Boolean) -> Unit): Modifier = pointerInput(onHold) {
    detectTapGestures(onPress = {
        onHold(true)
        try { tryAwaitRelease() } finally { onHold(false) }
    })
}

/**
 * La barre des touches (`KeyHintBar`), dont chaque aide se touche aussi : l'écran du bas est
 * tactile en secours (PRD). Les touches restent dans l'ordre de `docs/textes.md`.
 */
@Composable
fun TapHintBar(hints: List<TapHint>, modifier: Modifier = Modifier) {
    val colors = Game.colors
    val metrics = Game.metrics
    val chip = KeyChipColors(colors.keyBackground, colors.keyInk)
    val sorted = KeyOrder.sort(hints.map { it.spec }).map { spec -> hints.first { it.spec == spec } }
    Row(
        modifier.fillMaxWidth().height(metrics.dp(96)).background(colors.bar).padding(horizontal = metrics.dp(40)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(36)),
    ) {
        sorted.forEach { hint ->
            Row(
                hint.onTap?.let { Modifier.clickable(interactionSource = null, indication = null, onClick = it) }
                    ?: hint.onHold?.let { Modifier.holdable(it) } ?: Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
            ) {
                hint.spec.keys.forEach { KeyChip(it, colors = chip) }
                BasicText(hint.spec.label, style = GameText.body(28, colors.barInk), maxLines = 1)
            }
        }
    }
}

/**
 * Un écran du bas du jeu : le papier, un titre en Jersey 10 (64 px) et ce qui l'accompagne à droite,
 * le contenu, puis la barre des touches (`thorai-pixel.css`, `.screen`, `.head`, `.body`, `.hints`).
 */
@Composable
fun PaperScreen(
    title: String?,
    hints: List<TapHint>,
    modifier: Modifier = Modifier,
    headEnd: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = Game.metrics
    Column(modifier.fillMaxSize().background(Game.colors.paper)) {
        if (title != null || headEnd != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = metrics.dp(44), end = metrics.dp(44), top = metrics.dp(36), bottom = metrics.dp(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(title.orEmpty(), Modifier.weight(1f), style = GameText.display(64), maxLines = 1, overflow = TextOverflow.Ellipsis)
                headEnd?.invoke(this)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(horizontal = metrics.dp(44), vertical = metrics.dp(16)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(22)),
            content = content,
        )
        if (hints.isNotEmpty()) TapHintBar(hints)
    }
}

/** Une pastille : un carré d'état et un court texte (« Pont prêt »). */
@Composable
fun Pill(text: String, dot: Color, modifier: Modifier = Modifier, background: Color = Game.colors.paperDeep) {
    val metrics = Game.metrics
    Row(
        modifier.background(background).padding(horizontal = metrics.dp(14), vertical = metrics.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        Box(Modifier.size(metrics.art(3)).background(dot))
        BasicText(text, style = GameText.body(24), maxLines = 1)
    }
}

/**
 * Menu de choix, comme `ChoiceMenu` du kit, mais chaque option se touche aussi : [onTap] reçoit son
 * rang. Le curseur-flèche clignote devant l'option choisie.
 */
@Composable
fun MenuList(
    choices: List<Choice>,
    selected: Int,
    modifier: Modifier = Modifier,
    title: String? = null,
    showCursor: Boolean = true,
    onTap: ((Int) -> Unit)? = null,
) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(modifier, thick = true, contentPadding = PaddingValues(horizontal = metrics.dp(26), vertical = metrics.dp(22))) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
            title?.let { BasicText(it, Modifier.padding(start = metrics.dp(16), bottom = metrics.dp(12)), style = GameText.display(56)) }
            choices.forEachIndexed { index, choice ->
                val on = index == selected
                Box(
                    Modifier.fillMaxWidth()
                        .semantics { this.selected = on }
                        .then(if (on && showCursor) Modifier.background(colors.paperDeep) else Modifier)
                        .then(onTap?.let { tap -> Modifier.clickable(interactionSource = null, indication = null) { tap(index) } } ?: Modifier),
                ) {
                    if (on && showCursor) {
                        PixelArrow(ArrowDirection.RIGHT, Modifier.align(Alignment.CenterStart).padding(start = metrics.dp(14)), color = colors.focus, blink = true)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = metrics.dp(52), end = metrics.dp(16), top = metrics.dp(10), bottom = metrics.dp(10)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.dp(24)),
                    ) {
                        BasicText(choice.label, Modifier.weight(1f), style = GameText.body(36, if (choice.enabled) colors.ink else colors.muted, lineHeightPercent = 110), maxLines = 1)
                        choice.detail?.let { BasicText(it, style = GameText.body(24, colors.muted, lineHeightPercent = 110), maxLines = 1) }
                        choice.key?.let { KeyChip(it) }
                    }
                }
            }
        }
    }
}

/** Le décor des menus : le hameau au lever du soleil (`fond.titre`), assombri pour un menu. */
@Composable
fun MenuBackdrop(assets: GameAssets, modifier: Modifier = Modifier, dim: Boolean = true) {
    Box(modifier.fillMaxSize().background(Game.colors.bar).clipToBounds(), contentAlignment = Alignment.Center) {
        SpriteImage(TITLE_BACKGROUND, assets, placeholderPixels = 0)
        if (dim) Spacer(Modifier.fillMaxSize().background(Game.colors.scrim))
    }
}


const val TITLE_BACKGROUND = "fond.titre"
const val TOWER_BACKGROUND = "fond.tour"

/** La colonne du menu, à gauche des pages du menu de pause, assez large pour « Tout mettre en pause ». */
const val MENU_WIDTH = 600
