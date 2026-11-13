package app.regie.overview

import app.regie.domain.fake.FakeAgentGateway
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionEvent
import app.regie.domain.model.SessionId
import app.regie.input.Button
import app.regie.input.GamepadInput
import app.regie.input.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverviewControllerTest {

    private val mockup = FakeAgentGateway.MOCKUP.fold(Overview.EMPTY) { overview, event -> overview.apply(event) }
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
        overview.value = mockup.apply(SessionEvent.SessionUpdated(mockup.sessions[1].copy(pendingApproval = null)))

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
        overview.value = mockup.apply(SessionEvent.SessionRemoved(SessionId("thor-dev")))

        assertEquals(2, controller.state.value.focusIn(overview.value.sessions.size))
        press(Button.A)
        assertEquals(listOf<OverviewAction>(OverviewAction.OpenSession(SessionId("montage"))), actions)
    }

    @Test
    fun `the focus stays on the four visible cards when there are more sessions`() {
        val extra = (1..2).map { mockup.sessions[2].copy(id = SessionId("extra-$it"), pendingApproval = null) }
        overview.value = extra.fold(mockup.apply(SessionEvent.SessionUpdated(mockup.sessions[1].copy(pendingApproval = null)))) { o, s ->
            o.apply(SessionEvent.SessionUpdated(s))
        }
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
}
