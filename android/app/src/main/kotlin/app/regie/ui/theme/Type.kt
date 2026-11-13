package app.regie.ui.theme

import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified

/** Geist et Geist Mono, polices variables embarquées dans `assets/fonts` avec leur licence OFL. */
@Immutable
class RegieFonts(val sans: FontFamily, val mono: FontFamily) {
    companion object {
        private val SANS_WEIGHTS = listOf(400, 500, 600, 650, 700)
        private val MONO_WEIGHTS = listOf(400, 500, 600)

        fun load(assets: AssetManager) = RegieFonts(
            sans = family("fonts/Geist.ttf", SANS_WEIGHTS, assets),
            mono = family("fonts/GeistMono.ttf", MONO_WEIGHTS, assets),
        )

        private fun family(path: String, weights: List<Int>, assets: AssetManager) =
            FontFamily(weights.map { Font(path, assets, FontWeight(it)) })
    }
}

private val LocalFonts = staticCompositionLocalOf<RegieFonts> { error("RegieTheme absent") }

@Composable
fun RegieTheme(content: @Composable () -> Unit) {
    val assets = LocalContext.current.applicationContext.assets
    val fonts = remember(assets) { RegieFonts.load(assets) }
    CompositionLocalProvider(LocalFonts provides fonts, content = content)
}

/** Style en Geist. */
@Composable
fun sans(
    size: TextUnit,
    weight: Int = 400,
    color: Color = Palette.text,
    letterSpacing: TextUnit = Unspecified,
    lineHeight: TextUnit = Unspecified,
) = TextStyle(
    fontFamily = LocalFonts.current.sans,
    fontSize = size,
    fontWeight = FontWeight(weight),
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
)

/** Style en Geist Mono. */
@Composable
fun mono(
    size: TextUnit,
    weight: Int = 400,
    color: Color = Palette.text,
    letterSpacing: TextUnit = Unspecified,
) = TextStyle(
    fontFamily = LocalFonts.current.mono,
    fontSize = size,
    fontWeight = FontWeight(weight),
    color = color,
    letterSpacing = letterSpacing,
)
