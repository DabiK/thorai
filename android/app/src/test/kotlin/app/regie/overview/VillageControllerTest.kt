package app.regie.overview

import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.village.HouseCatalog
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.village.courierTile
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** La vue d'ensemble en hameau : régénération et manette du coursier (ticket 45). */
class VillageControllerTest {

    private val t0 = Instant.parse("2026-11-18T21:00:00Z")

    private fun session(id: String, project: String, status: SessionStatus, minutesAgo: Int, approval: Approval? = null, agent: Agent = Agent.CODEX) =
        Session(
            id = SessionId(id),
            agent = agent,
            project = project,
            branch = null,
            title = "tâche $id",
            status = status,
            active = true,
            updatedAt = t0 - minutesAgo.minutes,
            lastActivityAt = t0 - minutesAgo.minutes,
            pendingApproval = approval,
        )

    private val approval = Approval(ApprovalId("approval-notes"), SessionId("notes"), ApprovalKind.COMMAND, "npm test")

    private val sessions = listOf(
        session("atelier", "regie", SessionStatus.WORKING, minutesAgo = 1, agent = Agent.CLAUDE),
        session("notes", "api-meteo", SessionStatus.AWAITING_APPROVAL, minutesAgo = 2, approval),
        session("montage", "site-perso", SessionStatus.IDLE, minutesAgo = 3),
    )

    private val overview = MutableStateFlow(Overview.of(sessions, emptyList()))
    private val actions = mutableListOf<OverviewAction>()
    private val classic = OverviewController(overview) { actions += it }
    private val store = InMemoryVillageLayoutStore()
    private var shown = true
    private val opened = mutableListOf<SessionId>()
    private val village = VillageController(overview, classic, store, { HouseCatalog.DEFAULT }, { shown }, { opened += it }, Dispatchers.Unconfined)

    private var time = 0L

    private val scene: VillageSceneState get() = assertNotNull(village.scene.value, "le hameau est généré")

    private fun publish(next: Overview) {
        overview.value = next
        classic.syncFocus()
        runBlocking { village.update(next) }
    }

    private fun press(button: Button) = village.handle(GamepadInput.Pressed(button))

    /** Des images toutes les 16 ms jusqu'à ce que [done] soit vrai. */
    private fun runUntil(limit: Int = 3_000, done: () -> Boolean) {
        scene.onFrame(time)
        repeat(limit) {
            if (done()) return
            time += 16
            scene.onFrame(time)
        }
        error("toujours en route")
    }

    private fun runUntilStill() = runUntil { scene.destination == null && scene.action != CourierAction.WALK }

    @Test
    fun `the village is generated from the overview, one house per session, and its layout is kept`() {
        publish(overview.value)

        assertEquals(sessions.map { it.id }.toSet(), scene.village.houses.map { it.session.id }.toSet())
        assertEquals(1, store.saveCount)
        assertEquals(sessions.map { it.id }.toSet(), store.layout.houses.keys)
    }

    @Test
    fun `an overview that changes no house keeps the village, a new session regenerates it around the courier`() {
        publish(overview.value)
        val first = scene.village
        publish(Overview.of(sessions.map { it.copy(lastActivityAt = t0) }, emptyList()))
        assertSame(first, scene.village, "rien de visible n'a changé")

        val sceneBefore = scene
        val doorsBefore = first.houses.associate { it.session.id to it.door.offset(-first.origin.x, -first.origin.y) }
        publish(Overview.of(sessions + session("cache", "api-meteo", SessionStatus.WORKING, minutesAgo = 0), emptyList()))

        assertSame(sceneBefore, scene, "le coursier est le même, il garde sa place")
        assertNotEquals(first, scene.village)
        assertEquals(4, scene.village.houses.size)
        val now = scene.village
        doorsBefore.forEach { (id, door) ->
            assertEquals(door, now.houseOf(id)!!.door.offset(-now.origin.x, -now.origin.y), "la maison de $id n'a pas bougé")
        }
    }

