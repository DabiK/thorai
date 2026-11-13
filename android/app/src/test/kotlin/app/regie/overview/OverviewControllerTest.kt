package app.regie.overview

import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OverviewControllerTest {

    private val t0 = Instant.parse("2026-11-13T21:00:00Z")

    private fun session(id: String, status: SessionStatus, minutesAgo: Int, approval: Approval? = null) = Session(
        id = SessionId(id),
        agent = Agent.CODEX,
        project = id,
        branch = null,
        title = "tâche $id",
        status = status,
        active = true,
        updatedAt = t0 - minutesAgo.minutes,
        lastActivityAt = t0 - minutesAgo.minutes,
        pendingApproval = approval,
    )

    private val approval = Approval(ApprovalId("approval-atelier-meubles-1"), SessionId("atelier-meubles"), ApprovalKind.COMMAND, "pip install open_clip_torch")

    /** Quatre sessions, dans l'ordre de la vue d'ensemble (la plus récemment changée d'abord). */
    private val sessions = listOf(
        session("atelier", SessionStatus.WORKING, minutesAgo = 1),
        session("atelier-meubles", SessionStatus.AWAITING_APPROVAL, minutesAgo = 2, approval),
        session("montage", SessionStatus.IDLE, minutesAgo = 3),
        session("thor-dev", SessionStatus.STOPPED, minutesAgo = 4),
    )

    private fun overviewOf(list: List<Session>) = Overview.of(list, emptyList())

    private val mockup = overviewOf(sessions)
    private val overview = MutableStateFlow(mockup)
    private val actions = mutableListOf<OverviewAction>()
    private val controller = OverviewController(overview) { actions += it }

    private fun press(button: Button) = controller.handle(GamepadInput.Pressed(button))
    private val focus get() = controller.state.value.focusedIndex

    @Test
    fun `the first card has the focus at start`() {
        assertEquals(0, focus)
    }

    @Test
    fun `the d-pad moves the focus in the grid`() {
        press(Button.RIGHT)
        assertEquals(1, focus)
        press(Button.DOWN)
        assertEquals(3, focus)
        press(Button.LEFT)
        assertEquals(2, focus)
        press(Button.UP)
        assertEquals(0, focus)
    }

    @Test
    fun `holding L2 starts a dictation for the focused session and releasing it stops`() {
        press(Button.RIGHT)
        controller.handle(GamepadInput.TriggerDown(Trigger.L2))
        assertEquals(setOf(Trigger.L2), controller.state.value.heldTriggers)

        controller.handle(GamepadInput.TriggerUp(Trigger.L2))
        assertEquals(emptySet(), controller.state.value.heldTriggers)
        assertEquals(
            listOf(OverviewAction.StartDictation(SessionId("atelier-meubles")), OverviewAction.StopDictation),
            actions,
        )
    }

    @Test
    fun `holding R2 starts a voice command and releasing it stops`() {
        controller.handle(GamepadInput.TriggerDown(Trigger.R2))
        assertEquals(setOf(Trigger.R2), controller.state.value.heldTriggers)
        controller.handle(GamepadInput.TriggerUp(Trigger.R2))

        assertEquals(listOf(OverviewAction.StartVoiceCommand, OverviewAction.StopVoiceCommand), actions)
    }

    @Test
    fun `A opens the focused session`() {
        press(Button.DOWN)
        press(Button.A)

        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("montage"))), actions)
    }

    @Test
    fun `R1 opens the first pending approval`() {
        press(Button.R1)

        assertEquals(listOf<OverviewAction>(OverviewAction.OpenApproval(ApprovalId("approval-atelier-meubles-1"))), actions)
        assertEquals(0, focus)
    }

    @Test
    fun `without a pending approval R1 goes to the next session and L1 to the previous one`() {
        overview.value = overviewOf(sessions.map { it.copy(pendingApproval = null) })

        press(Button.R1)
        press(Button.R1)
        assertEquals(2, focus)
        press(Button.L1)
        assertEquals(1, focus)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `X, Y and Start call the overview actions`() {
        press(Button.X)
        press(Button.Y)
        press(Button.START)

        assertEquals(
            listOf(OverviewAction.NewSession, OverviewAction.SpeakSummary(SessionId("atelier")), OverviewAction.PauseAll),
            actions,
        )
    }

    @Test
    fun `B does nothing on the overview, which is the first screen`() {
        press(Button.B)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `the focus follows the list when sessions disappear`() {
        press(Button.DOWN)
        press(Button.RIGHT)
        overview.value = overviewOf(sessions.filterNot { it.id == SessionId("thor-dev") })
        controller.syncFocus()

        assertEquals(2, focus)
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("montage"))), actions)
    }

    /** Ce que fait la racine de composition à chaque nouvelle vue d'ensemble. */
    private fun publish(list: List<Session>) {
        overview.value = overviewOf(list)
        controller.syncFocus()
    }

    @Test
    fun `the focus follows its session when the cards change order`() {
        controller.syncFocus()
        press(Button.RIGHT)
        assertEquals(SessionId("atelier-meubles"), controller.state.value.focusedId)

        // « montage » change de statut : elle passe en tête, les autres reculent d'une case.
        publish(sessions.map { if (it.id == SessionId("montage")) it.copy(updatedAt = t0) else it })

        assertEquals(2, focus)
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("atelier-meubles"))), actions)
    }

    @Test
    fun `the first card keeps its session from the start, before any move`() {
        controller.syncFocus()

        publish(sessions.map { if (it.id == SessionId("thor-dev")) it.copy(updatedAt = t0) else it })

        assertEquals(1, focus)
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("atelier"))), actions)
    }

    @Test
    fun `a session leaving the four cards shown leaves the focus on its square`() {
        controller.syncFocus()
        press(Button.DOWN)
        assertEquals(SessionId("montage"), controller.state.value.focusedId)

        // Deux sessions plus récentes repoussent « montage » à la cinquième place.
        val newer = (1..2).map { session("new-$it", SessionStatus.WORKING, minutesAgo = 0).copy(updatedAt = t0 + it.minutes) }
        publish(newer + sessions)

        assertEquals(2, focus)
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("atelier"))), actions)
    }

    @Test
    fun `the focus stays on the four visible cards when there are more sessions`() {
        val extra = (1..2).map { session("extra-$it", SessionStatus.IDLE, minutesAgo = 10 + it) }
        overview.value = overviewOf(sessions.map { it.copy(pendingApproval = null) } + extra)
        assertEquals(6, overview.value.sessions.size)

        press(Button.DOWN)
        press(Button.DOWN)
        assertEquals(2, focus)
        press(Button.RIGHT)
        press(Button.R1)
        assertEquals(3, focus)
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("thor-dev"))), actions)
    }

    @Test
    fun `quitting while sessions work asks for confirmation, and A confirms`() {
        controller.requestQuit()

        assertEquals(QuitStep.Confirm(busy = 2), controller.state.value.quit)
        assertTrue(actions.isEmpty())
        press(Button.RIGHT)
        assertEquals(0, focus)
        press(Button.A)
        assertEquals(QuitStep.Stopping, controller.state.value.quit)
        assertEquals(listOf<OverviewAction>(OverviewAction.Quit), actions)
    }

    @Test
    fun `B cancels the confirmation and the overview answers again`() {
        controller.requestQuit()
        press(Button.B)

        assertEquals(null, controller.state.value.quit)
        press(Button.X)
        assertEquals(listOf<OverviewAction>(OverviewAction.NewSession), actions)
    }

    @Test
    fun `the confirmation can be answered by touch`() {
        controller.requestQuit()
        controller.cancelQuit()
        assertEquals(null, controller.state.value.quit)

        controller.requestQuit()
        controller.confirmQuit()
        controller.confirmQuit()
        assertEquals(listOf<OverviewAction>(OverviewAction.Quit), actions)
    }

    @Test
    fun `quitting with no session at work needs no confirmation`() {
        overview.value = overviewOf(sessions.map { it.copy(status = SessionStatus.IDLE, pendingApproval = null) })

        controller.requestQuit()

        assertEquals(QuitStep.Stopping, controller.state.value.quit)
        assertEquals(listOf<OverviewAction>(OverviewAction.Quit), actions)
    }

    @Test
    fun `a trigger held when the confirmation opens is still released`() {
        controller.handle(GamepadInput.TriggerDown(Trigger.L2))
        controller.requestQuit()
        controller.handle(GamepadInput.TriggerUp(Trigger.L2))

        assertEquals(emptySet(), controller.state.value.heldTriggers)
        assertEquals(OverviewAction.StopDictation, actions.last())
    }
}
