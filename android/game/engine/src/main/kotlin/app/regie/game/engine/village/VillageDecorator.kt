package app.regie.game.engine.village

import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import kotlin.math.abs

/** Ce que le décorateur lit du plan : la carte déjà peinte et bloquée, et les structures en coordonnées logiques. */
internal class DecorPlan(
    val map: TileMap,
    val frame: VillageFrame,
    val streets: List<StreetGeometry>,
    val houses: List<House>,
    val avenue: TileRect?,
    val trail: TileRect?,
    val water: IntRange,
    /** Les terrains libres, piquetés (ticket 48). */
    val freePlots: List<TileRect> = emptyList(),
)

/**
 * Le décor du hameau, abondant mais ordonné, d'après le hameau de l'artefact. Dans l'ordre :
 * 1. la place : fontaine, bancs, lanternes, jardinières ; au nord, le puits et le bosquet du pont ;
 * 2. le jardin de chaque maison bâtie : une lanterne au coin, une clôture (ou une haie, ou des fleurs)
 *    de part et d'autre de l'allée bordée de fleurs, et des accessoires contre les pignons (tonneaux,
 *    caisses, jardinière, bois, banc, petit arbre, buisson fleuri), tirés par session ;
 * 3. le bord sud des routes : buissons, fleurs et arbres en rythme avec les terrains ;
 * 4. les berges : roseaux et cailloux ;
 * 5. la ceinture d'arbres, dense et en quinconce ; puis, dans les prés, des bosquets et de petites choses.
 *
 * Règles, vérifiées par les tests : rien sur une route, une allée, une porte, l'eau ou le pont ; rien sur
 * un terrain libre ni sur la place d'un toit ; le jardin d'une maison reste dans son terrain et laisse
 * libre la colonne de son allée. Chaque tirage vient de la graine et de coordonnées logiques (ou de la
 * session) : une rue qui s'allonge ne déplace rien de ce qui existe déjà, hors de la ceinture.
 */
internal class VillageDecorator(private val decor: DecorCatalog, private val seed: Long) {
    fun decorate(plan: DecorPlan): List<Decoration> {
        val reserved = plan.streets.flatMap { it.plots }.flatMapTo(HashSet()) { it.reserve.tiles() }
        val layer = Layer(plan.map, plan.frame, reserved)
        plaza(layer, plan)
        plan.houses.forEach { house -> garden(layer, house, plan.streets[house.street].plots[house.plot].area) }
        plan.freePlots.forEach { stakes(layer, it) }
        plan.streets.forEach { verge(layer, it) }
        plan.avenue?.let { avenue(layer, it) }
        riverbanks(layer, plan)
        val frame = plan.frame
        for (y in frame.minY..frame.maxY) for (x in -frame.halfWidth..frame.halfWidth) {
            val tile = TilePos(x, y)
            if (frame.inBorder(tile)) belt(layer, tile) else field(layer, tile)
        }
        return layer.placed
    }

    // --- La place ---

    private fun plaza(layer: Layer, plan: DecorPlan) {
        val plaza = VillageGeometry.PLAZA
        layer.put(decor.fountain, VillageGeometry.FOUNTAIN.left, VillageGeometry.FOUNTAIN.bottom)
        layer.put(decor.lantern, plaza.left, plaza.top)
        layer.put(decor.lantern, plaza.right, plaza.top)
        // Les bancs, un rang sous le haut arrondi de la place, face à la fontaine.
        layer.put(decor.bench, -2, plaza.top + 1)
        layer.put(decor.bench, 2, plaza.top + 1)
        // Une jardinière de chaque côté de la fontaine, en laissant le tour praticable.
        val planter = decor.wideProps.first()
        layer.put(planter, plaza.left, 0)
        layer.put(planter, plaza.right - decor.sizeOf(planter).width + 1, 0)
        plan.streets.forEach { layer.put(decor.sign, it.sign.x, it.sign.y) }
        // Le poteau indicateur au carrefour, sous la place.
        layer.tryPut(decor.signpost, VillageGeometry.AVENUE_HALF_WIDTH + 1, VillageGeometry.roadTop(0) + VillageGeometry.ROAD_WIDTH)
        if (plan.trail == null || plan.water.isEmpty()) return
        // Entre la place et la rivière : le puits à l'ouest, un bosquet fleuri à l'est, deux lanternes au pont.
        val bank = plan.water.last + 1
        val edge = VillageGeometry.AVENUE_HALF_WIDTH + 1
        layer.tryPut(decor.lowLantern, -edge, bank)
        layer.tryPut(decor.lowLantern, edge, bank)
        layer.tryPut(decor.well, -VillageGeometry.PLAZA_HALF_WIDTH, plaza.top - 1)
        layer.tryPut(decor.trees.first(), VillageGeometry.PLAZA_HALF_WIDTH, plaza.top - 2)
        layer.tryPut(decor.floweringBushes.first(), edge, plaza.top - 1)
        layer.tryPut(decor.flowers.last(), -edge, plaza.top - 1)
    }