    @Test
    fun `a reloaded layout puts every house back in its place`() {
        publish(overview.value)
        val doors = scene.village.houses.associate { it.session.id to it.door }
        val again = VillageController(overview, OverviewController(overview) {}, store, { HouseCatalog.DEFAULT }, { true }, {}, Dispatchers.Unconfined)
        runBlocking { again.update(Overview.of(sessions.reversed(), emptyList())) }

        assertEquals(doors, again.scene.value!!.village.houses.associate { it.session.id to it.door })
    }

    @Test
    fun `a held direction walks the courier, a repeated press does nothing`() {
        publish(overview.value)
        val start = scene.foot
        press(Button.RIGHT)
        press(Button.RIGHT)
        runUntilStill()
        assertEquals(start, scene.foot, "les appuis ne font pas marcher")

        village.onDirection(Button.LEFT, held = true, free = true)
        time += 16 * 20
        scene.onFrame(time)
        village.onDirection(Button.LEFT, held = false, free = true)
        runUntilStill()
        assertTrue(scene.foot.x < start.x, "le coursier est allé à gauche")
        assertEquals(start.y, scene.foot.y)
        assertEquals(emptyList(), actions)
    }

    @Test
    fun `a direction pressed while something covers the overview is ignored, its release still passes`() {
        publish(overview.value)
        val start = scene.foot
        village.onDirection(Button.LEFT, held = true, free = false)
        runUntilStill()
        assertEquals(start, scene.foot)

        village.onDirection(Button.LEFT, held = true, free = true)
        time += 16
        scene.onFrame(time)
        village.onDirection(Button.LEFT, held = false, free = false)
        runUntilStill()
        val stopped = scene.foot
        time += 1_000
        scene.onFrame(time)
        assertEquals(stopped, scene.foot, "relâchée, la direction n'est plus tenue")
    }

    @Test
    fun `R1 walks first to the pending approval and enters that house`() {
        publish(overview.value)
        press(Button.R1)
        assertEquals(SessionId("notes"), scene.destination)
        runUntil { scene.entering != null }

        assertEquals(SessionId("notes"), scene.entering)
        assertEquals(scene.village.houseOf(SessionId("notes"))!!.entry, scene.courierTile)
        assertEquals(emptyList(), actions, "la session s'ouvre après l'iris, par l'écran")
    }

    @Test
    fun `back from the waiting house without answering, R1 goes back in instead of moving on`() {
        publish(overview.value)
        press(Button.R1)
        runUntil { scene.entering != null }
        assertEquals(SessionId("notes"), scene.entering)

        village.leaveHouse()
        assertEquals(scene.village.houseOf(SessionId("notes"))!!.entry, scene.courierTile, "ressorti devant la porte")
        press(Button.R1)

        assertEquals(SessionId("notes"), scene.entering, "la validation attend toujours : R1 y rentre")
    }

    @Test
    fun `with two pending approvals, R1 takes the overview's first one, even when the village is older`() {
        val other = Approval(ApprovalId("approval-montage"), SessionId("montage"), ApprovalKind.FILE_CHANGE, "README.md")
        val two = sessions.map { if (it.id == SessionId("montage")) it.copy(status = SessionStatus.AWAITING_APPROVAL, pendingApproval = other) else it }
        publish(Overview.of(two, emptyList()))
        assertEquals(SessionId("notes"), overview.value.firstPendingApproval?.session?.id)
        // Montage change plus tard : elle passe devant, sans rien changer aux maisons.
        val village = scene.village
        publish(Overview.of(two.map { if (it.id == SessionId("montage")) it.copy(updatedAt = t0) else it }, emptyList()))
        assertSame(village, scene.village)
        assertEquals(SessionId("montage"), overview.value.firstPendingApproval?.session?.id)

        press(Button.R1)

        assertEquals(SessionId("montage"), scene.destination, "la même validation que le bandeau du carnet")
    }

