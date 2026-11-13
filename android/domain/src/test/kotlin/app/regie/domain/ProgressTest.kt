package app.regie.domain

import app.regie.domain.model.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProgressTest {

    @Test
    fun `the step shown is the one in progress`() {
        assertEquals(3, Progress(done = 2, total = 5, currentStep = "lance les tests").step)
    }

    @Test
    fun `without a step in progress, the step shown is the last one done`() {
        assertEquals(2, Progress(done = 2, total = 5).step)
        assertEquals(0, Progress(done = 0, total = 5).step)
        assertEquals(5, Progress(done = 5, total = 5).step)
    }

    @Test
    fun `a count outside the plan is refused`() {
        assertFailsWith<IllegalArgumentException> { Progress(done = 6, total = 5) }
        assertFailsWith<IllegalArgumentException> { Progress(done = -1, total = 5) }
        assertFailsWith<IllegalArgumentException> { Progress(done = 0, total = 0) }
    }
}