    // --- Les jardins ---

    private fun garden(layer: Layer, house: House, area: TileRect) {
        val id = house.session.id.value
        val yard = house.entry.y
        val entry = house.entry.x
        // Le devant : lanterne, clôture (ou haie, ou fleurs), fleurs de part et d'autre de l'allée.
        layer.putInGarden(decor.lantern, area.left, yard, area)
        layer.putInGarden(pick(decor.flowers, "fleurs.allee", id, 0), entry - 1, yard, area)
        layer.putInGarden(pick(decor.flowers, "fleurs.allee", id, 1), entry + 1, yard, area)
        val front = StableHash.pick(FRONT_STYLES, seed, "devant", id)
        listOf(area.left + 1..entry - 2, entry + 2 until area.right).forEachIndexed { side, run ->
            run.forEachIndexed { i, x ->
                val piece = when {
                    front < FENCE_STYLES -> when {
                        run.first == run.last -> decor.fencePost
                        x == run.first -> decor.fenceStart
                        x == run.last -> decor.fenceEnd
                        else -> decor.fence
                    }
                    front < FENCE_STYLES + HEDGE_STYLES -> pick(decor.bushes, "haie", id, side)
                    else -> pick(decor.flowers, "massif", id, side * PERCENT + i)
                }
                layer.putInGarden(piece, x, yard, area)
            }
        }
        val corner = StableHash.pick(CORNER_KINDS, seed, "coin", id)
        val cornerItem = when (corner) {
            0 -> pick(decor.bushes, "coin.buisson", id)
            1 -> pick(decor.grasses, "coin.herbe", id)
            2 -> decor.lowLantern
            else -> pick(decor.flowers, "coin.fleurs", id)
        }
        layer.putInGarden(cornerItem, area.right, yard, area)
        // Les côtés : ce que l'habitant range contre ses pignons.
        side(layer, house, area, (area.left until house.footprint.left).reversed().toList(), "ouest")
        side(layer, house, area, (house.footprint.right + 1..area.right).toList(), "est")
    }

    /**
     * Un terrain libre se lit comme un terrain : une clôture de piquets à la place de la maison
     * ([PLOT_FENCE_WIDTH] × [PLOT_FENCE_HEIGHT] tuiles, au fond du terrain), ouverte au sud face à l'entrée.
     * L'intérieur reste de l'herbe libre ; l'entrée, au bord de la route, reste praticable.
     */
    private fun stakes(layer: Layer, area: TileRect) {
        val fence = decor.plotFence
        val left = area.left + (area.width - PLOT_FENCE_WIDTH) / 2
        val right = left + PLOT_FENCE_WIDTH - 1
        val bottom = area.bottom - VillageGeometry.YARD
        val top = bottom - PLOT_FENCE_HEIGHT + 1
        val gate = area.left + area.width / 2
        for (x in left..right) {
            val north = when (x) {
                left -> fence.topLeft
                right -> fence.topRight
                else -> fence.horizontal
            }
            layer.putInGarden(north, x, top, area)
            val south = when (x) {
                left -> fence.bottomLeft
                right -> fence.bottomRight
                gate -> null
                gate - 1 -> fence.gateLeft
                gate + 1 -> fence.gateRight
                else -> fence.horizontal
            }
            south?.let { layer.putInGarden(it, x, bottom, area) }
        }
        for (y in top + 1 until bottom) {
            layer.putInGarden(fence.vertical, left, y, area)
            layer.putInGarden(fence.vertical, right, y, area)
        }
    }

