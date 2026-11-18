package app.regie.game.ui.theme

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.regie.domain.model.Agent
import app.regie.game.ui.R

/** Agrandissement entier de la vue jeu : un pixel du dessin vaut 4 pixels de l'écran (`da-jeu.md`). */
object GameScale {
    const val WORLD = 4

    /** Les icônes d'interface, seule exception (`da-jeu.md`). */
    const val ICON = 2
}

/** Jetons du thème Aube (`docs/rebrand/outils/thorai-pixel.css`, `.t-aube`). */
@Immutable
data class GameColors(
    val paper: Color = Color(0xFFFBE6B2),
    val paperDeep: Color = Color(0xFFF1DBB3),
    val panel: Color = Color(0xFFFFF4D6),
    val ink: Color = Color(0xFF22343A),
    val muted: Color = Color(0xFF5E6B5F),
    val line: Color = Color(0xFF2D5354),
    val light: Color = Color(0xFFFFFFFF),
    val shade: Color = Color(0xFFE3C995),
    val bar: Color = Color(0xFF2D5354),
    val barInk: Color = Color(0xFFFBE6B2),
    val keyBackground: Color = Color(0xFFFBE6B2),
    val keyInk: Color = Color(0xFF22343A),
    val focus: Color = Color(0xFF1A8657),
    val mine: Color = Color(0xFFD9F0C4),
    val claude: Color = Color(0xFFC1663F),
    val codex: Color = Color(0xFF56708A),
    val opencode: Color = Color(0xFF4F8A3A),
    /** Un agent que le thème ne connaît pas encore : la liste des agents vient du pont. */
    val otherAgent: Color = Color(0xFF605751),
    val work: Color = Color(0xFF1A8657),
    val workInk: Color = Color(0xFFFFFFFF),
    val wait: Color = Color(0xFFF5B53D),
    val waitInk: Color = Color(0xFF3A2A10),
    val idle: Color = Color(0xFFC9C2A8),
    val idleInk: Color = Color(0xFF3E4A44),
    val error: Color = Color(0xFFC2412D),
    val errorInk: Color = Color(0xFFFFFFFF),
    /** Cadre d'alerte : validation et courrier (maquettes 03 et 13). */
    val alertLine: Color = Color(0xFFC98A1E),
    val alertPanel: Color = Color(0xFFFFF1D0),
    val diffAdded: Color = Color(0xFFD6EFC2),
    val diffRemoved: Color = Color(0xFFF7D8CB),
    val scrim: Color = Color(0x8C122022),
) {
    /** La couleur de la tenue de l'habitant, selon l'agent ; une couleur neutre pour un agent inconnu. */
    fun agent(agent: Agent): Color = when (agent.id) {
        "claude" -> claude
        "codex" -> codex
        "opencode" -> opencode
        else -> otherAgent
    }
}

/** Jersey 10 pour les titres, Pixelify Sans pour le texte, VT323 pour le code (SIL OFL 1.1). */
@Immutable
class GameFonts(val display: FontFamily, val body: FontFamily, val code: FontFamily) {
    companion object {
        val Default = GameFonts(
            display = FontFamily(Font(R.font.jersey10)),
            body = FontFamily(listOf(400, 500, 600, 700).map { Font(R.font.pixelify_sans, FontWeight(it)) }),
            code = FontFamily(Font(R.font.vt323)),
        )
    }
}

/**
 * Mesures de la vue jeu, en pixels de l'écran comme dans les maquettes (1920 × 1080 en haut).
 * Un pixel du dessin ([pixel]) fait toujours 4 pixels physiques, quelle que soit la densité : sur la
 * Thor (2,3 px par dp), passer par des dp ronds donnerait des bords flous ou irréguliers.
 */
@Immutable
class GameMetrics(private val density: Density, val scale: Int = GameScale.WORLD) {
    /** Un pixel du dessin, en pixels physiques. */
    val pixelPx: Int get() = scale

    /** Un pixel du dessin, en dp. */
    val pixel: Dp get() = dp(scale)

    /** [screenPx] pixels physiques, en dp. */
    fun dp(screenPx: Int): Dp = (screenPx / density.density).dp

    /** Une taille de police donnée en pixels de l'écran, en sp. */
    fun sp(screenPx: Int): TextUnit = (screenPx / (density.density * density.fontScale)).sp

    /** [pixels] pixels du dessin, en dp. */
    fun art(pixels: Int): Dp = dp(pixels * scale)
}

private val LocalGameColors = staticCompositionLocalOf { GameColors() }
private val LocalGameFonts = staticCompositionLocalOf<GameFonts> { error("GameTheme absent") }
private val LocalGameMetrics = staticCompositionLocalOf<GameMetrics> { error("GameTheme absent") }
private val LocalGameClock = staticCompositionLocalOf<State<Long>> { error("GameTheme absent") }

/**
 * Le thème de la vue jeu : jetons Aube, polices, mesures et horloge des animations.
 * [clock] donne le temps écoulé en millisecondes ; un test peut le figer.
 */
@Composable
fun GameTheme(
    colors: GameColors = GameColors(),
    fonts: GameFonts = GameFonts.Default,
    clock: State<Long> = rememberGameClock(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val metrics = remember(density) { GameMetrics(density) }
    CompositionLocalProvider(
        LocalGameColors provides colors,
        LocalGameFonts provides fonts,
        LocalGameMetrics provides metrics,
        LocalGameClock provides clock,
        content = content,
    )
}

/** Accès aux jetons du thème, comme `MaterialTheme`. */
object Game {
    val colors: GameColors @Composable get() = LocalGameColors.current
    val fonts: GameFonts @Composable get() = LocalGameFonts.current
    val metrics: GameMetrics @Composable get() = LocalGameMetrics.current

    /** Temps écoulé en ms. Le lire dans une phase de dessin évite de recomposer à chaque image. */
    val clock: State<Long> @Composable get() = LocalGameClock.current
}

/**
 * Horloge des animations : une valeur par image, depuis le premier affichage.
 * Elle passe par la politique des animations infinies, que les tests Compose mettent en pause.
 */
@Composable
fun rememberGameClock(): State<Long> {
    val time = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { time.longValue = it - start }
        }
    }
    return time
}

/** Styles de texte du jeu ; les tailles sont en pixels de l'écran, comme dans les maquettes. */
object GameText {
    @Composable
    fun display(sizePx: Int, color: Color = Game.colors.ink) =
        TextStyle(fontFamily = Game.fonts.display, fontSize = Game.metrics.sp(sizePx), color = color, lineHeight = Game.metrics.sp(sizePx))

    @Composable
    fun body(sizePx: Int, color: Color = Game.colors.ink, weight: Int = 400, lineHeightPercent: Int = 130) = TextStyle(
        fontFamily = Game.fonts.body,
        fontSize = Game.metrics.sp(sizePx),
        fontWeight = FontWeight(weight),
        color = color,
        lineHeight = Game.metrics.sp(sizePx * lineHeightPercent / 100),
    )

    @Composable
    fun code(sizePx: Int, color: Color = Game.colors.ink) =
        TextStyle(fontFamily = Game.fonts.code, fontSize = Game.metrics.sp(sizePx), color = color, lineHeight = Game.metrics.sp(sizePx * 125 / 100))
}
