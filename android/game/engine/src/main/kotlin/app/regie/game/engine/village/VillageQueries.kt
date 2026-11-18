package app.regie.game.engine.village

import app.regie.game.engine.world.TilePos
import kotlin.math.abs

/**
 * La maison la plus proche de [tile], d'après la distance (en tuiles, sur la grille) à sa tuile d'entrée,
 * à [maxDistance] au plus ; à égalité, la première dans l'ordre des rues et des terrains. C'est celle dont
 * l'interface montre le panneau quand le coursier passe devant.
 */
fun Village.nearestHouse(tile: TilePos, maxDistance: Int = Int.MAX_VALUE): House? =
    houses.asSequence()
        .map { it to abs(it.entry.x - tile.x) + abs(it.entry.y - tile.y) }
        .filter { (_, distance) -> distance <= maxDistance }
        .minWithOrNull(compareBy({ it.second }, { it.first.street }, { it.first.plot }))
        ?.first
