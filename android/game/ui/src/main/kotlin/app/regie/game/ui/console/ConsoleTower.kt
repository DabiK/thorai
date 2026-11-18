package app.regie.game.ui.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.menu.TOWER_BACKGROUND
import app.regie.game.ui.menu.inhabitantSprite
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/** La tour, au pixel natif : la bande montrée et les pieds de l'habitant (`fonds.json`, `scenes`). */
object TowerScene {
    /** Les pieds de l'habitant, sur son tapis devant le miroir. */
    const val INHABITANT_X = 236
    const val INHABITANT_Y = 176

    /** La bande montrée : 310 pixels de large (l'écran du bas), centrée sur l'habitant. */
    const val WIDTH = 310
    const val LEFT = INHABITANT_X - WIDTH / 2

    /** La hauteur de l'en-tête : le miroir et l'habitant, sans prendre la place du carnet. */
    const val HEADER_HEIGHT = 72

    /** Le haut de la bande : les pieds de l'habitant à six pixels du bas. */
    fun top(height: Int): Int = INHABITANT_Y + 6 - height

    /** La pose de l'habitant selon l'état de la session. */
    fun pose(status: SessionStatus): String = when (status) {
        SessionStatus.WORKING -> "travail"
        SessionStatus.AWAITING_APPROVAL -> "attente"
        SessionStatus.IDLE, SessionStatus.FAILED -> "repos"
        SessionStatus.STOPPED -> "sommeil"
    }
}

/**
 * L'en-tête de la tour de la console (maquette 12), au-dessus du carnet de la session quand une
 * autre appli occupe le haut : l'habitant devant son miroir, dans la pose de son état, le lieu,
 * l'agent qui pilote Android et l'état de la session. Il n'ajoute aucune information au carnet du
 * dessous et n'en retire aucune : fil, avis, brouillon et touches restent ceux de la session.
 */
@Composable
fun ConsoleTowerHeader(session: Session, assets: GameAssets, modifier: Modifier = Modifier, heightArt: Int = TowerScene.HEADER_HEIGHT) {
    val metrics = Game.metrics
    Box(modifier.fillMaxWidth().height(metrics.art(heightArt)).clipToBounds().testTag("console-tower")) {
        TowerBand(session, assets, heightArt)
        PixelFrame(
            Modifier.padding(start = metrics.dp(24), top = metrics.dp(20)),
            contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(10)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                BasicText(stringResource(R.string.game_tower_title), style = GameText.display(44), maxLines = 1)
                BasicText(stringResource(R.string.game_tower_pilots, session.agent.label), style = GameText.body(24, Game.colors.muted), maxLines = 1)
            }
        }
        StateTag(TagState.of(session.status), Modifier.align(Alignment.TopEnd).padding(top = metrics.dp(28), end = metrics.dp(24)))
    }
}

/** L'image de la tour, recadrée sur l'habitant, et l'habitant dans la pose de son état. */
@Composable
private fun TowerBand(session: Session, assets: GameAssets, heightArt: Int) {
    val metrics = Game.metrics
    val clock = Game.clock
    val p = metrics.pixelPx
    val top = TowerScene.top(heightArt)
    Canvas(Modifier.fillMaxSize().background(Color(0xFF1B5F53))) {
        assets.resolve(TOWER_BACKGROUND)?.let { tower ->
            val f = tower.frame
            drawImage(
                tower.atlas.image,
                srcOffset = IntOffset(f.x + TowerScene.LEFT, f.y + top),
                srcSize = IntSize(TowerScene.WIDTH, heightArt),
                dstSize = IntSize(TowerScene.WIDTH * p, heightArt * p),
                filterQuality = FilterQuality.None,
            )
        }
        val sprite = assets.resolve(inhabitantSprite(session.agent, TowerScene.pose(session.status)), clock.value) ?: return@Canvas
        val f = sprite.frame
        drawImage(
            sprite.atlas.image,
            srcOffset = IntOffset(f.x, f.y),
            srcSize = IntSize(f.w, f.h),
            dstOffset = IntOffset((TowerScene.INHABITANT_X - TowerScene.LEFT - f.ax) * p, (TowerScene.INHABITANT_Y - top - f.ay) * p),
            dstSize = IntSize(f.w * p, f.h * p),
            filterQuality = FilterQuality.None,
        )
    }
}
