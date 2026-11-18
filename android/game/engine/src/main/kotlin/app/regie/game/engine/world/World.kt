package app.regie.game.engine.world

/** Position d'une tuile de 16 px sur la carte ; x vers la droite, y vers le bas. */
data class TilePos(val x: Int, val y: Int) {
    fun offset(dx: Int, dy: Int) = TilePos(x + dx, y + dy)
}

/** Sol dessiné par le code (plan de la vue jeu) : le rendu calcule les bords selon les voisins. */
enum class Ground { GRASS, PATH, PLAZA, WATER, BRIDGE }

/** Couleurs d'un toit en ARGB ; elles remplacent les trois couleurs clés de l'atlas des maisons. */
data class RoofColors(val light: Int, val base: Int, val shadow: Int)

/**
 * Un objet d'atlas posé sur la carte : maison, arbre, clôture, chantier…
 * [anchor] est la tuile en bas à gauche de son empreinte ; [objectId] est une clé `objects` ou `frames` d'un atlas.
 */
data class Placed(val objectId: String, val anchor: TilePos, val roof: RoofColors? = null)

/** Sol et collisions d'une carte rectangulaire. */
class TileMap(val width: Int, val height: Int) {
    private val ground = Array(width * height) { Ground.GRASS }
    private val blocked = BooleanArray(width * height)

    fun inside(p: TilePos): Boolean = p.x in 0 until width && p.y in 0 until height

    fun ground(p: TilePos): Ground = ground[index(p)]

    fun setGround(p: TilePos, value: Ground) {
        ground[index(p)] = value
    }

    /** Hors de la carte, tout est bloqué. */
    fun isBlocked(p: TilePos): Boolean = !inside(p) || blocked[index(p)]

    fun setBlocked(p: TilePos, value: Boolean = true) {
        blocked[index(p)] = value
    }

    private fun index(p: TilePos): Int {
        require(inside(p)) { "Tuile hors de la carte : $p" }
        return p.y * width + p.x
    }
}

/** Ce que le rendu dessine : un sol et des objets posés. */
data class World(val map: TileMap, val objects: List<Placed>)
