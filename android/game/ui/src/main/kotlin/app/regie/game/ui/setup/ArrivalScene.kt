package app.regie.game.ui.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.regie.domain.model.SetupReview
import app.regie.domain.model.SetupStep
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.courier.CourierController
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.setup.ArrivalRoad
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.SignPanel
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.menu.inhabitantSprite
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.GroundPainter
import app.regie.game.ui.render.PixelWorld
import app.regie.game.ui.render.Sprite
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.scene.CourierLook
import app.regie.game.ui.scene.drawCourier
import app.regie.game.ui.scene.occludersOf
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * L'arrivée au hameau (ticket 53) : la route du parcours d'accueil, un panneau par étape. Le coursier
 * marche jusqu'au panneau de l'étape affichée, au pas du hameau, et le lit ; la caméra le suit. Une
 * case au-dessus de chaque autre panneau dit où on en est (verte : prête ; vide : à faire), comme le
 * journal de quête. Le panneau de l'étape porte son nom et son état, s'il tient dans la vue. Les
 * habitants des agents annoncés par le pont attendent près du panneau des comptes.
 *
 * [clearPx] : la hauteur, depuis le haut, que les cadres posés sur la scène laissent voir ; la caméra
 * y cadre le panneau et le coursier.
 *
 * Marcher ne recompose rien : la position du coursier est lue au dessin.
 */
@Composable
internal fun ArrivalScene(review: SetupReview, assets: GameAssets, modifier: Modifier = Modifier, clearPx: Int? = null) {
    val road = remember { ArrivalRoad.build() }
    val look = remember(assets) { CourierLook(assets) }
    val occluders = remember(road, assets) { occludersOf(road.world.objects, assets, emptyMap()) }
    val step = review.step
    val target = footX(road.stand(step))
    // Il arrive toujours de quelques pas plus loin : on le voit entrer, même sur une étape rouverte.
    val walk = remember { Walk(Animatable(maxOf(footX(road.entry), target - ARRIVAL_TILES * TILE).toFloat())) }
    LaunchedEffect(target) {
        val from = walk.x.value
        walk.from = from
        val millis = (abs(target - from) * CourierController.STEP_MILLIS / TILE).roundToInt()
        walk.x.animateTo(target.toFloat(), tween(millis, easing = LinearEasing))
    }
    val inhabitants = review.checks.agents.orEmpty().take(road.gathering.size).mapIndexed { index, agent ->
        val pose = when (agent.connected) {
            true -> "joie"
            false -> "attente"
            null -> "repos"
        }
        Sprite(inhabitantSprite(agent.agent, pose), foot(road.gathering[index]))
    }
    val sign = road.sign(step)
    val signFoot = footX(sign.anchor)
    val signTop = foot(sign.anchor).y - signHeight(assets, sign.objectId)
    val courierFoot = foot(road.stand(step)).y
    val colors = Game.colors
    val clock = Game.clock
    BoxWithConstraints(modifier.testTag("setup-road").semantics { contentDescription = "coursier.${step.name.lowercase()}" }) {
        val scale = GameScale.WORLD
        val viewHeight = (constraints.maxHeight + scale - 1) / scale
        val clear = clearPx?.let { it.coerceAtLeast(0) / scale }?.coerceAtMost(viewHeight) ?: viewHeight
        // Le panneau de l'étape et le coursier, au milieu de ce qui se voit ; sans la place, le coursier
        // et son panneau en bas de ce qui se voit.
        val top = signTop - PANEL_ART
        val bottom = courierFoot + 4
        val roomy = clear >= bottom - top
        val viewTop = if (roomy) top - (clear - (bottom - top)) / 2 else bottom - clear
        val focusY = viewTop + viewHeight / 2
        val focus = remember(focusY) { { ArtPoint(walk.x.value.roundToInt(), focusY) } }
        PixelWorld(road.world, assets, focus, Modifier.fillMaxSize(), sprites = inhabitants) { projection ->
            Canvas(Modifier.fillMaxSize()) {
                val origin = projection.origin
                val time = clock.value
                // Les cases de l'avancement, au-dessus de chaque panneau sauf celui de l'étape.
                road.signs.forEachIndexed { index, placed ->
                    val shown = SetupStep.entries[index]
                    if (shown == step && roomy) return@forEachIndexed
                    val markTop = foot(placed.anchor).y - signHeight(assets, placed.objectId) - MARK - 3
                    val left = footX(placed.anchor) - MARK / 2
                    drawMark(
                        when {
                            shown == step -> Mark.CURRENT
                            review.checks.isDone(shown) && shown != SetupStep.FINISH -> Mark.DONE
                            else -> Mark.TODO
                        },
                        (left - origin.x) * scale.toFloat(), (markTop - origin.y) * scale.toFloat(), scale.toFloat(),
                        colors.work, colors.wait, colors.idle, Blink.pulse(time),
                    )
                }
                val x = walk.x.value.roundToInt()
                val walking = walk.x.isRunning
                val facing = when {
                    !walking -> Direction.UP
                    target < walk.from -> Direction.LEFT
                    else -> Direction.RIGHT
                }
                val millis = if (walking) (abs(x - walk.from) * CourierController.STEP_MILLIS / TILE).toLong() else time
                val sprite = look.sprite(if (walking) CourierAction.WALK else CourierAction.IDLE, facing, millis)
                drawCourier(sprite, x, courierFoot, origin.x, origin.y, scale, occluders, assets, time, colors.focus, Color(assets.palette.outline))
            }
            if (roomy) {
                val done = review.checks.isDone(step)
                val state = when {
                    step == SetupStep.WELCOME || step == SetupStep.FINISH -> null
                    done -> TagState.WORKING
                    else -> TagState.WAITING
                }
                SignPanel(
                    shortLabel(step),
                    Modifier.anchorBottomCenter { projection.toScreen(ArtPoint(signFoot, signTop - 1)) }.testTag("setup-sign").semantics(mergeDescendants = true) {},
                    subtitle = counter(step),
                    state = state,
                    stateLabel = state?.let { stringResource(if (done) R.string.game_setup_sign_ready else R.string.game_setup_sign_todo) },
                )
            }
        }
    }
}

