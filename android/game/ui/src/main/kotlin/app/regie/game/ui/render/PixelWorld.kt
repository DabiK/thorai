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
import app.regie.game.ui.atlas.AtlasFrame
import app.regie.game.ui.atlas.Footprint
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.ResolvedAnimation
import app.regie.game.ui.atlas.ResolvedSprite
import app.regie.game.ui.atlas.WindowLight
import app.regie.game.ui.kit.TagState
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
 * [overlay] reçoit la projection pour accrocher panneaux et bulles ([anchorBottomCenter],
 * [app.regie.game.ui.village.VillageOverlay]).
 *
 * - Le sol est peint hors du fil principal, une fois par contenu ([GroundCache]) : un nouveau
 *   [World] au même sol ne le repeint pas.
 * - Un objet absent des atlas est dessiné par sa forme de repli ([fallbacks], par exemple
 *   [app.regie.game.engine.village.Village.fallbacks]), à l'empreinte que le moteur bloque.
 * - [states] donne l'état de la session de chaque maison ([app.regie.game.ui.village.houseStates]) :
 *   fenêtres allumées ou éteintes, fumée, lumière à la porte ([HouseEffects]).
 * - Chaque image ne dessine que ce qui est dans la vue, sans rien allouer : les objets, leurs images
 *   (toits, fenêtres) et leurs animations sont préparés une fois par monde.
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
    states: Map<Placed, TagState> = emptyMap(),
    overlay: @Composable BoxScope.(WorldProjection) -> Unit = {},
) {
    val palette = assets.palette
    val key = remember(world.map) { GroundKey.of(world.map) }
    var ground by remember { mutableStateOf(GroundCache.peek(key, palette)) }
    LaunchedEffect(key, palette) { ground = GroundCache.paint(key, palette) }
    val scene = remember(world.objects, assets, fallbacks, states) { PreparedScene.of(world.objects, assets, fallbacks, states) }
    val moving = remember(sprites, assets) { PreparedScene.sprites(sprites, assets) }
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
            val items = scene.items
            var next = 0
            for (i in items.indices) {
                val item = items[i]
                while (next < moving.size && moving[next].before(item.foot)) {
                    drawMoving(moving[next], ox, oy, scale, time)
                    next++
                }
                if (item.right < ox || item.left > ox + viewWidth || item.bottom < oy || item.top > oy + viewHeight) continue
                drawItem(item, scene.smoke, ox, oy, scale, time)
            }
            while (next < moving.size) drawMoving(moving[next++], ox, oy, scale, time)
        }
        overlay(projection)
    }
}

/** La lumière d'une porte ouverte, ambre comme l'attente d'une validation (`da-jeu.md`). */
private const val DOOR_LIGHT = 0xFFF5B53D

/**
 * Ce qu'un monde dessine, préparé une fois : les objets triés par profondeur avec leur boîte (pour ne
 * dessiner que ce qui est vu), leurs images et leurs effets.
 */
