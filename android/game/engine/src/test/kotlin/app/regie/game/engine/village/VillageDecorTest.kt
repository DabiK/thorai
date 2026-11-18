package app.regie.game.engine.village

import app.regie.domain.model.Agent
import app.regie.game.engine.pathfinding.findPath
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.TileRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Le décor du ticket 52 : abondant, ordonné, jamais sur un passage ni sur un terrain libre. */
class VillageDecorTest {
    private val decor = DecorCatalog.DEFAULT

    private fun village(count: Int, projects: Int = 4, seed: Long = 0): Village {
        val sessions = (0 until count).map { session("d$it", "projet-${it % projects}", if (it % 2 == 0) Agent.CLAUDE else Agent.CODEX) }
        return VillageGenerator(InMemoryVillageLayoutStore(), config = VillageConfig(seed = seed)).generate(overview(sessions))
    }

    private val villages by lazy { listOf(village(3, 2), village(8), village(20), village(20, 6, seed = 7)) }

    @Test
    fun `un pont franchit la riviere au bout du sentier qui monte de la place`() {
        villages.forEach { village ->
            val map = village.world.map
            val bridges = (0 until map.height).flatMap { y -> (0 until map.width).map { TilePos(it, y) } }
                .filter { map.ground(it) == Ground.BRIDGE }
            assertTrue(bridges.isNotEmpty(), "aucun pont\n${village.ascii()}")
            bridges.forEach { tile ->
                assertFalse(map.isBlocked(tile), "pont bloqué en $tile")
                assertTrue(listOf(tile.offset(-2, 0), tile.offset(2, 0)).any { map.inside(it) && map.ground(it) == Ground.WATER })
            }
            // Le sentier va de la place au bord nord de la carte, par le pont.
            val north = TilePos(village.origin.x, 0)
            assertEquals(Ground.PATH, map.ground(north))
            val path = assertNotNull(findPath(map, village.spawn, north), village.ascii())
            assertTrue(path.any { map.ground(it) == Ground.BRIDGE })
        }
    }

    @Test
    fun `le decor ne touche ni route, ni allee, ni porte, ni terrain libre, ni la place d'un toit libre`() {
        villages.forEach { village ->
            val map = village.world.map
            val doors = village.houses.flatMap { listOf(it.door, it.entry) }.toSet()
            val freeZones = village.freePlots.map { it.area.grownUp(ROOF_ROWS) }
            village.decorations.forEach { decoration ->
                val id = decoration.placed.objectId
                decoration.footprint.tiles().forEach { tile ->
                    assertTrue(map.ground(tile) == Ground.GRASS || map.ground(tile) == Ground.PLAZA, "$id sur ${map.ground(tile)} en $tile")
                    assertFalse(tile in doors, "$id sur une porte en $tile")
                }
                freeZones.forEach { zone -> assertFalse(decoration.footprint.intersects(zone), "$id sur un terrain libre $zone") }
            }
            // Devant chaque terrain libre, l'entrée reste praticable.
            village.freePlots.forEach { assertFalse(map.isBlocked(it.entry)) }
        }
    }

    @Test
    fun `chaque maison a son jardin, qui reste dans son terrain`() {
        villages.forEach { village ->
            village.houses.forEach { house ->
                val area = village.streets[house.street].plots[house.plot].area
                val inside = village.decorations.filter { it.footprint.intersects(area) }
                inside.forEach { decoration ->
                    assertTrue(decoration.footprint.within(area), "${decoration.placed.objectId} déborde du jardin de ${house.session.id}")
                }
                assertTrue(inside.size >= MIN_GARDEN, "jardin de ${house.session.id} : ${inside.map { it.placed.objectId }}")
                // Une lanterne, des fleurs de part et d'autre de l'allée.
                assertTrue(inside.any { it.placed.objectId == decor.lantern })
                assertTrue(inside.any { it.placed.anchor == house.entry.offset(-1, 0) && it.placed.objectId in decor.flowers })
                assertTrue(inside.any { it.placed.anchor == house.entry.offset(1, 0) && it.placed.objectId in decor.flowers })
            }
        }
    }

    @Test
    fun `chaque objet a l'empreinte de son catalogue et le decor est varie`() {
        villages.forEach { village ->
            village.decorations.forEach { decoration ->
                val size = decor.sizeOf(decoration.placed.objectId)
                assertEquals(TileRect(decoration.placed.anchor.x, decoration.placed.anchor.y - size.height + 1, size.width, size.height), decoration.footprint)
                assertEquals(decor.isBlocking(decoration.placed.objectId), decoration.blocking)
            }
        }
        val kinds = village(20).decorations.map { it.placed.objectId }.toSet()
        assertTrue(kinds.size >= 20, "seulement ${kinds.size} sortes : $kinds")
        listOf(decor.well, decor.signpost, decor.fenceStart, decor.fenceEnd).forEach { assertTrue(it in kinds, it) }
    }

    @Test
    fun `la ceinture d'arbres est dense`() {
        val village = village(8)
        val map = village.world.map
        val trees = village.decorations.filter { it.placed.objectId in decor.trees }.map { it.placed.anchor }.toSet()
        val border = (0 until map.height).flatMap { y -> (0 until map.width).map { TilePos(it, y) } }.filter { tile ->
            (tile.x < 3 || tile.y < 3 || map.width - tile.x <= 3 || map.height - tile.y <= 3) && map.ground(tile) == Ground.GRASS
        }
        val share = border.count { it in trees } * 100 / border.size
        assertTrue(share >= 22, "ceinture à $share %")
    }

    @Test
    fun `les maisons qui se voient n'ont ni le meme modele ni le meme toit`() {
        listOf(village(20), village(20, 6, seed = 7), village(30, 6, seed = 3)).forEach { village ->
            val cells = village.houses.associateWith { PlotCell.of(it.street, it.plot) }
            village.houses.forEach { a ->
                village.houses.filter { b -> cells.getValue(a).isBeside(cells.getValue(b)) }.forEach { b ->
                    assertNotEquals(a.modelId, b.modelId, "${a.session.id} et ${b.session.id}")
                    assertNotEquals(a.roof, b.roof, "${a.session.id} et ${b.session.id}")
                }
            }
            assertTrue(village.houses.map { it.roof }.toSet().size >= 6)
        }
    }

    @Test
    fun `les deux maisons de part et d'autre de la place different`() {
        val village = village(2, 2)
        val (east, west) = village.houses.partition { village.streets[it.street].side == StreetSide.EAST }
        assertNotEquals(east.single().modelId, west.single().modelId)
        assertNotEquals(east.single().roof, west.single().roof)
    }

    @Test
    fun `la maison la plus proche est celle dont on est devant la porte`() {
        val village = village(8)
        village.houses.forEach { house ->
            assertEquals(house, village.nearestHouse(house.entry))
            assertEquals(house, village.nearestHouse(house.entry.offset(0, 1), maxDistance = 1))
        }
        assertEquals(null, village.nearestHouse(TilePos(0, 0), maxDistance = 2))
    }

    private fun TileRect.grownUp(rows: Int) = TileRect(left, top - rows, width, height + rows)

    private fun TileRect.within(other: TileRect) =
        left >= other.left && right <= other.right && top >= other.top && bottom <= other.bottom

    private companion object {
        const val ROOF_ROWS = 2
        const val MIN_GARDEN = 6
    }
}
