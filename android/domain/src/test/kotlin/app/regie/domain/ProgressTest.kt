package app.regie.domain

import app.regie.domain.model.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProgressTest {

    @Test
    fun `a plan with no step started yet is accepted`() {
        assertEquals(0, Progress(step = 0, total = 5).step)
    }

    @Test
    fun `a step outside the plan is refused`() {
        assertFailsWith<IllegalArgumentException> { Progress(step = 6, total = 5) }
        assertFailsWith<IllegalArgumentException> { Progress(step = -1, total = 5) }
        assertFailsWith<IllegalArgumentException> { Progress(step = 0, total = 0) }
    }
}
