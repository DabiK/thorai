package app.regie.game.engine.courier

import app.regie.domain.model.Session
import app.regie.game.engine.village.House
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.session
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import app.regie.game.engine.world.World

/** Une maison de test : [footprint] bloquée, porte au milieu de la dernière rangée, entrée juste en dessous. */
fun testHouse(session: Session, footprint: TileRect, plot: Int = 0): House {
    val door = TilePos(footprint.left + footprint.width / 2, footprint.bottom)
    return House(
        session = session,
        inhabitant = InhabitantKind.SCRIBE,
        street = 0,
        plot = plot,
        modelId = "maison.test",
        roof = RoofColors(0, 0, 0),
        footprint = footprint,
        door = door,
        entry = door.offset(0, 1),
        labelAnchor = TilePos(door.x, footprint.top - 1),
        chimney = null,
        windows = emptyList(),
    )
}

/**
 * Un petit hameau dessiné à la main, 14 × 9 tuiles, la place en (6, 4) :
 *
 * ```
 * ..............
 * ..AAA...BBB...
 * ..AAA...BBB...     portes : A (3, 2), B (9, 2), C (6, 6)
 * ......#.......     un rocher en (6, 3)
 * ......@.......     la place, d'où part le coursier
 * .....CCC......
 * .....CCC......
 * ..............
 * ..............
 * ```
 */
class SmallVillage(
    val a: Session = session("a", "p"),
    val b: Session = session("b", "p"),
    val c: Session = session("c", "p"),
    /** Décale toute la carte vers la droite, comme un hameau qui s'élargit. */
    shift: Int = 0,
    extraBlocked: List<TilePos> = emptyList(),
    withB: Boolean = true,
) {
    val origin = TilePos(6 + shift, 4)
    val rock = TilePos(6 + shift, 3)
    val houseA = testHouse(a, TileRect(2 + shift, 1, 3, 2), plot = 0)
    val houseB = testHouse(b, TileRect(8 + shift, 1, 3, 2), plot = 1)
    val houseC = testHouse(c, TileRect(5 + shift, 5, 3, 2), plot = 2)
    val houses = listOfNotNull(houseA, houseB.takeIf { withB }, houseC)

    val village: Village = run {
        val map = TileMap(14 + shift * 2, 9)
        houses.forEach { house -> house.footprint.tiles().forEach { map.setBlocked(it) } }
        map.setBlocked(rock)
        extraBlocked.forEach { map.setBlocked(it) }
        Village(
            world = World(map, houses.map { it.placed }),
            origin = origin,
            spawn = origin,
            plaza = TileRect(origin.x, origin.y, 1, 1),
            streets = emptyList(),
            houses = houses,
            decorations = emptyList(),
        )
    }
}
