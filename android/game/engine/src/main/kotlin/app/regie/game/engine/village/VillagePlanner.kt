package app.regie.game.engine.village

import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.game.engine.world.RoofColors

/**
 * Attribue une rue à chaque projet et un terrain à chaque session, à partir de la disposition
 * précédente. Règles :
 *
 * - une rue ouverte garde sa place ; un projet nouveau ouvre une rue à la suite (par ordre alphabétique
 *   quand plusieurs arrivent ensemble) ;
 * - une session déjà placée garde son terrain, son modèle et son toit ;
 * - une session nouvelle prend le premier terrain libre de sa rue, en évitant ceux que gardent des
 *   sessions disparues ; à défaut, elle prend celui d'une session disparue ; à défaut, la rue s'allonge ;
 * - une rue garde toujours [VillageConfig.minFreePlots] terrains libres, quitte à s'allonger ;
 * - une rue ne raccourcit pas et ne disparaît pas, même sans session ;
 * - un terrain réservé ([VillageLayout.reservation], ticket 48) n'est donné qu'à la session du chantier ;
 *   tant qu'elle n'est pas connue, une session nouvelle de son projet attend pour être placée.
 *
 * Les sessions sont traitées par identifiant : l'ordre de la vue d'ensemble n'y change rien.
 */
