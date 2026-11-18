package app.regie.game.ui.render

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import app.regie.game.engine.world.TilePos

/** Un point en pixels du dessin (avant l'agrandissement ×4). */
data class ArtPoint(val x: Int, val y: Int)

/** La caméra : quelle partie du monde l'écran montre. */
object Camera {
    /** Le centre d'une tuile, en pixels du dessin. */
    fun center(tile: TilePos): ArtPoint =
        ArtPoint(tile.x * GroundPainter.TILE + GroundPainter.TILE / 2, tile.y * GroundPainter.TILE + GroundPainter.TILE / 2)

    /**
     * Le coin haut gauche de la vue, centrée sur [focus] puis bornée au monde : on ne voit jamais
     * au-delà du bord. Un monde plus petit que la vue est centré (coin négatif).
     */
    fun topLeft(focus: ArtPoint, worldWidth: Int, worldHeight: Int, viewWidth: Int, viewHeight: Int): ArtPoint =
        ArtPoint(axis(focus.x, worldWidth, viewWidth), axis(focus.y, worldHeight, viewHeight))

    private fun axis(focus: Int, world: Int, view: Int): Int =
        if (world <= view) -((view - world) / 2) else (focus - view / 2).coerceIn(0, world - view)
}

/**
 * Passage du monde à l'écran pour une vue donnée : [origin] est le coin haut gauche de la caméra,
 * [scale] le nombre de pixels physiques par pixel du dessin.
 */
data class WorldProjection(val origin: ArtPoint, val scale: Int) {
    /** La position à l'écran, en pixels physiques, d'un point du monde. */
    fun toScreenX(artX: Int): Int = (artX - origin.x) * scale
    fun toScreenY(artY: Int): Int = (artY - origin.y) * scale

    /** Un point du monde à l'écran, en pixels physiques. */
    fun toScreen(point: ArtPoint): IntOffset = IntOffset(toScreenX(point.x), toScreenY(point.y))

    /** Le milieu du bord haut d'une tuile, là où l'on accroche un panneau ou une bulle. */
    fun tileTop(tile: TilePos): IntOffset =
        toScreen(ArtPoint(tile.x * GroundPainter.TILE + GroundPainter.TILE / 2, tile.y * GroundPainter.TILE))
}

/**
 * Place l'élément pour que le milieu de son bord bas tombe sur [position], en pixels physiques
 * dans la vue du monde : un panneau ou une bulle pointe ainsi sur sa maison ([WorldProjection.tileTop]).
 * La position est lue au placement, sans recomposer quand la caméra bouge.
 */
fun Modifier.anchorBottomCenter(position: () -> IntOffset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val p = position()
        placeable.place(p.x - placeable.width / 2, p.y - placeable.height)
    }
}
