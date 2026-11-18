package app.regie.game.ui.render

import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.village.Village
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TileRect
import app.regie.game.ui.atlas.Footprint
import kotlin.math.hypot

/** Ce qu'un objet sans atlas représente : le rendu en dessine une forme de repli lisible. */
enum class FallbackKind { HOUSE, TREE, BUSH, FLOWERS, ROCK, FENCE, FOUNTAIN, BENCH, LANTERN, SIGN, BLOCK }

/**
 * La forme de repli d'un objet : sa nature, son empreinte au sol (celle que le moteur bloque) et,
 * pour une maison, la colonne de sa porte dans l'empreinte.
 */
data class Fallback(val kind: FallbackKind, val footprint: Footprint, val doorColumn: Int? = null)

/** Les formes de repli d'un hameau : chaque maison et chaque décor avec l'empreinte du moteur. */
fun Village.fallbacks(decor: DecorCatalog = DecorCatalog.DEFAULT): Map<Placed, Fallback> {
    fun kindOf(id: String) = when (id) {
        in decor.trees -> FallbackKind.TREE
        in decor.bushes -> FallbackKind.BUSH
        in decor.flowers -> FallbackKind.FLOWERS
        in decor.rocks -> FallbackKind.ROCK
        decor.fence -> FallbackKind.FENCE
        decor.fountain -> FallbackKind.FOUNTAIN
        decor.bench -> FallbackKind.BENCH
        decor.lantern -> FallbackKind.LANTERN
        decor.sign -> FallbackKind.SIGN
        else -> FallbackKind.BLOCK
    }
    fun TileRect.footprint() = Footprint(width, height)
    return houses.associate { it.placed to Fallback(FallbackKind.HOUSE, it.footprint.footprint(), it.door.x - it.footprint.left) } +
        decorations.associate { it.placed to Fallback(kindOf(it.placed.objectId), it.footprint.footprint()) }
}

/** Une image de repli et son ancrage, au milieu du bas de l'empreinte. */
class PlaceholderSprite(val image: PixelImage, val ax: Int, val ay: Int)

/**
 * Dessine les formes de repli, au pixel natif, dans la palette Aube : une maison (toit aux couleurs
 * de la session, murs, porte, fenêtres, cheminée), un arbre rond, un buisson, des fleurs, un rocher,
 * une clôture, la fontaine, un banc, une lanterne, un panneau. Déterministe, sans Android.
 */
object PlaceholderPainter {
    private const val TILE = GroundPainter.TILE
    private const val INK = 0xFF222F36.toInt()
    private const val WOOD_INK = 0xFF503835.toInt()
    private const val WALL = 0xFFF0DDD1.toInt()
    private const val WALL_SHADE = 0xFFD8AFA1.toInt()
    private const val TIMBER = 0xFFA16948.toInt()
    private const val DOOR = 0xFF9E5A37.toInt()
    private const val PLANK = 0xFFE5A669.toInt()
    private const val CREAM = 0xFFF1D8A6.toInt()
    private const val GLASS = 0xFF5A718C.toInt()
    private const val GOLD = 0xFFDDDC2C.toInt()
    private const val STONE = 0xFFA0A0A0.toInt()
    private const val STONE_DARK = 0xFF605751.toInt()
    private const val LEAF_LIGHT = 0xFFA0D944.toInt()
    private const val LEAF = 0xFF58A949.toInt()
    private const val LEAF_DARK = 0xFF22944C.toInt()
    private const val LEAF_INK = 0xFF1B5F53.toInt()
    private const val WATER = 0xFF27D1CB.toInt()
    private const val WATER_LIGHT = 0xFF53E6DE.toInt()
    private const val PINK = 0xFFD8AFA1.toInt()
    private const val RED = 0xFFDF724A.toInt()

    /** Le toit d'une maison sans couleurs de session. */
    val DEFAULT_ROOF = RoofColors(0xFFDF724A.toInt(), 0xFFDB6633.toInt(), 0xFF9E5A37.toInt())

