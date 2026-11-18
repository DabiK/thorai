package app.regie.game.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.game.engine.world.RoofColors
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale

/**
 * Une image d'atlas hors du monde : icône d'un bandeau, portrait d'un dialogue, parchemin…
 * Agrandie ×[scale] au plus proche voisin (×2 pour les icônes d'interface, `da-jeu.md`).
 * Un identifiant animé suit l'horloge du thème. Sans atlas, un carré de [placeholderPixels].
 */
@Composable
fun SpriteImage(
    id: String,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    scale: Int = GameScale.WORLD,
    roof: RoofColors? = null,
    placeholderPixels: Int = 16,
    clock: State<Long> = Game.clock,
) {
    val metrics = Game.metrics
    // La taille ne suit pas l'animation : toutes les images d'une animation ont la taille de la première.
    val first = assets.resolve(id, 0)
    val pixel = first?.atlas?.spec?.pixel ?: 1
    val width = first?.frame?.w?.times(scale)?.div(pixel) ?: (placeholderPixels * scale)
    val height = first?.frame?.h?.times(scale)?.div(pixel) ?: (placeholderPixels * scale)
    val outline = Color(assets.palette.outline)
    val fill = Color(assets.palette.path.light)
    Canvas(modifier.size(metrics.dp(width), metrics.dp(height))) {
        val sprite = assets.resolve(id, clock.value)
        if (sprite == null) {
            val p = scale.toFloat()
            drawRect(outline, Offset.Zero, Size(width.toFloat(), height.toFloat()))
            drawRect(fill, Offset(p, p), Size(width - 2 * p, height - 2 * p))
            return@Canvas
        }
        val f = sprite.frame
        val dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel)
        if (roof != null && sprite.roofKeys.isNotEmpty()) {
            drawImage(sprite.atlas.recoloured(sprite.frameId, sprite.roofKeys, roof), dstSize = dstSize, filterQuality = FilterQuality.None)
        } else {
            drawImage(
                sprite.atlas.image,
                srcOffset = IntOffset(f.x, f.y),
                srcSize = IntSize(f.w, f.h),
                dstSize = dstSize,
                filterQuality = FilterQuality.None,
            )
        }
    }
}
