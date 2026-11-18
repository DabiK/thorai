package app.regie.game.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.ui.atlas.AtlasAnim
import app.regie.game.ui.atlas.Footprint
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.ResolvedSprite
import app.regie.game.ui.render.Fallback
import app.regie.game.ui.render.FallbackKind
import app.regie.game.ui.render.PlaceholderPainter
import app.regie.game.ui.render.PlaceholderSprite
import app.regie.game.ui.render.WorldLayout
import app.regie.game.ui.render.toImageBitmap

/**
 * Les images du coursier dans l'atlas `personnages` : `coursier.debout.<direction>`,
 * `coursier.marche.<direction>` et `coursier.parle`. Tout est résolu une fois : choisir l'image
 * d'une animation n'alloue rien.
 */
internal class CourierLook(assets: GameAssets) {
    private val anims: Array<AtlasAnim?> = Array(CourierAction.entries.size * Direction.entries.size) { index ->
        val action = CourierAction.entries[index / Direction.entries.size]
        val direction = Direction.entries[index % Direction.entries.size]
        animOf(assets, idOf(action, direction))
    }
    private val frames: Map<String, ResolvedSprite> = anims.filterNotNull().flatMap { it.frames }.distinct()
        .mapNotNull { id -> assets.resolve(id)?.let { id to it } }.toMap()

    /** L'image du coursier pour [action] vers [facing], au temps [millis] de l'animation ; `null` sans atlas. */
    fun sprite(action: CourierAction, facing: Direction, millis: Long): ResolvedSprite? {
        val anim = anims[action.ordinal * Direction.entries.size + facing.ordinal] ?: return null
        return frames[anim.frameAt(millis)]
    }

    companion object {
        private val DIRECTIONS = mapOf(
            Direction.UP to "haut",
            Direction.RIGHT to "droite",
            Direction.DOWN to "bas",
            Direction.LEFT to "gauche",
        )

        fun idOf(action: CourierAction, facing: Direction): String = when (action) {
            CourierAction.IDLE -> "coursier.debout.${DIRECTIONS.getValue(facing)}"
            CourierAction.WALK -> "coursier.marche.${DIRECTIONS.getValue(facing)}"
            // L'atlas n'a qu'une image de parole, de face.
            CourierAction.TALK -> "coursier.parle"
        }

        /** L'animation [id], ou une image seule vue comme une animation d'une image. */
        private fun animOf(assets: GameAssets, id: String): AtlasAnim? =
            assets.atlases.firstNotNullOfOrNull { it.spec.anims[id] }
                ?: assets.atlases.firstOrNull { id in it.spec.frames }?.let { AtlasAnim(listOf(id), 1000) }
    }
}

/**
 * Ce qui, dans le monde, peut passer devant le coursier : chaque objet posé, dans l'ordre de
 * profondeur du rendu ([WorldLayout.drawOrder]), avec son rectangle en pixels du dessin.
 */
internal class Occluder(
    val footX: Int,
    val footY: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val id: String,
    val roof: RoofColors?,
    /** L'image fixe, ou `null` pour un objet animé (résolu au dessin) ou une forme de repli. */
    val still: ResolvedSprite?,
    val placeholder: ImageBitmap?,
    val placeholderSprite: PlaceholderSprite?,
)

internal fun occludersOf(objects: List<Placed>, assets: GameAssets, fallbacks: Map<Placed, Fallback>): List<Occluder> =
    WorldLayout.drawOrder(objects, emptyList()) { placed ->
        assets.objectSpec(placed.objectId)?.footprint ?: fallbacks[placed]?.footprint ?: Footprint(1, 1)
    }.map { item ->
        val resolved = assets.resolve(item.id)
        if (resolved == null) {
            val sprite = PlaceholderPainter.paint(item.placed?.let { fallbacks[it] } ?: Fallback(FallbackKind.BLOCK, item.footprint), item.roof)
            val left = item.foot.x - sprite.ax
            val top = item.foot.y - sprite.ay
            Occluder(
                item.foot.x, item.foot.y, left, top, left + sprite.image.width, top + sprite.image.height,
                item.id, item.roof, null, sprite.image.toImageBitmap(), sprite,
            )
        } else {
            val pixel = resolved.atlas.spec.pixel
            val f = resolved.frame
            val left = item.foot.x - f.ax / pixel
            val top = item.foot.y - f.ay / pixel
            val animated = assets.isAnimated(item.id)
            Occluder(
                item.foot.x, item.foot.y, left, top, left + f.w / pixel, top + f.h / pixel,
                item.id, item.roof, if (animated) null else resolved, null, null,
            )
        }
    }