    /** [columns] : du mur vers le bord du terrain. */
    private fun side(layer: Layer, house: House, area: TileRect, columns: List<Int>, name: String) {
        if (columns.isEmpty()) return
        val id = house.session.id.value
        val bottom = house.footprint.bottom
        val wall = columns.first()
        val wide = columns.size >= 2
        val left = columns.min()
        when (StableHash.pick(SIDE_KITS, seed, "pignon", name, id)) {
            // Le stock : un tonneau et une caisse.
            0 -> {
                layer.putInGarden(pick(decor.props, "stock", id, name), wall, bottom, area)
                layer.putInGarden(pick(decor.props, "stock.haut", id, name), wall, bottom - 1, area)
                if (wide) layer.putInGarden(pick(decor.flowers, "stock.fleurs", id, name), columns[1], bottom, area)
            }
            // Le potager : une jardinière ou des fleurs.
            1 -> if (wide) {
                layer.putInGarden(pick(decor.wideProps, "potager", id, name), left, bottom, area)
                layer.putInGarden(pick(decor.flowers, "potager.fleurs", id, name), wall, bottom - 1, area)
            } else {
                layer.putInGarden(pick(decor.flowers, "potager.fleurs", id, name), wall, bottom, area)
                layer.putInGarden(pick(decor.flowers, "potager.haut", id, name), wall, bottom - 1, area)
            }
            // Le coin repos : un banc contre le mur.
            2 -> {
                layer.putInGarden(decor.gardenBench, wall, bottom, area)
                if (wide) layer.putInGarden(pick(decor.saplings, "repos.arbre", id, name), columns[1], bottom - 1, area)
            }
            // Le verger : un buisson fleuri, ou un tonneau, et un petit arbre au fond.
            else -> if (wide) {
                layer.putInGarden(pick(decor.floweringBushes, "verger", id, name), left, bottom, area)
                layer.putInGarden(pick(decor.saplings, "verger.arbre", id, name), columns[1], bottom - 2, area)
            } else {
                layer.putInGarden(pick(decor.bushes, "verger.buisson", id, name), wall, bottom, area)
            }
        }
    }

    // --- Le bord des routes ---

    /**
     * Au sud de chaque route, deux rangées de pré : un buisson à chaque limite de terrain, des fleurs au
     * quart et aux trois quarts, des herbes ; puis un arbre une limite sur deux et quelques cailloux.
     * Le rythme suit les colonnes des terrains, de la place vers le bout de la rue.
     */
    private fun verge(layer: Layer, street: StreetGeometry) {
        val near = street.road.bottom + 1
        val far = near + 1
        for (x in street.road.left..street.road.right) {
            val fromPlaza = abs(x) - VillageGeometry.PLOT_START
            if (fromPlaza < 0) continue
            val column = fromPlaza % VillageGeometry.PLOT_WIDTH
            val plot = fromPlaza / VillageGeometry.PLOT_WIDTH
            when (column) {
                0 -> layer.tryPut(pick(decor.bushes, "bord", street.index, plot), x, near)
                2, 6 -> layer.tryPut(pick(decor.flowers, "bord.fleurs", street.index, plot, column), x, near)
                else -> if (roll("bord.herbe", x, near) < VERGE_GRASS) layer.tryPut(pick(decor.grasses, "bord.herbe", x), x, near)
            }
            when (column) {
                0 -> if (plot % 2 == 0) layer.tryPut(pick(decor.trees, "bord.arbre", street.index, plot), x, far)
                4 -> if (roll("bord.pre", x, far) < VERGE_PROP) layer.tryPut(pick(decor.fieldProps, "bord.pre", x), x, far)
            }
        }
    }

