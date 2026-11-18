package app.regie.game.engine.village

/** Un point en pixels natifs, relatif au coin haut gauche de l'empreinte ; y négatif = au-dessus (toit). */
data class PixelPoint(val x: Int, val y: Int)

/** Un rectangle en pixels natifs, relatif au coin haut gauche de l'empreinte. */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Un modèle de maison de l'atlas `maisons` (contrat des assets, plan-dev-jeu.md).
 *
 * - [footprintWidth] × [footprintHeight] : l'empreinte au sol, en tuiles, entièrement bloquée.
 * - [doorX], [doorY] : la tuile de la porte dans l'empreinte, depuis son coin haut gauche. Elle est
 *   sur la dernière rangée : la tuile juste en dessous reste accessible.
 * - [chimney] : le point d'où sort la fumée ; [windows] : les rectangles à éclairer.
 *
 * L'empreinte ne dépasse pas [MAX_FOOTPRINT_WIDTH] × [MAX_FOOTPRINT_HEIGHT] tuiles : c'est la taille des
 * terrains du hameau, fixe pour qu'un changement d'atlas ne déplace aucun terrain.
 */
data class HouseModel(
    val id: String,
    val footprintWidth: Int,
    val footprintHeight: Int,
    val doorX: Int,
    val doorY: Int,
    val chimney: PixelPoint? = null,
    val windows: List<PixelRect> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Modèle sans identifiant" }
        require(footprintWidth > 0 && footprintHeight > 0) { "$id : empreinte vide" }
        require(footprintWidth <= MAX_FOOTPRINT_WIDTH && footprintHeight <= MAX_FOOTPRINT_HEIGHT) {
            "$id : empreinte $footprintWidth × $footprintHeight au-delà de $MAX_FOOTPRINT_WIDTH × $MAX_FOOTPRINT_HEIGHT"
        }
        require(doorX in 0 until footprintWidth) { "$id : porte hors de l'empreinte" }
        require(doorY == footprintHeight - 1) { "$id : la porte doit être sur la dernière rangée de l'empreinte" }
    }

    companion object {
        /** Empreinte maximale d'une maison, en tuiles : la contrainte que l'atlas `maisons` doit respecter. */
        const val MAX_FOOTPRINT_WIDTH = 7
        const val MAX_FOOTPRINT_HEIGHT = 5
    }
}

/**
 * Les modèles de maison disponibles, dans un ordre fixe (le tirage d'un modèle en dépend).
 * Le jeu les lira dans `maisons.json` (ticket 42) ; [DEFAULT] tient lieu d'atlas en attendant.
 */
class HouseCatalog(models: List<HouseModel>) {
    val models: List<HouseModel> = models.toList()

    init {
        require(this.models.isNotEmpty()) { "Catalogue de maisons vide" }
        require(this.models.map { it.id }.toSet().size == this.models.size) { "Modèles en double" }
    }

    private val byId = this.models.associateBy { it.id }

    operator fun get(id: String): HouseModel? = byId[id]

    operator fun contains(id: String): Boolean = id in byId

    companion object {
        private const val TILE = 16

        /** Six modèles ; le premier est l'exemple du contrat des assets. */
        val DEFAULT = HouseCatalog(
            listOf(
                HouseModel(
                    "maison.chaumiere", 6, 4, 3, 3,
                    chimney = PixelPoint(4 * TILE + 4, -18),
                    windows = listOf(PixelRect(16, 36, 10, 10), PixelRect(68, 36, 10, 10)),
                ),
                HouseModel(
                    "maison.atelier", 7, 4, 2, 3,
                    chimney = PixelPoint(6 * TILE - 6, -20),
                    windows = listOf(PixelRect(56, 34, 16, 10), PixelRect(80, 34, 16, 10)),
                ),
                HouseModel(
                    "maison.colombages", 5, 4, 2, 3,
                    chimney = PixelPoint(TILE, -16),
                    windows = listOf(PixelRect(8, 36, 10, 12), PixelRect(62, 36, 10, 12)),
                ),
                HouseModel(
                    "maison.moulin", 6, 5, 2, 4,
                    windows = listOf(PixelRect(56, 40, 12, 12), PixelRect(56, 16, 12, 10)),
                ),
                HouseModel(
                    "maison.cottage", 5, 3, 2, 2,
                    chimney = PixelPoint(3 * TILE + 6, -14),
                    windows = listOf(PixelRect(10, 22, 10, 10), PixelRect(60, 22, 10, 10)),
                ),
                HouseModel(
                    "maison.longere", 7, 3, 4, 2,
                    chimney = PixelPoint(TILE + 4, -14),
                    windows = listOf(PixelRect(12, 22, 12, 10), PixelRect(36, 22, 12, 10), PixelRect(92, 22, 12, 10)),
                ),
            ),
        )
    }
}