/**
 * Dessine le coursier, pieds en ([footX], [footY]) dans le monde, la caméra en ([originX], [originY]),
 * puis, dans son seul rectangle, tout ce qui est devant lui : un arbre ou un toit le cache comme
 * dans [app.regie.game.ui.render.PixelWorld], sans retrier le monde à chaque image.
 */
internal fun DrawScope.drawCourier(
    sprite: ResolvedSprite?,
    footX: Int,
    footY: Int,
    originX: Int,
    originY: Int,
    scale: Int,
    occluders: List<Occluder>,
    assets: GameAssets,
    time: Long,
    fallbackColor: Color,
    outline: Color,
) {
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int
    if (sprite == null) {
        // Sans atlas : une silhouette de 10 × 16 pixels.
        left = footX - 5
        top = footY - 15
        right = left + 10
        bottom = footY + 1
        val s = scale.toFloat()
        drawRect(outline, Offset((left - originX) * s, (top - originY) * s), Size(10 * s, 16 * s))
        drawRect(fallbackColor, Offset((left + 1 - originX) * s, (top + 6 - originY) * s), Size(8 * s, 9 * s))
    } else {
        val pixel = sprite.atlas.spec.pixel
        val f = sprite.frame
        left = footX - f.ax / pixel
        top = footY - f.ay / pixel
        right = left + f.w / pixel
        bottom = top + f.h / pixel
        drawImage(
            sprite.atlas.image,
            srcOffset = IntOffset(f.x, f.y),
            srcSize = IntSize(f.w, f.h),
            dstOffset = IntOffset((left - originX) * scale, (top - originY) * scale),
            dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel),
            filterQuality = FilterQuality.None,
        )
    }
    clipRect(
        ((left - originX) * scale).toFloat(),
        ((top - originY) * scale).toFloat(),
        ((right - originX) * scale).toFloat(),
        ((bottom - originY) * scale).toFloat(),
    ) {
        for (i in occluders.indices) {
            val o = occluders[i]
            // Même ordre que le rendu : par le bas, puis de gauche à droite ; à égalité, l'objet d'abord.
            if (o.footY < footY || (o.footY == footY && o.footX <= footX)) continue
            if (o.right <= left || o.left >= right || o.bottom <= top || o.top >= bottom) continue
            drawOccluder(o, assets, originX, originY, scale, time)
        }
    }
}

private fun DrawScope.drawOccluder(o: Occluder, assets: GameAssets, originX: Int, originY: Int, scale: Int, time: Long) {
    if (o.placeholder != null && o.placeholderSprite != null) {
        drawImage(
            o.placeholder,
            dstOffset = IntOffset((o.left - originX) * scale, (o.top - originY) * scale),
            dstSize = IntSize(o.placeholder.width * scale, o.placeholder.height * scale),
            filterQuality = FilterQuality.None,
        )
        return
    }
    val sprite = o.still ?: assets.resolve(o.id, time) ?: return
    val pixel = sprite.atlas.spec.pixel
    val f = sprite.frame
    val dstOffset = IntOffset((o.footX - f.ax / pixel - originX) * scale, (o.footY - f.ay / pixel - originY) * scale)
    val dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel)
    if (o.roof != null && sprite.roofKeys.isNotEmpty()) {
        drawImage(sprite.atlas.recoloured(sprite.frameId, sprite.roofKeys, o.roof), dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.None)
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
