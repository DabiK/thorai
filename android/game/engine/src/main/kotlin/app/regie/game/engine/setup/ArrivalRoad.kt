package app.regie.game.engine.setup

import app.regie.domain.model.SetupStep
import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.village.HouseCatalog
import app.regie.game.engine.village.RoofPalette
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import app.regie.game.engine.world.World

/**
 * Le décor du parcours d'accueil (ticket 53, ADR 23) : la route qui mène au hameau. Le coursier arrive
 * par le bord gauche ([entry]) ; un panneau par étape ([signs]) borde la route, et il lit chacun debout
 * juste en dessous ([stands]). Les habitants attendent près du panneau des agents ([gathering]) ; la
 * place du hameau et ses maisons ferment la route, après le dernier panneau.
 *
 * Tout est fixe et déterministe : ce chemin ne dépend que du nombre d'étapes, jamais des sessions.
 */
class ArrivalRoad private constructor(
    val world: World,
    val signs: List<Placed>,
    val stands: List<TilePos>,
    val gathering: List<TilePos>,
    val doors: List<TilePos>,
    private val areas: Map<Placed, TileRect>,
) {
    /** La tuile d'où le coursier entre dans le monde, sur la route. */
    val entry: TilePos = TilePos(0, ROAD_ROW)

    fun sign(step: SetupStep): Placed = signs[step.ordinal]

    fun stand(step: SetupStep): TilePos = stands[step.ordinal]

    /** Les tuiles qu'occupe [placed]. */
    fun area(placed: Placed): TileRect = areas.getValue(placed)

    companion object {
        /** Tuiles entre deux panneaux. */
        const val SPACING = 6

        /** La rangée où le coursier marche ; la route en a deux. */
        const val ROAD_ROW = 11

        private const val START = 4

        /** Assez de pré autour de la route pour que la caméra la cadre sous un grand cadre de texte. */
        private const val HEIGHT = 26
        private const val SIGN_ROW = ROAD_ROW - 1
        private const val PLAZA_TOP = ROAD_ROW - 3
        private const val PLAZA_BOTTOM = ROAD_ROW + 3

        fun build(
            steps: List<SetupStep> = SetupStep.entries,
            decor: DecorCatalog = DecorCatalog.DEFAULT,
            houses: HouseCatalog = HouseCatalog.DEFAULT,
        ): ArrivalRoad {
            val signX = steps.indices.map { START + it * SPACING }
            val plazaLeft = signX.last() + 3
            val width = plazaLeft + 15
            val map = TileMap(width, HEIGHT)
            for (x in 0 until plazaLeft) {
                map.setGround(TilePos(x, ROAD_ROW), Ground.PATH)
                map.setGround(TilePos(x, ROAD_ROW + 1), Ground.PATH)
            }
            for (y in PLAZA_TOP..PLAZA_BOTTOM) for (x in plazaLeft until width) map.setGround(TilePos(x, y), Ground.PLAZA)

            val objects = ArrayList<Placed>()
            val areas = LinkedHashMap<Placed, TileRect>()
            fun put(id: String, rect: TileRect, roof: RoofColors? = null): Placed {
                val placed = Placed(id, rect.bottomLeft, roof)
                objects += placed
                areas[placed] = rect
                if (decor.isBlocking(id)) rect.tiles().forEach { map.setBlocked(it) }
                return placed
            }
            fun decorAt(id: String, x: Int, y: Int): Placed {
                val size = decor.sizeOf(id)
                return put(id, TileRect(x, y - size.height + 1, size.width, size.height))
            }

            val signs = signX.map { decorAt(decor.sign, it, SIGN_ROW) }
            val stands = signX.map { TilePos(it, ROAD_ROW) }
            val agents = steps.indexOf(SetupStep.AGENTS).takeIf { it >= 0 } ?: steps.lastIndex
            val gathering = (2..4).map { TilePos(signX[agents] + it, SIGN_ROW) }

            // Le pré qui borde la route : une haie d'arbres derrière, un arbre entre deux panneaux,
            // des buissons et des fleurs devant, quelques rochers. Jamais sur la route ni devant un habitant.
            signX.forEachIndexed { i, x ->
                if (i < signX.lastIndex) {
                    decorAt(decor.trees[i % decor.trees.size], x + 3, PLAZA_TOP)
                    decorAt(decor.flowers[i % decor.flowers.size], x + 4, ROAD_ROW + 2)
                    if (i % 2 == 1) decorAt(decor.rocks.first(), x + 2, ROAD_ROW + 2)
                }
                decorAt(decor.bushes.first(), x + 1, PLAZA_BOTTOM)
            }
            // La lisière derrière, jamais derrière les toits ; les prés devant, jusqu'au bas du monde.
            listOf(PLAZA_TOP - 5, PLAZA_TOP - 2).forEachIndexed { band, row ->
                for (x in 1 + band until plazaLeft - 1 step 3) decorAt(decor.trees[(x / 3 + band) % decor.trees.size], x, row)
            }
            (PLAZA_BOTTOM + 1 until HEIGHT step 3).forEachIndexed { band, row ->
                for (x in 2 - band % 2 until width step 3) decorAt(decor.trees[(x / 3 + band + 2) % decor.trees.size], x, row)
            }

            // Le hameau : deux maisons au-dessus de la place, la fontaine au milieu, des lanternes à l'entrée.
            val doors = ArrayList<TilePos>()
            listOf("maison.chaumiere" to plazaLeft + 1, "maison.atelier" to plazaLeft + 8).forEachIndexed { i, (id, x) ->
                val model = houses[id] ?: houses.models[i % houses.models.size]
                val rect = TileRect(x, PLAZA_TOP - model.footprintHeight, model.footprintWidth, model.footprintHeight)
                put(model.id, rect, RoofPalette.AUBE[i % RoofPalette.AUBE.size])
                doors += TilePos(rect.x + model.doorX, rect.y + model.doorY)
            }
            decorAt(decor.fountain, plazaLeft + 5, ROAD_ROW + 2)
            decorAt(decor.lantern, plazaLeft, PLAZA_TOP + 1)
            decorAt(decor.lantern, plazaLeft, PLAZA_BOTTOM)
            decorAt(decor.bench, plazaLeft + 11, PLAZA_BOTTOM - 1)

            return ArrivalRoad(World(map, objects.toList()), signs, stands, gathering, doors.toList(), areas.toMap())
        }
    }
}
