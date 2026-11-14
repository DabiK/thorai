package app.regie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
fun BarAction(button: String, label: String, onClick: () -> Unit) {
    ButtonChip(button = button, label = label, modifier = Modifier.tappable(onClick))
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
