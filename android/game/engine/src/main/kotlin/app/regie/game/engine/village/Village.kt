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
 * - [origin] : le centre de la place. Quand le hameau s'agrandit vers l'ouest, la carte s'élargit et
 *   tout glisse d'autant ; [relocate] reporte une position d'un hameau au suivant.
 * - [spawn] : la tuile d'arrivée du coursier, devant la fontaine.
 * - [houses] : rangées par rue puis par terrain.
 * - [decorations] : tout objet posé hors maisons, avec son empreinte ; aussi présents dans `world.objects`.
 */
data class Village(
    val world: World,
    val origin: TilePos,
    val spawn: TilePos,
    val plaza: TileRect,
    val streets: List<Street>,
    val houses: List<House>,
    val decorations: List<Decoration>,
) {
    /** Les terrains sans maison, rue par rue. */
    val freePlots: List<Plot> get() = streets.flatMap { street -> street.plots.filter { it.sessionId == null } }

    fun houseOf(id: SessionId): House? = houses.firstOrNull { it.session.id == id }

    /** La maison dont [tile] est la tuile d'entrée : le coursier y entre avec A. */
    fun houseAtEntry(tile: TilePos): House? = houses.firstOrNull { it.entry == tile }

    /** Où se trouve, dans ce hameau, la tuile [pos] de [previous]. */
    fun relocate(pos: TilePos, previous: Village): TilePos =
        pos.offset(origin.x - previous.origin.x, origin.y - previous.origin.y)
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
 * [sessionId] est `null` pour un terrain libre.
 */
data class Plot(
    val street: Int,
    val index: Int,
    val area: TileRect,
    val entry: TilePos,
    val sessionId: SessionId?,
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
