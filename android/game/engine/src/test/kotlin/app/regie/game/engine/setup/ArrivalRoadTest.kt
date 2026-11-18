package app.regie.game.engine.setup

import app.regie.domain.model.SetupStep
import app.regie.game.engine.pathfinding.findPath
import app.regie.game.engine.world.Ground
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Ticket 53 : le chemin d'arrivée au hameau, un panneau par étape du parcours d'accueil. */
class ArrivalRoadTest {
    private val road = ArrivalRoad.build()
    private val map = road.world.map

    @Test
    fun `un panneau par etape, de gauche a droite, chacun au-dessus de la tuile ou le coursier le lit`() {
        assertEquals(SetupStep.entries.size, road.signs.size)
        assertEquals(SetupStep.entries.size, road.stands.size)
        road.signs.zipWithNext().forEach { (a, b) -> assertTrue(a.anchor.x < b.anchor.x) }
        SetupStep.entries.forEach { step ->
            val sign = road.sign(step)
            val stand = road.stand(step)
            assertEquals(stand.offset(0, -1), sign.anchor, "panneau de $step")
            assertTrue(sign in road.world.objects)
        }
    }

    @Test
    fun `le coursier arrive par la route et atteint chaque panneau`() {
        assertEquals(Ground.PATH, map.ground(road.entry))
        assertEquals(0, road.entry.x, "il arrive du bord du monde")
        SetupStep.entries.forEach { step ->
            val stand = road.stand(step)
            assertEquals(Ground.PATH, map.ground(stand), "$step")
            assertFalse(map.isBlocked(stand))
            val path = assertNotNull(findPath(map, road.entry, stand), "$step injoignable")
            // Il reste sur la route : il marche, il ne coupe pas à travers le pré.
            assertTrue(path.all { map.ground(it) == Ground.PATH || map.ground(it) == Ground.PLAZA })
        }
    }

    @Test
    fun `rien ne se chevauche, rien ne deborde, rien n'est pose sur la route`() {
        val areas = road.world.objects.map { it to road.area(it) }
        areas.forEach { (placed, area) ->
            area.tiles().forEach { tile ->
                assertTrue(map.inside(tile), "${placed.objectId} hors de la carte en $tile")
                assertTrue(map.ground(tile) != Ground.PATH, "${placed.objectId} sur la route en $tile")
            }
        }
        areas.forEachIndexed { i, (a, areaA) ->
            areas.drop(i + 1).forEach { (b, areaB) -> assertFalse(areaA.intersects(areaB), "${a.objectId} $areaA sur ${b.objectId} $areaB") }
        }
    }

    @Test
    fun `le hameau attend au bout de la route, avec ses maisons et leur porte praticable`() {
        val houses = road.world.objects.filter { it.objectId.startsWith("maison.") }
        assertTrue(houses.size >= 2)
        houses.forEach { house ->
            assertNotNull(house.roof, "un toit aux couleurs de la palette, jamais les couleurs clés")
            assertTrue(house.anchor.x > road.sign(SetupStep.FINISH).anchor.x)
        }
        road.doors.forEach { door ->
            val below = door.offset(0, 1)
            assertEquals(Ground.PLAZA, map.ground(below))
            assertNotNull(findPath(map, road.entry, below))
        }
    }

    @Test
    fun `les habitants attendent pres du panneau des agents, sur l'herbe libre`() {
        val agents = road.sign(SetupStep.AGENTS).anchor
        assertEquals(3, road.gathering.size)
        road.gathering.forEach { tile ->
            assertFalse(map.isBlocked(tile), "$tile")
            assertEquals(Ground.GRASS, map.ground(tile))
            assertEquals(agents.y, tile.y)
            assertTrue(tile.x in agents.x + 1..agents.x + ArrivalRoad.SPACING - 2, "$tile trop loin du panneau $agents")
        }
    }

    @Test
    fun `le chemin est toujours le meme`() {
        val again = ArrivalRoad.build()
        assertEquals(road.world.objects, again.world.objects)
        assertEquals(road.stands, again.stands)
    }
}