    @Test
    fun `L1 stops in front of a door, then A enters`() {
        publish(Overview.of(sessions.map { it.copy(pendingApproval = null, status = SessionStatus.IDLE) }, emptyList()))
        press(Button.L1)
        runUntilStill()
        val door = assertNotNull(scene.facingDoor, "devant une porte")
        assertNull(scene.entering)

        press(Button.A)
        assertEquals(door, scene.entering)
    }

    @Test
    fun `A away from any door walks to the chosen house and enters it`() {
        publish(overview.value)
        val nearest = scene.village.houses.minBy { house ->
            val tile = scene.courierTile
            kotlin.math.abs(house.entry.x - tile.x) + kotlin.math.abs(house.entry.y - tile.y)
        }.session.id
        press(Button.A)
        assertEquals(nearest, scene.destination)
        runUntil { scene.entering != null }
        assertEquals(nearest, scene.entering)
    }

    @Test
    fun `the focus follows the courier, so L2 dictates to the house he stands before`() {
        publish(Overview.of(sessions.map { it.copy(pendingApproval = null, status = SessionStatus.IDLE) }, emptyList()))
        press(Button.R1)
        runUntilStill()
        val house = assertNotNull(scene.facingDoor)

        village.handle(GamepadInput.TriggerDown(Trigger.L2))

        assertEquals(listOf<OverviewAction>(OverviewAction.StartDictation(house)), actions)
        assertEquals(house, classic.state.value.focusedId)
    }

    @Test
    fun `a tapped row sends the courier to that house`() {
        publish(overview.value)
        village.goTo(SessionId("montage"))
        assertEquals(SessionId("montage"), scene.destination)
        runUntil { scene.entering != null }
        assertEquals(SessionId("montage"), scene.entering)
        assertEquals(emptyList(), opened)
    }

    @Test
    fun `X and Y keep their overview actions`() {
        publish(overview.value)
        press(Button.X)
        press(Button.Y)
        assertEquals(listOf(OverviewAction.NewSession, OverviewAction.ConsoleSession), actions)
    }

    @Test
    fun `walking to a house answers whether the courier could go`() {
        publish(overview.value)
        assertTrue(village.walkTo(SessionId("notes")))
        assertEquals(SessionId("notes"), scene.destination)
        assertEquals(false, village.walkTo(SessionId("absente")))
        shown = false
        assertEquals(false, village.walkTo(SessionId("montage")))
    }

    @Test
    fun `without the top screen, the classic overview answers and a tapped row opens directly`() {
        publish(overview.value)
        shown = false
        val start = scene.foot

        press(Button.A)
        village.goTo(SessionId("montage"))
        village.onDirection(Button.LEFT, held = true, free = true)
        runUntilStill()

        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(sessions.first().id)), actions)
        assertEquals(listOf(SessionId("montage")), opened)
        assertEquals(start, scene.foot)
    }

    @Test
    fun `before the first village, the classic overview answers`() {
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(sessions.first().id)), actions)
    }

    @Test
    fun `while quitting, A confirms instead of moving the courier`() {
        publish(overview.value)
        classic.requestQuit()
        press(Button.A)

        assertNull(scene.destination)
        assertEquals(listOf<OverviewAction>(OverviewAction.Quit), actions)
    }

    @Test
    fun `back from a house, the courier stands at its door`() {
        publish(overview.value)
        village.goTo(SessionId("montage"))
        runUntil { scene.entering != null }

        village.leaveHouse()

        assertNull(scene.entering)
        assertTrue(scene.leaving)
        assertEquals(scene.village.houseOf(SessionId("montage"))!!.entry, scene.courierTile)
        press(Button.L1)
        assertNotNull(scene.destination, "il répond de nouveau")
    }

    @Test
    fun `the courier talks while the dictation is open`() {
        publish(overview.value)
        village.talk(true)
        assertEquals(CourierAction.TALK, scene.action)
        village.talk(false)
        assertEquals(CourierAction.IDLE, scene.action)
    }
}
