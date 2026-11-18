package app.regie.game.ui.render

import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos

/**
 * Les bords d'une tuile de sol, calculés selon ses huit voisines.
 * Chaque sol ne dessine que ses propres bords : l'herbe est le fond, le chemin et la place
 * se bordent contre l'herbe, l'eau a une berge, le pont des garde-corps au-dessus de l'eau.
 */
object GroundEdges {
    const val N = 1
    const val E = 2
    const val S = 4
    const val W = 8

    /** Coins intérieurs : la diagonale tranche alors que les deux côtés voisins ne tranchent pas. */
    const val NE = 16
    const val SE = 32
    const val SW = 64
    const val NW = 128

    /** Vrai si [self] dessine un bord du côté d'une voisine [other]. */
    fun contrasts(self: Ground, other: Ground): Boolean = when (self) {
        Ground.GRASS -> false
        Ground.PATH -> other == Ground.GRASS
        Ground.PLAZA -> other == Ground.GRASS || other == Ground.PATH
        Ground.WATER -> other != Ground.WATER && other != Ground.BRIDGE
        Ground.BRIDGE -> other == Ground.WATER
    }

    /** Le masque des bords de la tuile [p]. Hors de la carte, la voisine est du même sol : pas de bord. */
    fun mask(map: TileMap, p: TilePos): Int {
        val self = map.ground(p)
        fun edge(dx: Int, dy: Int): Boolean {
            val q = p.offset(dx, dy)
            return map.inside(q) && contrasts(self, map.ground(q))
        }
        var mask = 0
        if (edge(0, -1)) mask = mask or N
        if (edge(1, 0)) mask = mask or E
        if (edge(0, 1)) mask = mask or S
        if (edge(-1, 0)) mask = mask or W
        if ((mask and (N or E)) == 0 && edge(1, -1)) mask = mask or NE
        if ((mask and (S or E)) == 0 && edge(1, 1)) mask = mask or SE
        if ((mask and (S or W)) == 0 && edge(-1, 1)) mask = mask or SW
        if ((mask and (N or W)) == 0 && edge(-1, -1)) mask = mask or NW
        return mask
    }

    /** Un pont se traverse d'ouest en est quand l'eau le borde au nord ou au sud. */
    fun bridgeRunsEastWest(map: TileMap, p: TilePos): Boolean {
        fun water(dx: Int, dy: Int) = p.offset(dx, dy).let { map.inside(it) && map.ground(it) == Ground.WATER }
        return water(0, -1) || water(0, 1)
    }
}
