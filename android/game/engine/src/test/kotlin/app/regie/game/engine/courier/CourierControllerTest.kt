package app.regie.game.engine.courier

import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierCommand.GoTo
import app.regie.game.engine.courier.CourierCommand.Interact
import app.regie.game.engine.courier.CourierCommand.Move
import app.regie.game.engine.courier.CourierCommand.Next
import app.regie.game.engine.courier.CourierCommand.Previous
import app.regie.game.engine.courier.CourierCommand.Talk
import app.regie.game.engine.courier.CourierController.Companion.STEP_MILLIS
import app.regie.game.engine.courier.CourierEvent.ArrivedAtDoor
import app.regie.game.engine.courier.CourierEvent.EnterHouse
import app.regie.game.engine.pathfinding.findPath
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.session
import app.regie.game.engine.world.TilePos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourierControllerTest {
    private val events = mutableListOf<CourierEvent>()
    private val small = SmallVillage()

    private fun courier(
        village: Village = small.village,
        at: TilePos = village.spawn,
        facing: Direction = Direction.DOWN,
    ) = CourierController(village, at, facing) { events += it }

    /** Avance pas à pas et note chaque tuile atteinte, jusqu'à l'arrêt. */
    private fun CourierController.walkToEnd(limit: Int = 200): List<TilePos> {
        val tiles = mutableListOf(tile)
        repeat(limit) {
            if (!busy) return tiles
            advance(STEP_MILLIS.toLong())
            if (tiles.last() != tile) tiles += tile
        }
        error("le coursier marche encore")
    }

    private fun Session.waiting() = copy(pendingApproval = Approval(ApprovalId("v-${id.value}"), id, ApprovalKind.COMMAND, "npm test"))

    // Pas et collisions

    @Test
    fun `a press walks exactly one tile in one step, at constant speed`() {
        val courier = courier()
        courier.handle(Move(Direction.RIGHT, pressed = true))
        assertTrue(courier.moving)
        assertEquals(Direction.RIGHT, courier.facing)
        courier.advance(STEP_MILLIS / 2L)
        assertEquals(TilePos(6, 4), courier.tile, "la tuile change à la fin du pas")
        assertEquals(STEP_MILLIS / 2, courier.stepMillis)
        courier.handle(Move(Direction.RIGHT, pressed = false))
        courier.advance(STEP_MILLIS / 2L)
        assertEquals(TilePos(7, 4), courier.tile)
        assertFalse(courier.moving)
        assertFalse(courier.busy)
        assertEquals(CourierAction.IDLE, courier.action)
    }

    @Test
    fun `holding a direction keeps walking, one tile per step, even across a long frame`() {
        val courier = courier()
        courier.handle(Move(Direction.RIGHT, pressed = true))
        courier.advance(STEP_MILLIS * 3L + 40)
        assertEquals(TilePos(9, 4), courier.tile)
        assertTrue(courier.moving)
        assertEquals(40, courier.stepMillis)
        courier.handle(Move(Direction.RIGHT, pressed = false))
        courier.advance(STEP_MILLIS * 5L)
        assertEquals(TilePos(10, 4), courier.tile, "il finit son pas puis s'arrête")
        assertFalse(courier.moving)
    }

    @Test
    fun `a blocked tile turns the courier without moving him`() {
        val courier = courier()
        courier.handle(Move(Direction.UP, pressed = true))
        assertEquals(Direction.UP, courier.facing)
        assertFalse(courier.moving, "le rocher bloque")
        courier.advance(STEP_MILLIS * 4L)
        assertEquals(TilePos(6, 4), courier.tile)
        courier.handle(Move(Direction.DOWN, pressed = true))
        assertFalse(courier.moving, "la maison C bloque")
        assertEquals(Direction.DOWN, courier.facing)
    }

    @Test
    fun `the courier stops at a wall while the direction is still held`() {
        val courier = courier(at = TilePos(11, 4))
        courier.handle(Move(Direction.RIGHT, pressed = true))
        courier.advance(STEP_MILLIS * 10L)
        assertEquals(TilePos(13, 4), courier.tile, "le bord de la carte arrête le coursier")
        assertFalse(courier.moving)
        assertFalse(courier.busy)
        assertEquals(CourierAction.IDLE, courier.action)
    }

    @Test
    fun `a tap during a step is buffered and played after it, even once released`() {
        val courier = courier()
        courier.handle(Move(Direction.RIGHT, pressed = true))
        courier.advance(40)
        courier.handle(Move(Direction.UP, pressed = true))
        courier.advance(20)
        courier.handle(Move(Direction.UP, pressed = false))
        courier.handle(Move(Direction.RIGHT, pressed = false))
        courier.advance(100)
        assertEquals(TilePos(7, 4), courier.tile)
        assertTrue(courier.moving, "le pas mis en réserve commence aussitôt")
        assertEquals(Direction.UP, courier.facing)
        courier.advance(STEP_MILLIS * 3L)
        assertEquals(TilePos(7, 3), courier.tile)
        assertFalse(courier.moving)
    }

    @Test
    fun `with two directions held, the last pressed wins and the other resumes`() {
        val courier = courier(at = TilePos(10, 4))
        courier.handle(Move(Direction.DOWN, pressed = true))
        courier.advance(10)
        courier.handle(Move(Direction.RIGHT, pressed = true))
        courier.advance(STEP_MILLIS * 2L - 10)
        // Un pas vers le bas (déjà commencé), puis un vers la droite.
        assertEquals(TilePos(11, 5), courier.tile)
        assertEquals(Direction.RIGHT, courier.stepDirection)
        courier.handle(Move(Direction.RIGHT, pressed = false))
        courier.advance(STEP_MILLIS.toLong())
        assertEquals(TilePos(12, 5), courier.tile)
        assertEquals(Direction.DOWN, courier.stepDirection, "la direction encore tenue reprend")
        courier.advance(STEP_MILLIS.toLong())
        assertEquals(TilePos(12, 6), courier.tile)
    }

    // Entrer

    @Test
    fun `A facing a door enters the house`() {
        val courier = courier(at = small.houseA.entry, facing = Direction.UP)
        courier.handle(Interact)
        assertEquals(listOf<CourierEvent>(EnterHouse(small.a.id)), events)
    }

    @Test
    fun `A anywhere else does nothing`() {
        courier(at = small.houseA.entry, facing = Direction.LEFT).handle(Interact)
        courier(at = TilePos(1, 3), facing = Direction.UP).handle(Interact)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `A pressed during the step that reaches the door enters on arrival`() {
        val courier = courier(at = small.houseA.entry.offset(0, 1))
        courier.handle(Move(Direction.UP, pressed = true))
        courier.handle(Move(Direction.UP, pressed = false))
        courier.advance(100)
        courier.handle(Interact)
        assertTrue(events.isEmpty())
        courier.advance(60)
        assertEquals(listOf<CourierEvent>(EnterHouse(small.a.id)), events)
    }

    // Marche automatique

    @Test
    fun `go to walks the shortest path to the entry, faces the door and enters`() {
        val courier = courier()
        courier.handle(GoTo(small.a.id))
        assertEquals(small.a.id, courier.destination)
        val walked = courier.walkToEnd()
        assertEquals(findPath(small.village.world.map, small.village.spawn, small.houseA.entry), walked)
        assertEquals(Direction.UP, courier.facing)
        assertNull(courier.destination)
        assertEquals(listOf<CourierEvent>(EnterHouse(small.a.id)), events)
    }

    @Test
    fun `go to without entering stops in front of the door`() {
        val courier = courier()
        courier.handle(GoTo(small.b.id, enter = false))
        courier.walkToEnd()
        assertEquals(small.houseB.entry, courier.tile)
        assertEquals(Direction.UP, courier.facing)
        assertEquals(listOf<CourierEvent>(ArrivedAtDoor(small.b.id)), events)
    }

    @Test
    fun `go to from the entry itself only turns to the door`() {
        val courier = courier(at = small.houseC.entry, facing = Direction.LEFT)
        courier.handle(GoTo(small.c.id))
        assertEquals(Direction.UP, courier.facing)
        assertEquals(listOf<CourierEvent>(EnterHouse(small.c.id)), events)
        assertFalse(courier.busy)
    }

    @Test
    fun `go to an unknown or unreachable house does nothing`() {
        val walledIn = SmallVillage(extraBlocked = listOf(TilePos(2, 3), TilePos(4, 3), TilePos(3, 4)))
        val courier = courier(walledIn.village)
        courier.handle(GoTo(SessionId("absente")))
        courier.handle(GoTo(walledIn.a.id))
        assertNull(courier.destination)
        assertFalse(courier.busy)
    }

    @Test
    fun `any manual input cancels the auto walk, after the current step`() {
        val courier = courier()
        courier.handle(GoTo(small.b.id))
        courier.advance(STEP_MILLIS + 50L)
        val reached = courier.tile
        val stepping = assertNotNull(courier.stepDirection)
        courier.handle(Move(Direction.LEFT, pressed = true))
        courier.handle(Move(Direction.LEFT, pressed = false))
        assertNull(courier.destination)
        courier.advance(STEP_MILLIS - 50L)
        assertEquals(stepping.next(reached), courier.tile, "le pas commencé se termine sur la grille")
        assertEquals(Direction.LEFT, courier.facing)
        courier.walkToEnd()
        assertTrue(events.isEmpty())

        val other = courier()
        other.handle(GoTo(small.b.id))
        other.advance(50)
        other.handle(Interact)
        assertNull(other.destination)
        other.walkToEnd()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a new go to during a step takes over from the next tile`() {
        val courier = courier()
        courier.handle(GoTo(small.b.id))
        courier.advance(50)
        courier.handle(GoTo(small.a.id))
        courier.walkToEnd()
        assertEquals(small.houseA.entry, courier.tile)
        assertEquals(listOf<CourierEvent>(EnterHouse(small.a.id)), events)
    }

    // L1, R1

    @Test
    fun `R1 goes to the pending approval first and enters`() {
        val village = SmallVillage(c = session("c", "p").waiting())
        val courier = courier(village.village)
        courier.handle(Next)
        assertEquals(village.c.id, courier.destination)
        courier.walkToEnd()
        assertEquals(listOf<CourierEvent>(EnterHouse(village.c.id)), events)
    }

    @Test
    fun `R1 at the waiting house goes on to the next house`() {
        val village = SmallVillage(b = session("b", "p").waiting())
        val courier = courier(village.village, at = village.houseB.entry)
        courier.handle(Next)
        assertEquals(village.c.id, courier.destination)
    }

    @Test
    fun `without approval, L1 and R1 go through the houses in village order, around the ends`() {
        val courier = courier(at = small.houseA.entry, facing = Direction.UP)
        courier.handle(Next)
        assertEquals(small.b.id, courier.destination)
        courier.handle(Next)
        assertEquals(small.c.id, courier.destination, "L1 et R1 partent de la maison visée")
        courier.handle(Next)
        assertEquals(small.a.id, courier.destination)
        courier.handle(Previous)
        assertEquals(small.c.id, courier.destination)
        courier.walkToEnd()
        assertEquals(listOf<CourierEvent>(ArrivedAtDoor(small.c.id)), events, "L1 et R1 s'arrêtent devant la porte")
        courier.handle(Previous)
        assertEquals(small.b.id, courier.destination)
    }

    @Test
    fun `away from any house, L1 and R1 go to the nearest one`() {
        courier(at = TilePos(11, 4)).apply { handle(Next) }.let { assertEquals(small.b.id, it.destination) }
        courier(at = TilePos(1, 4)).apply { handle(Previous) }.let { assertEquals(small.a.id, it.destination) }
    }

    @Test
    fun `L1 and R1 in an empty village do nothing`() {
        val empty = SmallVillage().village.let { it.copy(houses = emptyList()) }
        val courier = courier(empty)
        courier.handle(Next)
        courier.handle(Previous)
        assertNull(courier.destination)
    }

    // Régénération du hameau

    @Test
    fun `a wider village keeps the courier at the same place relative to the plaza`() {
        val courier = courier(at = TilePos(10, 4), facing = Direction.LEFT)
        val wider = SmallVillage(shift = 3)
        courier.setVillage(wider.village)
        assertEquals(TilePos(13, 4), courier.tile)
        assertEquals(Direction.LEFT, courier.facing)
    }

    @Test
    fun `a regeneration during an auto walk re-paths to the moved house`() {
        val courier = courier()
        courier.handle(GoTo(small.b.id))
        courier.advance(STEP_MILLIS + 70L)
        val wider = SmallVillage(shift = 3)
        courier.setVillage(wider.village)
        assertTrue(courier.moving, "le pas en cours continue")
        assertEquals(small.b.id, courier.destination)
        courier.walkToEnd()
        assertEquals(wider.houseB.entry, courier.tile)
        assertEquals(listOf<CourierEvent>(EnterHouse(small.b.id)), events)
    }

    @Test
    fun `a step towards a tile that became blocked ends on a free tile`() {
        val courier = courier(at = TilePos(10, 4))
        courier.handle(Move(Direction.RIGHT, pressed = true))
        courier.handle(Move(Direction.RIGHT, pressed = false))
        courier.advance(80)
        val built = SmallVillage(extraBlocked = listOf(TilePos(11, 4)))
        courier.setVillage(built.village)
        courier.walkToEnd()
        assertFalse(built.village.world.map.isBlocked(courier.tile))
        assertFalse(courier.moving)
    }

    @Test
    fun `the house of the auto walk disappearing cancels it`() {
        val courier = courier()
        courier.handle(GoTo(small.b.id))
        courier.advance(50)
        courier.setVillage(SmallVillage(withB = false).village)
        assertNull(courier.destination)
        courier.walkToEnd()
        assertTrue(events.isEmpty())
    }

    // Animation

    @Test
    fun `the animation follows the action, its clock runs across steps and restarts on change`() {
        val courier = courier()
        assertEquals(CourierAction.IDLE, courier.action)
        courier.handle(Move(Direction.RIGHT, pressed = true))
        assertEquals(CourierAction.WALK, courier.action)
        assertEquals(0L, courier.actionMillis)
        courier.advance(STEP_MILLIS * 2L + 30)
        assertEquals(CourierAction.WALK, courier.action)
        assertEquals(STEP_MILLIS * 2L + 30, courier.actionMillis, "la marche ne recommence pas à chaque pas")
        courier.handle(Move(Direction.RIGHT, pressed = false))
        courier.advance(STEP_MILLIS.toLong())
        assertEquals(CourierAction.IDLE, courier.action)
        assertEquals(30L, courier.actionMillis)
        courier.handle(Talk(active = true))
        assertEquals(CourierAction.TALK, courier.action)
        assertEquals(0L, courier.actionMillis)
        courier.handle(Move(Direction.LEFT, pressed = true))
        assertEquals(CourierAction.WALK, courier.action, "il marche en parlant")
        courier.handle(Move(Direction.LEFT, pressed = false))
        courier.advance(STEP_MILLIS.toLong())
        assertEquals(CourierAction.TALK, courier.action)
        courier.handle(Talk(active = false))
        assertEquals(CourierAction.IDLE, courier.action)
    }
}
