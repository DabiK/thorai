package app.regie.game.engine.courier

import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierController.Companion.STEP_MILLIS
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.engine.village.overview
import app.regie.game.engine.village.relative
import app.regie.game.engine.village.session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Le coursier dans un vrai hameau généré, qui s'agrandit pendant qu'il marche. */
class CourierInVillageTest {
    private val generator = VillageGenerator(InMemoryVillageLayoutStore())

    @Test
    fun `every house can be reached from the spawn and entered`() {
        val village = generator.generate(
            overview((0 until 9).map { session("s$it", listOf("regie", "carnet", "api", "site")[it % 4]) }),
        )
        village.houses.forEach { house ->
            val events = mutableListOf<CourierEvent>()
            val courier = CourierController(village) { events += it }
            courier.handle(CourierCommand.GoTo(house.session.id))
            var guard = 0
            while (courier.busy && guard++ < 500) courier.advance(STEP_MILLIS.toLong())
            assertEquals(house.entry, courier.tile, "maison ${house.session.id}")
            assertEquals(listOf<CourierEvent>(CourierEvent.EnterHouse(house.session.id)), events)
        }
    }

    @Test
    fun `a street growing during the walk shifts the map, the courier keeps walking to the door`() {
        val west = session("w", "aaa-ouest")
        val east = (0 until 2).map { session("e$it", "regie") }
        // Les projets arrivés ensemble ouvrent leurs rues par ordre alphabétique : aaa-ouest à l'est, regie à l'ouest.
        val before = generator.generate(overview(east + west))
        val target = before.houses.first { it.street == 1 }.session.id
        val events = mutableListOf<CourierEvent>()
        val courier = CourierController(before) { events += it }
        courier.handle(CourierCommand.GoTo(target))
        courier.advance(STEP_MILLIS * 3L + 40)
        val relativeBefore = before.relative(courier.tile)

        // La rue de la maison visée s'allonge : la carte s'élargit et tout glisse.
        val grown = east + west + (0 until 4).map { session("w$it", before.houses.first { it.street == 1 }.session.project) }
        val after = generator.generate(overview(grown))
        assertNotEquals(before.origin, after.origin, "la carte s'est élargie")
        courier.setVillage(after)
        assertEquals(relativeBefore, after.relative(courier.tile))
        assertTrue(courier.moving)

        var guard = 0
        while (courier.busy && guard++ < 500) courier.advance(STEP_MILLIS.toLong())
        assertEquals(after.houseOf(SessionId(target.value))!!.entry, courier.tile)
        assertEquals(listOf<CourierEvent>(CourierEvent.EnterHouse(target)), events)
    }
}
