package app.regie.game.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.regie.domain.model.SessionStatus
import app.regie.game.ui.R
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/** Les états qu'une maison affiche de loin (`da-jeu.md`, principe 2). */
enum class TagState {
    WORKING, WAITING, IDLE, FAILED;

    companion object {
        /** Une session arrêtée se lit comme au repos : sa maison reste, l'habitant dort. */
        fun of(status: SessionStatus): TagState = when (status) {
            SessionStatus.WORKING -> WORKING
            SessionStatus.AWAITING_APPROVAL -> WAITING
            SessionStatus.IDLE, SessionStatus.STOPPED -> IDLE
            SessionStatus.FAILED -> FAILED
        }
    }
}

/** Libellé par défaut d'un état. */
@Composable
fun tagLabel(state: TagState): String = stringResource(
    when (state) {
        TagState.WORKING -> R.string.game_state_working
        TagState.WAITING -> R.string.game_state_waiting
        TagState.IDLE -> R.string.game_state_idle
        TagState.FAILED -> R.string.game_state_failed
    },
)

/** Fond et encre d'un état. */
@Composable
fun tagColors(state: TagState): Pair<Color, Color> = Game.colors.let {
    when (state) {
        TagState.WORKING -> it.work to it.workInk
        TagState.WAITING -> it.wait to it.waitInk
        TagState.IDLE -> it.idle to it.idleInk
        TagState.FAILED -> it.error to it.errorInk
    }
}

/**
 * Étiquette d'état : au travail avec trois points animés, attend ta validation avec « ! »,
 * au repos, échec. [label] remplace le libellé (« Étape 3 sur 5 »), jamais la couleur.
 */
@Composable
fun StateTag(state: TagState, modifier: Modifier = Modifier, label: String = tagLabel(state)) {
    val metrics = Game.metrics
    val (background, ink) = tagColors(state)
    Row(
        modifier.background(background).padding(horizontal = metrics.dp(14), vertical = metrics.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        if (state == TagState.WORKING) WorkDots(ink)
        BasicText(if (state == TagState.WAITING) "! $label" else label, style = GameText.body(24, ink, lineHeightPercent = 100), maxLines = 1)
    }
}

/** Trois points de deux pixels qui s'allument tour à tour. */
@Composable
fun WorkDots(color: Color, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val clock = Game.clock
    Canvas(modifier.size(metrics.art(8), metrics.art(2))) {
        val p = metrics.pixelPx.toFloat()
        val time = clock.value
        repeat(3) { i ->
            drawRect(color.copy(alpha = Blink.dot(time, i)), Offset(i * 3 * p, 0f), Size(2 * p, 2 * p))
        }
    }
}