private class PreparedScene(val items: Array<PreparedItem>, val smoke: ResolvedAnimation?) {
    companion object {
        fun of(objects: List<Placed>, assets: GameAssets, fallbacks: Map<Placed, Fallback>, states: Map<Placed, TagState>): PreparedScene {
            val items = WorldLayout.drawOrder(objects, emptyList()) { placed ->
                assets.objectSpec(placed.objectId)?.footprint ?: fallbacks[placed]?.footprint ?: Footprint(1, 1)
            }.map { item ->
                val placed = item.placed
                val animation = assets.animation(item.id)
                if (animation == null) {
                    // Le repli ne sert que si aucun atlas ne connaît l'objet.
                    val (image, sprite) = PlaceholderBitmaps.of(
                        placed?.let { fallbacks[it] } ?: Fallback(FallbackKind.BLOCK, item.footprint),
                        item.roof,
                    )
                    return@map PreparedItem(item.foot, image.width, image.height, sprite.ax, sprite.ay, placeholder = image)
                }
                val spec = assets.objectSpec(item.id)
                val state = placed?.let { states[it] }
                val first = animation.frames[0]
                val f = first.frame
                if (spec != null && placed != null && placed in states) {
                    val light = HouseEffects.light(state)
                    val keys = first.roofKeys
                    val lit = first.atlas.house(first.frameId, keys, item.roof, spec.windows, light)
                    val images = if (HouseEffects.flickers(state)) {
                        arrayOf(lit, first.atlas.house(first.frameId, keys, item.roof, spec.windows, WindowLight.GLOW))
                    } else {
                        arrayOf(lit)
                    }
                    val door = spec.door?.takeIf { HouseEffects.doorGlows(state) }?.let { door ->
                        val left = item.foot.x - item.footprint.w * GroundPainter.TILE / 2
                        ArtPoint(left + door.x * GroundPainter.TILE + GroundPainter.TILE / 2, item.foot.y + HouseEffects.DOOR_LIGHT_TOP)
                    }
                    val chimney = spec.chimney?.takeIf { HouseEffects.smokes(state) }
                    return@map PreparedItem(
                        item.foot, f.w, f.h, f.ax, f.ay,
                        house = images,
                        frame = f,
                        phase = phaseOf(placed),
                        chimney = chimney?.let { ArtPoint(item.foot.x - f.ax + it.x, item.foot.y - f.ay + it.y) },
                        drift = if (chimney != null && chimney.x < f.w / 2) -1 else 1,
                        door = door,
                    )
                }
                // Une image fixe à toit recoloré est préparée tout de suite ; une animation se résout au dessin.
                val still = if (item.roof != null && first.roofKeys.isNotEmpty()) first.atlas.recoloured(first.frameId, first.roofKeys, item.roof) else null
                PreparedItem(item.foot, f.w, f.h, f.ax, f.ay, animation = animation, still = still)
            }
            return PreparedScene(items.toTypedArray(), assets.animation(SMOKE))
        }

        /** Les sprites libres, triés par profondeur. */
        fun sprites(sprites: List<Sprite>, assets: GameAssets): Array<PreparedSprite> =
            sprites.sortedWith(compareBy({ it.foot.y }, { it.foot.x })).map { sprite ->
                PreparedSprite(sprite.foot, assets.animation(sprite.id), sprite.roof)
            }.toTypedArray()

        /** Un décalage stable par maison : les fumées et les vacillements ne battent pas ensemble. */
        private fun phaseOf(placed: Placed): Long = Math.floorMod(placed.anchor.x * 7919L + placed.anchor.y * 104_729L, 10_000L)

        private const val SMOKE = "fumee"
    }
}

/**
 * Un objet préparé : son ancrage, sa boîte en pixels du dessin et ce qu'il faut dessiner ; une seule
 * des sources est remplie : [placeholder], [house] (une maison et sa lumière), [still] ou [animation].
 */
private class PreparedItem(
    val foot: ArtPoint,
    width: Int,
    height: Int,
    val ax: Int,
    val ay: Int,
    val placeholder: ImageBitmap? = null,
    val house: Array<ImageBitmap>? = null,
    val frame: AtlasFrame? = null,
    val phase: Long = 0,
    val chimney: ArtPoint? = null,
    val drift: Int = 1,
    /** Le haut de la lumière qui sort de la porte, pour une maison qui attend. */
    val door: ArtPoint? = null,
    val animation: ResolvedAnimation? = null,
    val still: ImageBitmap? = null,
) {
    val left = foot.x - ax
    val top = foot.y - ay - if (chimney != null) HouseEffects.SMOKE_RISE + SMOKE_MARGIN else 0
    val right = foot.x - ax + width
    val bottom = foot.y - ay + height + if (door != null) HouseEffects.DOOR_LIGHT.size else 0

    private companion object {
        const val SMOKE_MARGIN = 16
    }
}

private class PreparedSprite(val foot: ArtPoint, val animation: ResolvedAnimation?, val roof: RoofColors?) {
    fun before(other: ArtPoint): Boolean = foot.y < other.y || (foot.y == other.y && foot.x < other.x)
}

