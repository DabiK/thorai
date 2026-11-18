package app.regie.game.engine.village

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HouseCatalogTest {
    @Test
    fun `le catalogue par defaut suit l'exemple du contrat et compte six modeles`() {
        val catalog = HouseCatalog.DEFAULT
        assertTrue(catalog.models.size >= 6)
        val chaumiere = catalog["maison.chaumiere"]!!
        assertEquals(6, chaumiere.footprintWidth)
        assertEquals(4, chaumiere.footprintHeight)
        assertEquals(3, chaumiere.doorX)
        assertEquals(3, chaumiere.doorY)
    }

    @Test
    fun `une porte doit etre sur la derniere rangee de l'empreinte`() {
        assertFailsWith<IllegalArgumentException> { HouseModel("maison.x", 4, 3, 1, 1) }
        assertFailsWith<IllegalArgumentException> { HouseModel("maison.x", 4, 3, 4, 2) }
    }

    @Test
    fun `une maison ne depasse pas l'empreinte maximale d'un terrain`() {
        val w = HouseModel.MAX_FOOTPRINT_WIDTH
        val h = HouseModel.MAX_FOOTPRINT_HEIGHT
        HouseModel("maison.max", w, h, 0, h - 1)
        assertFailsWith<IllegalArgumentException> { HouseModel("maison.large", w + 1, 3, 1, 2) }
        assertFailsWith<IllegalArgumentException> { HouseModel("maison.haute", 4, h + 1, 1, h) }
    }

    @Test
    fun `changer de catalogue ne deplace ni les rues ni les terrains`() {
        val sessions = overview(session("a", "regie"), session("b", "regie"), session("c", "site-perso"))
        val store = InMemoryVillageLayoutStore()
        val before = VillageGenerator(store).generate(sessions)
        val small = HouseCatalog(listOf(HouseModel("maison.petite", 3, 2, 1, 1)))
        val after = VillageGenerator(store, catalog = small).generate(sessions)
        assertEquals(before.streets, after.streets)
        assertEquals(before.origin, after.origin)
    }

    @Test
    fun `un catalogue injecte remplace le catalogue par defaut`() {
        val catalog = HouseCatalog(listOf(HouseModel("maison.unique", 4, 3, 1, 2)))
        val village = VillageGenerator(InMemoryVillageLayoutStore(), catalog = catalog)
            .generate(overview(session("a", "regie"), session("b", "regie")))
        assertTrue(village.houses.all { it.modelId == "maison.unique" && it.footprint.width == 4 })
    }

    @Test
    fun `un modele retire du catalogue est remplace, la maison reste sur son terrain`() {
        val store = InMemoryVillageLayoutStore()
        val first = VillageGenerator(store).generate(overview(session("a", "regie")))
        val house = first.houses.single()
        val remaining = HouseCatalog(HouseCatalog.DEFAULT.models.filter { it.id != house.modelId })
        val second = VillageGenerator(store, catalog = remaining).generate(overview(session("a", "regie")))
        val moved = second.houses.single()
        assertTrue(moved.modelId != house.modelId)
        assertEquals(house.plot, moved.plot)
        assertEquals(house.roof, moved.roof)
    }
}