    /**
     * Le long de l'avenue, des deux côtés, en rythme de six tuiles : une petite lanterne, des fleurs, un
     * buisson, un vide, des fleurs, un buisson ; un arbre de loin en loin, un rang plus à l'écart.
     */
    private fun avenue(layer: Layer, avenue: TileRect) {
        for (y in avenue.top..avenue.bottom) {
            for (side in listOf(-1, 1)) {
                val x = if (side < 0) avenue.left - 1 else avenue.right + 1
                when (Math.floorMod(y, AVENUE_RHYTHM)) {
                    0 -> layer.tryPut(decor.lowLantern, x, y)
                    1, 4 -> layer.tryPut(pick(decor.flowers, "avenue.fleurs", y, side), x, y)
                    2, 5 -> layer.tryPut(pick(decor.bushes + decor.grasses, "avenue.buisson", y, side), x, y)
                }
                if (Math.floorMod(y, AVENUE_TREES) == 1) layer.tryPut(pick(decor.trees, "avenue.arbre", y, side), x + side * 2, y)
            }
        }
    }

    // --- Les berges ---

    private fun riverbanks(layer: Layer, plan: DecorPlan) {
        if (plan.water.isEmpty()) return
        val frame = plan.frame
        for (y in listOf(plan.water.first - 1, plan.water.last + 1)) {
            for (x in -frame.halfWidth..frame.halfWidth) {
                val r = roll("berge", x, y)
                when {
                    r < BANK_REEDS -> layer.tryPut(pick(decor.grasses, "roseau", x, y), x, y)
                    r < BANK_REEDS + BANK_STONES -> layer.tryPut(pick(decor.rocks + decor.fieldProps.takeLast(1), "galet", x, y), x, y)
                }
            }
        }
    }

    // --- La ceinture et les prés ---

    /** La ceinture : des arbres en quinconce, serrés, et des buissons entre eux. */
    private fun belt(layer: Layer, tile: TilePos) {
        val r = roll("ceinture", tile.x, tile.y)
        if (Math.floorMod(tile.x + 2 * tile.y, 3) == 0) {
            if (r < BELT_TREES) layer.tryPut(belted(tile), tile.x, tile.y)
        } else if (r < BELT_BUSHES) {
            layer.tryPut(pick(decor.bushes + decor.floweringBushes, "ceinture.buisson", tile.x, tile.y), tile.x, tile.y)
        }
    }

    /** Les essences de la ceinture : surtout des feuillus ronds, des sapins par plaques, quelques cerisiers. */
    private fun belted(tile: TilePos): String {
        val patch = StableHash.pick(PERCENT, seed, "essence", Math.floorDiv(tile.x, PATCH), Math.floorDiv(tile.y, PATCH))
        val r = roll("essence.arbre", tile.x, tile.y)
        val trees = decor.trees
        return when {
            trees.size < 3 -> trees[r % trees.size]
            patch < 30 && r < 70 -> trees[1]
            r < 20 -> trees[2]
            else -> trees[0]
        }
    }

    /** Les prés : des bosquets par endroits, ailleurs de petites choses espacées. */
    private fun field(layer: Layer, tile: TilePos) {
        if (!layer.isFree(tile) || layer.hasNeighbour(tile)) return
        val grove = StableHash.pick(PERCENT, seed, "bosquet", Math.floorDiv(tile.x, PATCH), Math.floorDiv(tile.y, PATCH)) < GROVES
        val r = roll("pre", tile.x, tile.y)
        val kind = roll("pre.genre", tile.x, tile.y)
        if (grove) {
            if (r >= GROVE_DENSITY) return
            val id = when {
                kind < 50 -> belted(tile)
                kind < 80 -> pick(decor.bushes + decor.floweringBushes, "bosquet.buisson", tile.x, tile.y)
                else -> pick(decor.flowers, "bosquet.fleurs", tile.x, tile.y)
            }
            layer.tryPut(id, tile.x, tile.y)
            return
        }
        if (r >= FIELD_DENSITY) return
        val choices = when {
            kind < 30 -> decor.grasses
            kind < 55 -> decor.flowers
            kind < 70 -> decor.bushes
            kind < 82 -> decor.trees
            kind < 94 -> decor.fieldProps
            else -> decor.rocks
        }
        layer.tryPut(pick(choices, "pre.objet", tile.x, tile.y), tile.x, tile.y)
    }

