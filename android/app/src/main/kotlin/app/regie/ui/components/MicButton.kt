package app.regie.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import app.regie.R
import app.regie.ui.theme.Palette

/**
 * Bouton micro : maintenu au doigt, il fait comme L2 maintenu.
 * [active] le colore quand une gâchette est tenue.
 */
@Composable
fun MicButton(
    active: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 116.dp,
    halo: Dp = 10.dp,
) {
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val ring = if (active) Palette.mic else Palette.stepPending
    val haloColor = Palette.mic.copy(alpha = if (active) 0.22f else 0.08f)
    Box(
        modifier = modifier
            .size(size)
            .drawBehind { drawCircle(haloColor, radius = this.size.minDimension / 2 + halo.toPx()) }
            .background(Palette.surface, CircleShape)
            .border(2.dp, ring, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    press()
                    // Un toucher annulé (composition fermée) doit aussi relâcher la gâchette.
                    try {
                        tryAwaitRelease()
                    } finally {
                        release()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = stringResource(R.string.mic_description),
            colorFilter = ColorFilter.tint(if (active) Palette.mic else Palette.text),
            modifier = Modifier.size((size * 0.345f).coerceAtLeast(18.dp)),
        )
    }
}
