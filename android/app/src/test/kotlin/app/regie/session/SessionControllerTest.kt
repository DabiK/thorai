package app.regie.session

import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Delivery
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Overview
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.SessionThread
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SessionControllerTest {

    private val t0 = Instant.parse("2026-11-13T21:00:00Z")

    private fun session(id: String, status: SessionStatus, minutesAgo: Int) = Session(
        id = SessionId(id),
        agent = Agent.CLAUDE,
        project = id,
        branch = null,
        title = null,
        status = status,
        active = true,
        updatedAt = t0 - minutesAgo.minutes,
        lastActivityAt = t0 - minutesAgo.minutes,
    )

    /** Cinq sessions, dans l'ordre de la vue d'ensemble : la cinquième n'est pas dans les cartes. */
    private val sessions = listOf(
        session("atelier", SessionStatus.WORKING, 1),
        session("atelier-meubles", SessionStatus.AWAITING_APPROVAL, 2),
        session("montage", SessionStatus.IDLE, 3),
        session("thor-dev", SessionStatus.STOPPED, 4),
        session("ancienne", SessionStatus.STOPPED, 5),
    )

    private val overview = MutableStateFlow(Overview.of(sessions, emptyList()))
    private var current: SessionId? = SessionId("atelier")
    private val live = MutableStateFlow<LiveSession?>(null)
    private val actions = mutableListOf<SessionAction>()
    private val controller = SessionController(live, overview, { current }) { actions += it }

    private fun press(button: Button) = controller.handle(GamepadInput.Pressed(button))

    private fun show(id: String) {
        current = SessionId(id)
        live.value = LiveSession.of(sessions.single { it.id.value == id }, SessionThread.EMPTY)
    }

    @Test
    fun `a late title update does not reopen a dismissed report but a new turn does`() {
        show("montage")
        press(Button.B)
        val previous = live.value!!
        live.value = previous.copy(session = previous.session.copy(title = "Titre tardif", updatedAt = t0))
        kotlin.test.assertFalse(controller.state.value.showsSummary(live.value!!))
        live.value = LiveSession.of(previous.session, SessionThread(listOf(app.regie.domain.model.ThreadEntry.UserMessage(4, "Nouveau tour")), loaded = true))
        assertTrue(controller.state.value.showsSummary(live.value!!))
    }

    @Test
    fun `completion opens a short report automatically and B dismisses it before returning`() {
        show("montage")
        assertTrue(controller.state.value.showsSummary(live.value!!))
        press(Button.B)
        kotlin.test.assertFalse(controller.state.value.showsSummary(live.value!!))
        assertTrue(actions.isEmpty())
        press(Button.Y)
        assertTrue(controller.state.value.showsSummary(live.value!!))
        press(Button.B)
        press(Button.B)
        assertEquals(SessionAction.Back, actions.last())
    }

    @Test
    fun `R1 and L1 open the next and previous sessions in the overview order, without wrapping`() {
        show("atelier")
        press(Button.L1)
        press(Button.R1)
        show("thor-dev")
        press(Button.R1)
        show("ancienne")
        press(Button.R1)

        assertEquals(
            listOf<SessionAction>(SessionAction.Open(SessionId("atelier-meubles")), SessionAction.Open(SessionId("ancienne"))),
            actions,
        )
    }

    @Test
    fun `R1 opens a waiting approval from another session before moving to a neighbour`() {
        val approval = Approval(ApprovalId("a"), SessionId("montage"), ApprovalKind.COMMAND, "rm x")
        overview.value = Overview.of(sessions.map { if (it.id.value == "montage") it.copy(pendingApproval = approval) else it }, emptyList())
        show("atelier")
        assertEquals(approval, overview.value.firstPendingApproval?.approval)

        press(Button.R1)

        assertEquals(listOf<SessionAction>(SessionAction.OpenApproval(approval.id)), actions)
    }

    @Test
    fun `B goes back to the overview when no summary is open`() {
        show("atelier")
        press(Button.B)

        assertEquals(listOf<SessionAction>(SessionAction.Back), actions)
    }

    @Test
    fun `Start interrupts a session at work, and does nothing on an idle one`() {
        show("montage")
        press(Button.START)
        assertTrue(actions.isEmpty())

        show("atelier-meubles")
        press(Button.START)
        assertEquals(listOf<SessionAction>(SessionAction.Interrupt(SessionId("atelier-meubles"))), actions)
        assertEquals(SessionNotice.Interrupting, controller.state.value.notice)

        controller.onInterrupted(SessionId("atelier-meubles"), heard = true)
        assertEquals(SessionNotice.Interrupted(heard = true), controller.state.value.notice)
    }

    @Test
    fun `nothing leaves without A, then A sends the typed text and shows how it left`() {
        show("atelier")
        controller.onDraftChange("  Arrête-toi après le premier fichier.  ")
        assertTrue(actions.isEmpty())

        press(Button.A)

        assertEquals(listOf<SessionAction>(SessionAction.Send(SessionId("atelier"), "Arrête-toi après le premier fichier.")), actions)
        assertEquals("", controller.draft.value)
        assertEquals(SessionNotice.Sending, controller.state.value.notice)
        controller.onDelivered(SessionId("atelier"), "Arrête-toi après le premier fichier.", Delivery.STEERING)
        assertEquals(SessionNotice.Delivered(Delivery.STEERING), controller.state.value.notice)
    }

    @Test
    fun `A with nothing typed sends nothing, and a message that did not leave comes back`() {
        show("atelier")
        press(Button.A)
        assertTrue(actions.isEmpty())

        controller.onDraftChange("Relance les tests")
        controller.send()
        controller.onDelivered(SessionId("atelier"), "Relance les tests", Delivery.NOT_SENT)

        assertEquals("Relance les tests", controller.draft.value)
        assertEquals(SessionNotice.Delivered(Delivery.NOT_SENT), controller.state.value.notice)
    }

    @Test
    fun `an unknown delivery leaves the draft empty and a second A cannot duplicate it`() {
        show("atelier")
        controller.onDraftChange("Relance les tests")
        press(Button.A)
        controller.onDelivered(SessionId("atelier"), "Relance les tests", Delivery.UNKNOWN)
        assertEquals("", controller.draft.value)
        assertEquals(SessionNotice.Delivered(Delivery.UNKNOWN), controller.state.value.notice)
        press(Button.A)
        assertEquals(listOf<SessionAction>(SessionAction.Send(SessionId("atelier"), "Relance les tests")), actions)
    }

    @Test
    fun `a message cancelled before delivery comes back for retry`() {
        show("atelier")
        controller.onDraftChange("Relance les tests")
        controller.send()
        controller.onDelivered(SessionId("atelier"), "Relance les tests", Delivery.CANCELLED)

        assertEquals("Relance les tests", controller.draft.value)
        assertEquals(SessionNotice.Delivered(Delivery.CANCELLED), controller.state.value.notice)
    }

    @Test
    fun `Y opens the written summary and asks for the spoken one, Y or B closes it`() {
        show("atelier")
        press(Button.Y)
        assertTrue(controller.state.value.summaryOpen)
        press(Button.Y)
        assertEquals(false, controller.state.value.summaryOpen)
        press(Button.Y)
        press(Button.B)

        assertEquals(false, controller.state.value.summaryOpen)
        assertEquals(
            listOf<SessionAction>(SessionAction.ShowSummary(SessionId("atelier")), SessionAction.ShowSummary(SessionId("atelier"))),
            actions,
        )
    }

    @Test
    fun `X says the diff cannot be seen yet`() {
        show("atelier")
        press(Button.X)

        assertEquals(SessionNotice.DiffUnavailable, controller.state.value.notice)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `the d-pad and the right stick scroll the thread`() = runTest {
        show("atelier")
        val steps = async(start = CoroutineStart.UNDISPATCHED) { controller.scrollSteps.take(3).toList() }

        press(Button.UP)
        press(Button.UP)
        press(Button.DOWN)
        controller.handle(GamepadInput.Scroll(-0.8f))

        assertEquals(listOf(-1, -1, 1), steps.await())
        assertEquals(-0.8f, controller.scrollVelocity.value)
        controller.handle(GamepadInput.Scroll(0f))
        assertEquals(0f, controller.scrollVelocity.value)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `L2 held dictates to the session shown, and a screen change releases it once`() {
        show("montage")
        controller.handle(GamepadInput.TriggerDown(Trigger.L2))
        controller.releaseTriggers()
        controller.handle(GamepadInput.TriggerUp(Trigger.L2))

        assertEquals(listOf(SessionAction.StartDictation(SessionId("montage")), SessionAction.StopDictation), actions)
    }

    @Test
    fun `opening another session forgets the draft, the notice and the summary`() {
        show("atelier")
        controller.onDraftChange("brouillon")
        press(Button.X)
        press(Button.Y)

        controller.reset()

        assertEquals("", controller.draft.value)
        assertEquals(SessionControlState(), controller.state.value)
    }

    @Test
    fun `a delivery for a session no longer shown is not reported`() {
        show("atelier")
        controller.onDraftChange("x")
        controller.send()
        show("montage")
        controller.reset()

        controller.onDelivered(SessionId("atelier"), "x", Delivery.NOT_SENT)

        assertEquals(null, controller.state.value.notice)
        assertEquals("", controller.draft.value)
    }
    @Test
    fun `R1 opens pending approval before moving to a neighbour`() {
        val request = Approval(ApprovalId("a"), sessions.first().id, ApprovalKind.COMMAND, "touch x")
        overview.value = Overview.of(listOf(sessions.first().copy(pendingApproval = request)), emptyList())
        press(Button.R1)
        assertEquals(listOf<SessionAction>(SessionAction.OpenApproval(request.id)), actions)
    }

}
