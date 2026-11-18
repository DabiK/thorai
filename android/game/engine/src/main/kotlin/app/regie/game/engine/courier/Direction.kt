package app.regie.game.engine.courier

import app.regie.game.engine.world.TilePos

/** Les quatre directions du coursier, sur la grille ; y vers le bas. */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0),
    ;

    /** La tuile voisine de [from] dans cette direction. */
    fun next(from: TilePos): TilePos = from.offset(dx, dy)

    companion object {
        /** La direction qui mène de [from] à sa voisine [to], ou `null` si elles ne se touchent pas par un côté. */
        fun between(from: TilePos, to: TilePos): Direction? =
            entries.firstOrNull { from.x + it.dx == to.x && from.y + it.dy == to.y }
    }
}
