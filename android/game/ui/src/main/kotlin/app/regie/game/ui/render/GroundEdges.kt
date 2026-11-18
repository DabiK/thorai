package app.regie.game.ui.render

import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Les bords d'une tuile de sol, calculés selon ses huit voisines.
 * Chaque sol ne dessine que ses propres bords : l'herbe est le fond ; le chemin et la place laissent
 * déborder l'herbe ; la place se borde aussi contre le chemin ; l'eau a une berge ; le pont des
 * garde-corps au-dessus de l'eau.
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

    /** Pas de bord : distance « infinie ». */
    const val FAR = 99f

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
        return maskWhere(map, p) { contrasts(self, it) }
    }

    /** Le masque des voisines de [p] qui vérifient [test] ; les coins ne comptent que seuls. */
    fun maskWhere(map: TileMap, p: TilePos, test: (Ground) -> Boolean): Int {
        fun edge(dx: Int, dy: Int): Boolean {
            val x = p.x + dx
            val y = p.y + dy
            return x in 0 until map.width && y in 0 until map.height && test(map.ground(TilePos(x, y)))
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

    /**
     * Distance, en pixels, du centre du pixel ([lx], [ly]) d'une tuile de [tile] pixels au bord
     * le plus proche décrit par [mask]. Les coins extérieurs sont arrondis d'un rayon [radius],
     * les coins intérieurs mordent d'un pixel. [FAR] sans bord.
     */
    fun distance(mask: Int, lx: Int, ly: Int, tile: Int, radius: Float): Float {
        if (mask == 0) return FAR
        val dN = ly + 0.5f
        val dS = tile - ly - 0.5f
        val dW = lx + 0.5f
        val dE = tile - lx - 0.5f
        val n = (mask and N) != 0
        val e = (mask and E) != 0
        val s = (mask and S) != 0
        val w = (mask and W) != 0
        var d = FAR
        if (n) d = min(d, dN)
        if (e) d = min(d, dE)
        if (s) d = min(d, dS)
        if (w) d = min(d, dW)
        if (n && w && dN < radius && dW < radius) d = min(d, radius - hypot(radius - dW, radius - dN))
        if (n && e && dN < radius && dE < radius) d = min(d, radius - hypot(radius - dE, radius - dN))
        if (s && w && dS < radius && dW < radius) d = min(d, radius - hypot(radius - dW, radius - dS))
        if (s && e && dS < radius && dE < radius) d = min(d, radius - hypot(radius - dE, radius - dS))
        if ((mask and NE) != 0) d = min(d, hypot(dE, dN) - 1f)
        if ((mask and SE) != 0) d = min(d, hypot(dE, dS) - 1f)
        if ((mask and SW) != 0) d = min(d, hypot(dW, dS) - 1f)
        if ((mask and NW) != 0) d = min(d, hypot(dW, dN) - 1f)
        return d
    }

    /** Un pont se traverse d'ouest en est quand l'eau le borde au nord ou au sud. */
    fun bridgeRunsEastWest(map: TileMap, p: TilePos): Boolean {
        fun water(dx: Int, dy: Int) = p.offset(dx, dy).let { map.inside(it) && map.ground(it) == Ground.WATER }
        return water(0, -1) || water(0, 1)
    }
}