/** La marche en cours : la position des pieds, en pixels du dessin, et d'où elle est partie. */
private class Walk(val x: Animatable<Float, *>) {
    var from: Float = x.value
}

private enum class Mark { DONE, CURRENT, TODO }

/** Prête : case verte ; l'étape affichée : case ambre qui clignote ; à faire : case vide (journal de quête). */
private fun DrawScope.drawMark(mark: Mark, left: Float, top: Float, p: Float, done: Color, current: Color, todo: Color, pulse: Float) {
    val size = Size(MARK * p, MARK * p)
    when (mark) {
        Mark.DONE -> drawRect(done, Offset(left, top), size)
        Mark.CURRENT -> drawRect(current.copy(alpha = pulse), Offset(left, top), size)
        Mark.TODO -> drawRect(todo, Offset(left + p / 2, top + p / 2), Size(size.width - p, size.height - p), style = Stroke(p))
    }
}

private const val TILE = GroundPainter.TILE

/** Les pas du coursier avant d'atteindre le panneau, à l'ouverture. */
private const val ARRIVAL_TILES = 4

/** La hauteur du panneau d'étape et de sa pointe, en pixels du dessin. */
private const val PANEL_ART = 46

/** Le côté d'une case d'avancement, en pixels du dessin. */
private const val MARK = 5

/** Les pieds d'un personnage ou d'un objet d'une tuile, posé sur [tile]. */
private fun foot(tile: TilePos) = ArtPoint(tile.x * TILE + TILE / 2, tile.y * TILE + TILE - 1)

private fun footX(tile: TilePos) = tile.x * TILE + TILE / 2

/** La hauteur de l'image d'un objet, en pixels du dessin ; une tuile et demie sans atlas. */
private fun signHeight(assets: GameAssets, id: String): Int =
    assets.resolve(id)?.let { it.frame.h / it.atlas.spec.pixel } ?: (TILE * 3 / 2)
