package app.regie.game.ui.render

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import app.regie.game.ui.atlas.ResolvedSprite
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale
import java.util.concurrent.ConcurrentHashMap

/**
 * Un sprite libre, placé au pixel près : le coursier qui marche, l'oiseau messager…
 * [foot] est le point du monde où tombe son ancrage (`ax`, `ay`).
 */
data class Sprite(val id: String, val foot: ArtPoint, val roof: RoofColors? = null)

/** Un objet à dessiner, dans l'ordre de profondeur ; [placed] est `null` pour un sprite libre. */
data class DrawItem(val id: String, val foot: ArtPoint, val roof: RoofColors?, val footprint: Footprint, val placed: Placed? = null)

/** Placement des objets dans le monde, sans Android. */
object WorldLayout {
    /**
     * Le point d'ancrage d'un objet posé : le milieu de la dernière rangée de son empreinte.
     * [Placed.anchor] est la tuile en bas à gauche de l'empreinte.
     */
    fun footOf(placed: Placed, footprint: Footprint): ArtPoint = ArtPoint(
        placed.anchor.x * GroundPainter.TILE + footprint.w * GroundPainter.TILE / 2,
        (placed.anchor.y + 1) * GroundPainter.TILE - 1,
    )

    /** Tout ce qui se dessine au-dessus du sol, trié par le bas (y de l'ancrage) puis de gauche à droite. */
    fun drawOrder(objects: List<Placed>, sprites: List<Sprite>, footprintOf: (Placed) -> Footprint): List<DrawItem> =
        (
            objects.map { placed ->
                val footprint = footprintOf(placed)
                DrawItem(placed.objectId, footOf(placed, footprint), placed.roof, footprint, placed)
            } + sprites.map { DrawItem(it.id, it.foot, it.roof, Footprint(1, 1)) }
            ).sortedWith(compareBy({ it.foot.y }, { it.foot.x }))
}

/** Les formes de repli déjà converties en images, partagées par les deux écrans. */
private object PlaceholderBitmaps {
    private val cache = ConcurrentHashMap<Pair<Fallback, RoofColors?>, Pair<ImageBitmap, PlaceholderSprite>>()

    fun of(fallback: Fallback, roof: RoofColors?): Pair<ImageBitmap, PlaceholderSprite> = cache.getOrPut(fallback to roof) {
        val sprite = PlaceholderPainter.paint(fallback, roof)
        sprite.image.toImageBitmap() to sprite
    }
}

/**
 * Le monde en pixel art : le sol dessiné par le code, puis les objets des atlas triés par
 * profondeur, agrandis ×[scale] au plus proche voisin. La caméra se centre sur [focus] et reste
 * dans le monde ; [focus] est lu au dessin, la caméra suit donc le coursier sans recomposer.
 * [overlay] reçoit la projection pour accrocher panneaux et bulles ([anchorBottomCenter]).
 *
 * - Le sol est peint hors du fil principal, une fois par contenu ([GroundCache]) : un nouveau
 *   [World] au même sol ne le repeint pas.
 * - Un objet absent des atlas est dessiné par sa forme de repli ([fallbacks], par exemple
 *   [app.regie.game.engine.village.Village.fallbacks]), à l'empreinte que le moteur bloque.
 */
