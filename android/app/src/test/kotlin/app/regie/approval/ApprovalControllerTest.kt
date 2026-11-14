package app.regie.approval

import app.regie.domain.model.ApprovalDecision
import app.regie.input.Button
import app.regie.input.GamepadInput
import kotlin.test.Test
import kotlin.test.assertEquals

class ApprovalControllerTest {
    @Test
    fun `A X B answer and R1 moves only on explicit press`() {
        val decisions = mutableListOf<ApprovalDecision>()
        var next = 0
        var back = 0
        val controller = ApprovalController({ decisions += it }, { next++ }, { back++ })
        listOf(Button.A, Button.X, Button.B, Button.R1, Button.L1).forEach {
            controller.handle(GamepadInput.Pressed(it))
        }
        assertEquals(listOf(ApprovalDecision.ONCE, ApprovalDecision.SESSION, ApprovalDecision.REJECT), decisions)
        assertEquals(1, next)
        assertEquals(1, back)
    }
}
