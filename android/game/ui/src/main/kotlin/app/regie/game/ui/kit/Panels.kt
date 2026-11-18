package app.regie.game.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/**
 * Panneau d'une maison : le projet, la session et l'état, avec une pointe vers la maison (sous le
 * panneau ; [pointer] faux pour la poser soi-même, comme [app.regie.game.ui.village.VillageOverlay]).
 * Une validation en attente le passe en ambre et le fait sautiller.
 */
@Composable
fun SignPanel(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: TagState? = null,
    stateLabel: String? = state?.let { tagLabel(it) },
    colors: FrameColors = if (state == TagState.WAITING) FrameDefaults.filled(Game.colors.wait) else FrameDefaults.panel(),
    bob: Boolean = state == TagState.WAITING,
    pointer: Boolean = true,
) {
    val metrics = Game.metrics
    val palette = Game.colors
    val clock = Game.clock
    val waiting = state == TagState.WAITING
    val ink = if (waiting) palette.waitInk else palette.ink
    Column(
        modifier.offset { IntOffset(0, if (bob && Blink.bobUp(clock.value)) -2 * metrics.pixelPx else 0) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelFrame(
            colors = colors,
            contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(12)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
                BasicText(title, style = GameText.body(30, ink, weight = 700, lineHeightPercent = 100), maxLines = 1)
                subtitle?.let { BasicText(it, style = GameText.body(24, if (waiting) ink else palette.muted, lineHeightPercent = 100), maxLines = 1) }
                if (state != null && stateLabel != null) {
                    if (waiting) {
                        BasicText("! $stateLabel", style = GameText.body(22, ink, weight = 600, lineHeightPercent = 100), maxLines = 1)
                    } else {
                        val (square, text) = when (state) {
                            TagState.WORKING -> palette.work to palette.work
                            TagState.FAILED -> palette.error to palette.error
                            else -> palette.idle to palette.muted
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                            Box(Modifier.size(metrics.art(4)).background(square))
                            BasicText(stateLabel, style = GameText.body(22, text, weight = 600, lineHeightPercent = 100), maxLines = 1)
                        }
                    }
                }
            }
        }
        if (pointer) PixelArrow(ArrowDirection.DOWN, rows = 2)
    }
}

/** Où part la pointe d'une bulle. */
enum class BubbleTail { NONE, BOTTOM, BOTTOM_START, LEFT }

/** Bulle : l'activité de l'agent (« Modification · Overview.kt ») ou la parole du coursier. */
@Composable
fun Bubble(
    modifier: Modifier = Modifier,
    tail: BubbleTail = BubbleTail.BOTTOM,
    content: @Composable RowScope.() -> Unit,
) {
    val metrics = Game.metrics
    val frame = @Composable {
        PixelFrame(thick = true, contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(16))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14)), content = content)
        }
    }
    when (tail) {
        BubbleTail.NONE -> Box(modifier) { frame() }
        BubbleTail.LEFT -> Row(modifier, verticalAlignment = Alignment.Bottom) {
            PixelArrow(ArrowDirection.LEFT, Modifier.padding(bottom = metrics.art(3)))
            frame()
        }
        BubbleTail.BOTTOM, BubbleTail.BOTTOM_START -> Column(
            modifier,
            horizontalAlignment = if (tail == BubbleTail.BOTTOM) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            frame()
            PixelArrow(ArrowDirection.DOWN, if (tail == BubbleTail.BOTTOM_START) Modifier.padding(start = metrics.art(6)) else Modifier)
        }
    }
}

/** Texte d'une bulle. */
@Composable
fun BubbleText(text: String) {
    BasicText(text, style = GameText.body(30, lineHeightPercent = 125))
}

/**
 * Bandeau de notification, avec son icône (une image d'atlas, par exemple la lettre) et une
 * action à droite. [alert] le passe en ambre, pour une validation.
 */
@Composable
fun Toast(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    alert: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    action: KeyHintSpec? = null,
) {
    val metrics = Game.metrics
    PixelFrame(
        modifier,
        thick = true,
        colors = if (alert) FrameDefaults.alert() else FrameDefaults.panel(),
        contentPadding = PaddingValues(horizontal = metrics.dp(28), vertical = metrics.dp(18)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(24))) {
            icon?.invoke()
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
                BasicText(title, style = GameText.body(40, weight = 600, lineHeightPercent = 110), maxLines = 1)
                subtitle?.let { BasicText(it, style = GameText.body(28, Game.colors.muted, lineHeightPercent = 110), maxLines = 1) }
            }
            action?.let { KeyHint(it) }
        }
    }
}

/** Le bandeau descend du haut de l'écran quand [visible] passe à vrai, et y remonte ensuite. */
@Composable
fun ToastHost(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
    ) { content() }
}
