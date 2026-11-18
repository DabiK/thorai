package app.regie.game.engine.village

import app.regie.domain.model.SessionId
import app.regie.game.engine.world.RoofColors

/**
 * Ce que le hameau retient d'une fois sur l'autre pour qu'une maison bâtie ne bouge plus.
 *
 * - [streets] : les rues, dans leur ordre d'ouverture ; l'index d'une rue fixe sa place.
 * - [houses] : pour chaque session, sa rue (par le projet), son terrain, son modèle et son toit.
 *   On y garde aussi les sessions disparues de la vue d'ensemble : leur terrain s'affiche libre,
 *   mais elles le retrouvent si elles reviennent avant qu'une autre session ne le prenne.
 * - [reservation] : le terrain retenu pour la session en cours de création (ticket 48) ; sa maison y
 *   sera bâtie, et nulle part ailleurs.
 */
data class VillageLayout(
    val streets: List<StreetLayout>,
    val houses: Map<SessionId, HouseLayout>,
    val reservation: PlotReservation? = null,
) {
    companion object {
        val EMPTY = VillageLayout(emptyList(), emptyMap())
    }
}

/** Une rue : son projet et son nombre de terrains. Une rue ne raccourcit jamais. */
data class StreetLayout(val project: String, val plotCount: Int)

/** La maison d'une session : le terrain [plot] de la rue de [project], avec son modèle et son toit. */
data class HouseLayout(val project: String, val plot: Int, val modelId: String, val roof: RoofColors)

/**
 * Un terrain réservé avant la création d'une session : le terrain [plot] de la rue de [project].
 *
 * - [session] : la session créée, dès que le pont l'a nommée. Tant qu'elle est inconnue, une session
 *   nouvelle du même projet attend pour être placée : elle pourrait être celle du chantier.
 * - [opensStreet] : la réservation a ouvert la rue de [project] (nouveau projet) ; si elle tombe sans
 *   maison bâtie, la rue disparaît avec elle.
 */
data class PlotReservation(
    val project: String,
    val plot: Int,
    val session: SessionId? = null,
    val opensStreet: Boolean = false,
) {
    init {
        require(plot >= 0) { "Terrain $plot" }
    }
}

/**
 * Port : la disposition du hameau, persistée. L'adapter Android (`SharedPreferences`) vit dans
 * `android/app` ; [InMemoryVillageLayoutStore] sert aux tests et aux aperçus.
 */
interface VillageLayoutStore {
    /** La dernière disposition enregistrée, ou [VillageLayout.EMPTY]. */
    fun load(): VillageLayout

    fun save(layout: VillageLayout)
}

/** Version factice en mémoire de [VillageLayoutStore]. */
class InMemoryVillageLayoutStore(initial: VillageLayout = VillageLayout.EMPTY) : VillageLayoutStore {
    var layout: VillageLayout = initial
        private set

    /** Nombre d'enregistrements : le générateur n'enregistre que ce qui change. */
    var saveCount: Int = 0
        private set

    override fun load(): VillageLayout = layout

    override fun save(layout: VillageLayout) {
        this.layout = layout
        saveCount++
    }
}
