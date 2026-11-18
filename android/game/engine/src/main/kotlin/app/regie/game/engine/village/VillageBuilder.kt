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
 * - la place et sa fontaine ; une rivière au nord, franchie par un pont au bout du sentier qui monte de
 *   la place ; une ceinture d'arbres autour ;
 * - les rues vont par paires sur des rangées : la rue 0 part à l'est de la place, la rue 1 à l'ouest,
 *   les rues 2 et 3 une rangée plus bas, et ainsi de suite. Une avenue descend de la place pour
 *   desservir les rangées suivantes ;
 * - les terrains, de même largeur, s'alignent au nord de leur route, en colonnes d'une rangée à l'autre.
 *   Leur taille est fixe, faite pour l'empreinte maximale d'une maison ([HouseModel.MAX_FOOTPRINT_WIDTH] ×
 *   [HouseModel.MAX_FOOTPRINT_HEIGHT]) : elle ne dépend pas du catalogue. Chaque maison a sa porte au sud,
 *   un jardin et une allée jusqu'à la route.
 *
 * Le décor est posé par [VillageDecorator]. La carte est symétrique autour de la place : toute rue qui
 * s'allonge au-delà des autres, à l'est comme à l'ouest, élargit la carte et déplace [Village.origin].
 */
internal class VillageBuilder(
    private val catalog: HouseCatalog,
    private val decor: DecorCatalog,
    private val config: VillageConfig,
    private val inhabitants: (Agent) -> InhabitantKind,
) {
    fun build(sessions: List<Session>, layout: VillageLayout): Village {
        val present = sessions.distinctBy { it.id }.associateBy { it.id }
        val occupant = HashMap<Pair<Int, Int>, Session>()
        val streetOf = layout.streets.withIndex().associate { (index, street) -> street.project to index }
        layout.houses.forEach { (id, house) ->
            val session = present[id] ?: return@forEach
            val street = streetOf[house.project] ?: return@forEach
            if (house.project == session.project) occupant[street to house.plot] = session
        }

        val plaza = VillageGeometry.PLAZA
        val streets = layout.streets.mapIndexed { index, street -> streetGeometry(index, street) }
        val lastRow = streets.maxOfOrNull { it.row } ?: 0
        val avenue = if (lastRow >= 1) {
            val top = plaza.bottom + 1
            TileRect(-VillageGeometry.AVENUE_HALF_WIDTH, top, 2 * VillageGeometry.AVENUE_HALF_WIDTH + 1, VillageGeometry.roadTop(lastRow) + VillageGeometry.ROAD_WIDTH - top)
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
            val top = minY - 1 - VillageGeometry.WATER_ROWS
            minY = top
            top until top + VillageGeometry.WATER_ROWS
        } else {
            IntRange.EMPTY
        }
        minY -= VillageGeometry.BORDER
        maxY += VillageGeometry.BORDER
        val half = maxOf(-structures.minOf { it.left }, structures.maxOf { it.right }) + VillageGeometry.BORDER
        val halfWidth = maxOf(half, config.minWidth / 2)
        if (maxY - minY + 1 < config.minHeight) maxY = minY + config.minHeight - 1
        val frame = VillageFrame(halfWidth, minY, maxY)

        val map = TileMap(frame.width, frame.height)
        fun paint(rect: TileRect, ground: Ground) = rect.tiles().forEach { map.setGround(frame.world(it), ground) }
        for (y in water) for (x in -halfWidth..halfWidth) {
            map.setGround(frame.world(TilePos(x, y)), Ground.WATER)
            map.setBlocked(frame.world(TilePos(x, y)))
        }
        streets.forEach { paint(it.road, Ground.PATH) }
        avenue?.let { paint(it, Ground.PATH) }
        // Le sentier du nord : de la place jusqu'au bord de la carte, avec un pont sur la rivière.
        val trail = if (config.water) {
            TileRect(-VillageGeometry.AVENUE_HALF_WIDTH, frame.minY, 2 * VillageGeometry.AVENUE_HALF_WIDTH + 1, plaza.top - frame.minY)
        } else {
            null
        }
        trail?.tiles()?.forEach { tile ->
            val world = frame.world(tile)
            if (tile.y in water) {
                map.setGround(world, Ground.BRIDGE)
                map.setBlocked(world, false)
            } else {
                map.setGround(world, Ground.PATH)
            }
        }
        // La place, aux coins arrondis en escalier : les rues y entrent par les coins du bas.
        plaza.tiles().filter { it !in VillageGeometry.PLAZA_CORNERS }.forEach { map.setGround(frame.world(it), Ground.PLAZA) }

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
        val decorations = VillageDecorator(decor, config.seed).decorate(
            DecorPlan(map, frame, streets, houses, avenue, trail, water),
        )

        val worldHouses = houses.map { it.inWorld(frame) }
        val worldDecor = decorations.map { it.inWorld(frame) }
        val objects = (worldHouses.map { it.placed } + worldDecor.map { it.placed })
            .sortedWith(compareBy<Placed>({ it.anchor.y }, { it.anchor.x }))
        return Village(
            world = World(map, objects),
            origin = frame.world(TilePos(0, 0)),
            spawn = frame.world(TilePos(0, VillageGeometry.FOUNTAIN.bottom + 1)),
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

    private fun house(session: Session, layout: HouseLayout, street: StreetGeometry, plot: PlotGeometry): House {
        val model = catalog[layout.modelId] ?: catalog.models.first()
        val left = plot.area.left + (VillageGeometry.PLOT_WIDTH - model.footprintWidth) / 2
        val bottom = street.roadTop - VillageGeometry.YARD - 1
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
            labelAnchor = TilePos(left + model.footprintWidth / 2, footprint.top - VillageGeometry.ROOF_OVERHANG),
            chimney = model.chimney,
            windows = model.windows,
        )
    }

    private fun streetGeometry(index: Int, street: StreetLayout): StreetGeometry {
        val row = PlotCell.rowOf(index)
        val side = PlotCell.sideOf(index)
        val roadTop = VillageGeometry.roadTop(row)
        val houseBottom = roadTop - VillageGeometry.YARD - 1
        val plotWidth = VillageGeometry.PLOT_WIDTH
        val plots = (0 until street.plotCount).map { plot ->
            val left = if (side == StreetSide.EAST) {
                VillageGeometry.PLOT_START + plot * plotWidth
            } else {
                -(VillageGeometry.PLOT_START + (plot + 1) * plotWidth - 1)
            }
            val area = TileRect(left, houseBottom - VillageGeometry.HOUSE_ROWS + 1, plotWidth, VillageGeometry.HOUSE_ROWS + VillageGeometry.YARD)
            PlotGeometry(
                index = plot,
                area = area,
                reserve = TileRect(left, area.top - VillageGeometry.ROOF_OVERHANG, plotWidth, area.height + VillageGeometry.ROOF_OVERHANG),
                entry = TilePos(left + plotWidth / 2, roadTop - 1),
            )
        }
        val length = VillageGeometry.PLOT_START + street.plotCount * plotWidth
        val road = if (side == StreetSide.EAST) {
            TileRect(0, roadTop, length, VillageGeometry.ROAD_WIDTH)
        } else {
            TileRect(-(length - 1), roadTop, length, VillageGeometry.ROAD_WIDTH)
        }
        val signX = if (side == StreetSide.EAST) VillageGeometry.PLOT_START - 1 else -(VillageGeometry.PLOT_START - 1)
        return StreetGeometry(index, street.project, side, row, roadTop, road, TilePos(signX, roadTop - 1), plots)
    }

    private fun House.inWorld(frame: VillageFrame) = copy(
        footprint = frame.world(footprint),
        door = frame.world(door),
        entry = frame.world(entry),
        labelAnchor = frame.world(labelAnchor),
    )

    private fun Decoration.inWorld(frame: VillageFrame) =
        copy(placed = placed.copy(anchor = frame.world(placed.anchor)), footprint = frame.world(footprint))
}

/** Les mesures du plan, en tuiles, en coordonnées logiques (la place en 0, 0). */
internal object VillageGeometry {
    const val PLAZA_HALF_WIDTH = 4
    const val PLAZA_HALF_HEIGHT = 3
    const val AVENUE_HALF_WIDTH = 1
    const val ROAD_WIDTH = 2
    const val YARD = 1
    const val ROOF_OVERHANG = 2
    const val LAWN_ROWS = 2

    // La géométrie des terrains ne dépend pas du catalogue : changer d'atlas ne déplace aucun terrain.
    const val PLOT_GAP = 2
    const val PLOT_WIDTH = HouseModel.MAX_FOOTPRINT_WIDTH + PLOT_GAP
    const val HOUSE_ROWS = HouseModel.MAX_FOOTPRINT_HEIGHT
    const val PLOT_START = PLAZA_HALF_WIDTH + 2
    const val ROW_SPACING = HOUSE_ROWS + ROOF_OVERHANG + YARD + ROAD_WIDTH + LAWN_ROWS
    const val WATER_ROWS = 3
    const val BORDER = 3

    val PLAZA = TileRect(-PLAZA_HALF_WIDTH, -PLAZA_HALF_HEIGHT, 2 * PLAZA_HALF_WIDTH + 1, 2 * PLAZA_HALF_HEIGHT + 1)

    /** Les tuiles retirées aux coins de la place pour l'arrondir ; celles du bas restent à la route. */
    val PLAZA_CORNERS: Set<TilePos> = listOf(-1, 1).flatMap { side ->
        listOf(
            TilePos(side * PLAZA_HALF_WIDTH, -PLAZA_HALF_HEIGHT),
            TilePos(side * (PLAZA_HALF_WIDTH - 1), -PLAZA_HALF_HEIGHT),
            TilePos(side * PLAZA_HALF_WIDTH, -PLAZA_HALF_HEIGHT + 1),
            TilePos(side * PLAZA_HALF_WIDTH, PLAZA_HALF_HEIGHT),
        )
    }.toSet()

    /** La fontaine, au centre de la place ; le coursier arrive juste devant. */
    val FOUNTAIN = TileRect(-1, -1, 3, 3)

    fun roadTop(row: Int) = PLAZA_HALF_HEIGHT + row * ROW_SPACING
}

/** Passage des coordonnées logiques (place en 0, 0) à celles de la carte. */
internal class VillageFrame(val halfWidth: Int, val minY: Int, val maxY: Int) {
    val width = 2 * halfWidth + 1
    val height = maxY - minY + 1

    fun world(p: TilePos) = TilePos(p.x + halfWidth, p.y - minY)

    fun world(r: TileRect) = r.offset(halfWidth, -minY)

    /** Dans l'une des [VillageGeometry.BORDER] rangées ou colonnes du bord de la carte. */
    fun inBorder(p: TilePos): Boolean = p.x + halfWidth < VillageGeometry.BORDER || halfWidth - p.x < VillageGeometry.BORDER ||
        p.y - minY < VillageGeometry.BORDER || maxY - p.y < VillageGeometry.BORDER
}

internal class StreetGeometry(
    val index: Int,
    val project: String,
    val side: StreetSide,
    val row: Int,
    val roadTop: Int,
    val road: TileRect,
    val sign: TilePos,
    val plots: List<PlotGeometry>,
)

/** [reserve] : le terrain et la place de son toit, interdits au décor sauf au jardin de sa maison. */
internal class PlotGeometry(val index: Int, val area: TileRect, val reserve: TileRect, val entry: TilePos)
