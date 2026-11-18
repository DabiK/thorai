package app.regie.game.engine.village

import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.pathfinding.findPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bâtir (ticket 48) : le terrain est réservé avant la création, et la maison y est bâtie, nulle part ailleurs. */
class VillageConstructionTest {
    private val store = InMemoryVillageLayoutStore()
    private val generator = VillageGenerator(store)
    private val decor = DecorCatalog.DEFAULT

    private val a = session("a", "regie")
    private val b = session("b", "regie")
    private val c = session("c", "regie")

    @Test
    fun `la fiche montre le terrain vise sans rien reserver, puis A reserve exactement celui-la`() {
        generator.generate(overview(a, b))
        val saves = store.saveCount

        val preview = generator.generate(overview(a, b), Construction("regie"))
        val target = assertNotNull(preview.site)
        assertNull(target.stage, "pendant la fiche, rien n'est en travaux")
        assertNull(target.placed)
        assertEquals(saves, store.saveCount, "la fiche n'enregistre rien")
        assertNull(store.layout.reservation)
        assertTrue(preview.freePlots.any { it.street == target.street && it.index == target.plot }, "le terrain visé reste libre")

        val reservation = generator.reserve(overview(a, b), "regie")
        assertEquals(PlotReservation("regie", target.plot), reservation)
        assertEquals(reservation, store.layout.reservation, "réservé dans la disposition enregistrée")

        val reserved = generator.generate(overview(a, b), Construction("regie", stage = ConstructionStage.PLOT))
        val site = assertNotNull(reserved.site)
        assertEquals(target.plot, site.plot)
        assertEquals(ConstructionStage.PLOT, site.stage)
        assertEquals(decor.sites[0], site.placed?.objectId)
        assertTrue(reserved.freePlots.none { it.street == site.street && it.index == site.plot }, "un terrain réservé n'est plus libre")
        site.footprint.tiles().forEach { assertTrue(reserved.world.map.isBlocked(it)) }
        assertNotNull(findPath(reserved.world.map, reserved.spawn, site.entry), "le devant du chantier reste accessible")
    }

    @Test
    fun `la maison de la session creee est batie sur le terrain reserve, meme si un autre se libere`() {
        generator.generate(overview(a, b, c))
        val plot = generator.reserve(overview(a, b, c), "regie").plot
        assertEquals(3, plot)
        // b disparaît : son terrain se libère, plus près de la place que le terrain réservé.
        generator.generate(overview(a, c))

        generator.bind(SessionId("n"))
        val village = generator.generate(overview(a, c, session("n", "regie")))
        assertEquals(plot, village.houseOf(SessionId("n"))?.plot)
        assertNull(store.layout.reservation, "la réservation est tenue")
        assertEquals(plot, store.layout.houses.getValue(SessionId("n")).plot)
    }

    @Test
    fun `une session du projet qui parait avant la reponse du pont attend, les autres sont placees`() {
        generator.generate(overview(a))
        val plot = generator.reserve(overview(a), "regie").plot

        // La vue d'ensemble a la nouvelle session avant la réponse de session.create.
        val early = generator.generate(overview(a, session("n", "regie"), session("x", "site-perso")))
        assertNull(early.houseOf(SessionId("n")), "elle attend d'être reconnue")
        assertNotNull(early.houseOf(SessionId("x")))
        assertEquals(plot, early.site?.plot)

        generator.bind(SessionId("n"))
        val built = generator.generate(overview(a, session("n", "regie"), session("x", "site-perso")))
        assertEquals(plot, built.houseOf(SessionId("n"))?.plot)
    }

    @Test
    fun `un nouveau projet ouvre une rue, a la suite des autres, et sa maison y est batie`() {
        val before = generator.generate(overview(a, session("s", "site-perso")))
        val preview = generator.generate(overview(a, session("s", "site-perso")), Construction("meteo", newProject = true))
        assertEquals(before.streets.size + 1, preview.streets.size, "la rue que le projet ouvrirait")
        assertEquals("meteo", preview.streets.last().project)
        assertEquals(preview.streets.last().index, preview.site?.street)
        assertEquals(before.streets.size, store.layout.streets.size, "rien n'est enregistré pendant la fiche")

        val reservation = generator.reserve(overview(a, session("s", "site-perso")), "meteo")
        assertEquals(PlotReservation("meteo", 0, opensStreet = true), reservation)
        generator.bind(SessionId("m"))
        val built = generator.generate(overview(a, session("s", "site-perso"), session("m", "meteo")))
        val house = assertNotNull(built.houseOf(SessionId("m")))
        assertEquals(built.streets.indexOfFirst { it.project == "meteo" }, house.street)
        assertEquals(0, house.plot)
        assertEquals(preview.relative(preview.site!!.entry), built.relative(built.streets[house.street].plots[0].entry))
    }

