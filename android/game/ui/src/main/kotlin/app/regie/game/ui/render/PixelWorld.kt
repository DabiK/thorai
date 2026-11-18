package app.regie.game.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.World
import app.regie.game.ui.atlas.Footprint
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale

/**
 * Un sprite libre, placé au pixel près : le coursier qui marche, l'oiseau messager…
 * [foot] est le point du monde où tombe son ancrage (`ax`, `ay`).
 */
data class Sprite(val id: String, val foot: ArtPoint, val roof: RoofColors? = null)

/** Un objet à dessiner, dans l'ordre de profondeur. */
data class DrawItem(val id: String, val foot: ArtPoint, val roof: RoofColors?, val footprint: Footprint)

/** Placement des objets dans le monde, sans Android. */
object WorldLayout {
    /**
     * Le point d'ancrage d'un objet posé : le milieu du bas de son empreinte. [Placed.anchor] est
     * la tuile en bas à gauche de l'empreinte ; l'ancrage tombe sur la dernière ligne de pixels.
     */
    fun footOf(placed: Placed, footprint: Footprint): ArtPoint = ArtPoint(
        placed.anchor.x * GroundPainter.TILE + footprint.w * GroundPainter.TILE / 2,
        (placed.anchor.y + 1) * GroundPainter.TILE - 1,
    )

    /** Tout ce qui se dessine au-dessus du sol, trié par le bas (y de l'ancrage) puis de gauche à droite. */
    fun drawOrder(objects: List<Placed>, sprites: List<Sprite>, footprintOf: (String) -> Footprint): List<DrawItem> =
        (
            objects.map { DrawItem(it.objectId, footOf(it, footprintOf(it.objectId)), it.roof, footprintOf(it.objectId)) } +
                sprites.map { DrawItem(it.id, it.foot, it.roof, Footprint(1, 1)) }
            ).sortedWith(compareBy({ it.foot.y }, { it.foot.x }))
}

/**
 * Le monde en pixel art : le sol dessiné par le code, puis les objets des atlas triés par
 * profondeur, agrandis ×[scale] au plus proche voisin. La caméra se centre sur [focus] et
 * reste dans le monde. [overlay] reçoit la projection pour accrocher panneaux et bulles.
 *
 * Le sol est calculé une fois par carte : une carte modifiée doit arriver dans un nouveau [World].
 */
@Composable
fun PixelWorld(
    world: World,
    assets: GameAssets,
    focus: ArtPoint,
    modifier: Modifier = Modifier,
    sprites: List<Sprite> = emptyList(),
    scale: Int = GameScale.WORLD,
    clock: State<Long> = Game.clock,
    overlay: @Composable BoxScope.(WorldProjection) -> Unit = {},
) {
    val ground = remember(world.map, assets.palette) { GroundPainter.paint(world.map, assets.palette).toImageBitmap() }
    val items = remember(world.objects, sprites, assets) { WorldLayout.drawOrder(world.objects, sprites, assets::footprint) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val viewWidth = (constraints.maxWidth + scale - 1) / scale
        val viewHeight = (constraints.maxHeight + scale - 1) / scale
        val origin = Camera.topLeft(focus, ground.width, ground.height, viewWidth, viewHeight)
        val projection = WorldProjection(origin, scale)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(assets.palette.grass.dark))
            drawImage(
                ground,
                dstOffset = IntOffset(projection.toScreenX(0), projection.toScreenY(0)),
                dstSize = IntSize(ground.width * scale, ground.height * scale),
                filterQuality = FilterQuality.None,
            )
            val time = clock.value
            items.forEach { drawItem(it, assets, projection, time) }
        }
        overlay(projection)
    }
}

private fun DrawScope.drawItem(item: DrawItem, assets: GameAssets, projection: WorldProjection, time: Long) {
    val sprite = assets.resolve(item.id, time)
    if (sprite == null) {
        drawPlaceholder(item, assets, projection)
        return
    }
    val pixel = sprite.atlas.spec.pixel
    val scale = projection.scale
    val f = sprite.frame
    val left = projection.toScreenX(item.foot.x) - f.ax * scale / pixel
    val top = projection.toScreenY(item.foot.y) - f.ay * scale / pixel
    val dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel)
    if (item.roof != null && sprite.roofKeys.isNotEmpty()) {
        drawImage(
            sprite.atlas.recoloured(sprite.frameId, sprite.roofKeys, item.roof),
            dstOffset = IntOffset(left, top),
            dstSize = dstSize,
            filterQuality = FilterQuality.None,
        )
    } else {
        drawImage(
            sprite.atlas.image,
            srcOffset = IntOffset(f.x, f.y),
            srcSize = IntSize(f.w, f.h),
            dstOffset = IntOffset(left, top),
            dstSize = dstSize,
            filterQuality = FilterQuality.None,
        )
    }
}

/** Forme de remplacement tant qu'un atlas manque : un bloc de l'empreinte, toit compris. */
private fun DrawScope.drawPlaceholder(item: DrawItem, assets: GameAssets, projection: WorldProjection) {
    val s = projection.scale.toFloat()
    val tile = GroundPainter.TILE
    val w = item.footprint.w * tile
    val h = item.footprint.h * tile
    val left = projection.toScreenX(item.foot.x - w / 2).toFloat()
    val top = projection.toScreenY(item.foot.y + 1 - h).toFloat()
    val outline = Color(assets.palette.outline)
    drawRect(outline, Offset(left, top), Size(w * s, h * s))
    drawRect(Color(assets.palette.path.light), Offset(left + s, top + s), Size((w - 2) * s, (h - 2) * s))
    val roof = item.roof ?: return
    val roofHeight = h / 2
    drawRect(Color(roof.base), Offset(left + s, top + s), Size((w - 2) * s, (roofHeight - 1) * s))
    drawRect(Color(roof.light), Offset(left + s, top + s), Size((w - 2) * s, s))
    drawRect(Color(roof.shadow), Offset(left + s, top + (roofHeight - 1) * s), Size((w - 2) * s, s))
}

private fun PixelImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
