package app.regie.game.ui.village

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.KeyHint
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.SignPanel
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.scene.VillageScene
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.theme.Game

/** Ce qu'un calque du hameau reçoit : la scène (hameau et coursier) et la projection de la caméra. */
@Stable
class VillageOverlayScope(val scene: VillageSceneState, val projection: WorldProjection)

/**
 * Un calque accroché au hameau, sous l'iris. [HouseStateOverlay] est celui du ticket 45 ; un calque
 * plus riche (ticket 52) se branche à sa place par le paramètre `overlay` de [VillageTopScreen].
 */
typealias VillageOverlay = @Composable BoxScope.(VillageOverlayScope) -> Unit

/** Le calque par défaut : l'état de chaque maison et le panneau de la maison choisie. */
val DefaultVillageOverlay: VillageOverlay = { HouseStateOverlay(it) }

/**
 * L'écran du haut de la vue d'ensemble (maquette 03) : le hameau généré, le coursier et le calque
 * des maisons. [onEnterHouse] : l'iris s'est fermé sur une porte, l'app ouvre la session.
 *
 * - [scene] : `null` tant que le premier hameau n'est pas généré ; l'herbe seule s'affiche.
 * - [carnet] : en mode un seul écran, le carnet compact sous le hameau.
 * - [prompt] : ce qui se pose par-dessus tout l'écran (la confirmation de « Quitter »).
 */
@Composable
fun VillageTopScreen(
    scene: VillageSceneState?,
    assets: GameAssets,
    onEnterHouse: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
    overlay: VillageOverlay = DefaultVillageOverlay,
    carnet: (@Composable () -> Unit)? = null,
    prompt: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().testTag("village-top")) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(assets.palette.grass.base))) {
                if (scene != null) {
                    VillageScene(scene, assets, onEnterHouse = onEnterHouse) { projection ->
                        val scope = remember(scene, projection) { VillageOverlayScope(scene, projection) }
                        overlay(scope)
                    }
                }
            }
            carnet?.invoke()
        }
        prompt?.invoke()
    }
}

/**
 * L'état de chaque maison, lisible de loin (`da-jeu.md`, principe 2) : au travail, attend ta
 * validation, au repos, arrêtée ou en échec. La maison choisie ([focusedHouse]) porte son panneau
 * (projet, session, état) ; devant sa porte, « A Entrer » monte au-dessus du coursier.
 */
@Composable
fun HouseStateOverlay(scope: VillageOverlayScope) {
    val scene = scope.scene
    val projection = scope.projection
    val focused by remember(scene) { derivedStateOf { scene.focusedHouse() } }
    scene.village.houses.forEach { house ->
        key(house.session.id) {
            val session = house.session
            val state = TagState.of(session.status)
            val label = stateLabel(state, session.status == SessionStatus.STOPPED)
            val anchor = Modifier.anchorBottomCenter { projection.tileTop(house.labelAnchor) }
            if (session.id == focused) {
                // Le panneau reste lisible quand la maison sort par le haut de l'écran.
                SignPanel(
                    session.project,
                    Modifier.anchorInside { projection.tileTop(house.labelAnchor) }.testTag("house-panel"),
                    subtitle = session.title,
                    state = state,
                    stateLabel = label,
                )
            } else {
                StateTag(state, anchor.testTag("house-state-${session.id.value}"), label = label)
            }
        }
    }
    val door = scene.facingDoor
    if (door != null && scene.entering == null) {
        val metrics = Game.metrics
        PixelFrame(
            Modifier.anchorBottomCenter { projection.toScreen(ArtPoint(scene.foot.x, scene.foot.y - COURIER_HEIGHT)) }.testTag("enter-hint"),
            contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(10)),
        ) {
            KeyHint(KeyHintSpec("A", stringResource(R.string.hameau_enter)), textSizePx = 30)
        }
    }
}

/**
 * Comme [anchorBottomCenter], mais l'élément reste entier dans la vue du monde : poussé contre le
 * bord qu'il dépasserait. La position est lue au placement, sans recomposer quand la caméra bouge.
 */
private fun Modifier.anchorInside(position: () -> IntOffset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val p = position()
        val x = (p.x - placeable.width / 2).coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
        val y = (p.y - placeable.height).coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
        placeable.place(x, y)
    }
}

/** Le coursier fait 30 pixels de haut ; l'aide se pose juste au-dessus de sa tête. */
private const val COURIER_HEIGHT = 34
