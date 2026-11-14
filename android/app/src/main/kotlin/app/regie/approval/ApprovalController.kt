package app.regie.approval

import app.regie.domain.model.ApprovalDecision
import app.regie.input.Button
import app.regie.input.GamepadInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ApprovalController(
    private val answer: (ApprovalDecision) -> Unit,
    private val next: () -> Unit,
    private val back: () -> Unit,
) {
    val scrollSteps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val scrollVelocity = MutableStateFlow(0f)

    fun handle(input: GamepadInput) {
        when (input) {
            is GamepadInput.Pressed -> when (input.button) {
                Button.A -> answer(ApprovalDecision.ONCE)
                Button.X -> answer(ApprovalDecision.SESSION)
                Button.B -> answer(ApprovalDecision.REJECT)
                Button.R1 -> next()
                Button.L1 -> back()
                Button.UP -> scrollSteps.tryEmit(-1)
                Button.DOWN -> scrollSteps.tryEmit(1)
                else -> Unit
            }
            is GamepadInput.Scroll -> scrollVelocity.value = input.velocity
            else -> Unit // La dictée du refus sera branchée par le ticket 08.
        }
    }
}