    private fun pick(choices: List<String>, vararg parts: Any): String = choices[StableHash.pick(choices.size, seed, *parts)]

    /** Un tirage de 0 à 99, stable. */
    private fun roll(vararg parts: Any): Int = StableHash.pick(PERCENT, seed, *parts)

    /** Le décor posé, en coordonnées logiques, avec ses collisions reportées sur la carte. */
    private inner class Layer(private val map: TileMap, private val frame: VillageFrame, private val reserved: Set<TilePos>) {
        val placed = mutableListOf<Decoration>()
        private val taken = HashSet<TilePos>()

        /** De l'herbe libre, hors des terrains (sauf [garden], le terrain de la maison qu'on meuble). */
        fun isFree(tile: TilePos, garden: TileRect? = null): Boolean {
            val world = frame.world(tile)
            return map.inside(world) && map.ground(world) == Ground.GRASS && !map.isBlocked(world) && tile !in taken &&
                (tile !in reserved || (garden != null && tile in garden))
        }

        fun hasNeighbour(tile: TilePos): Boolean =
            tile.offset(0, -1) in taken || tile.offset(1, 0) in taken || tile.offset(0, 1) in taken || tile.offset(-1, 0) in taken

        private fun footprint(objectId: String, x: Int, y: Int): TileRect {
            val size = decor.sizeOf(objectId)
            return TileRect(x, y - size.height + 1, size.width, size.height)
        }

        /** Pose sans vérifier : la place et ce qu'elle porte. */
        fun put(objectId: String, x: Int, y: Int) = add(objectId, footprint(objectId, x, y))

        /** Pose sur de l'herbe libre, hors des terrains ; sinon ne fait rien. */
        fun tryPut(objectId: String, x: Int, y: Int): Boolean = tryPut(objectId, footprint(objectId, x, y), null)

        /** Pose dans le jardin [garden] (le terrain d'une maison bâtie), sur de l'herbe libre. */
        fun putInGarden(objectId: String, x: Int, y: Int, garden: TileRect): Boolean =
            tryPut(objectId, footprint(objectId, x, y), garden)

        private fun tryPut(objectId: String, rect: TileRect, garden: TileRect?): Boolean {
            if (garden != null && !(rect.left >= garden.left && rect.right <= garden.right && rect.top >= garden.top && rect.bottom <= garden.bottom)) return false
            if (!rect.tiles().all { isFree(it, garden) }) return false
            add(objectId, rect)
            return true
        }

        private fun add(objectId: String, rect: TileRect) {
            val blocking = decor.isBlocking(objectId)
            placed += Decoration(Placed(objectId, rect.bottomLeft), rect, blocking)
            rect.tiles().forEach { tile ->
                taken += tile
                if (blocking) map.setBlocked(frame.world(tile))
            }
        }
    }

    private companion object {
        const val PERCENT = 100

        /** La clôture d'un terrain libre : la place d'une maison de taille moyenne. */
        const val PLOT_FENCE_WIDTH = 7
        const val PLOT_FENCE_HEIGHT = 4

        /** Sur dix maisons : six clôtures, deux haies, deux massifs de fleurs. */
        const val FRONT_STYLES = 10
        const val FENCE_STYLES = 6
        const val HEDGE_STYLES = 2
        const val CORNER_KINDS = 4
        const val SIDE_KITS = 4

        const val AVENUE_RHYTHM = 6
        const val AVENUE_TREES = 6
        const val VERGE_GRASS = 35
        const val VERGE_PROP = 45
        const val BANK_REEDS = 22
        const val BANK_STONES = 8
        const val BELT_TREES = 88
        const val BELT_BUSHES = 22

        /** Les plaques d'essences et de bosquets, en tuiles. */
        const val PATCH = 5
        const val GROVES = 22
        const val GROVE_DENSITY = 55
        const val FIELD_DENSITY = 9
    }
}
