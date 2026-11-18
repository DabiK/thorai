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
 * - une rue ne raccourcit pas et ne disparaît pas, même sans session.
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
        val streets = mutableListOf<StreetLayout>()
        val streetOf = HashMap<String, Int>()
        previous.streets.forEach { street ->
            if (street.project !in streetOf) {
                streetOf[street.project] = streets.size
                streets += street.copy(plotCount = maxOf(street.plotCount, 1))
            }
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

        val toPlace = mutableListOf<Session>()
        for (session in present) {
            val street = streetOf.getValue(session.project)
            val kept = houses[session.id]
            if (kept != null && kept.project == session.project &&
                kept.plot in 0 until streets[street].plotCount && (street to kept.plot) !in occupant
            ) {
                occupant[street to kept.plot] = session.id
            } else {
                toPlace += session
            }
        }

        for (session in toPlace) {
            val street = streetOf.getValue(session.project)
            val plot = choosePlot(street, streets, houses, presentIds, occupant)
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
            val house = houses.getValue(session.id)
            if (house.modelId !in catalog) {
                val street = streetOf.getValue(session.project)
                houses[session.id] = house.copy(modelId = pickModel(session.id, street, house.plot, houses, occupant))
            }
        }

        streets.indices.forEach { index ->
            val street = streets[index]
            val used = occupant.keys.count { it.first == index }
            val missing = config.minFreePlots - (street.plotCount - used)
            if (missing > 0) streets[index] = street.copy(plotCount = street.plotCount + missing)
        }

        return VillageLayout(streets.toList(), houses.toMap())
    }

    private fun choosePlot(
        street: Int,
        streets: MutableList<StreetLayout>,
        houses: MutableMap<SessionId, HouseLayout>,
        presentIds: Set<SessionId>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): Int {
        val layout = streets[street]
        val kept = houses.filter { (id, house) -> id !in presentIds && house.project == layout.project }
        val keptPlots = kept.values.mapTo(HashSet()) { it.plot }
        val free = (0 until layout.plotCount).filter { (street to it) !in occupant }
        free.firstOrNull { it !in keptPlots }?.let { return it }
        free.firstOrNull()?.let { plot ->
            kept.filterValues { it.plot == plot }.keys.forEach { houses.remove(it) }
            return plot
        }
        streets[street] = layout.copy(plotCount = layout.plotCount + 1)
        return layout.plotCount
    }

    private fun neighbours(
        street: Int,
        plot: Int,
        houses: Map<SessionId, HouseLayout>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): List<HouseLayout> =
        listOf(plot - 1, plot + 1).mapNotNull { occupant[street to it] }.mapNotNull { houses[it] }

    private fun pickModel(
        id: SessionId,
        street: Int,
        plot: Int,
        houses: Map<SessionId, HouseLayout>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): String {
        val avoid = neighbours(street, plot, houses, occupant).mapTo(HashSet()) { it.modelId }
        val models = catalog.models
        val start = StableHash.pick(models.size, config.seed, "maison", id.value)
        return models.indices.map { models[(start + it) % models.size].id }.firstOrNull { it !in avoid } ?: models[start].id
    }

    private fun pickRoof(
        id: SessionId,
        street: Int,
        plot: Int,
        houses: Map<SessionId, HouseLayout>,
        occupant: Map<Pair<Int, Int>, SessionId>,
    ): RoofColors {
        val avoid = neighbours(street, plot, houses, occupant).mapTo(HashSet()) { it.roof }
        val start = StableHash.pick(roofs.size, config.seed, "toit", id.value)
        return roofs.indices.map { roofs[(start + it) % roofs.size] }.firstOrNull { it !in avoid } ?: roofs[start]
    }
}
