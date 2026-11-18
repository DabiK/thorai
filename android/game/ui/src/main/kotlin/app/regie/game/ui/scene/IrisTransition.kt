package app.regie.game.ui.scene

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import app.regie.game.ui.theme.GameScale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Le noir vert de l'iris (maquette 04), jamais du noir pur. */
val IrisColor = Color(0xFF12201F)

/**
 * La transition en iris : un cercle qui se ferme sur [center] (la porte), ou s'y rouvre si
 * [opening], puis [onFinished]. Le bord est en vrais pixels : des rangées de [scale] pixels physiques,
 * sans anticrénelage. Fermé, l'iris reste noir jusqu'à ce qu'on le retire.
 *
 * [center] est en pixels physiques dans la zone dessinée. Rien n'est alloué d'une image à l'autre.
 */
@Composable
fun IrisTransition(
    center: IntOffset,
    modifier: Modifier = Modifier,
    opening: Boolean = false,
    durationMillis: Int = IRIS_MILLIS,
    scale: Int = GameScale.WORLD,
    color: Color = IrisColor,
    onFinished: () -> Unit = {},
) {
    // 0 : ouvert, 1 : fermé.
    val closed = remember(opening) { mutableFloatStateOf(if (opening) 1f else 0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(opening, durationMillis) {
        animate(closed.floatValue, if (opening) 0f else 1f, animationSpec = tween(durationMillis, easing = LinearEasing)) { value, _ ->
            closed.floatValue = value
        }
        finish()
    }
    Canvas(modifier) {
        drawIris(center, closed.floatValue, scale, color)
    }
}

/** Durée de l'iris, ouverture comme fermeture. */
const val IRIS_MILLIS = 480

/**
 * L'iris fermé à [closed] (0 ouvert, 1 fermé) : tout est noir hors d'un disque centré sur [center],
 * dont le rayon va du coin le plus loin à zéro. Une rangée par pixel du dessin.
 */
internal fun DrawScope.drawIris(center: IntOffset, closed: Float, scale: Int, color: Color) {
    val width = size.width
    val height = size.height
    val farthest = maxOf(
        hypot(center.x.toFloat(), center.y.toFloat()),
        hypot(width - center.x, center.y.toFloat()),
        hypot(center.x.toFloat(), height - center.y),
        hypot(width - center.x, height - center.y),
    )
    // Le rayon, en pixels du dessin, arrondi au pixel.
    val radius = ((1f - closed) * farthest / scale).roundToInt()
    if (radius <= 0) {
        drawRect(color, Offset.Zero, size)
        return
    }
    val rows = ceil(height / scale).toInt()
    val cy = center.y.toFloat() / scale
    val cx = center.x.toFloat() / scale
    val px = scale.toFloat()
    for (row in 0 until rows) {
        val top = row * px
        val dy = row + 0.5f - cy
        if (dy * dy >= radius * radius) {
            drawRect(color, Offset(0f, top), Size(width, px))
            continue
        }
        val half = sqrt(radius * radius - dy * dy)
        val left = (cx - half).roundToInt() * px
        val right = (cx + half).roundToInt() * px
        if (left > 0f) drawRect(color, Offset(0f, top), Size(left, px))
        if (right < width) drawRect(color, Offset(right, top), Size(width - right, px))
    }
}
