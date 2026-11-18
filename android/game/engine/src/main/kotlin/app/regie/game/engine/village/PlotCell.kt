package app.regie.game.engine.village

import kotlin.math.abs

/**
 * La place d'un terrain dans la grille du hameau, telle qu'on la voit : [row] la rangée (les rues 0 et 1
 * sur la première, 2 et 3 sur la suivante…), [column] la colonne, positive à l'est de l'avenue et négative
 * à l'ouest. Le terrain 0 de la rue 0 (colonne 0) et celui de la rue 1 (colonne −1) se font face, de part
 * et d'autre de la place.
 */
internal data class PlotCell(val row: Int, val column: Int) {
    /** Deux terrains qui se voient : côte à côte, de part et d'autre de la place, d'une rangée à la suivante, diagonales comprises. */
    fun isNear(other: PlotCell): Boolean = this != other && abs(row - other.row) <= 1 && abs(column - other.column) <= 1

    /** Voisins francs : côte à côte sur la rangée (place comprise) ou l'un au-dessus de l'autre. */
    fun isBeside(other: PlotCell): Boolean = abs(row - other.row) + abs(column - other.column) == 1

    companion object {
        fun rowOf(street: Int): Int = street / 2

        fun sideOf(street: Int): StreetSide = if (street % 2 == 0) StreetSide.EAST else StreetSide.WEST

        fun of(street: Int, plot: Int): PlotCell =
            PlotCell(rowOf(street), if (sideOf(street) == StreetSide.EAST) plot else -(plot + 1))
    }
}
