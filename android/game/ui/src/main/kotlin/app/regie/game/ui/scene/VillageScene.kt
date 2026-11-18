package app.regie.game.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierAction
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.render.Camera
import app.regie.game.ui.render.GroundPainter
import app.regie.game.ui.render.PixelWorld
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.render.fallbacks
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale
import app.regie.game.ui.village.houseStates

/**
 * La scène du hameau : le monde ([PixelWorld]), le coursier qui glisse d'une tuile à l'autre, la
 * caméra qui le suit, et l'iris qui se ferme sur la porte quand il entre dans une maison.
 *
 * - [state] porte le coursier ; l'app lui envoie les commandes de la manette.
 * - [onEnterHouse] : l'iris est fermé sur la porte de la session ; l'app ouvre alors la maison, puis
 *   appelle [VillageSceneState.leaveHouse] au retour.
 * - [overlay] : ce qu'on accroche au monde (panneaux des maisons, bulles), avec la projection de la
 *   caméra ; il passe sous l'iris.
 *
 * Marcher ne recompose rien : la caméra et le coursier sont lus au dessin.
 */
@Composable
fun VillageScene(
    state: VillageSceneState,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    onEnterHouse: (SessionId) -> Unit = {},
    scale: Int = GameScale.WORLD,
    clock: State<Long> = Game.clock,
    overlay: @Composable BoxScope.(WorldProjection) -> Unit = {},
) {
    val village = state.village
    val fallbacks = remember(village) { village.fallbacks() }
    // Fenêtres, fumée et lumière des portes selon l'état de chaque session (ticket 52).
    val states = remember(village) { village.houseStates() }
    val look = remember(assets) { CourierLook(assets) }
    val occluders = remember(village.world.objects, assets, fallbacks, states) { occludersOf(village.world.objects, assets, fallbacks, states) }
    val enter by rememberUpdatedState(onEnterHouse)
    LaunchedEffect(state) { state.runFrames() }

    val focus = remember(state) { { state.cameraFocus } }
    val outline = Color(assets.palette.outline)
    val fallbackColor = Game.colors.focus
    PixelWorld(village.world, assets, focus, modifier.fillMaxSize(), fallbacks = fallbacks, scale = scale, clock = clock, states = states) { projection ->
        Canvas(Modifier.fillMaxSize()) {
            // La même caméra que PixelWorld, calculée sans allouer.
            val viewWidth = (size.width.toInt() + scale - 1) / scale
            val viewHeight = (size.height.toInt() + scale - 1) / scale
            val map = state.village.world.map
            val foot = state.foot
            val camera = state.cameraFocus
            val originX = Camera.axis(camera.x, map.width * GroundPainter.TILE, viewWidth)
            val originY = Camera.axis(camera.y, map.height * GroundPainter.TILE, viewHeight)
            val action = state.action
            // La marche suit le coursier (elle repart à chaque départ) ; le reste suit l'horloge du thème.
            val time = clock.value
            val sprite = look.sprite(action, state.facing, if (action == CourierAction.WALK) state.actionMillis else time)
            drawCourier(sprite, foot.x, foot.y, originX, originY, scale, occluders, assets, time, fallbackColor, outline)
        }
        overlay(projection)
        val entering = state.entering
        if (entering != null) {
            // Sur la porte ; sur le coursier si la maison a disparu entre-temps, pour finir quand même.
            val door = village.houseOf(entering)?.door
            IrisTransition(
                center = projection.toScreen(door?.let(Camera::center) ?: state.cameraFocus),
                modifier = Modifier.fillMaxSize(),
                scale = scale,
                onFinished = { enter(entering) },
            )
        } else if (state.leaving) {
            // Le coursier est devant la porte ; l'iris se rouvre sur lui.
            IrisTransition(
                center = projection.toScreen(state.cameraFocus),
                modifier = Modifier.fillMaxSize(),
                opening = true,
                scale = scale,
                onFinished = state::irisOpened,
            )
        }
    }
}

