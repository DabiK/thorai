package app.regie.game.ui.courier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.game.engine.courier.Courier
import app.regie.game.engine.courier.Letter
import app.regie.game.engine.courier.LetterAge
import app.regie.game.engine.courier.LetterKind
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.Toast
import app.regie.game.ui.kit.ToastHost
import app.regie.game.ui.menu.PaperScreen
import app.regie.game.ui.menu.TapHint
import app.regie.game.ui.menu.tapped
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** L'icône d'une lettre : la cloche d'une validation, la lettre d'une question, le rouleau d'une fin, le parchemin d'un échec. */
fun letterIcon(kind: LetterKind): String = when (kind) {
    LetterKind.APPROVAL -> "ui.cloche"
    LetterKind.QUESTION -> "ui.lettre"
    LetterKind.FINISHED -> "ui.rouleau"
    LetterKind.FAILED -> "ui.parchemin"
}

@Composable
private fun iconBackground(kind: LetterKind): Color = when (kind) {
    LetterKind.APPROVAL -> Game.colors.wait
    LetterKind.FAILED -> Game.colors.diffRemoved
    LetterKind.QUESTION, LetterKind.FINISHED -> Game.colors.paperDeep
}

@Composable
fun ageLabel(age: LetterAge): String = when (age.unit) {
    LetterAge.Unit.NOW -> stringResource(R.string.game_age_now)
    LetterAge.Unit.MINUTES -> stringResource(R.string.game_age_minutes, age.value.toInt())
    LetterAge.Unit.HOURS -> stringResource(R.string.game_age_hours, age.value.toInt())
    LetterAge.Unit.DAYS -> stringResource(R.string.game_age_days, age.value.toInt())
}

/** Une lettre : pastille non lue, icône, titre et détail du domaine, ancienneté. */
@Composable
private fun LetterRow(letter: Letter, focused: Boolean, now: Instant, assets: GameAssets, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    Box(modifier.fillMaxWidth().semantics { selected = focused }) {
        if (focused) PixelArrow(ArrowDirection.RIGHT, Modifier.align(Alignment.CenterStart), color = colors.focus, blink = true)
        PixelFrame(
            Modifier.fillMaxWidth().padding(start = metrics.dp(36)),
            colors = if (focused) FrameDefaults.focus() else FrameDefaults.panel(),
            contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(12)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(20))) {
                Box(Modifier.size(metrics.dp(16)).background(if (letter.read) Color.Transparent else colors.error))
                Box(Modifier.size(metrics.dp(88)).background(iconBackground(letter.kind)), contentAlignment = Alignment.Center) {
                    SpriteImage(letterIcon(letter.kind), assets, scale = GameScale.ICON, placeholderPixels = 20)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
                    BasicText(
                        letter.title,
                        style = GameText.body(32, if (letter.kind == LetterKind.FAILED) colors.error else colors.ink, weight = 600),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(letter.detail, style = GameText.body(26, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicText(ageLabel(Courier.age(letter.receivedAt, now)), style = GameText.body(24, colors.muted), maxLines = 1)
            }
        }
    }
}

/** Les premières lettres, pour le menu de pause ; [focused] : la lettre choisie, -1 sans curseur. */
@Composable
fun CourierRows(courier: Courier, focused: Int, now: Instant, assets: GameAssets, limit: Int = Int.MAX_VALUE) {
    Column(verticalArrangement = Arrangement.spacedBy(Game.metrics.dp(12))) {
        val start = if (focused >= limit) focused - limit + 1 else 0
        courier.letters.drop(start).take(limit).forEachIndexed { index, letter ->
            LetterRow(letter, index + start == focused, now, assets)
        }
    }
}

/** Le courrier, sur l'écran du bas (maquette 13) : la liste, le nombre de lettres non lues et les touches. */
@Composable
fun CourierScreen(
    courier: Courier,
    selected: Int,
    now: Instant,
    assets: GameAssets,
    hints: List<TapHint>,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit = {},
) {
    val list = rememberLazyListState()
    LaunchedEffect(selected, courier.letters.size) {
        if (courier.letters.isNotEmpty()) list.animateScrollToItem(selected.coerceIn(courier.letters.indices))
    }
    PaperScreen(
        stringResource(R.string.game_courier_title),
        hints,
        modifier.testTag("game-courier"),
        headEnd = {
            if (courier.unread > 0) {
                BasicText(pluralStringResource(R.plurals.game_menu_unread, courier.unread, courier.unread), style = GameText.body(28, Game.colors.muted))
            }
        },
    ) {
        if (courier.letters.isEmpty()) {
            Spacer(Modifier.weight(1f))
            BasicText(stringResource(R.string.game_courier_empty), Modifier.align(Alignment.CenterHorizontally), style = GameText.body(30, Game.colors.muted))
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(Game.metrics.dp(16))) {
                itemsIndexed(courier.letters, key = { _, letter -> letter.key }) { index, letter ->
                    LetterRow(letter, index == selected, now, assets, Modifier.tapped { onTap(index) })
                }
            }
        }
    }
}

/** Combien de temps l'oiseau reste, et jusqu'à quand une lettre est encore « qui arrive ». */
object CourierTiming {
    val SHOWN = 5.seconds
    val FRESH = 8.seconds
}

/**
 * L'oiseau messager (maquette 13) : quand une lettre arrive, un bandeau descend du haut de l'écran,
 * avec l'oiseau qui bat des ailes, le titre et le détail de la lettre. [action] : la touche qui
 * l'ouvre là où on est (R1 sur le hameau pour une validation), sinon aucune.
 * Une lettre déjà ancienne (activité recréée, par exemple) ne refait pas venir l'oiseau.
 */
@Composable
fun CourierToast(
    letter: Letter?,
    now: () -> Instant,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    action: KeyHintSpec? = null,
) {
    var shown by remember { mutableStateOf<Letter?>(null) }
    LaunchedEffect(letter?.key) {
        val fresh = letter?.takeIf { now() - it.receivedAt < CourierTiming.FRESH } ?: return@LaunchedEffect
        shown = fresh
        delay(CourierTiming.SHOWN)
        if (shown?.key == fresh.key) shown = null
    }
    val metrics = Game.metrics
    ToastHost(shown != null, modifier.padding(top = metrics.dp(36)).testTag("game-courier-toast")) {
        val current = shown ?: letter ?: return@ToastHost
        Toast(
            current.title,
            subtitle = current.detail,
            alert = current.kind == LetterKind.APPROVAL,
            icon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteImage("ui.oiseau.vol", assets, scale = GameScale.ICON, placeholderPixels = 32)
                    Spacer(Modifier.width(metrics.dp(8)))
                    SpriteImage("ui.lettre", assets, scale = GameScale.ICON, placeholderPixels = 20)
                }
            },
            action = action?.takeIf { current.kind == LetterKind.APPROVAL },
        )
    }
}