    fun paint(fallback: Fallback, roof: RoofColors?): PlaceholderSprite = when (fallback.kind) {
        FallbackKind.HOUSE -> house(fallback.footprint, fallback.doorColumn ?: (fallback.footprint.w / 2), roof ?: DEFAULT_ROOF)
        FallbackKind.TREE -> tree()
        FallbackKind.BUSH -> bush()
        FallbackKind.FLOWERS -> flowers()
        FallbackKind.ROCK -> rock()
        FallbackKind.FENCE -> fence()
        FallbackKind.FOUNTAIN -> fountain(fallback.footprint)
        FallbackKind.BENCH -> bench()
        FallbackKind.LANTERN -> lantern()
        FallbackKind.SIGN -> sign()
        FallbackKind.BLOCK -> block(fallback.footprint)
    }

    /** Une toile de pixels, transparente au départ. */
    private class Canvas(val width: Int, val height: Int) {
        val pixels = IntArray(width * height)

        operator fun set(x: Int, y: Int, color: Int) {
            if (x in 0 until width && y in 0 until height) pixels[y * width + x] = color
        }

        operator fun get(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0

        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            for (yy in y until y + h) for (xx in x until x + w) this[xx, yy] = color
        }

        /** Entoure d'un contour tout pixel plein qui touche le vide. */
        fun outline(color: Int) {
            val filled = pixels.copyOf()
            for (y in 0 until height) for (x in 0 until width) {
                if (filled[y * width + x] == 0) continue
                val edge = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (nx, ny) ->
                    nx !in 0 until width || ny !in 0 until height || filled[ny * width + nx] == 0
                }
                if (edge) pixels[y * width + x] = color
            }
        }

