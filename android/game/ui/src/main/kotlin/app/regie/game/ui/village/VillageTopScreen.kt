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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.regie.domain.model.SessionId
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.KeyHint
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.scene.VillageScene
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.theme.Game

/** Ce qu'un calque du hameau reçoit : la scène (hameau et coursier), les atlas et la projection de la caméra. */
@Stable
class VillageLayerScope(val scene: VillageSceneState, val assets: GameAssets, val projection: WorldProjection)

/** Un calque accroché au hameau, sous l'iris ; [DefaultVillageLayer] par défaut. */
typealias VillageLayer = @Composable BoxScope.(VillageLayerScope) -> Unit

/**
 * Le calque par défaut : les bulles d'état et le panneau de la maison choisie ([VillageOverlay],
 * ticket 52), puis « A Entrer » au-dessus du coursier devant une porte.
 */
val DefaultVillageLayer: VillageLayer = { scope ->
    val scene = scope.scene
    // La maison choisie : celle où il va (ligne touchée, L1, R1), sinon devant laquelle il se tient, sinon la plus proche.
    val selected by remember(scene) { derivedStateOf { scene.focusedHouse() } }
    VillageOverlay(scene.village, scope.assets, scope.projection, selected = selected)
    EnterHint(scope)
}

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
    layer: VillageLayer = DefaultVillageLayer,
    carnet: (@Composable () -> Unit)? = null,
    prompt: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().testTag("village-top")) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(assets.palette.grass.base))) {
                if (scene != null) {
                    VillageScene(scene, assets, onEnterHouse = onEnterHouse) { projection ->
                        val scope = remember(scene, assets, projection) { VillageLayerScope(scene, assets, projection) }
                        layer(scope)
                    }
                }
            }
            carnet?.invoke()
        }
        prompt?.invoke()
    }
}

/** Devant une porte, « A Entrer » monte au-dessus de la tête du coursier (maquette 04). */
@Composable
fun EnterHint(scope: VillageLayerScope) {
    val scene = scope.scene
    if (scene.facingDoor == null || scene.entering != null) return
    val metrics = Game.metrics
    PixelFrame(
        Modifier.anchorBottomCenter { scope.projection.toScreen(ArtPoint(scene.foot.x, scene.foot.y - COURIER_HEIGHT)) }.testTag("enter-hint"),
        contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(10)),
    ) {
        KeyHint(KeyHintSpec("A", stringResource(R.string.hameau_enter)), textSizePx = 30)
    }
}

/** Le coursier fait 30 pixels de haut ; l'aide se pose juste au-dessus de sa tête. */
private const val COURIER_HEIGHT = 34
