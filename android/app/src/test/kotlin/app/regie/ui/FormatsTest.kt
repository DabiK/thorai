package app.regie.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FormatsTest {

    @Test
    fun `the age uses the largest whole unit`() {
        assertEquals("12 s", formatAge(12.seconds))
        assertEquals("40 s", formatAge(40.seconds))
        assertEquals("6 min", formatAge(6.minutes + 30.seconds))
        assertEquals("2 h", formatAge(2.hours + 59.minutes))
        assertEquals("3 j", formatAge(3.days + 1.hours))
    }

    @Test
    fun `a negative age is shown as zero seconds`() {
        assertEquals("0 s", formatAge((-5).seconds))
    }
}