private fun DrawScope.drawItem(item: PreparedItem, smoke: ResolvedAnimation?, ox: Int, oy: Int, scale: Int, time: Long) {
    val x = (item.foot.x - ox) * scale
    val y = (item.foot.y - oy) * scale
    item.placeholder?.let { image ->
        drawImage(image, dstOffset = IntOffset(x - item.ax * scale, y - item.ay * scale), dstSize = IntSize(image.width * scale, image.height * scale), filterQuality = FilterQuality.None)
        return
    }
    val house = item.house
    if (house != null) {
        val f = item.frame ?: return
        val image = if (house.size > 1 && HouseEffects.glowing(time, item.phase)) house[1] else house[0]
        drawImage(image, dstOffset = IntOffset(x - f.ax * scale, y - f.ay * scale), dstSize = IntSize(f.w * scale, f.h * scale), filterQuality = FilterQuality.None)
        val chimney = item.chimney
        if (chimney != null && smoke != null) drawSmoke(smoke, chimney, item.drift, item.phase, ox, oy, scale, time)
        // Sur les marches et le seuil : la lumière de la porte ouverte d'une maison qui attend.
        item.door?.let { drawDoorLight(it, ox, oy, scale, HouseEffects.doorAlpha(time)) }
        return
    }
    val still = item.still
    val animation = item.animation ?: return
    val sprite = animation.at(time)
    if (still != null) {
        val f = sprite.frame
        drawImage(still, dstOffset = IntOffset(x - f.ax * scale, y - f.ay * scale), dstSize = IntSize(f.w * scale, f.h * scale), filterQuality = FilterQuality.None)
    } else {
        drawFrame(sprite, x, y, scale)
    }
}

private fun DrawScope.drawMoving(sprite: PreparedSprite, ox: Int, oy: Int, scale: Int, time: Long) {
    val resolved = sprite.animation?.at(time) ?: return
    val x = (sprite.foot.x - ox) * scale
    val y = (sprite.foot.y - oy) * scale
    val roof = sprite.roof
    if (roof != null && resolved.roofKeys.isNotEmpty()) {
        val f = resolved.frame
        drawImage(
            resolved.atlas.recoloured(resolved.frameId, resolved.roofKeys, roof),
            dstOffset = IntOffset(x - f.ax * scale, y - f.ay * scale),
            dstSize = IntSize(f.w * scale, f.h * scale),
            filterQuality = FilterQuality.None,
        )
    } else {
        drawFrame(resolved, x, y, scale)
    }
}

/** Une image d'atlas dont l'ancrage tombe en ([footX], [footY]), en pixels de l'écran. */
private fun DrawScope.drawFrame(sprite: ResolvedSprite, footX: Int, footY: Int, scale: Int, alpha: Float = 1f) {
    val pixel = sprite.atlas.spec.pixel
    val f = sprite.frame
    drawImage(
        sprite.atlas.image,
        srcOffset = IntOffset(f.x, f.y),
        srcSize = IntSize(f.w, f.h),
        dstOffset = IntOffset(footX - f.ax * scale / pixel, footY - f.ay * scale / pixel),
        dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}

/** Les panaches de fumée d'une cheminée, qui montent, grossissent, dérivent et pâlissent. */
private fun DrawScope.drawSmoke(smoke: ResolvedAnimation, chimney: ArtPoint, drift: Int, phase: Long, ox: Int, oy: Int, scale: Int, time: Long) {
    val frames = smoke.frames
    for (i in 0 until HouseEffects.SMOKE_PUFFS) {
        val progress = HouseEffects.puff(time, phase, i)
        val frame = frames[minOf(HouseEffects.puffFrame(progress), frames.size - 1)]
        val x = chimney.x + HouseEffects.puffDx(progress, drift) - ox
        val y = chimney.y + HouseEffects.puffDy(progress) - oy
        drawFrame(frame, x * scale, y * scale, scale, HouseEffects.puffAlpha(progress))
    }
}

/** La flaque de lumière devant une porte ouverte : des rangées de pixels, du seuil vers la route. */
private fun DrawScope.drawDoorLight(door: ArtPoint, ox: Int, oy: Int, scale: Int, alpha: Float) {
    val color = Color(DOOR_LIGHT)
    val rows = HouseEffects.DOOR_LIGHT
    val s = scale.toFloat()
    for (row in rows.indices) {
        val width = rows[row]
        drawRect(
            color,
            topLeft = Offset((door.x - width / 2 - ox) * s, (door.y + row - oy) * s),
            size = Size(width * s, s),
            alpha = alpha * (rows.size - row) / rows.size,
        )
    }
}

internal fun PixelImage.toImageBitmap(): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
