package app.regie.game.ui.render

import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos

/** Une image ARGB au pixel du dessin, ligne par ligne. */
class PixelImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "Image de $width × $height : ${pixels.size} pixels" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

/**
 * Le sol dessiné par le code (ADR 21) : une texture déterministe par matière, puis les bords
 * calculés par [GroundEdges]. Le résultat ne dépend que de la carte et de la palette.
 */
object GroundPainter {
    const val TILE = 16

    fun paint(map: TileMap, palette: GroundPalette): PixelImage {
        val width = map.width * TILE
        val pixels = IntArray(width * map.height * TILE)
        for (ty in 0 until map.height) {
            for (tx in 0 until map.width) {
                paintTile(map, TilePos(tx, ty), palette, pixels, width)
            }
        }
        return PixelImage(width, map.height * TILE, pixels)
    }

    private fun paintTile(map: TileMap, p: TilePos, palette: GroundPalette, pixels: IntArray, width: Int) {
        val ground = map.ground(p)
        val mask = GroundEdges.mask(map, p)
        val eastWest = ground == Ground.BRIDGE && GroundEdges.bridgeRunsEastWest(map, p)
        for (ly in 0 until TILE) {
            for (lx in 0 until TILE) {
                val gx = p.x * TILE + lx
                val gy = p.y * TILE + ly
                val color = edge(map, p, ground, mask, lx, ly, palette)
                    ?: texture(ground, palette.tones(ground), gx, gy, eastWest)
                pixels[gy * width + gx] = color
            }
        }
    }

    /** Texture de fond d'une matière, en coordonnées globales pour se raccorder d'une tuile à l'autre. */
    internal fun texture(ground: Ground, tones: Tones, gx: Int, gy: Int, bridgeEastWest: Boolean): Int = when (ground) {
        Ground.GRASS -> when {
            hash(gx, gy + 1) % 71 == 0 -> tones.light // haut d'une touffe
            hash(gx, gy) % 71 == 0 -> tones.dark
            hash(gx, gy) % 37 == 1 -> tones.light
            hash(gx, gy) % 43 == 2 -> tones.dark
            else -> tones.base
        }
        Ground.PATH -> when {
            hash(gx, gy) % 53 == 0 -> tones.dark
            hash(gx, gy) % 59 == 1 -> tones.light
            else -> tones.base
        }
        Ground.PLAZA -> {
            // Pavés de 8 × 8 en quinconce : joint sombre en bas à droite, lumière en haut à gauche.
            val sx = (gx + if ((gy / 8) % 2 == 1) 4 else 0) % 8
            val sy = gy % 8
            when {
                sx == 7 || sy == 7 -> tones.dark
                sx == 0 || sy == 0 -> tones.light
                else -> tones.base
            }
        }
        Ground.WATER -> when {
            gy % 5 == 2 && hash(gx / 4, gy) % 4 == 0 -> tones.light
            hash(gx, gy) % 61 == 0 -> tones.dark
            else -> tones.base
        }
        Ground.BRIDGE -> {
            // Planches perpendiculaires au passage.
            val along = if (bridgeEastWest) gx else gy
            when (along % 4) {
                3 -> tones.dark
                0 -> tones.light
                else -> tones.base
            }
        }
    }

    private fun band(ground: Ground): Int = when (ground) {
        Ground.GRASS -> 0
        Ground.PATH, Ground.PLAZA -> 1
        Ground.WATER, Ground.BRIDGE -> 2
    }

    private fun edgeColor(ground: Ground, neighbour: Ground, depth: Int, palette: GroundPalette): Int = when (ground) {
        Ground.WATER -> if (depth == 0) palette.outline else palette.water.light
        Ground.BRIDGE -> if (depth == 0) palette.bridge.dark else palette.bridge.light
        else -> palette.tones(neighbour).dark
    }

    /** La couleur de bord du pixel ([lx], [ly]) de la tuile [p], ou `null` s'il est hors des bords. */
    private fun edge(map: TileMap, p: TilePos, ground: Ground, mask: Int, lx: Int, ly: Int, palette: GroundPalette): Int? {
        val band = band(ground)
        if (band == 0 || mask == 0) return null
        val last = TILE - 1
        fun at(dx: Int, dy: Int) = map.ground(p.offset(dx, dy))
        // Côtés : (bit, profondeur du pixel depuis ce côté, décalage de la voisine).
        val sides = listOf(
            Triple(GroundEdges.N, ly, 0 to -1),
            Triple(GroundEdges.E, last - lx, 1 to 0),
            Triple(GroundEdges.S, last - ly, 0 to 1),
            Triple(GroundEdges.W, lx, -1 to 0),
        ).filter { (bit, depth, _) -> (mask and bit) != 0 && depth < band }
        if (sides.size >= 2 && sides.all { it.second == 0 }) {
            // Coin extérieur : arrondi par la couleur de la voisine.
            val (_, _, d) = sides.first()
            return palette.tones(at(d.first, d.second)).base
        }
        sides.minByOrNull { it.second }?.let { (_, depth, d) ->
            return edgeColor(ground, at(d.first, d.second), depth, palette)
        }
        val corners = listOf(
            Triple(GroundEdges.NE, maxOf(ly, last - lx), 1 to -1),
            Triple(GroundEdges.SE, maxOf(last - ly, last - lx), 1 to 1),
            Triple(GroundEdges.SW, maxOf(last - ly, lx), -1 to 1),
            Triple(GroundEdges.NW, maxOf(ly, lx), -1 to -1),
        )
        corners.firstOrNull { (bit, depth, _) -> (mask and bit) != 0 && depth < band }?.let { (_, depth, d) ->
            return edgeColor(ground, at(d.first, d.second), depth, palette)
        }
        return null
    }

    /** Hachage entier stable, sans aléa : la même carte donne toujours le même sol. */
    internal fun hash(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }
}
