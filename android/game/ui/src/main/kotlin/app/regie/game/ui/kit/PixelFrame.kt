package app.regie.game.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameMetrics

/** Couleurs d'un cadre : le trait, le fond, la lumière en haut et l'ombre en bas. */
@Immutable
data class FrameColors(val line: Color, val fill: Color, val light: Color, val shade: Color)

/** Les cadres du thème Aube. */
object FrameDefaults {
    @Composable
    fun panel() = Game.colors.let { FrameColors(it.line, it.panel, it.light, it.shade) }

    /** Cadre choisi (focus). */
    @Composable
    fun focus() = Game.colors.let { FrameColors(it.focus, it.panel, it.light, it.shade) }

    /** Validation en attente, courrier. */
    @Composable
    fun alert() = Game.colors.let { FrameColors(it.alertLine, it.alertPanel, it.light, it.shade) }

    /** Sur le papier du bas, sans fond propre (terrain libre). */
    @Composable
    fun paper() = Game.colors.let { FrameColors(it.line, it.paper, it.light, it.shade) }

    /** Fond plein d'une couleur d'état (panneau qui attend ta validation). */
    @Composable
    fun filled(fill: Color) = Game.colors.let { FrameColors(it.line, fill, fill, fill) }
}

/**
 * Dessine un cadre pixel autour du contenu, bord compris dans la mise en page :
 * - fin : un pixel de trait, coins crantés ;
 * - épais : deux pixels de trait, coins en escalier.
 * Une ligne de lumière en haut et d'ombre en bas, à l'intérieur (`thorai-pixel.css`, `.pbox` et `.frame`).
 */
fun Modifier.pixelFrame(colors: FrameColors, metrics: GameMetrics, thick: Boolean = false): Modifier =
    drawBehind { drawPixelFrame(colors, metrics.pixelPx.toFloat(), thick) }
        .padding(metrics.art(if (thick) 2 else 1))

internal fun DrawScope.drawPixelFrame(colors: FrameColors, p: Float, thick: Boolean) {
    val w = size.width
    val h = size.height
    val b = if (thick) 2 * p else p
    // Trait : quatre bandes, sans les coins.
    drawRect(colors.line, Offset(b, 0f), Size(w - 2 * b, b))
    drawRect(colors.line, Offset(b, h - b), Size(w - 2 * b, b))
    drawRect(colors.line, Offset(0f, b), Size(b, h - 2 * b))
    drawRect(colors.line, Offset(w - b, b), Size(b, h - 2 * b))
    if (thick) {
        // Marche de l'escalier dans chaque coin.
        listOf(Offset(p, p), Offset(w - 2 * p, p), Offset(p, h - 2 * p), Offset(w - 2 * p, h - 2 * p))
            .forEach { drawRect(colors.line, it, Size(p, p)) }
    }
    drawRect(colors.fill, Offset(b, b), Size(w - 2 * b, h - 2 * b))
    drawRect(colors.light, Offset(b, b), Size(w - 2 * b, p))
    drawRect(colors.shade, Offset(b, h - b - p), Size(w - 2 * b, p))
}

/** Un cadre pixel avec son contenu. */
@Composable
fun PixelFrame(
    modifier: Modifier = Modifier,
    thick: Boolean = false,
    colors: FrameColors = FrameDefaults.panel(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.pixelFrame(colors, Game.metrics, thick).padding(contentPadding), content = content)
}

/**
 * Formes au pixel près : rectangle aux coins crantés d'un pixel, et octogone en escalier
 * des touches A, B, X et Y. [pixelPx] est la taille d'un pixel du dessin en pixels physiques.
 */
class PixelShape private constructor(private val pixelPx: Float, private val octagon: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = pixelPx
        val w = size.width
        val h = size.height
        val points = if (octagon) {
            val q = 3 * p
            listOf(
                q to 0f, w - q to 0f, w - q to p, w - p to p, w - p to q, w to q,
                w to h - q, w - p to h - q, w - p to h - p, w - q to h - p, w - q to h,
                q to h, q to h - p, p to h - p, p to h - q, 0f to h - q,
                0f to q, p to q, p to p, q to p,
            )
        } else {
            listOf(
                0f to p, p to p, p to 0f, w - p to 0f, w - p to p, w to p,
                w to h - p, w - p to h - p, w - p to h, p to h, p to h - p, 0f to h - p,
            )
        }
        val path = Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { (x, y) -> lineTo(x, y) }
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is PixelShape && other.pixelPx == pixelPx && other.octagon == octagon
    override fun hashCode() = 31 * pixelPx.hashCode() + octagon.hashCode()

    companion object {
        fun notched(pixelPx: Int) = PixelShape(pixelPx.toFloat(), octagon = false)
        fun octagon(pixelPx: Int) = PixelShape(pixelPx.toFloat(), octagon = true)
    }
}
