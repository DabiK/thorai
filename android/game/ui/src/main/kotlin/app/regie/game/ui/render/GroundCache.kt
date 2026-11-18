package app.regie.game.ui.render

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * L'empreinte du contenu d'un sol : ses dimensions et la matière de chaque tuile. Deux cartes
 * différentes au même sol ont la même clé (le générateur du hameau en crée une à chaque vue
 * d'ensemble) : le sol n'est alors pas repeint. C'est aussi une copie, lisible hors du fil principal.
 */
class GroundKey private constructor(val width: Int, val height: Int, private val cells: ByteArray) {
    private val hash = 31 * (31 * width + height) + cells.contentHashCode()

    fun ground(x: Int, y: Int): Ground = GROUNDS[cells[y * width + x].toInt()]

    /** Une carte de même sol, sans collisions : ce que le peintre lit. */
    fun toMap(): TileMap = TileMap(width, height).also { map ->
        for (y in 0 until height) for (x in 0 until width) map.setGround(TilePos(x, y), ground(x, y))
    }

    override fun equals(other: Any?): Boolean =
        other is GroundKey && other.width == width && other.height == height && other.cells.contentEquals(cells)

    override fun hashCode(): Int = hash

    companion object {
        private val GROUNDS = Ground.entries

        fun of(map: TileMap): GroundKey {
            val cells = ByteArray(map.width * map.height)
            for (y in 0 until map.height) for (x in 0 until map.width) {
                cells[y * map.width + x] = map.ground(TilePos(x, y)).ordinal.toByte()
            }
            return GroundKey(map.width, map.height, cells)
        }
    }
}

/** Un sol peint, prêt à dessiner. */
class GroundImage(val key: GroundKey, val palette: GroundPalette, val image: ImageBitmap) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}

/**
 * Les sols peints, partagés par les deux écrans : les derniers [CAPACITY] couples (sol, palette).
 * Le dessin se fait hors du fil principal ([paint]) ; [prepare] le fait tout de suite (tests, préchargement).
 */
object GroundCache {
    private const val CAPACITY = 4
    private val entries = ArrayDeque<GroundImage>()

    /** Le sol déjà peint pour ce couple, sinon `null`. */
    fun peek(key: GroundKey, palette: GroundPalette): GroundImage? = synchronized(entries) {
        entries.firstOrNull { it.key == key && it.palette == palette }
    }

    /** Peint le sol sur un fil de calcul, ou le reprend du cache. */
    suspend fun paint(key: GroundKey, palette: GroundPalette): GroundImage =
        peek(key, palette) ?: withContext(Dispatchers.Default) { prepare(key, palette) }

    /** Peint le sol sur le fil courant, ou le reprend du cache. */
    fun prepare(key: GroundKey, palette: GroundPalette): GroundImage {
        peek(key, palette)?.let { return it }
        val pixels = GroundPainter.paint(key.toMap(), palette)
        val bitmap = Bitmap.createBitmap(pixels.pixels, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
        val painted = GroundImage(key, palette, bitmap.asImageBitmap())
        synchronized(entries) {
            entries.removeAll { it.key == key && it.palette == palette }
            entries.addFirst(painted)
            while (entries.size > CAPACITY) entries.removeLast()
        }
        return painted
    }

    /** Pour les tests : repartir d'un cache vide. */
    internal fun clear() = synchronized(entries) { entries.clear() }
}
