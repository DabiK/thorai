package app.regie.game.engine.village

import app.regie.domain.model.Agent
import app.regie.domain.model.SessionId
import app.regie.game.engine.pathfinding.findPath
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TilePos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class VillageGeneratorTest {
    private val sample = overview(
        session("s1", "regie"),
        session("s2", "regie", Agent.CODEX),
        session("s3", "site-perso", Agent.CODEX),
        session("s4", "api-meteo"),
        session("s5", "regie"),
    )

    private fun generator(store: VillageLayoutStore = InMemoryVillageLayoutStore(), seed: Long = 0) =
        VillageGenerator(store, config = VillageConfig(seed = seed))

    // --- Déterminisme ---

    @Test
    fun `memes sessions et meme graine donnent le meme hameau`() {
        val first = generator().generate(sample)
        val second = generator().generate(sample)
        assertEquals(first.fingerprint(), second.fingerprint())
        assertEquals(first.houses, second.houses)
        assertEquals(first.streets, second.streets)
    }

    @Test
    fun `l'ordre de la vue d'ensemble ne change rien`() {
        val shuffled = overview(sample.sessions.reversed())
        // Overview.of trie ; on contourne le tri pour vérifier que le hameau n'en dépend pas.
        val raw = sample.copy(sessions = sample.sessions.reversed())
        val reference = generator().generate(sample).fingerprint()
        assertEquals(reference, generator().generate(shuffled).fingerprint())
        assertEquals(reference, generator().generate(raw).fingerprint())
    }

    @Test
    fun `une autre graine change les tirages mais pas les rues ni les terrains`() {
        val a = generator(seed = 1).generate(sample)
        val b = generator(seed = 2).generate(sample)
        assertNotEquals(a.fingerprint(), b.fingerprint())
        assertEquals(a.streets, b.streets)
    }

    @Test
    fun `regenerer avec la meme disposition ne reenregistre rien`() {
        val store = InMemoryVillageLayoutStore()
        val generator = generator(store)
        val first = generator.generate(sample)
        assertEquals(1, store.saveCount)
        val second = generator.generate(sample)
        assertEquals(1, store.saveCount)
        assertEquals(first.fingerprint(), second.fingerprint())
    }

    // --- Plan ---

    @Test
    fun `une rue par projet, une maison par session sur la rue de son projet`() {
        val village = generator().generate(sample)
        assertEquals(listOf("api-meteo", "regie", "site-perso"), village.streets.map { it.project })
        assertEquals(5, village.houses.size)
        village.houses.forEach { house ->
            val street = village.streets[house.street]
            assertEquals(house.session.project, street.project)
            assertEquals(house.session.id, street.plots[house.plot].sessionId)
        }
        assertTrue(village.streets.all { street -> street.plots.any { it.sessionId == null } }, "chaque rue a un terrain libre")
        assertEquals(village.streets.sumOf { it.plots.size } - 5, village.freePlots.size)
    }

    @Test
    fun `l'habitant suit l'agent de la session`() {
        val village = generator().generate(sample)
        assertEquals(InhabitantKind.SCRIBE, village.houseOf(SessionId("s1"))?.inhabitant)
        assertEquals(InhabitantKind.BRICOLEUR, village.houseOf(SessionId("s2"))?.inhabitant)
    }

    @Test
    fun `la place a sa fontaine et le coursier arrive devant`() {
        val village = generator().generate(overview())
        val map = village.world.map
        assertTrue(village.spawn in village.plaza)
        assertEquals(Ground.PLAZA, map.ground(village.spawn))
        assertFalse(map.isBlocked(village.spawn))
        assertTrue(map.isBlocked(village.spawn.offset(0, -1)), "la fontaine est juste au-dessus")
        assertTrue(village.world.objects.any { it.objectId == DecorCatalog.DEFAULT.fountain })
        assertTrue(map.width >= 30 && map.height >= 17)
    }

    @Test
    fun `aucune superposition`() {
        val village = generator().generate(sample)
        val map = village.world.map
        val occupied = HashMap<TilePos, String>()
        val footprints = village.houses.map { it.modelId to it.footprint } +
            village.decorations.map { it.placed.objectId to it.footprint }
        footprints.forEach { (id, rect) ->
            rect.tiles().forEach { tile ->
                assertTrue(map.inside(tile), "$id hors de la carte")
                val other = occupied.put(tile, id)
                assertNull(other, "$id et $other se superposent en $tile")
            }
        }
        village.houses.forEach { house ->
            assertTrue(house.footprint.tiles().all { map.ground(it) == Ground.GRASS && map.isBlocked(it) })
            assertTrue(house.door in house.footprint)
            assertEquals(house.door.offset(0, 1), house.entry)
            assertFalse(map.isBlocked(house.entry))
            assertEquals(Ground.PATH, map.ground(house.entry))
        }
        village.decorations.forEach { decoration ->
            decoration.footprint.tiles().forEach { tile ->
                val ground = map.ground(tile)
                assertTrue(ground == Ground.GRASS || ground == Ground.PLAZA, "${decoration.placed.objectId} sur $ground")
                assertEquals(decoration.blocking, map.isBlocked(tile))
            }
        }
        // Rien ne bloque un terrain libre ni une route.
        village.freePlots.forEach { plot -> assertTrue(plot.area.tiles().none { map.isBlocked(it) }, "terrain $plot") }
        village.streets.forEach { street -> assertTrue(street.road.tiles().none { map.isBlocked(it) }, street.project) }
        // Les terrains ne se chevauchent pas.
        val plots = village.streets.flatMap { it.plots }
        plots.forEachIndexed { i, a -> plots.drop(i + 1).forEach { b -> assertFalse(a.area.intersects(b.area), "$a / $b") } }
    }

    @Test
    fun `chaque porte et chaque terrain libre sont accessibles depuis la place`() {
        val village = generator().generate(sample)
        val map = village.world.map
        (village.houses.map { it.entry } + village.freePlots.map { it.entry }).forEach { entry ->
            assertNotNull(findPath(map, village.spawn, entry), "inaccessible : $entry\n${village.ascii()}")
        }
    }

    @Test
    fun `les objets du monde sont les maisons et le decor, du fond vers l'avant`() {
        val village = generator().generate(sample)
        val objects = village.world.objects
        assertEquals(village.houses.size + village.decorations.size, objects.size)
        assertEquals(objects.sortedBy { it.anchor.y }.map { it.anchor.y }, objects.map { it.anchor.y })
        village.houses.forEach { house ->
            val placed = objects.single { it.anchor == house.footprint.bottomLeft && it.objectId == house.modelId }
            assertEquals(house.roof, placed.roof)
        }
    }

    @Test
    fun `deux maisons voisines n'ont ni le meme modele ni le meme toit`() {
        val sessions = (1..8).map { session("v$it", "regie") }
        val village = generator().generate(overview(sessions))
        village.houses.sortedBy { it.plot }.zipWithNext().forEach { (a, b) ->
            assertNotEquals(a.modelId, b.modelId)
            assertNotEquals(a.roof, b.roof)
        }
        assertTrue(village.houses.map { it.modelId }.toSet().size >= 3)
    }

    @Test
    fun `30 sessions sur 6 projets se generent vite et restent accessibles`() {
        val sessions = (0 until 30).map { session("p$it", "projet-${it % 6}", if (it % 2 == 0) Agent.CLAUDE else Agent.CODEX) }
        val generator = generator()
        lateinit var village: Village
        generator.generate(overview(sessions))
        val elapsed = measureTime { repeat(5) { village = generator.generate(overview(sessions)) } }
        assertEquals(30, village.houses.size)
        assertEquals(6, village.streets.size)
        assertTrue(elapsed.inWholeMilliseconds < 2_000, "5 générations en $elapsed")
        village.houses.forEach { house -> assertNotNull(findPath(village.world.map, village.spawn, house.entry)) }
    }
}