@Composable
fun PixelWorld(
    world: World,
    assets: GameAssets,
    focus: () -> ArtPoint,
    modifier: Modifier = Modifier,
    sprites: List<Sprite> = emptyList(),
    fallbacks: Map<Placed, Fallback> = emptyMap(),
    scale: Int = GameScale.WORLD,
    clock: State<Long> = Game.clock,
    overlay: @Composable BoxScope.(WorldProjection) -> Unit = {},
) {
    val palette = assets.palette
    val key = remember(world.map) { GroundKey.of(world.map) }
    var ground by remember { mutableStateOf(GroundCache.peek(key, palette)) }
    LaunchedEffect(key, palette) { ground = GroundCache.paint(key, palette) }
    val items = remember(world.objects, sprites, assets, fallbacks) {
        WorldLayout.drawOrder(world.objects, sprites) { placed ->
            assets.objectSpec(placed.objectId)?.footprint ?: fallbacks[placed]?.footprint ?: Footprint(1, 1)
        }.map { item ->
            val resolved = assets.resolve(item.id)
            when {
                // Le repli ne sert que si aucun atlas ne connaît l'objet.
                resolved == null -> {
                    val (image, sprite) = PlaceholderBitmaps.of(
                        item.placed?.let { fallbacks[it] } ?: Fallback(FallbackKind.BLOCK, item.footprint),
                        item.roof,
                    )
                    Prepared(item, image = image, placeholder = sprite)
                }
                assets.isAnimated(item.id) -> Prepared(item)
                else -> Prepared(item, still = resolved)
            }
        }
    }
    val currentFocus by rememberUpdatedState(focus)
    BoxWithConstraints(modifier.clipToBounds()) {
        val viewWidth = (constraints.maxWidth + scale - 1) / scale
        val viewHeight = (constraints.maxHeight + scale - 1) / scale
        val worldWidth = world.map.width * GroundPainter.TILE
        val worldHeight = world.map.height * GroundPainter.TILE
        val projection = remember(scale, viewWidth, viewHeight, worldWidth, worldHeight) {
            WorldProjection(scale) { Camera.topLeft(currentFocus(), worldWidth, worldHeight, viewWidth, viewHeight) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val focusNow = currentFocus()
            val ox = Camera.axis(focusNow.x, worldWidth, viewWidth)
            val oy = Camera.axis(focusNow.y, worldHeight, viewHeight)
            drawRect(Color(palette.grass.base))
            ground?.let {
                drawImage(
                    it.image,
                    dstOffset = IntOffset(-ox * scale, -oy * scale),
                    dstSize = IntSize(it.width * scale, it.height * scale),
                    filterQuality = FilterQuality.None,
                )
            }
            val time = clock.value
            for (i in items.indices) {
                val prepared = items[i]
                val item = prepared.item
                val x = (item.foot.x - ox) * scale
                val y = (item.foot.y - oy) * scale
                if (prepared.image != null && prepared.placeholder != null) {
                    drawPlaceholder(prepared.image, prepared.placeholder, x, y, scale)
                } else {
                    val sprite = prepared.still ?: assets.resolve(item.id, time) ?: continue
                    drawSprite(item, sprite, x, y, scale)
                }
            }
        }
        overlay(projection)
    }
}

/** Un objet prêt à dessiner : son image fixe, ou sa forme de repli ; ni l'une ni l'autre s'il est animé. */
private class Prepared(
    val item: DrawItem,
    val still: ResolvedSprite? = null,
    val image: ImageBitmap? = null,
    val placeholder: PlaceholderSprite? = null,
)

private fun DrawScope.drawSprite(item: DrawItem, sprite: ResolvedSprite, footX: Int, footY: Int, scale: Int) {
    val pixel = sprite.atlas.spec.pixel
    val f = sprite.frame
    val dstOffset = IntOffset(footX - f.ax * scale / pixel, footY - f.ay * scale / pixel)
    val dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel)
    if (item.roof != null && sprite.roofKeys.isNotEmpty()) {
        drawImage(
            sprite.atlas.recoloured(sprite.frameId, sprite.roofKeys, item.roof),
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.None,
        )
    } else {
        drawImage(
            sprite.atlas.image,
            srcOffset = IntOffset(f.x, f.y),
            srcSize = IntSize(f.w, f.h),
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.None,
        )
    }
}

private fun DrawScope.drawPlaceholder(image: ImageBitmap, sprite: PlaceholderSprite, footX: Int, footY: Int, scale: Int) {
    drawImage(
        image,
        dstOffset = IntOffset(footX - sprite.ax * scale, footY - sprite.ay * scale),
        dstSize = IntSize(image.width * scale, image.height * scale),
        filterQuality = FilterQuality.None,
    )
}

internal fun PixelImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
