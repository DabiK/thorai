package app.regie.game.ui.render

import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import kotlin.math.sqrt

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
        val bridgeMask = if (ground == Ground.WATER) GroundEdges.maskWhere(map, p) { it == Ground.BRIDGE } else 0
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
                    Ground.WATER -> {
                        // L'ombre du pont sur l'eau, de part et d'autre.
                        if (GroundEdges.distance(bridgeMask and (GroundEdges.E or GroundEdges.W), lx, ly, TILE, 0f) < 2f) {
                            palette.water.dark
                        } else {
                            water(GroundEdges.distance(landMask, lx, ly, TILE, CORNER), gx, gy, palette)
                        }
                    }
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
        // Plaques douces : un cœur plein, puis une trame à 50 % et à 25 % vers le fond.
        val n = 0.7f * noise(gx, gy, 24, 1) + 0.3f * noise(gx, gy, 9, 2)
        val bayer = BAYER[(gy and 3) * 4 + (gx and 3)]
        val dark = n > 0.74f || (n > 0.68f && bayer < 8) || (n > 0.63f && bayer < 3)
        val patch = n
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

    /** Le chemin : crème, de rares reflets clairs et des cailloux roses ou gris, éclairés en haut à gauche. */
    private fun path(gx: Int, gy: Int, palette: GroundPalette): Int {
        val cx = Math.floorDiv(gx, 7)
        val cy = Math.floorDiv(gy, 6)
        val h = hash(cx, cy, 17)
        if (h % 3 == 0) {
            val px = cx * 7 + 1 + (h ushr 4) % 4
            val py = cy * 6 + 1 + (h ushr 8) % 4
            val pebble = if ((h ushr 12) % 3 == 0) palette.stone.base else palette.plaza.dark
            if (gy == py && (gx == px || ((h ushr 14) % 2 == 0 && gx == px + 1))) return pebble
            if (gy == py - 1 && gx == px) return palette.path.light
        }
        val n = noise(gx, gy, 9, 19)
        return if (n > 0.9f && BAYER[(gy and 3) * 4 + (gx and 3)] < 8) palette.path.light else palette.path.base
    }

    /**
     * La place : des pavés arrondis et irréguliers (cellules de Voronoï sur une grille de 8 × 7),
     * joints gris, chaque pavé éclairé en haut à gauche et ombré en bas à droite.
     */
    private fun cobble(gx: Int, gy: Int, palette: GroundPalette): Int {
        val cell = stoneCell(gx, gy, 8, 7, 23, edge = 2.6f)
        return when (cell and 3) {
            STONE_JOINT -> palette.stone.base
            STONE_LIT -> palette.plaza.light
            STONE_SHADED -> palette.plaza.dark
            else -> if ((cell ushr 2) % 5 == 0) palette.plaza.light else palette.plaza.base
        }
    }

    /** Une pierre de berge : petits moellons gris arrondis, joints sombres. */
    private fun bankStone(gx: Int, gy: Int, palette: GroundPalette): Int = when (stoneCell(gx, gy, 6, 4, 47, edge = 1.8f) and 3) {
        STONE_JOINT -> palette.stone.dark
        STONE_LIT -> palette.stone.light
        else -> palette.stone.base
    }

    private const val STONE_JOINT = 0
    private const val STONE_LIT = 1
    private const val STONE_SHADED = 2
    private const val STONE_FACE = 3

    /**
     * Pavage de Voronoï : chaque cellule de [w] × [h] a un centre tiré au hasard ; le pixel appartient
     * au centre le plus proche. Renvoie la classe du pixel dans les 2 bits bas (joint, bord éclairé,
     * bord ombré, face), et le hachage de sa pierre au-dessus. Sans allocation.
     */
    private fun stoneCell(gx: Int, gy: Int, w: Int, h: Int, salt: Int, edge: Float): Int {
        val cx = Math.floorDiv(gx, w)
        val cy = Math.floorDiv(gy, h)
        var best = Float.MAX_VALUE
        var second = Float.MAX_VALUE
        var bestDx = 0f
        var bestDy = 0f
        var bestCell = 0
        for (oy in -1..1) for (ox in -1..1) {
            val nx = cx + ox
            val ny = cy + oy
            val cellHash = hash(nx, ny, salt)
            val px = nx * w + 1.5f + (cellHash and 0xFF) / 255f * (w - 3)
            val py = ny * h + 1.5f + ((cellHash ushr 8) and 0xFF) / 255f * (h - 3)
            val dx = gx + 0.5f - px
            val dy = gy + 0.5f - py
            val d = dx * dx + dy * dy
            if (d < best) {
                second = best
                best = d
                bestDx = dx
                bestDy = dy
                bestCell = cellHash
            } else if (d < second) {
                second = d
            }
        }
        val gap = sqrt(second) - sqrt(best)
        val kind = when {
            gap < 1.1f -> STONE_JOINT
            gap < edge && bestDx + bestDy < -1f -> STONE_LIT
            gap < edge && bestDx + bestDy > 1f -> STONE_SHADED
            else -> STONE_FACE
        }
        return ((bestCell ushr 4) shl 2) or kind
    }

    /** L'eau : liseré d'herbe, berge de pierres, écume, puis remous et reflets. */
    private fun water(distance: Float, gx: Int, gy: Int, palette: GroundPalette): Int {
        if (distance < 8f) {
            val lip = 0.5f + 1.5f * noise(gx, gy, 4, 31)
            if (distance < lip) return grass(gx, gy, palette)
            if (distance < lip + 4f) return bankStone(gx, gy, palette)
            if (distance < lip + 5f) return palette.water.dark
            if (distance < lip + 6f) return palette.waterMid
        }
        if (distance > 9f) {
            val pad = lilyPad(gx, gy, palette)
            if (pad != NONE) return pad
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

    /**
     * Les nénuphars : une feuille ronde échancrée par cellule de 29 × 13, une fois sur quatre, cernée de
     * vert profond, parfois une fleur rose ; [NONE] ailleurs.
     */
    private fun lilyPad(gx: Int, gy: Int, palette: GroundPalette): Int {
        val cx = Math.floorDiv(gx, 29)
        val cy = Math.floorDiv(gy, 13)
        val h = hash(cx, cy, 53)
        if (h % 4 != 0) return NONE
        val px = cx * 29 + 6 + (h ushr 4) % 17
        val py = cy * 13 + 4 + (h ushr 9) % 5
        val dx = gx - px
        val dy = gy - py
        val d = dx * dx * 4 + dy * dy * 9
        return when {
            d > 40 -> NONE
            dx > 0 && dy == 0 -> palette.water.dark
            (h ushr 13) % 3 == 0 && dx == -1 && dy == -1 -> palette.flowers[1]
            d > 24 -> palette.outline
            dx < 0 && dy < 0 -> palette.grass.base
            else -> palette.grassDeep
        }
    }

    /**
     * Le pont : des planches de 5 pixels en travers du passage, joints sombres, bord éclairé en haut et
     * veines ; contre l'eau, un garde-corps cerné de sombre, avec un poteau tous les 10 pixels.
     */
    private fun bridge(distance: Float, gx: Int, gy: Int, eastWest: Boolean, palette: GroundPalette): Int {
        val along = if (eastWest) gx else gy
        val across = if (eastWest) gy else gx
        if (distance < 1f) return palette.outline
        if (distance < 4f) {
            if (Math.floorMod(along, 10) < 2) return if (distance < 2f) palette.bridge.dark else palette.bridge.base
            return when {
                distance < 2f -> palette.bridge.base
                distance < 3f -> palette.bridge.light
                else -> palette.bridge.dark
            }
        }
        val plank = Math.floorDiv(along, 5)
        val h = hash(plank, 0, 43)
        return when (Math.floorMod(along, 5)) {
            0 -> palette.bridge.dark
            1 -> palette.bridge.light
            else -> {
                // Les veines du bois, et un clou au bout de chaque planche.
                if (distance < 6f && Math.floorMod(along, 5) == 2) return palette.bridge.dark
                if (Math.floorMod(across + h % 13, 13) < 3 && (h ushr 5) % 2 == 0) palette.bridge.base else palette.bridge.light
            }
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
