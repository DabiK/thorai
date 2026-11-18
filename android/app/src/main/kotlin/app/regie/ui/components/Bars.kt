package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.ui.theme.Palette
import app.regie.ui.theme.sans

/** Un toucher sans effet visuel : les boutons de la maquette n'en ont pas. */
@Composable
fun Modifier.tappable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

/** Action de la barre du bas : puce du bouton et libellé, au doigt comme à la manette. */
@Composable
fun BarAction(button: String, label: String, size: TextUnit = 13.sp, onClick: () -> Unit) {
    ButtonChip(button = button, label = label, modifier = Modifier.tappable(onClick), size = size)
}

/** Un avis à lire : pont injoignable, accès des agents à Android… */
@Composable
fun Notice(title: String, text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).background(color, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = sans(13.sp, weight = 600, color = color))
            BasicText(text, style = sans(12.sp, color = Palette.textMuted, lineHeight = 17.sp))
        }
    }
}

fun Modifier.topDivider() = drawBehind {
    drawLine(Palette.divider, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
}

fun Modifier.bottomDivider() = drawBehind {
    val y = size.height - 0.5f
    drawLine(Palette.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

fun Modifier.startDivider() = drawBehind {
    drawLine(Palette.divider, Offset(0.5f, 0f), Offset(0.5f, size.height), strokeWidth = 1.dp.toPx())
}

/** Même état vide ou chargement sur les deux écrans. */
@Composable
fun StateMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = sans(14.sp, color = Palette.textMuted, lineHeight = 20.sp))
    }
}

/**
 * L'action principale d'un panneau, sur toute la largeur : fond clair, puce du bouton. Désactivée,
 * elle reste lisible sur fond sombre au lieu de passer en gris clair.
 */
@Composable
fun PrimaryAction(key: String?, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 48.dp else 60.dp)
            .background(if (enabled) Palette.text else Palette.surface, RoundedCornerShape(14.dp))
            // Désactivée, elle le dit aussi aux services d'accessibilité et aux tests.
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (key != null) KeyChip(key)
        BasicText(label, style = sans(if (compact) 15.sp else 17.sp, weight = 600, color = if (enabled) Palette.background else Palette.textSubtle), maxLines = 1)
    }
}

/** Où en est une vérification : faite, en cours ou en attente, à faire ; [NONE] : une simple information. */
enum class Mark { DONE, WAITING, TODO, NONE }

/** Repère d'état : coche verte, point ambre, cercle vide. Même forme sur les deux écrans. */
@Composable
fun StatusMark(mark: Mark, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Box(modifier.size(size).drawBehind {
        val stroke = 1.6.dp.toPx()
        when (mark) {
            Mark.DONE -> {
                drawCircle(Palette.done, radius = this.size.minDimension / 2)
                val w = this.size.width
                val path = Path().apply {
                    moveTo(w * 0.28f, w * 0.52f); lineTo(w * 0.44f, w * 0.67f); lineTo(w * 0.73f, w * 0.36f)
                }
                drawPath(path, Palette.background, style = Stroke(width = stroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Mark.WAITING -> {
                drawCircle(Palette.waiting, radius = this.size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                drawCircle(Palette.waiting, radius = this.size.minDimension / 5)
            }
            Mark.TODO -> drawCircle(Palette.keyBorder, radius = this.size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            Mark.NONE -> drawCircle(Palette.textSubtle, radius = this.size.minDimension / 7)
        }
    })
}

/** Barre de progression fine ; sans valeur connue, une barre pleine atténuée. */
@Composable
fun ProgressLine(progress: Float?, modifier: Modifier = Modifier, color: Color = Palette.running) {
    Box(modifier.fillMaxWidth().height(6.dp).background(Palette.surfaceHigh, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(progress?.coerceIn(0f, 1f) ?: 1f).fillMaxHeight()
            .background(if (progress == null) color.copy(alpha = 0.35f) else color, RoundedCornerShape(3.dp)))
    }
}
