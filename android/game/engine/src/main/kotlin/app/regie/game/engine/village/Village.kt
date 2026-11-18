package app.regie.game.engine.village

import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import app.regie.game.engine.world.World

/**
 * Le hameau construit à partir de la vue d'ensemble. Toutes les positions sont en tuiles de [world].
 *
 * - [origin] : le centre de la place. La carte est symétrique autour d'elle : dès qu'une rue, à l'est
 *   comme à l'ouest, s'allonge au-delà des autres, la carte s'élargit et tout glisse d'autant. Après
 *   chaque régénération, [relocate] reporte la position du coursier d'un hameau au suivant.
 * - [spawn] : la tuile d'arrivée du coursier, devant la fontaine.
 * - [houses] : rangées par rue puis par terrain.
 * - [decorations] : tout objet posé hors maisons, avec son empreinte ; aussi présents dans `world.objects`.
 * - [site] : le chantier d'une nouvelle session (ticket 48), s'il y en a un.
 */
data class Village(
    val world: World,
    val origin: TilePos,
    val spawn: TilePos,
    val plaza: TileRect,
    val streets: List<Street>,
    val houses: List<House>,
    val decorations: List<Decoration>,
    val site: Site? = null,
) {
    /** Les terrains sans maison ni chantier, rue par rue. */
    val freePlots: List<Plot> get() = streets.flatMap { street -> street.plots.filter { it.sessionId == null && !it.reserved } }

    /** La maison est encore un chantier : on la dessine en travaux, sans bulle ni jardin. */
    fun isUnderConstruction(house: House): Boolean =
        site != null && site.session == house.session.id && site.stage != null && site.stage != ConstructionStage.FINISHED

    fun houseOf(id: SessionId): House? = houses.firstOrNull { it.session.id == id }

    /** La maison dont [tile] est la tuile d'entrée : le coursier y entre avec A. */
    fun houseAtEntry(tile: TilePos): House? = houses.firstOrNull { it.entry == tile }

    /**
     * Où mettre, dans ce hameau, ce qui était sur la tuile [pos] de [previous] (le coursier) :
     * la même tuile par rapport à la place si elle est praticable et reliée à la place ; sinon la plus
     * proche qui l'est (une maison a pu être bâtie dessus). À appeler à chaque régénération.
     */
    fun relocate(pos: TilePos, previous: Village): TilePos {
        val map = world.map
        val moved = pos.offset(origin.x - previous.origin.x, origin.y - previous.origin.y)
        val start = TilePos(moved.x.coerceIn(0, map.width - 1), moved.y.coerceIn(0, map.height - 1))
        val reachable = reachableFromSpawn()
        val seen = BooleanArray(map.width * map.height)
        val queue = ArrayDeque(listOf(start))
        seen[start.y * map.width + start.x] = true
        while (queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            if (reachable[tile.y * map.width + tile.x]) return tile
            for ((dx, dy) in NEIGHBOURS) {
                val next = tile.offset(dx, dy)
                if (!map.inside(next) || seen[next.y * map.width + next.x]) continue
                seen[next.y * map.width + next.x] = true
                queue.addLast(next)
            }
        }
        return spawn
    }

    /** Les tuiles où le coursier peut aller depuis la place. */
    private fun reachableFromSpawn(): BooleanArray {
        val map = world.map
        val reachable = BooleanArray(map.width * map.height)
        val queue = ArrayDeque(listOf(spawn))
        reachable[spawn.y * map.width + spawn.x] = true
        while (queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            for ((dx, dy) in NEIGHBOURS) {
                val next = tile.offset(dx, dy)
                if (map.isBlocked(next) || reachable[next.y * map.width + next.x]) continue
                reachable[next.y * map.width + next.x] = true
                queue.addLast(next)
            }
        }
        return reachable
    }

    private companion object {
        /** Haut, droite, bas, gauche : l'ordre départage les tuiles à égale distance. */
        val NEIGHBOURS = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
    }
}

/** Côté de l'avenue où part une rue. */
enum class StreetSide { EAST, WEST }

/**
 * La rue d'un projet. Elle part de la place (ou de l'avenue) vers [side] ; ses terrains sont au nord
 * de la route, numérotés depuis la place. [sign] : le panneau du début de rue, où l'interface écrit le projet.
 */
data class Street(
    val index: Int,
    val project: String,
    val side: StreetSide,
    val road: TileRect,
    val sign: TilePos,
    val plots: List<Plot>,
)

/**
 * Un terrain : [area] couvre la maison et son jardin, [entry] est la tuile devant, au bord de la route.
 * [sessionId] est `null` pour un terrain libre ; [reserved] : le terrain est retenu pour un chantier
 * dont la session n'est pas encore placée.
 */
data class Plot(
    val street: Int,
    val index: Int,
    val area: TileRect,
    val entry: TilePos,
    val sessionId: SessionId?,
    val reserved: Boolean = false,
)

/**
 * Le chantier d'une nouvelle session, sur le terrain [plot] de la rue [street] (celle de [project]).
 *
 * - [stage] : `null` pendant la fiche, quand le terrain n'est que visé ; sinon l'étape à dessiner.
 * - [footprint] : la place du chantier, au milieu du terrain ; [entry] : la tuile devant, au bord de la route.
 * - [session] : la session, dès que le pont l'a créée ; sa maison est alors dans [Village.houses].
 * - [opensStreet] : un nouveau projet, dont la rue s'ouvre avec ce chantier.
 * - [placed] : l'objet dessiné (atlas `decor`, `chantier.*`), `null` sans travaux ou une fois la maison finie.
 */
data class Site(
    val project: String,
    val street: Int,
    val plot: Int,
    val area: TileRect,
    val footprint: TileRect,
    val entry: TilePos,
    val stage: ConstructionStage?,
    val session: SessionId?,
    val opensStreet: Boolean,
    val failed: Boolean,
    val placed: Placed?,
)

/**
 * La maison d'une session.
 *
 * - [footprint] : l'empreinte bloquée ; [placed] l'ancre en bas à gauche, avec son toit.
 * - [door] : la tuile de la porte ; [entry] : la tuile juste en dessous, libre, où le coursier se tient.
 * - [labelAnchor] : la tuile au-dessus du toit où pointe le panneau de la maison.
 * - [chimney], [windows] : en pixels natifs depuis le coin haut gauche de l'empreinte (voir [HouseModel]).
 */
data class House(
    val session: Session,
    val inhabitant: InhabitantKind,
    val street: Int,
    val plot: Int,
    val modelId: String,
    val roof: RoofColors,
    val footprint: TileRect,
    val door: TilePos,
    val entry: TilePos,
    val labelAnchor: TilePos,
    val chimney: PixelPoint?,
    val windows: List<PixelRect>,
) {
    val placed: Placed get() = Placed(modelId, footprint.bottomLeft, roof)
}

/** Un objet de décor posé : [footprint] au sol ; [blocking] s'il arrête le coursier (les fleurs, non). */
data class Decoration(val placed: Placed, val footprint: TileRect, val blocking: Boolean)
