package app.regie.game.engine.village

import app.regie.domain.model.Agent
import app.regie.domain.model.Session
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import app.regie.game.engine.world.World

/**
 * Dessine le hameau d'une disposition : sol, maisons, décor et collisions.
 *
 * Plan, en coordonnées logiques centrées sur la place (0, 0) :
 * - la place et sa fontaine ; une rivière au nord, une ceinture d'arbres autour ;
 * - les rues vont par paires sur des rangées : la rue 0 part à l'est de la place, la rue 1 à l'ouest,
 *   les rues 2 et 3 une rangée plus bas, et ainsi de suite. Une avenue descend de la place pour
 *   desservir les rangées suivantes ;
 * - les terrains, de même largeur, s'alignent au nord de leur route, en colonnes d'une rangée à l'autre.
 *   Leur taille est fixe, faite pour l'empreinte maximale d'une maison ([HouseModel.MAX_FOOTPRINT_WIDTH] ×
 *   [HouseModel.MAX_FOOTPRINT_HEIGHT]) : elle ne dépend pas du catalogue. Chaque maison a sa porte au sud,
 *   un jardin clos et une allée jusqu'à la route.
 *
 * Le décor de l'intérieur est tiré par tuile logique : une rue qui s'allonge ne déplace aucun arbre de
 * l'intérieur. La ceinture d'arbres, plus dense, suit les bords de la carte : elle se redessine quand la
 * carte grandit.
 *
 * La carte est symétrique autour de la place : toute rue qui s'allonge au-delà des autres, à l'est comme
 * à l'ouest, élargit la carte et déplace [Village.origin].
 */
