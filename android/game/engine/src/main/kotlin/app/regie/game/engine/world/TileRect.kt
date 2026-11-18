package app.regie.game.engine.world

/** Rectangle de tuiles : [x], [y] en haut à gauche, [width] × [height] tuiles. */
data class TileRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Rectangle vide : $width × $height" }
    }

    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + width - 1
    val bottom: Int get() = y + height - 1

    /** La tuile en bas à gauche : l'ancre d'un [Placed] dont c'est l'empreinte. */
    val bottomLeft: TilePos get() = TilePos(left, bottom)

    operator fun contains(p: TilePos): Boolean = p.x in left..right && p.y in top..bottom

    fun intersects(other: TileRect): Boolean =
        left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom

    fun offset(dx: Int, dy: Int) = TileRect(x + dx, y + dy, width, height)

    /** Les tuiles, ligne par ligne, de haut en bas. */
    fun tiles(): List<TilePos> = (top..bottom).flatMap { ty -> (left..right).map { tx -> TilePos(tx, ty) } }
}