internal class VillagePlanner(
    private val catalog: HouseCatalog,
    private val roofs: List<RoofColors>,
    private val config: VillageConfig,
) {
    init {
        require(roofs.isNotEmpty()) { "Palette de toits vide" }
    }

    fun plan(sessions: List<Session>, previous: VillageLayout): VillageLayout {
        val reservation = previous.reservation
        val streets = mutableListOf<StreetLayout>()
        val streetOf = HashMap<String, Int>()
        previous.streets.forEach { street ->
            if (street.project !in streetOf) {
                streetOf[street.project] = streets.size
                streets += street.copy(plotCount = maxOf(street.plotCount, 1))
            }
        }
        // La rue d'un nouveau projet réservé s'ouvre avant celles des autres projets nouveaux.
        if (reservation != null && reservation.project !in streetOf) {
            streetOf[reservation.project] = streets.size
            streets += StreetLayout(reservation.project, config.initialPlots)
        }
        sessions.map { it.project }.distinct().filter { it !in streetOf }.sorted().forEach { project ->
            streetOf[project] = streets.size
            streets += StreetLayout(project, config.initialPlots)
        }

        val present = sessions.distinctBy { it.id }.sortedBy { it.id.value }
        val presentIds = present.mapTo(HashSet()) { it.id }
        // Une maison sans rue (disposition abîmée) est oubliée : sa session sera replacée.
        val houses = previous.houses.filterValues { it.project in streetOf }.toMutableMap()
        val occupant = HashMap<Pair<Int, Int>, SessionId>()

        // Le terrain réservé : la rue s'allonge jusqu'à lui ; une session disparue qui le gardait le perd.
        val reserved = reservation?.let { streetOf.getValue(it.project) to it.plot }
        if (reservation != null && reserved != null) {
            val street = reserved.first
            if (streets[street].plotCount <= reservation.plot) streets[street] = streets[street].copy(plotCount = reservation.plot + 1)
            houses.entries.removeAll { (id, house) ->
                id != reservation.session && id !in presentIds && house.project == reservation.project && house.plot == reservation.plot
            }
        }
        val builder = present.firstOrNull { it.id == reservation?.session && it.project == reservation.project }
        val blocked = if (reserved != null && builder == null) setOf(reserved) else emptySet()

        val toPlace = mutableListOf<Session>()
        for (session in present) {
            if (session == builder) continue
            val street = streetOf.getValue(session.project)
            val kept = houses[session.id]
            if (kept != null && kept.project == session.project &&
                kept.plot in 0 until streets[street].plotCount && (street to kept.plot) !in occupant && (street to kept.plot) !in blocked &&
                (street to kept.plot) != reserved
            ) {
                occupant[street to kept.plot] = session.id
            } else if (reservation != null && reservation.session == null && session.project == reservation.project) {
                // Une session nouvelle du projet en chantier attend : elle est peut-être celle du chantier.
                continue
            } else {
                toPlace += session
            }
        }

        // La session du chantier prend son terrain réservé, et rien d'autre.
        if (builder != null && reserved != null) {
            occupant[reserved] = builder.id
            val former = houses[builder.id]?.takeIf { it.project == builder.project && it.plot == reserved.second }
            houses[builder.id] = HouseLayout(
                project = builder.project,
                plot = reserved.second,
                modelId = former?.modelId?.takeIf { it in catalog } ?: "",
                roof = former?.roof ?: pickRoof(builder.id, reserved.first, reserved.second, houses, occupant),
            )
        }

        for (session in toPlace) {
            val street = streetOf.getValue(session.project)
            val plot = choosePlot(street, streets, houses, presentIds, occupant, blocked)
            occupant[street to plot] = session.id
            val former = houses[session.id]
            houses[session.id] = HouseLayout(
                project = session.project,
                plot = plot,
                modelId = former?.modelId?.takeIf { it in catalog } ?: "",
                roof = former?.roof ?: pickRoof(session.id, street, plot, houses, occupant),
            )
        }

        // Un modèle vide (nouvelle maison) ou retiré du catalogue est tiré à nouveau.
        present.forEach { session ->
            val house = houses[session.id] ?: return@forEach
            if (house.modelId !in catalog) {
                val street = streetOf.getValue(session.project)
                houses[session.id] = house.copy(modelId = pickModel(session.id, street, house.plot, houses, occupant))
            }
        }

        streets.indices.forEach { index ->
            val street = streets[index]
            val used = occupant.keys.count { it.first == index } + blocked.count { it.first == index }
            val missing = config.minFreePlots - (street.plotCount - used)
            if (missing > 0) streets[index] = street.copy(plotCount = street.plotCount + missing)
        }

        return VillageLayout(streets.toList(), houses.toMap(), reservation.takeIf { builder == null })
    }

    /**
     * Réserve un terrain de la rue de [project] pour la session qu'on va créer : le premier terrain libre
     * que ne garde aucune session disparue, sinon le premier libre, sinon un terrain de plus au bout de la
     * rue. Un projet sans rue en ouvre une, à la suite des autres. Le terrain choisi est celui où le
     * coursier se rend pendant la fiche ; la maison y sera bâtie, et nulle part ailleurs.
     */
    fun reserve(sessions: List<Session>, previous: VillageLayout, project: String): VillageLayout {
        val base = plan(sessions, previous.copy(reservation = null))
        val street = base.streets.indexOfFirst { it.project == project }
        if (street < 0) return plan(sessions, base.copy(reservation = PlotReservation(project, 0, opensStreet = true)))
        val presentIds = sessions.mapTo(HashSet()) { it.id }
        val ofProject = base.houses.filterValues { it.project == project }
        val used = ofProject.filterKeys { it in presentIds }.values.mapTo(HashSet()) { it.plot }
        val kept = ofProject.filterKeys { it !in presentIds }.values.mapTo(HashSet()) { it.plot }
        val count = base.streets[street].plotCount
        val plot = (0 until count).firstOrNull { it !in used && it !in kept } ?: (0 until count).firstOrNull { it !in used } ?: count
        return plan(sessions, base.copy(reservation = PlotReservation(project, plot)))
    }

    /**
     * La réservation tombe (création refusée, incertaine ou annulée) : le terrain redevient libre. Une rue
     * ouverte par la réservation disparaît si aucune maison n'y est bâtie et qu'aucune rue n'a été ouverte
     * après elle ; sinon elle reste, vide, comme toute rue.
     */
    fun release(previous: VillageLayout): VillageLayout {
        val reservation = previous.reservation ?: return previous
        val street = previous.streets.indexOfFirst { it.project == reservation.project }
        val empty = previous.houses.values.none { it.project == reservation.project }
        val streets = if (reservation.opensStreet && empty && street == previous.streets.lastIndex) previous.streets.dropLast(1) else previous.streets
        return previous.copy(streets = streets, reservation = null)
    }

    private fun choosePlot(
        street: Int,
        streets: MutableList<StreetLayout>,
        houses: MutableMap<SessionId, HouseLayout>,
        presentIds: Set<SessionId>,
        occupant: Map<Pair<Int, Int>, SessionId>,
        blocked: Set<Pair<Int, Int>>,
    ): Int {
        val layout = streets[street]
        val kept = houses.filter { (id, house) -> id !in presentIds && house.project == layout.project }
        val keptPlots = kept.values.mapTo(HashSet()) { it.plot }
        val free = (0 until layout.plotCount).filter { (street to it) !in occupant && (street to it) !in blocked }
        free.firstOrNull { it !in keptPlots }?.let { return it }
        free.firstOrNull()?.let { plot ->
            kept.filterValues { it.plot == plot }.keys.forEach { houses.remove(it) }
            return plot
        }
        streets[street] = layout.copy(plotCount = layout.plotCount + 1)
        return layout.plotCount
    }

    /**
     * Les maisons qu'on voit à côté de ([street], [plot]) : [Neighbours.adjacent] sur la même rue, aux
     * terrains voisins ; [Neighbours.beside] en face, de l'autre côté de la place, ou juste au-dessus et
     * au-dessous ; [Neighbours.near] tout ce qui se voit autour (voir [PlotCell.isNear]).
     */
    private fun neighbours(
        street: Int,
        plot: Int,
        houses: Map<SessionId, HouseLayout>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): Neighbours {
        val cell = PlotCell.of(street, plot)
        val adjacent = listOf(plot - 1, plot + 1).mapNotNull { occupant[street to it] }.mapNotNull { houses[it] }
        val around = occupant.entries
            .filter { (where, _) -> cell.isNear(PlotCell.of(where.first, where.second)) }
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
        fun houses(cells: List<Map.Entry<Pair<Int, Int>, SessionId>>) = cells.mapNotNull { houses[it.value] }
        val beside = around.filter { (where, _) -> cell.isBeside(PlotCell.of(where.first, where.second)) }
        return Neighbours(adjacent, houses(beside), houses(around))
    }

    private class Neighbours(val adjacent: List<HouseLayout>, val beside: List<HouseLayout>, val near: List<HouseLayout>)

    private fun pickModel(
        id: SessionId,
        street: Int,
        plot: Int,
        houses: Map<SessionId, HouseLayout>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): String {
        val around = neighbours(street, plot, houses, occupant)
        val models = catalog.models.map { it.id }
        val start = StableHash.pick(models.size, config.seed, "maison", id.value)
        return pickAvoiding(models, start, around) { it.modelId }
    }

    private fun pickRoof(
        id: SessionId,
        street: Int,
        plot: Int,
        houses: Map<SessionId, HouseLayout>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): RoofColors {
        val around = neighbours(street, plot, houses, occupant)
        val start = StableHash.pick(roofs.size, config.seed, "toit", id.value)
        return pickAvoiding(roofs, start, around) { it.roof }
    }

    /**
     * Le choix le moins répété autour : jamais celui d'une voisine de la même rue s'il en reste un autre,
     * puis jamais celui d'une voisine franche (en face, au-dessus, au-dessous), puis le moins vu dans
     * le voisinage. À égalité, l'ordre part de [start], tiré de la session : stable.
     */
    private fun <T> pickAvoiding(options: List<T>, start: Int, around: Neighbours, value: (HouseLayout) -> T): T {
        val adjacent = around.adjacent.map(value)
        val beside = around.beside.map(value)
        val near = around.near.map(value)
        return options.indices.map { options[(start + it) % options.size] }.minBy { option ->
            ADJACENT_WEIGHT * adjacent.count { it == option } + BESIDE_WEIGHT * beside.count { it == option } +
                near.count { it == option }
        }
    }

    private companion object {
        /** Une voisine de la même rue pèse plus que tout le reste du voisinage réuni ; une voisine franche, plus que le reste du voisinage. */
        const val ADJACENT_WEIGHT = 1000
        const val BESIDE_WEIGHT = 20
    }
}
