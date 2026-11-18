package app.regie.game.engine.pathfinding

import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * Plus court chemin A* sur [map], en 4 directions, de [from] à [to] inclus.
 *
 * - `null` si [to] est bloqué, hors de la carte ou inaccessible.
 * - [from] peut être bloqué (le coursier y est déjà) ; les tuiles suivantes, non.
 * - Déterministe : à coût égal, l'ordre des voisins (haut, droite, bas, gauche) départage.
 */
fun findPath(map: TileMap, from: TilePos, to: TilePos): List<TilePos>? {
    if (!map.inside(from) || map.isBlocked(to)) return null
    if (from == to) return listOf(from)

    val size = map.width * map.height
    val cost = IntArray(size) { Int.MAX_VALUE }
    val cameFrom = IntArray(size) { -1 }
    val closed = BooleanArray(size)
    val open = PriorityQueue(compareBy<Node>({ it.estimate }, { it.remaining }, { it.order }))
    var order = 0L

    fun indexOf(p: TilePos) = p.y * map.width + p.x
    fun posOf(index: Int) = TilePos(index % map.width, index / map.width)
    fun distance(p: TilePos) = abs(p.x - to.x) + abs(p.y - to.y)

    val start = indexOf(from)
    val goal = indexOf(to)
    cost[start] = 0
    open += Node(start, distance(from), distance(from), order++)

    while (open.isNotEmpty()) {
        val node = open.poll()
        if (closed[node.index]) continue
        if (node.index == goal) return rebuild(cameFrom, goal, ::posOf)
        closed[node.index] = true
        val here = posOf(node.index)
        for ((dx, dy) in STEPS) {
            val next = here.offset(dx, dy)
            if (map.isBlocked(next)) continue
            val nextIndex = indexOf(next)
            val nextCost = cost[node.index] + 1
            if (closed[nextIndex] || nextCost >= cost[nextIndex]) continue
            cost[nextIndex] = nextCost
            cameFrom[nextIndex] = node.index
            val remaining = distance(next)
            open += Node(nextIndex, nextCost + remaining, remaining, order++)
        }
    }
    return null
}

private class Node(val index: Int, val estimate: Int, val remaining: Int, val order: Long)

private val STEPS = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)

private fun rebuild(cameFrom: IntArray, goal: Int, posOf: (Int) -> TilePos): List<TilePos> {
    val path = ArrayList<TilePos>()
    var index = goal
    while (index != -1) {
        path += posOf(index)
        index = cameFrom[index]
    }
    path.reverse()
    return path
}
