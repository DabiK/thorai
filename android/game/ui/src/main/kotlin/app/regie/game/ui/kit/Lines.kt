package app.regie.game.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.DiffKind
import app.regie.domain.model.DiffLine
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlin.math.roundToInt

/**
 * Une ligne de diff sur la feuille épinglée : numéro, signe, texte en VT323. Ajoutée en vert pâle,
 * retirée en rose ; la ligne choisie est encadrée. Le texte de l'agent n'est jamais réécrit.
 */
@Composable
fun PixelDiffLine(line: DiffLine, modifier: Modifier = Modifier, selected: Boolean = false) {
    val metrics = Game.metrics
    val colors = Game.colors
    val background = when (line.kind) {
        DiffKind.ADDED -> colors.diffAdded
        DiffKind.REMOVED -> colors.diffRemoved
        DiffKind.CONTEXT -> Color.Transparent
    }
    val sign = when (line.kind) {
        DiffKind.ADDED -> "+"
        DiffKind.REMOVED -> "−"
        DiffKind.CONTEXT -> " "
    }
    val number = (if (line.kind == DiffKind.REMOVED) line.oldLine else line.newLine ?: line.oldLine)?.toString().orEmpty()
    val frame = colors.focus
    val stroke = metrics.pixelPx.toFloat()
    Row(
        modifier.fillMaxWidth().background(background)
            .drawWithContent {
                drawContent()
                if (selected) {
                    drawRect(frame, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                }
            }
            .padding(horizontal = metrics.dp(18)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(18)),
    ) {
        BasicText(number, Modifier.width(metrics.dp(48)), style = GameText.code(34, colors.muted).copy(textAlign = TextAlign.End), maxLines = 1)
        BasicText(sign, Modifier.width(metrics.dp(16)), style = GameText.code(34), maxLines = 1)
        BasicText(line.text, style = GameText.code(34), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** Hauteurs des barres du niveau du micro, en pixels du dessin : des marches, jamais nulles. */
object MicLevels {
    fun steps(levels: List<Float>, maxSteps: Int): List<Int> =
        levels.map { (it.coerceIn(0f, 1f) * maxSteps).roundToInt().coerceIn(1, maxSteps) }
}

/** Niveau du micro : des barres en escalier, de 0 à 1, en couleur « au travail ». */
@Composable
fun MicMeter(levels: List<Float>, modifier: Modifier = Modifier, heightPx: Int = 64, color: Color = Game.colors.work) {
    val metrics = Game.metrics
    val p = metrics.pixelPx
    val barWidth = 3
    val gap = 2
    val maxSteps = heightPx / p
    val heights = MicLevels.steps(levels, maxSteps)
    val widthArt = if (heights.isEmpty()) 0 else heights.size * barWidth + (heights.size - 1) * gap
    Canvas(modifier.width(metrics.art(widthArt)).height(metrics.art(maxSteps))) {
        heights.forEachIndexed { i, steps ->
            drawRect(
                color,
                Offset(i * (barWidth + gap) * p.toFloat(), (maxSteps - steps) * p.toFloat()),
                Size(barWidth * p.toFloat(), steps * p.toFloat()),
            )
        }
    }
}
