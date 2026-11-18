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
 * Le sol dessiné par le code (ADR 21), d'après le hameau de l'artefact (`docs/rebrand/assets/aube/hameau.png`) :
 * - l'herbe en trois tons, par plaques douces tramées, avec des touffes, des brins clairs et de petites fleurs ;
 * - le chemin crème, caillouteux, bordé d'un liseré, l'herbe débordant un peu sur ses bords arrondis ;
 * - la place en pavés arrondis clairs, aux joints gris ;
 * - l'eau turquoise, ses remous et ses reflets, bordée de pierres ;
 * - le pont en planches, avec ses garde-corps au-dessus de l'eau.
 * Tout est déterministe (hachage des coordonnées) et sans allocation par pixel.
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
        // Les masques se calculent une fois par tuile ; la boucle des pixels n'alloue rien.
        val grassMask = GroundEdges.maskWhere(map, p) { it == Ground.GRASS }
        val pathMask = if (ground == Ground.PLAZA) GroundEdges.maskWhere(map, p) { it == Ground.PATH } else 0
        val landMask = if (ground == Ground.WATER) GroundEdges.mask(map, p) else 0
        val waterMask = if (ground == Ground.BRIDGE) GroundEdges.maskWhere(map, p) { it == Ground.WATER } else 0
        val eastWest = ground == Ground.BRIDGE && GroundEdges.bridgeRunsEastWest(map, p)
        for (ly in 0 until TILE) {
            for (lx in 0 until TILE) {
                val gx = p.x * TILE + lx
                val gy = p.y * TILE + ly
                pixels[gy * width + gx] = when (ground) {
                    Ground.GRASS -> grass(gx, gy, palette)
                    Ground.PATH -> paved(GroundEdges.distance(grassMask, lx, ly, TILE, CORNER), gx, gy, palette) { path(gx, gy, palette) }
                    Ground.PLAZA -> {
                        val nearPath = GroundEdges.distance(pathMask, lx, ly, TILE, 0f)
                        if (nearPath < 1f) {
                            palette.path.dark
                        } else {
                            paved(GroundEdges.distance(grassMask, lx, ly, TILE, CORNER), gx, gy, palette) { cobble(gx, gy, palette) }
                        }
                    }
                    Ground.WATER -> water(GroundEdges.distance(landMask, lx, ly, TILE, CORNER), gx, gy, palette)
                    Ground.BRIDGE -> bridge(GroundEdges.distance(waterMask, lx, ly, TILE, 0f), gx, gy, eastWest, palette)
                }
            }
        }
    }

    /** Rien à cet endroit (une couleur transparente, jamais employée par le sol). */
    private const val NONE = 0
    private const val TUFT_BASE = 1
    private const val TUFT_BLADE = 2

    /** Rayon des coins extérieurs, en pixels. */
    private const val CORNER = 6f

    /** Chemin ou place : l'herbe déborde de 0 à 2 pixels, puis un liseré, puis la matière. */
    private inline fun paved(distance: Float, gx: Int, gy: Int, palette: GroundPalette, inside: () -> Int): Int {
        if (distance >= 4f) return inside()
        val overhang = 0.6f + 1.8f * noise(gx, gy, 3, 11)
        return when {
            distance < overhang -> if (distance > overhang - 1f && hash(gx, gy, 5) % 3 == 0) palette.grassDeep else grass(gx, gy, palette)
            distance < overhang + 1f -> palette.path.dark
            else -> inside()
        }
    }

    /** L'herbe : plaques douces tramées, touffes, brins clairs et petites fleurs. */
    internal fun grass(gx: Int, gy: Int, palette: GroundPalette): Int {
        val bloom = flower(gx, gy, palette)
        if (bloom != NONE) return bloom
        val n = 0.65f * noise(gx, gy, 22, 1) + 0.35f * noise(gx, gy, 8, 2)
        val dither = (BAYER[(gy and 3) * 4 + (gx and 3)] - 7.5f) / 16f * 0.14f
        val patch = n + dither
        val dark = patch > 0.60f
        when (tuft(gx, gy)) {
            TUFT_BASE -> return palette.grassDeep
            TUFT_BLADE -> return if (dark) palette.grassDeep else palette.grass.dark
        }
        return when {
            patch < 0.22f && hash(gx, gy, 3) % 5 == 0 -> palette.grass.light
            hash(gx, gy, 4) % 89 == 0 -> palette.grass.light
            dark -> palette.grass.dark
            else -> palette.grass.base
        }
    }

    /**
     * Une touffe par cellule de 7 × 6, une fois sur trois : trois brins sur une base.
     * Renvoie [TUFT_BASE], [TUFT_BLADE] ou [NONE] (des entiers : pas d'objet par pixel).
     */
    private fun tuft(gx: Int, gy: Int): Int {
        val cx = Math.floorDiv(gx, 7)
        val cy = Math.floorDiv(gy, 6)
        val h = hash(cx, cy, 7)
        if (h % 3 != 0) return NONE
        val ax = cx * 7 + 2 + (h ushr 4) % 3
        val ay = cy * 6 + 3 + (h ushr 8) % 2
        val dx = gx - ax
        val dy = gy - ay
        return when {
            dy == 0 && dx in -1..1 -> TUFT_BASE
            dy == -1 && (dx == -1 || dx == 1) -> TUFT_BLADE
            dy == -2 && dx == 0 -> TUFT_BLADE
            else -> NONE
        }
    }

    /** Une petite fleur par cellule de 13 × 11, une fois sur huit : quatre pétales et un cœur ; [NONE] sinon. */
    private fun flower(gx: Int, gy: Int, palette: GroundPalette): Int {
        val cx = Math.floorDiv(gx, 13)
        val cy = Math.floorDiv(gy, 11)
        val h = hash(cx, cy, 13)
        if (h % 8 != 0) return NONE
        val fx = cx * 13 + 3 + (h ushr 4) % 7
        val fy = cy * 11 + 3 + (h ushr 8) % 5
        val petal = palette.flowers[(h ushr 12) % (GroundPalette.FLOWER_ROLES - 1)]
        val dx = gx - fx
        val dy = gy - fy
        return when {
            dx == 0 && dy == 0 -> palette.flowers[GroundPalette.FLOWER_ROLES - 1]
            (dx == 0 && (dy == -1 || dy == 1)) || (dy == 0 && (dx == -1 || dx == 1)) -> petal
            dx == 0 && dy == 2 -> palette.grassDeep
            else -> NONE
        }
    }

    /** Le chemin : crème, quelques plaques plus claires et des cailloux éclairés en haut à gauche. */
    private fun path(gx: Int, gy: Int, palette: GroundPalette): Int {
        val cx = Math.floorDiv(gx, 6)
        val cy = Math.floorDiv(gy, 5)
        val h = hash(cx, cy, 17)
        if (h % 4 == 0) {
            val px = cx * 6 + 1 + (h ushr 4) % 3
            val py = cy * 5 + 1 + (h ushr 8) % 3
            if (gy == py && (gx == px || gx == px + 1)) return palette.path.dark
            if (gy == py - 1 && gx == px) return palette.path.light
        }
        val n = noise(gx, gy, 14, 19) + (BAYER[(gy and 3) * 4 + (gx and 3)] - 7.5f) / 16f * 0.12f
        return if (n > 0.72f) palette.path.light else palette.path.base
    }

    /** La place : pavés arrondis de 6 × 5 en quinconce, joints gris, lumière en haut à gauche. */
    private fun cobble(gx: Int, gy: Int, palette: GroundPalette): Int {
        val row = Math.floorDiv(gy, 5)
        val shift = (row % 2) * 3 + (hash(row, 0, 23) % 2)
        val col = Math.floorDiv(gx + shift, 6)
        val sx = Math.floorMod(gx + shift, 6)
        val sy = Math.floorMod(gy, 5)
        val joint = sx == 5 || sy == 4 || (sx == 0 || sx == 4) && (sy == 0 || sy == 3)
        if (joint) return palette.stone.base
        val pale = hash(col, row, 29) % 4 == 0
        return when {
            sy == 0 || sx == 0 -> palette.plaza.light
            sx == 4 || sy == 3 -> palette.plaza.dark
            pale -> palette.plaza.light
            else -> palette.plaza.base
        }
    }

    /** L'eau : liseré d'herbe, berge de pierres, écume, puis remous et reflets. */
    private fun water(distance: Float, gx: Int, gy: Int, palette: GroundPalette): Int {
        if (distance < 6f) {
            val lip = 0.4f + 1.2f * noise(gx, gy, 4, 31)
            if (distance < lip) return grass(gx, gy, palette)
            if (distance < lip + 3f) return bankStone(gx, gy, palette)
            if (distance < lip + 4f) return palette.water.light
            if (distance < lip + 5f) return palette.waterMid
        }
        val cx = Math.floorDiv(gx, 11)
        val cy = Math.floorDiv(gy, 5)
        val h = hash(cx, cy, 37)
        if (h % 2 == 0) {
            val rx = cx * 11 + (h ushr 4) % 5
            val ry = cy * 5 + (h ushr 8) % 4
            val length = 2 + (h ushr 12) % 3
            if (gy == ry && gx >= rx && gx < rx + length) return palette.water.light
            if (gy == ry + 1 && gx > rx && gx < rx + length + 1) return palette.waterMid
        }
        val n = noise(gx, gy, 18, 41) + (BAYER[(gy and 3) * 4 + (gx and 3)] - 7.5f) / 16f * 0.16f
        return if (n > 0.66f) palette.waterMid else palette.water.base
    }

    /** Pierres de berge, de 5 × 4 en quinconce. */
    private fun bankStone(gx: Int, gy: Int, palette: GroundPalette): Int {
        val row = Math.floorDiv(gy, 4)
        val shift = (row % 2) * 2
        val sx = Math.floorMod(gx + shift, 5)
        val sy = Math.floorMod(gy, 4)
        return when {
            sx == 4 || sy == 3 -> palette.stone.dark
            sy == 0 && sx < 3 -> palette.stone.light
            else -> palette.stone.base
        }
    }

    /** Le pont : planches de 4 pixels perpendiculaires au passage, garde-corps contre l'eau. */
    private fun bridge(distance: Float, gx: Int, gy: Int, eastWest: Boolean, palette: GroundPalette): Int {
        if (distance < 1f) return palette.bridge.dark
        if (distance < 2f) return palette.path.light
        if (distance < 3f) return palette.bridge.base
        val along = if (eastWest) gx else gy
        val across = if (eastWest) gy else gx
        val plank = Math.floorDiv(along, 4)
        return when (Math.floorMod(along, 4)) {
            3 -> palette.bridge.base
            0 -> palette.path.light
            else -> if (Math.floorMod(across + hash(plank, 0, 43) % 9, 9) == 0) palette.bridge.base else palette.bridge.light
        }
    }

    /** Matrice de Bayer 4 × 4 : la trame des transitions douces. */
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Bruit de valeur lissé, entre 0 et 1, sur une grille de [cell] pixels. */
    internal fun noise(gx: Int, gy: Int, cell: Int, salt: Int): Float {
        val cx = Math.floorDiv(gx, cell)
        val cy = Math.floorDiv(gy, cell)
        val fx = smooth((gx - cx * cell + 0.5f) / cell)
        val fy = smooth((gy - cy * cell + 0.5f) / cell)
        val a = unit(cx, cy, salt)
        val b = unit(cx + 1, cy, salt)
        val c = unit(cx, cy + 1, salt)
        val d = unit(cx + 1, cy + 1, salt)
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return top + (bottom - top) * fy
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)

    private fun unit(x: Int, y: Int, salt: Int): Float = (hash(x, y, salt) and 0xFFFF) / 65535f

    /** Hachage entier stable, sans aléa : la même carte donne toujours le même sol. */
    internal fun hash(x: Int, y: Int, salt: Int = 0): Int {
        var h = x * 374761393 + y * 668265263 + salt * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0x7FFFFFFF
    }
}