    @Test
    fun `un echec avant la creation libere le terrain, et la rue d'un nouveau projet disparait`() {
        generator.generate(overview(a))
        val plot = generator.reserve(overview(a), "regie").plot
        generator.release()
        val village = generator.generate(overview(a))
        assertNull(village.site)
        assertTrue(village.freePlots.any { it.street == 0 && it.index == plot })

        generator.reserve(overview(a), "meteo")
        generator.release()
        assertEquals(listOf("regie"), store.layout.streets.map { it.project })
        assertNull(generator.generate(overview(a)).streets.firstOrNull { it.project == "meteo" })
    }

    @Test
    fun `au lancement, une reservation sans session tombe, une reservation liee attend sa session`() {
        generator.generate(overview(a))
        generator.reserve(overview(a), "regie")
        VillageGenerator(store).forgetUnboundReservation()
        assertNull(store.layout.reservation)

        val plot = generator.reserve(overview(a), "regie").plot
        generator.bind(SessionId("n"))
        VillageGenerator(store).forgetUnboundReservation()
        assertEquals(SessionId("n"), store.layout.reservation?.session)
        val restarted = VillageGenerator(store).generate(overview(a))
        assertEquals(ConstructionStage.FOUNDATIONS, restarted.site?.stage)
        assertEquals(plot, VillageGenerator(store).generate(overview(a, session("n", "regie"))).houseOf(SessionId("n"))?.plot)
    }

    @Test
    fun `la maison en chantier se dessine en travaux jusqu'a la premiere reponse`() {
        generator.generate(overview(a))
        generator.reserve(overview(a), "regie")
        generator.bind(SessionId("n"))
        val n = session("n", "regie", status = SessionStatus.WORKING)
        val stages = ConstructionStage.entries.associateWith { stage ->
            generator.generate(overview(a, n), Construction("regie", stage = stage, session = SessionId("n")))
        }
        ConstructionStage.entries.filter { it != ConstructionStage.FINISHED }.forEach { stage ->
            val village = stages.getValue(stage)
            val house = assertNotNull(village.houseOf(SessionId("n")), "la session existe : sa maison aussi")
            assertTrue(village.isUnderConstruction(house))
            val objects = village.world.objects.map { it.objectId }
            assertTrue(decor.sites[stage.ordinal] in objects, "$stage")
            assertFalse(house.placed in village.world.objects, "la maison n'est pas encore dessinée")
        }
        val finished = stages.getValue(ConstructionStage.FINISHED)
        val house = assertNotNull(finished.houseOf(SessionId("n")))
        assertFalse(finished.isUnderConstruction(house))
        assertTrue(house.placed in finished.world.objects)
        assertTrue(finished.world.objects.none { it.objectId in decor.sites })
        // Le chantier et la maison finie sont au même endroit.
        assertTrue(stages.getValue(ConstructionStage.FRAME).site!!.area == finished.site!!.area)
    }

    @Test
    fun `un terrain libre a sa cloture de piquets, ouverte face a l'entree`() {
        val village = generator.generate(overview(a, b))
        val map = village.world.map
        assertTrue(village.freePlots.isNotEmpty())
        village.freePlots.forEach { plot ->
            val stakes = village.decorations.filter { it.footprint.within(plot.area) }
            assertTrue(stakes.isNotEmpty() && stakes.all { it.placed.objectId in decor.plotFence.all }, "terrain $plot : ${stakes.map { it.placed.objectId }}")
            assertFalse(map.isBlocked(plot.entry))
            // Par l'ouverture, on entre dans le terrain.
            assertFalse(map.isBlocked(plot.entry.offset(0, -1)))
            assertFalse(map.isBlocked(plot.entry.offset(0, -2)))
        }
    }

    @Test
    fun `la carte du hameau liste les rues, les maisons et les terrains, chantier compris`() {
        generator.generate(overview(a, b, session("s", "site-perso")))
        generator.reserve(overview(a, b, session("s", "site-perso")), "regie")
        val village = generator.generate(overview(a, b, session("s", "site-perso")), Construction("regie", stage = ConstructionStage.PLOT))
        val map = village.map()
        assertEquals(listOf("regie", "site-perso"), map.streets.map { it.project })
        assertEquals(3, map.houseCount)
        val regie = map.streets[0].lots
        assertEquals(listOf(SessionId("a"), SessionId("b")), regie.filterIsInstance<MapLot.Built>().map { it.house.session.id })
        assertEquals(1, regie.filterIsInstance<MapLot.Building>().size)
        assertTrue(map.streets[1].lots.any { it is MapLot.Free })
        assertNotEquals(regie.size, regie.filterIsInstance<MapLot.Built>().size)
    }
}
