package app.regie.game.ui.house

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.Camera
import app.regie.game.ui.render.Sprite
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale

/** Taille d'un fond d'intérieur, en pixels du dessin (atlas `fonds`). */
object InteriorSize {
    const val WIDTH = 480
    const val HEIGHT = 270

    /** Le fond sombre autour de la pièce (`da-jeu.md`, atlas du ticket 42). */
    val NIGHT = Color(0xFF203648)
}

/**
 * L'intérieur d'une maison : le fond vide de l'atlas `fonds`, puis les personnages triés par le bas,
 * agrandis ×4 au plus proche voisin. La vue se centre sur [focus] et reste dans le fond ; [lift]
 * remonte le fond de quelques pixels pour dégager le bas de l'écran (la boîte de dialogue).
 * [overlay] reçoit la projection pour accrocher bulles et panneaux aux personnages.
 */
@Composable
fun HouseScene(
    assets: GameAssets,
    background: String,
    sprites: List<Sprite>,
    focus: ArtPoint,
    modifier: Modifier = Modifier,
    lift: Int = 0,
    overlay: @Composable BoxScope.(WorldProjection) -> Unit = {},
) {
    val clock = Game.clock
    val scale = GameScale.WORLD
    BoxWithConstraints(modifier.clipToBounds()) {
        val viewWidth = (constraints.maxWidth + scale - 1) / scale
        val viewHeight = (constraints.maxHeight + scale - 1) / scale
        val origin = Camera.topLeft(focus, InteriorSize.WIDTH, InteriorSize.HEIGHT, viewWidth, viewHeight).let { ArtPoint(it.x, it.y + lift) }
        val projection = WorldProjection(origin, scale)
        val ordered = sprites.sortedWith(compareBy({ it.foot.y }, { it.foot.x }))
        Canvas(Modifier.fillMaxSize()) {
            drawRect(InteriorSize.NIGHT)
            val time = clock.value
            if (!drawAtlasImage(assets, background, time, projection, ArtPoint(0, 0), anchored = false)) drawRoomPlaceholder(projection)
            ordered.forEach { sprite ->
                if (!drawAtlasImage(assets, sprite.id, time, projection, sprite.foot, anchored = true)) drawPersonPlaceholder(projection, sprite.foot)
            }
        }
        overlay(projection)
    }
}

/** Dessine [id] ; ancré, ses pieds (`ax`, `ay`) tombent sur [at]. Faux si aucun atlas ne le connaît. */
private fun DrawScope.drawAtlasImage(assets: GameAssets, id: String, time: Long, projection: WorldProjection, at: ArtPoint, anchored: Boolean): Boolean {
    val sprite = assets.resolve(id, time) ?: return false
    val pixel = sprite.atlas.spec.pixel
    val scale = projection.scale
    val f = sprite.frame
    val left = projection.toScreenX(at.x) - if (anchored) f.ax * scale / pixel else 0
    val top = projection.toScreenY(at.y) - if (anchored) f.ay * scale / pixel else 0
    drawImage(
        sprite.atlas.image,
        srcOffset = IntOffset(f.x, f.y),
        srcSize = IntSize(f.w, f.h),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(f.w * scale / pixel, f.h * scale / pixel),
        filterQuality = FilterQuality.None,
    )
    return true
}

/** Sans l'atlas des fonds : une pièce de papier, à la place de celle du ticket 42. */
private fun DrawScope.drawRoomPlaceholder(projection: WorldProjection) {
    val s = projection.scale.toFloat()
    val left = projection.toScreenX(86).toFloat()
    val top = projection.toScreenY(33).toFloat()
    drawRect(Color(0xFF1F583A), Offset(left, top), Size(307 * s, 204 * s))
    drawRect(Color(0xFFF1D8A6), Offset(left + s, top + s), Size(305 * s, 202 * s))
}

/** Sans l'atlas des personnages : une silhouette de 10 × 30 pixels, pieds sur [foot]. */
private fun DrawScope.drawPersonPlaceholder(projection: WorldProjection, foot: ArtPoint) {
    val s = projection.scale.toFloat()
    val left = projection.toScreenX(foot.x - 5).toFloat()
    val top = projection.toScreenY(foot.y - 29).toFloat()
    drawRect(Color(0xFF1F583A), Offset(left, top), Size(10 * s, 30 * s))
    drawRect(Color(0xFFDF724A), Offset(left + s, top + s), Size(8 * s, 28 * s))
}

/** Place l'élément pour que son coin bas gauche tombe sur [position], en pixels physiques. */
fun Modifier.anchorBottomStart(position: () -> IntOffset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val p = position()
        placeable.place(p.x, p.y - placeable.height)
    }
}
