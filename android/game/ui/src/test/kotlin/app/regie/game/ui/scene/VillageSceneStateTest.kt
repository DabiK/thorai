package app.regie.game.ui.scene

import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.Overview
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.courier.CourierController.Companion.STEP_MILLIS
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.ui.demo.DemoScene
import app.regie.game.ui.render.ArtPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VillageSceneStateTest {
    private val generator = VillageGenerator(InMemoryVillageLayoutStore())
    private val village = generator.generate(Overview.of(DemoScene.sessions(3), emptyList(), BridgeStatus.Connected))
    private val spawnFoot = ArtPoint(village.spawn.x * 16 + 8, village.spawn.y * 16 + 15)

    /** Des images toutes les 16 ms, jusqu'à l'arrêt du coursier. */
    private fun VillageSceneState.runUntilStill(from: Long = 0, limit: Int = 2_000): Long {
        var t = from
        onFrame(t)
        repeat(limit) {
            if (destination == null && action != CourierAction.WALK) return t
            t += 16
            onFrame(t)
        }
        error("le coursier marche encore")
    }

    @Test
    fun `the courier glides from tile to tile, the camera with him`() {
        val state = VillageSceneState(village)
        assertEquals(spawnFoot, state.foot)
        assertEquals(ArtPoint(spawnFoot.x, spawnFoot.y - 7), state.cameraFocus)
        state.send(CourierCommand.Move(Direction.LEFT, pressed = true))
        state.onFrame(1_000)
        state.onFrame(1_000 + STEP_MILLIS / 4L)
        assertEquals(ArtPoint(spawnFoot.x - 4, spawnFoot.y), state.foot, "un quart de pas : 4 pixels")
        assertEquals(state.foot.x, state.cameraFocus.x)
        assertEquals(CourierAction.WALK, state.action)
        assertEquals(Direction.LEFT, state.facing)
        state.send(CourierCommand.Move(Direction.LEFT, pressed = false))
        state.onFrame(1_100)
        state.onFrame(1_000 + STEP_MILLIS.toLong())
        assertEquals(ArtPoint(spawnFoot.x - 16, spawnFoot.y), state.foot)
        assertEquals(CourierAction.IDLE, state.action)
    }

    @Test
    fun `a long pause between two frames does not teleport the courier`() {
        val state = VillageSceneState(village)
        state.send(CourierCommand.Move(Direction.LEFT, pressed = true))
        state.onFrame(0)
        state.onFrame(60_000)
        assertEquals(spawnFoot.x - 16 * VillageSceneState.MAX_FRAME_MILLIS.toInt() / STEP_MILLIS, state.foot.x)
    }

    @Test
    fun `an auto walk ends at the door, the scene enters, then ignores input until leaving`() {
        val house = village.houses.first()
        val state = VillageSceneState(village)
        state.send(CourierCommand.GoTo(house.session.id, enter = false))
        assertEquals(house.session.id, state.destination)
        var t = state.runUntilStill()
        assertEquals(ArtPoint(house.entry.x * 16 + 8, house.entry.y * 16 + 15), state.foot)
        assertEquals(Direction.UP, state.facing)
        assertEquals(house.session.id, state.facingDoor, "devant la porte : A entre")
        assertNull(state.entering)

        state.send(CourierCommand.Interact)
        assertEquals(house.session.id, state.entering)
        state.send(CourierCommand.Move(Direction.DOWN, pressed = true))
        t = state.runUntilStill(t)
        assertEquals(ArtPoint(house.entry.x * 16 + 8, house.entry.y * 16 + 15), state.foot, "pendant l'iris, le coursier ne bouge pas")
        state.send(CourierCommand.Move(Direction.DOWN, pressed = false))

        state.leaveHouse()
        assertNull(state.entering)
        assertTrue(state.leaving)
        assertEquals(Direction.DOWN, state.facing)
        assertNull(state.facingDoor)
        state.irisOpened()
        assertFalse(state.leaving)
        state.send(CourierCommand.Move(Direction.DOWN, pressed = true))
        state.send(CourierCommand.Move(Direction.DOWN, pressed = false))
        state.runUntilStill(t)
        assertEquals(house.entry.y * 16 + 31, state.foot.y)
    }

    @Test
    fun `a regenerated village keeps the courier at his place by the plaza`() {
        val state = VillageSceneState(village)
        state.send(CourierCommand.Move(Direction.LEFT, pressed = true))
        state.send(CourierCommand.Move(Direction.LEFT, pressed = false))
        state.runUntilStill()
        val relative = (state.foot.x - village.origin.x * 16) to (state.foot.y - village.origin.y * 16)
        val grown = generator.generate(Overview.of(DemoScene.sessions(8), emptyList(), BridgeStatus.Connected))
        state.updateVillage(grown)
        assertEquals(grown, state.village)
        assertEquals(relative, (state.foot.x - grown.origin.x * 16) to (state.foot.y - grown.origin.y * 16))
    }
}
