package app.regie.game.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import app.regie.game.ui.theme.Game

/**
 * Les animations du kit, en pas francs comme `steps(1)` dans les maquettes (`thorai-pixel.css`).
 * Elles ne dépendent que du temps : un test peut les vérifier sans horloge.
 */
object Blink {
    /** Flèche « suite », curseur : plein la moitié de 0,8 s, pâle l'autre moitié. */
    fun pulse(timeMillis: Long): Float = if (Math.floorMod(timeMillis, 800L) < 400L) 1f else 0.35f

    /** Points « au travail » : chacun visible 60 % de 1,2 s, décalés de 0,4 s. */
    fun dot(timeMillis: Long, index: Int): Float = if (Math.floorMod(timeMillis - index * 400L, 1200L) < 720L) 1f else 0.15f

    /** Panneau qui sautille : levé d'un cran la seconde moitié de chaque seconde. */
    fun bobUp(timeMillis: Long): Boolean = Math.floorMod(timeMillis, 1000L) >= 500L
}

enum class ArrowDirection { DOWN, RIGHT, LEFT, UP }

/**
 * Une flèche en escalier de [rows] pixels de profondeur : pointe de la bulle, du panneau,
 * flèche « suite » de la boîte de dialogue, curseur du menu. [blink] la fait clignoter.
 */
@Composable
fun PixelArrow(
    direction: ArrowDirection,
    modifier: Modifier = Modifier,
    color: Color = Game.colors.line,
    rows: Int = 3,
    blink: Boolean = false,
) {
    val metrics = Game.metrics
    val clock = Game.clock
    val long = metrics.art(2 * rows)
    val short = metrics.art(rows)
    val sized = if (direction == ArrowDirection.DOWN || direction == ArrowDirection.UP) modifier.size(long, short) else modifier.size(short, long)
    Canvas(if (blink) sized.graphicsLayer { alpha = Blink.pulse(clock.value) } else sized) {
        val p = metrics.pixelPx.toFloat()
        for (i in 0 until rows) {
            // Rangée i : de i à 2·rows − i, en partant de la base de la flèche.
            val span = (2 * rows - 2 * i) * p
            when (direction) {
                ArrowDirection.DOWN -> drawRect(color, Offset(i * p, i * p), Size(span, p))
                ArrowDirection.RIGHT -> drawRect(color, Offset(i * p, i * p), Size(p, span))
                ArrowDirection.LEFT -> drawRect(color, Offset((rows - 1 - i) * p, i * p), Size(p, span))
                ArrowDirection.UP -> drawRect(color, Offset(i * p, (rows - 1 - i) * p), Size(span, p))
            }
        }
    }
}
