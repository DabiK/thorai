package app.regie.overview

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** La grille de la vue d'ensemble : 2 × 2 cartes visibles. */
object OverviewGrid {
    const val COLUMNS = 2
    const val ROWS = 2
    const val VISIBLE_CARDS = COLUMNS * ROWS
}

/** Déplacement du focus dans une grille de cartes remplie ligne par ligne. Le focus s'arrête aux bords. */
class FocusGrid(private val columns: Int) {

    fun move(index: Int, direction: Direction, count: Int): Int {
        if (count == 0) return 0
        val column = index % columns
        val target = when (direction) {
            Direction.LEFT -> if (column > 0) index - 1 else index
            Direction.RIGHT -> if (column < columns - 1) index + 1 else index
            Direction.UP -> index - columns
            Direction.DOWN -> index + columns
        }
        return if (target in 0 until count) target else index
    }

    fun next(index: Int, count: Int): Int = if (count == 0) 0 else (index + 1).coerceAtMost(count - 1)

    fun previous(index: Int, count: Int): Int = if (count == 0) 0 else (index - 1).coerceAtLeast(0)
}