        fun sprite(ax: Int, ay: Int) = PlaceholderSprite(PixelImage(width, height, pixels), ax, ay)
    }

    /** Un disque ombré, lumière en haut à gauche. */
    private fun Canvas.ball(cx: Float, cy: Float, r: Float, light: Int, base: Int, dark: Int, salt: Int) {
        for (y in 0 until height) for (x in 0 until width) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            if (hypot(dx, dy) > r) continue
            val s = (dx + dy) / (2 * r)
            this[x, y] = when {
                s < -0.32f -> light
                s > 0.22f -> dark
                GroundPainter.hash(x, y, salt) % 7 == 0 -> light
                else -> base
            }
        }
    }

    private fun house(footprint: Footprint, doorColumn: Int, roof: RoofColors): PlaceholderSprite {
        val w = footprint.w * TILE
        val rise = 10
        val h = footprint.h * TILE + rise
        val c = Canvas(w, h)
        val wallHeight = (footprint.h * 7).coerceIn(18, 30)
        val wallTop = h - wallHeight
        // Murs : enduit clair, poteaux de bois, soubassement de pierre.
        c.rect(1, wallTop, w - 2, wallHeight, WALL)
        c.rect(w - 6, wallTop, 5, wallHeight, WALL_SHADE)
        for (x in 1 until w - 1 step TILE) c.rect(x, wallTop, 2, wallHeight, TIMBER)
        c.rect(w - 3, wallTop, 2, wallHeight, TIMBER)
        c.rect(1, h - 3, w - 2, 3, STONE)
        for (x in 1 until w - 1 step 5) c[x, h - 2] = STONE_DARK
        // Fenêtres, de part et d'autre de la porte.
        listOf(doorColumn - 2, doorColumn + 2, doorColumn - 4, doorColumn + 4).filter { it in 0 until footprint.w }.forEach { col ->
            val x = col * TILE + 4
            val y = wallTop + (wallHeight - 7) / 2 - 1
            c.rect(x - 1, y - 1, 10, 9, TIMBER)
            c.rect(x, y, 8, 7, GLASS)
            c.rect(x + 3, y, 2, 7, TIMBER)
            c.rect(x, y + 3, 8, 1, TIMBER)
            c[x, y] = CREAM
            c[x + 1, y] = CREAM
            c[x, y + 1] = CREAM
        }
        // La porte, arrondie en haut, avec sa poignée.
        val doorX = doorColumn * TILE + 4
        val doorTop = h - 16
        c.rect(doorX - 1, doorTop - 1, 10, 17, WOOD_INK)
        c.rect(doorX, doorTop, 8, 16, DOOR)
        c[doorX, doorTop] = WOOD_INK
        c[doorX + 7, doorTop] = WOOD_INK
        c.rect(doorX + 2, doorTop + 1, 1, 15, TIMBER)
        c.rect(doorX + 5, doorTop + 1, 1, 15, TIMBER)
        c[doorX + 6, doorTop + 8] = GOLD
        // Le toit : en croupe, tuiles en quinconce, faîte clair, avant-toit sombre.
        val roofBottom = wallTop + 3
        for (y in 0 until roofBottom) {
            val inset = (6 - y).coerceAtLeast(0)
            for (x in inset until w - inset) {
                val ry = y - 1
                c[x, y] = when {
                    y >= roofBottom - 2 -> roof.shadow
                    y < 4 -> roof.light
                    Math.floorMod(ry, 5) == 4 -> roof.shadow
                    Math.floorMod(x + (ry / 5 % 2) * 4, 8) == 0 -> roof.shadow
                    x < w / 3 && Math.floorMod(ry, 5) == 0 -> roof.light
                    else -> roof.base
                }
            }
        }
        // La cheminée.
        val chimneyX = w * 3 / 4
        c.rect(chimneyX, 0, 7, 10, STONE)
        c.rect(chimneyX + 5, 1, 2, 9, STONE_DARK)
        c.rect(chimneyX - 1, 0, 9, 2, STONE_DARK)
        c.outline(INK)
        // La porte garde son seuil visible : une marche claire sous le contour.
        c.rect(doorX, h - 1, 8, 1, STONE)
        return c.sprite(w / 2, h - 1)
    }

    private fun tree(): PlaceholderSprite {
        val c = Canvas(28, 34)
        c.rect(11, 22, 6, 12, TIMBER)
        c.rect(15, 22, 2, 12, DOOR)
        c.ball(8.5f, 17f, 8f, LEAF_LIGHT, LEAF, LEAF_DARK, 51)
        c.ball(19.5f, 17f, 8f, LEAF_LIGHT, LEAF, LEAF_DARK, 52)
        c.ball(14f, 11f, 11f, LEAF_LIGHT, LEAF, LEAF_DARK, 53)
        c.outline(LEAF_INK)
        return c.sprite(14, 33)
    }

    private fun bush(): PlaceholderSprite {
        val c = Canvas(18, 13)
        c.ball(6f, 8f, 5.5f, LEAF_LIGHT, LEAF, LEAF_DARK, 61)
        c.ball(12f, 8f, 5.5f, LEAF_LIGHT, LEAF, LEAF_DARK, 62)
        c.ball(9f, 5.5f, 5.5f, LEAF_LIGHT, LEAF, LEAF_DARK, 63)
        c.outline(LEAF_INK)
        return c.sprite(9, 12)
    }

    private fun flowers(): PlaceholderSprite {
        val c = Canvas(16, 12)
        c.ball(5f, 7.5f, 4.5f, LEAF, LEAF, LEAF_DARK, 71)
        c.ball(11f, 7.5f, 4.5f, LEAF, LEAF, LEAF_DARK, 72)
        c.outline(LEAF_INK)
        listOf(Triple(4, 5, WALL), Triple(8, 3, PINK), Triple(12, 6, RED), Triple(7, 8, WALL), Triple(11, 9, PINK)).forEach { (x, y, petal) ->
            c[x - 1, y] = petal
            c[x + 1, y] = petal
            c[x, y - 1] = petal
            c[x, y + 1] = petal
            c[x, y] = GOLD
        }
        return c.sprite(8, 11)
    }

    private fun rock(): PlaceholderSprite {
        val c = Canvas(14, 10)
        for (y in 0 until 10) for (x in 0 until 14) {
            val dx = (x + 0.5f - 7f) / 6.5f
            val dy = (y + 0.5f - 6f) / 4.5f
            if (dx * dx + dy * dy <= 1f) c[x, y] = if (dx + dy < -0.5f) WALL else if (dx + dy > 0.4f) STONE_DARK else STONE
        }
        c.outline(STONE_DARK)
        return c.sprite(7, 9)
    }

    private fun fence(): PlaceholderSprite {
        val c = Canvas(16, 14)
        c.rect(0, 4, 16, 2, PLANK)
        c.rect(0, 6, 16, 1, TIMBER)
        c.rect(0, 9, 16, 2, PLANK)
        c.rect(0, 11, 16, 1, TIMBER)
        c.rect(6, 1, 4, 13, TIMBER)
        c.rect(6, 1, 1, 13, PLANK)
        c.rect(6, 0, 4, 1, WOOD_INK)
        c.rect(6, 1, 4, 1, PLANK)
        for (x in 0 until 16) {
            if (c[x, 3] == 0) c[x, 3] = WOOD_INK
            if (c[x, 8] == 0) c[x, 8] = WOOD_INK
            c[x, 7] = if (x in 6..9) c[x, 7] else WOOD_INK
            c[x, 12] = if (x in 6..9) c[x, 12] else WOOD_INK
        }
        c.rect(5, 1, 1, 13, WOOD_INK)
        c.rect(10, 1, 1, 13, WOOD_INK)
        return c.sprite(8, 13)
    }

    private fun fountain(footprint: Footprint): PlaceholderSprite {
        val w = footprint.w * TILE
        val h = footprint.h * TILE + 14
        val c = Canvas(w, h)
        val cx = w / 2f
        val cy = h - footprint.h * TILE / 2f
        val r = w / 2f - 1
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x + 0.5f - cx
            val dy = (y + 0.5f - cy) * 1.25f
            val d = hypot(dx, dy)
            when {
                d > r -> Unit
                d > r - 4 -> c[x, y] = if (dy < 0) WALL else if (dy > r * 0.5f) STONE_DARK else STONE
                else -> c[x, y] = if (GroundPainter.hash(x, y, 81) % 6 == 0 || (dx + dy < -r * 0.6f)) WATER_LIGHT else WATER
            }
        }
        // La vasque et sa colonne.
        val px = (cx - 2).toInt()
        c.rect(px, 6, 4, (cy - 6).toInt(), WALL)
        c.rect(px + 3, 6, 1, (cy - 6).toInt(), STONE)
        c.rect(px - 5, 10, 14, 3, WALL)
        c.rect(px - 5, 12, 14, 1, STONE)
        c.rect(px - 4, 9, 12, 1, WATER)
        c.rect(px + 1, 2, 2, 4, WATER_LIGHT)
        c[px - 1, 4] = WATER_LIGHT
        c[px + 4, 4] = WATER_LIGHT
        c.outline(STONE_DARK)
        return c.sprite(w / 2, h - 1)
    }

    private fun bench(): PlaceholderSprite {
        val c = Canvas(18, 11)
        c.rect(1, 0, 16, 3, PLANK)
        c.rect(1, 2, 16, 1, TIMBER)
        c.rect(0, 5, 18, 3, PLANK)
        c.rect(0, 7, 18, 1, TIMBER)
        c.rect(2, 3, 2, 8, TIMBER)
        c.rect(14, 3, 2, 8, TIMBER)
        c.outline(WOOD_INK)
        return c.sprite(9, 10)
    }

    private fun lantern(): PlaceholderSprite {
        val c = Canvas(9, 28)
        c.rect(3, 9, 3, 19, STONE_DARK)
        c.rect(1, 2, 7, 8, GOLD)
        c.rect(1, 2, 7, 1, STONE_DARK)
        c.rect(0, 0, 9, 2, STONE_DARK)
        c.rect(2, 4, 2, 3, WALL)
        c.rect(2, 26, 5, 2, STONE)
        c.outline(INK)
        return c.sprite(4, 27)
    }

    private fun sign(): PlaceholderSprite {
        val c = Canvas(18, 24)
        c.rect(8, 8, 3, 16, TIMBER)
        c.rect(1, 2, 16, 9, PLANK)
        c.rect(1, 6, 16, 1, TIMBER)
        c.rect(1, 10, 16, 1, TIMBER)
        c.outline(WOOD_INK)
        return c.sprite(9, 23)
    }

    private fun block(footprint: Footprint): PlaceholderSprite {
        val w = footprint.w * TILE
        val h = footprint.h * TILE
        val c = Canvas(w, h)
        c.rect(0, 0, w, h, STONE)
        c.rect(0, 0, w, 2, WALL)
        c.outline(STONE_DARK)
        return c.sprite(w / 2, h - 1)
    }
}
