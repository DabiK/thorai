package app.regie.game.engine.village

/**
 * La carte du hameau (maquette 11), lue dans le hameau généré, sans rien y ajouter : les rues dans
 * leur ordre d'ouverture et, pour chacune, ses terrains de la place vers le bout de la rue.
 */
data class VillageMap(val streets: List<MapStreet>) {
    /** Les maisons bâties ou en chantier dont la session existe. */
    val houseCount: Int get() = streets.sumOf { street -> street.lots.count { it is MapLot.Built } }
}

/** Une rue : son projet et ses terrains, dans l'ordre. [opening] : la rue s'ouvre avec le chantier. */
data class MapStreet(val index: Int, val project: String, val lots: List<MapLot>, val opening: Boolean)

/** Un terrain de la carte. */
sealed interface MapLot {
    val plot: Int

    /** La maison d'une session ; [underConstruction] tant que son chantier n'est pas fini. */
    data class Built(override val plot: Int, val house: House, val underConstruction: Boolean) : MapLot

    /** Le chantier d'une session pas encore créée, ou visé pendant la fiche ([Site.stage] `null`). */
    data class Building(override val plot: Int, val site: Site) : MapLot

    data class Free(override val plot: Int) : MapLot
}

/** La carte de ce hameau. */
fun Village.map(): VillageMap = VillageMap(
    streets.map { street ->
        val siteHere = site?.takeIf { it.street == street.index }
        MapStreet(
            index = street.index,
            project = street.project,
            lots = street.plots.map { plot ->
                val house = plot.sessionId?.let(::houseOf)
                when {
                    house != null -> MapLot.Built(plot.index, house, isUnderConstruction(house))
                    siteHere != null && siteHere.plot == plot.index -> MapLot.Building(plot.index, siteHere)
                    else -> MapLot.Free(plot.index)
                }
            },
            opening = siteHere?.opensStreet == true && siteHere.session == null,
        )
    },
)