internal class VillageBuilder(
    private val catalog: HouseCatalog,
    private val decor: DecorCatalog,
    private val config: VillageConfig,
    private val inhabitants: (Agent) -> InhabitantKind,
) {
    // La géométrie des terrains ne dépend pas du catalogue : changer d'atlas ne déplace aucun terrain.
    private val plotWidth = HouseModel.MAX_FOOTPRINT_WIDTH + PLOT_GAP
    private val houseRows = HouseModel.MAX_FOOTPRINT_HEIGHT
    private val rowSpacing = houseRows + ROOF_OVERHANG + YARD + ROAD_WIDTH + LAWN_ROWS

    fun build(sessions: List<Session>, layout: VillageLayout): Village {
        val present = sessions.distinctBy { it.id }.associateBy { it.id }
        val occupant = HashMap<Pair<Int, Int>, Session>()
        val streetOf = layout.streets.withIndex().associate { (index, street) -> street.project to index }
        layout.houses.forEach { (id, house) ->
            val session = present[id] ?: return@forEach
            val street = streetOf[house.project] ?: return@forEach
            if (house.project == session.project) occupant[street to house.plot] = session
        }

        val plaza = TileRect(-PLAZA_HALF_WIDTH, -PLAZA_HALF_HEIGHT, 2 * PLAZA_HALF_WIDTH + 1, 2 * PLAZA_HALF_HEIGHT + 1)
        val streets = layout.streets.mapIndexed { index, street -> streetGeometry(index, street) }
        val lastRow = streets.maxOfOrNull { it.row } ?: 0
        val avenue = if (lastRow >= 1) {
            val top = PLAZA_HALF_HEIGHT + 1
            TileRect(-AVENUE_HALF_WIDTH, top, 2 * AVENUE_HALF_WIDTH + 1, roadTop(lastRow) + ROAD_WIDTH - top)
        } else {
            null
        }

        // Les limites de la carte.
        val structures = listOfNotNull(plaza, avenue) + streets.flatMap { street ->
            listOf(street.road, TileRect(street.sign.x, street.sign.y, 1, 1)) + street.plots.map { it.reserve }
        }
        var minY = structures.minOf { it.top }
        var maxY = structures.maxOf { it.bottom }
        val water = if (config.water) {
            val top = minY - 1 - WATER_ROWS
            minY = top
            top until top + WATER_ROWS
        } else {
            IntRange.EMPTY
        }
        minY -= BORDER
        maxY += BORDER
        val half = maxOf(-structures.minOf { it.left }, structures.maxOf { it.right }) + BORDER
        val halfWidth = maxOf(half, config.minWidth / 2)
        if (maxY - minY + 1 < config.minHeight) maxY = minY + config.minHeight - 1
        val frame = Frame(halfWidth, minY, maxY)

        val map = TileMap(frame.width, frame.height)
        fun paint(rect: TileRect, ground: Ground) = rect.tiles().forEach { map.setGround(frame.world(it), ground) }
        for (y in water) for (x in -halfWidth..halfWidth) {
            map.setGround(frame.world(TilePos(x, y)), Ground.WATER)
            map.setBlocked(frame.world(TilePos(x, y)))
        }
        streets.forEach { paint(it.road, Ground.PATH) }
        avenue?.let { paint(it, Ground.PATH) }
        paint(plaza, Ground.PLAZA)

        // Les maisons.
        val houses = streets.flatMap { street ->
            street.plots.mapNotNull { plot ->
                val session = occupant[street.index to plot.index] ?: return@mapNotNull null
                house(session, layout.houses.getValue(session.id), street, plot)
            }
        }
        houses.forEach { house ->
            map.setGround(frame.world(house.entry), Ground.PATH)
            house.footprint.tiles().forEach { map.setBlocked(frame.world(it)) }
        }

        // Le décor.
        val decorations = Decorations(map, frame, streets.flatMap { it.plots }.flatMap { it.reserve.tiles() }.toSet())
        decorations.put(decor.fountain, FOUNTAIN)
        decorations.put(decor.lantern, plaza.left, plaza.top)
        decorations.put(decor.lantern, plaza.right, plaza.top)
        decorations.put(decor.bench, -2, plaza.top)
        decorations.put(decor.bench, 2, plaza.top)
        streets.forEach { decorations.put(decor.sign, it.sign.x, it.sign.y) }
        houses.forEach { house ->
            for (x in house.footprint.left..house.footprint.right) {
                if (x == house.entry.x) continue
                val id = if (x == house.entry.x - 1 || x == house.entry.x + 1) {
                    decor.flowers[StableHash.pick(decor.flowers.size, config.seed, "jardin", x, house.entry.y)]
                } else {
                    decor.fence
                }
                decorations.put(id, x, house.entry.y)
            }
            // Une lanterne au coin du jardin, et un buisson contre un pignon.
            val area = streets[house.street].plots[house.plot].area
            if (area.left < house.footprint.left) decorations.put(decor.lantern, area.left, house.entry.y)
            val side = StableHash.pick(2, config.seed, "pignon", house.session.id.value)
            val gable = if (side == 0) house.footprint.left - 1 else house.footprint.right + 1
            if (gable in area.left + 1 until area.right) {
                val bush = decor.bushes[StableHash.pick(decor.bushes.size, config.seed, "buisson", house.session.id.value)]
                decorations.put(bush, gable, house.footprint.bottom)
            }
        }
        for (y in frame.minY..frame.maxY) for (x in -halfWidth..halfWidth) {
            val inBorder = x - (-halfWidth) < BORDER || halfWidth - x < BORDER ||
                y - frame.minY < BORDER || frame.maxY - y < BORDER
            scatter(decorations, TilePos(x, y), inBorder)
        }

        val worldHouses = houses.map { it.inWorld(frame) }
        val worldDecor = decorations.placed.map { it.inWorld(frame) }
        val objects = (worldHouses.map { it.placed } + worldDecor.map { it.placed })
            .sortedWith(compareBy<Placed>({ it.anchor.y }, { it.anchor.x }))
        return Village(
            world = World(map, objects),
            origin = frame.world(TilePos(0, 0)),
            spawn = frame.world(TilePos(0, FOUNTAIN.bottom + 1)),
            plaza = frame.world(plaza),
            streets = streets.map { street ->
                Street(
                    index = street.index,
                    project = street.project,
                    side = street.side,
                    road = frame.world(street.road),
                    sign = frame.world(street.sign),
                    plots = street.plots.map { plot ->
                        Plot(
                            street = street.index,
                            index = plot.index,
                            area = frame.world(plot.area),
                            entry = frame.world(plot.entry),
                            sessionId = occupant[street.index to plot.index]?.id,
                        )
                    },
                )
            },
            houses = worldHouses,
            decorations = worldDecor,
        )
    }

    private fun scatter(decorations: Decorations, tile: TilePos, inBorder: Boolean) {
        if (!decorations.isFree(tile) || decorations.hasNeighbour(tile)) return
        val roll = StableHash.of(config.seed, "decor", tile.x, tile.y)
        val chance = Math.floorMod(roll, PER_MILLE.toLong()).toInt()
        if (chance >= if (inBorder) BORDER_DENSITY else FIELD_DENSITY) return
        val kind = Math.floorMod(roll ushr KIND_SHIFT, PERCENT.toLong()).toInt()
        val variant = Math.floorMod(roll ushr VARIANT_SHIFT, Int.MAX_VALUE.toLong()).toInt()
        val choices = when {
            inBorder && kind < 70 -> decor.trees
            inBorder && kind < 90 -> decor.bushes
            inBorder -> decor.rocks
            kind < 35 -> decor.trees
            kind < 60 -> decor.bushes
            kind < 90 -> decor.flowers
            else -> decor.rocks
        }
        decorations.put(choices[variant % choices.size], tile.x, tile.y)
    }

    private fun house(session: Session, layout: HouseLayout, street: StreetGeometry, plot: PlotGeometry): House {
        val model = catalog[layout.modelId] ?: catalog.models.first()
        val left = plot.area.left + (plotWidth - model.footprintWidth) / 2
        val bottom = street.roadTop - YARD - 1
        val footprint = TileRect(left, bottom - model.footprintHeight + 1, model.footprintWidth, model.footprintHeight)
        val door = TilePos(left + model.doorX, footprint.top + model.doorY)
        return House(
            session = session,
            inhabitant = inhabitants(session.agent),
            street = street.index,
            plot = plot.index,
            modelId = model.id,
            roof = layout.roof,
            footprint = footprint,
            door = door,
            entry = door.offset(0, 1),
            labelAnchor = TilePos(left + model.footprintWidth / 2, footprint.top - ROOF_OVERHANG),
            chimney = model.chimney,
            windows = model.windows,
        )
    }

    private fun roadTop(row: Int) = PLAZA_HALF_HEIGHT + row * rowSpacing

    private fun streetGeometry(index: Int, street: StreetLayout): StreetGeometry {
        val row = index / 2
        val side = if (index % 2 == 0) StreetSide.EAST else StreetSide.WEST
        val roadTop = roadTop(row)
        val houseBottom = roadTop - YARD - 1
        val plots = (0 until street.plotCount).map { plot ->
            val left = if (side == StreetSide.EAST) {
                PLOT_START + plot * plotWidth
            } else {
                -(PLOT_START + (plot + 1) * plotWidth - 1)
            }
            val area = TileRect(left, houseBottom - houseRows + 1, plotWidth, houseRows + YARD)
            PlotGeometry(
                index = plot,
                area = area,
                reserve = TileRect(left, area.top - ROOF_OVERHANG, plotWidth, area.height + ROOF_OVERHANG),
                entry = TilePos(left + plotWidth / 2, roadTop - 1),
            )
        }
        val length = PLOT_START + street.plotCount * plotWidth
        val road = if (side == StreetSide.EAST) {
            TileRect(0, roadTop, length, ROAD_WIDTH)
        } else {
            TileRect(-(length - 1), roadTop, length, ROAD_WIDTH)
        }
        val signX = if (side == StreetSide.EAST) PLOT_START - 1 else -(PLOT_START - 1)
        return StreetGeometry(index, street.project, side, row, roadTop, road, TilePos(signX, roadTop - 1), plots)
    }

    /** Passage des coordonnées logiques (place en 0, 0) à celles de la carte. */
    private class Frame(val halfWidth: Int, val minY: Int, val maxY: Int) {
        val width = 2 * halfWidth + 1
        val height = maxY - minY + 1

        fun world(p: TilePos) = TilePos(p.x + halfWidth, p.y - minY)

        fun world(r: TileRect) = r.offset(halfWidth, -minY)
    }

    /** Le décor posé, en coordonnées logiques, avec ses collisions reportées sur la carte. */
    private inner class Decorations(
        private val map: TileMap,
        private val frame: Frame,
        private val reserved: Set<TilePos>,
    ) {
        val placed = mutableListOf<Decoration>()
        private val taken = HashSet<TilePos>()

        fun isFree(tile: TilePos): Boolean {
            val world = frame.world(tile)
            return map.inside(world) && map.ground(world) == Ground.GRASS && !map.isBlocked(world) &&
                tile !in reserved && tile !in taken
        }

        fun hasNeighbour(tile: TilePos): Boolean =
            listOf(tile.offset(0, -1), tile.offset(1, 0), tile.offset(0, 1), tile.offset(-1, 0)).any { it in taken }

        fun put(objectId: String, x: Int, y: Int) = put(objectId, TileRect(x, y, 1, 1))

        fun put(objectId: String, footprint: TileRect) {
            val blocking = decor.isBlocking(objectId)
            placed += Decoration(Placed(objectId, footprint.bottomLeft), footprint, blocking)
            footprint.tiles().forEach { tile ->
                taken += tile
                if (blocking) map.setBlocked(frame.world(tile))
            }
        }
    }

    private fun House.inWorld(frame: Frame) = copy(
        footprint = frame.world(footprint),
        door = frame.world(door),
        entry = frame.world(entry),
        labelAnchor = frame.world(labelAnchor),
    )

    private fun Decoration.inWorld(frame: Frame) =
        copy(placed = placed.copy(anchor = frame.world(placed.anchor)), footprint = frame.world(footprint))

    private class StreetGeometry(
        val index: Int,
        val project: String,
        val side: StreetSide,
        val row: Int,
        val roadTop: Int,
        val road: TileRect,
        val sign: TilePos,
        val plots: List<PlotGeometry>,
    )

    /** [reserve] : le terrain et la place de son toit, interdits au décor. */
    private class PlotGeometry(val index: Int, val area: TileRect, val reserve: TileRect, val entry: TilePos)

    private companion object {
        const val PLAZA_HALF_WIDTH = 4
        const val PLAZA_HALF_HEIGHT = 3
        const val AVENUE_HALF_WIDTH = 1
        const val ROAD_WIDTH = 2
        const val YARD = 1
        const val ROOF_OVERHANG = 2
        const val LAWN_ROWS = 2
        const val PLOT_GAP = 2
        const val PLOT_START = PLAZA_HALF_WIDTH + 2
        const val WATER_ROWS = 2
        const val BORDER = 3
        const val PER_MILLE = 1000
        const val PERCENT = 100
        const val BORDER_DENSITY = 550
        const val FIELD_DENSITY = 100
        const val KIND_SHIFT = 16
        const val VARIANT_SHIFT = 32

        /** La fontaine, au centre de la place ; le coursier arrive juste devant. */
        val FOUNTAIN = TileRect(-1, -1, 3, 3)
    }
}
