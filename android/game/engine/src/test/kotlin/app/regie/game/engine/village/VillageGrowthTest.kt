package app.regie.game.engine.village

import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.game.engine.pathfinding.findPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stabilité et agrandissement : une maison bâtie ne bouge plus. */
class VillageGrowthTest {
    private val store = InMemoryVillageLayoutStore()
    private val generator = VillageGenerator(store)

    private fun generate(vararg sessions: Session) = generator.generate(overview(*sessions))

    /** Ce qui ne doit pas bouger d'une maison, en coordonnées relatives à la place. */
    private fun Village.spotOf(id: String): Triple<Any, String, Any>? {
        val house = houseOf(SessionId(id)) ?: return null
        return Triple(relative(house.footprint.bottomLeft), house.modelId, house.roof)
    }

    @Test
    fun `ajouter une session ne deplace aucune maison`() {
        val a = session("a", "regie")
        val b = session("b", "regie")
        val before = generate(a, b)
        val after = generate(a, b, session("c", "regie"), session("d", "site-perso"))
        assertEquals(before.spotOf("a"), after.spotOf("a"))
        assertEquals(before.spotOf("b"), after.spotOf("b"))
        assertEquals(4, after.houses.size)
    }

    @Test
    fun `retirer une session libere son terrain sans toucher aux autres`() {
        val a = session("a", "regie")
        val b = session("b", "regie")
        val c = session("c", "regie")
        val before = generate(a, b, c)
        val plotOfB = before.houseOf(SessionId("b"))!!.plot
        val after = generate(a, c)
        assertEquals(before.spotOf("a"), after.spotOf("a"))
        assertEquals(before.spotOf("c"), after.spotOf("c"))
        assertNull(after.houseOf(SessionId("b")))
        assertTrue(after.freePlots.any { it.index == plotOfB && it.street == 0 })
    }

    @Test
    fun `une session qui revient retrouve sa maison`() {
        val a = session("a", "regie")
        val b = session("b", "regie")
        val before = generate(a, b)
        generate(a)
        generate()
        val back = generate(a, b)
        assertEquals(before.spotOf("a"), back.spotOf("a"))
        assertEquals(before.spotOf("b"), back.spotOf("b"))
    }

    @Test
    fun `une session nouvelle evite le terrain d'une session disparue tant qu'il y en a d'autres`() {
        val a = session("a", "regie")
        val b = session("b", "regie")
        val before = generate(a, b)
        val plotOfB = before.houseOf(SessionId("b"))!!.plot
        val after = generate(a, session("n", "regie"))
        assertTrue(after.houseOf(SessionId("n"))!!.plot != plotOfB)
        // b revient : son terrain l'attendait.
        val back = generate(a, b, session("n", "regie"))
        assertEquals(before.spotOf("b"), back.spotOf("b"))
    }

    @Test
    fun `une rue pleine s'allonge et le hameau grandit`() {
        val first = generate(session("r0", "regie"))
        val initialPlots = first.streets.single().plots.size
        assertEquals(VillageConfig().initialPlots, initialPlots)

        val many = (0 until 7).map { session("r$it", "regie") }
        val grown = generator.generate(overview(many))
        val street = grown.streets.single()
        assertEquals(8, street.plots.size, "7 maisons et un terrain libre")
        assertEquals(1, grown.freePlots.size)
        assertTrue(street.road.width > first.streets.single().road.width)
        assertTrue(grown.world.map.width > first.world.map.width)
        assertEquals(first.spotOf("r0"), grown.spotOf("r0"))
        grown.houses.forEach { assertNotNull(findPath(grown.world.map, grown.spawn, it.entry)) }
    }

    @Test
    fun `une rue ne raccourcit pas quand ses sessions partent`() {
        val many = (0 until 6).map { session("r$it", "regie") }
        val grown = generator.generate(overview(many))
        val emptied = generate(session("r0", "regie"))
        assertEquals(grown.streets.single().plots.size, emptied.streets.single().plots.size)
        assertEquals(grown.spotOf("r0"), emptied.spotOf("r0"))
    }

    @Test
    fun `un nouveau projet ouvre une nouvelle rue a la suite`() {
        val before = generate(session("a", "regie"), session("b", "site-perso"))
        val after = generate(session("a", "regie"), session("b", "site-perso"), session("c", "api-meteo"))
        assertEquals(listOf("regie", "site-perso", "api-meteo"), after.streets.map { it.project })
        before.streets.forEach { street ->
            val same = after.streets[street.index]
            assertEquals(street.project, same.project)
            assertEquals(
                street.plots.map { before.relative(it.area.bottomLeft) },
                same.plots.map { after.relative(it.area.bottomLeft) },
            )
        }
        val third = after.streets[2]
        assertTrue(third.road.top > after.streets[0].road.top, "la troisième rue est sur la rangée suivante")
        assertNotNull(findPath(after.world.map, after.spawn, after.houseOf(SessionId("c"))!!.entry))
    }

    @Test
    fun `une rue sans session reste en place`() {
        generate(session("a", "regie"), session("b", "site-perso"))
        val after = generate(session("a", "regie"))
        assertEquals(listOf("regie", "site-perso"), after.streets.map { it.project })
        assertTrue(after.streets[1].plots.all { it.sessionId == null })
    }

    @Test
    fun `relocate reporte une position quand la carte s'elargit a l'ouest`() {
        val before = generate(session("a", "regie"), session("b", "site-perso"))
        val west = (0 until 8).map { session("w$it", "site-perso") }
        val after = generator.generate(overview(listOf(session("a", "regie")) + west))
        val houseA = before.houseOf(SessionId("a"))!!
        assertEquals(after.houseOf(SessionId("a"))!!.entry, after.relocate(houseA.entry, before))
    }

    @Test
    fun `la disposition enregistree reproduit le hameau avec un nouveau generateur`() {
        val sessions = arrayOf(session("a", "regie"), session("b", "site-perso"), session("c", "regie"))
        generate(*sessions)
        generate(sessions[0], sessions[2])
        val expected = generate(*sessions)
        val again = VillageGenerator(InMemoryVillageLayoutStore(store.layout)).generate(overview(*sessions))
        assertEquals(expected.fingerprint(), again.fingerprint())
    }

    @Test
    fun `une session qui change de projet demenage dans la bonne rue`() {
        generate(session("a", "regie"))
        val moved = generate(session("a", "site-perso"))
        val house = moved.houseOf(SessionId("a"))!!
        assertEquals("site-perso", moved.streets[house.street].project)
        assertTrue(moved.streets[0].plots.all { it.sessionId == null })
    }
}
