package app.regie.game.engine.courier

import app.regie.domain.model.ApprovalId
import app.regie.domain.model.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CourierTest {
    private fun letter(key: String, kind: LetterKind = LetterKind.FINISHED, at: Long = 0, approval: String? = null) =
        Letter(key, kind, SessionId("s-$key"), approval?.let(::ApprovalId), "Titre $key", "projet · $key", Instant.fromEpochMilliseconds(at))

    @Test
    fun `the newest letter comes first and is the one the bird brings`() {
        val courier = Courier().receive(letter("a", at = 1)).receive(letter("b", at = 2))
        assertEquals(listOf("b", "a"), courier.letters.map { it.key })
        assertEquals("b", courier.latest?.key)
        assertEquals(2, courier.unread)
    }

    @Test
    fun `the same event never makes two letters`() {
        val courier = Courier().receive(letter("a", at = 1)).receive(letter("b", at = 2)).receive(letter("a", at = 3))
        assertEquals(listOf("a", "b"), courier.letters.map { it.key })
    }

    @Test
    fun `the courier keeps only the recent letters`() {
        val courier = (1..Courier.MAX_LETTERS + 5).fold(Courier()) { c, i -> c.receive(letter("$i", at = i.toLong())) }
        assertEquals(Courier.MAX_LETTERS, courier.letters.size)
        assertEquals("${Courier.MAX_LETTERS + 5}", courier.letters.first().key)
    }

    @Test
    fun `marking all read keeps the letters`() {
        val courier = Courier().receive(letter("a")).receive(letter("b")).markAllRead()
        assertEquals(0, courier.unread)
        assertEquals(2, courier.letters.size)
    }

    @Test
    fun `a validation answered elsewhere is read, and opening a letter reads it`() {
        val courier = Courier().receive(letter("a", LetterKind.APPROVAL, approval = "p1")).receive(letter("b"))
        val settled = courier.settle(ApprovalId("p1"))
        assertTrue(settled.letters.single { it.key == "a" }.read)
        assertEquals(1, settled.unread)
        assertTrue(courier.read("b").letters.single { it.key == "b" }.read)
    }

    @Test
    fun `an empty courier has nothing to bring`() {
        assertNull(Courier().latest)
        assertEquals(0, Courier().unread)
    }

    @Test
    fun `the age is short and rounded down`() {
        val now = Instant.fromEpochMilliseconds(3 * 3_600_000L)
        fun ago(ms: Long) = Courier.age(Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - ms), now)
        assertEquals(LetterAge(LetterAge.Unit.NOW, 0), ago(20_000))
        assertEquals(LetterAge(LetterAge.Unit.MINUTES, 5), ago(5 * 60_000L + 10_000))
        assertEquals(LetterAge(LetterAge.Unit.HOURS, 2), ago(2 * 3_600_000L + 60_000))
        assertEquals(LetterAge(LetterAge.Unit.DAYS, 3), ago(3 * 86_400_000L))
        assertEquals(LetterAge(LetterAge.Unit.NOW, 0), ago(-5_000))
    }
}
