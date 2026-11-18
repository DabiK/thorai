package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.port.*
import app.regie.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DictationReviewTest {
    @Test fun `release preserves final text for review and only A delivers to original target`() = runTest {
        val capture = TestCapture()
        val sent = mutableListOf<Pair<DictationTarget, String>>()
        val review = ReviewDictation(capture, { target, text ->
            sent += target to text
            DictationDelivery("Envoyé")
        }, backgroundScope)
        val target: DictationTarget = DictationTarget.Message(SessionId("one"))
        review.start(target, listOf("PassageCheck.ts"))
        capture.events.value = CaptureState(text = "Dans PassageCheck.ts", recording = true)
        runCurrent()
        review.finish()
        capture.events.value = CaptureState(text = "Dans PassageCheck.ts, ajoute un test.", complete = true)
        runCurrent()
        assertEquals("Dans PassageCheck.ts, ajoute un test.", review.state.value.text)
        assertTrue(sent.isEmpty())
        review.start(DictationTarget.Message(SessionId("other")), listOf("Other.kt"))
        review.send()
        review.send()
        runCurrent()
        assertEquals(listOf(target to "Dans PassageCheck.ts, ajoute un test."), sent)
        assertEquals(listOf("PassageCheck.ts"), capture.terms)
    }
    @Test fun `L2 released in silence sends nothing, closes and says so in one line`() = runTest {
        val capture = TestCapture()
        var deliveries = 0
        val heard = mutableListOf<DictationTarget>()
        val shownId = SessionId("one")
        val review = ReviewDictation(capture, { _, _ -> deliveries++; DictationDelivery("ok") }, backgroundScope,
            shown = { it == shownId }, onNothingHeard = { heard += it })
        review.start(DictationTarget.Message(shownId), emptyList())
        capture.events.value = CaptureState(recording = true)
        runCurrent()
        review.finish()
        capture.events.value = CaptureState(text = "", complete = true)
        runCurrent()
        assertFalse(review.state.value.visible, "the bottom goes back to the settings by itself")
        assertEquals(listOf<DictationTarget>(DictationTarget.Message(shownId)), heard)
        review.send(); runCurrent()
        assertEquals(0, deliveries)

        // Away from its session, the review stays with the line until B.
        review.start(DictationTarget.Message(SessionId("other")), emptyList())
        review.finish()
        capture.events.value = CaptureState(text = "  ", complete = true)
        runCurrent()
        assertEquals(DictationPhase.REVIEW, review.state.value.phase)
        assertEquals(ReviewDictation.NOTHING_HEARD, review.state.value.notice)
        review.send(); runCurrent()
        assertEquals(0, deliveries)
        review.discard()

        // A silent append keeps the text already reviewed.
        review.start(DictationTarget.Message(shownId), emptyList())
        capture.events.value = CaptureState(text = "Bonjour", complete = true)
        review.finish(); runCurrent()
        review.append(); review.record()
        capture.events.value = CaptureState(recording = true); runCurrent()
        review.finish()
        capture.events.value = CaptureState(text = "", complete = true); runCurrent()
        assertEquals("Bonjour", review.state.value.text)
        assertEquals(DictationPhase.REVIEW, review.state.value.phase)
        assertEquals(1, heard.size)
    }

    @Test fun `early final cannot be sent while L2 is still held`() = runTest {
        val capture = TestCapture()
        var deliveries = 0
        val review = ReviewDictation(capture, { _, _ -> deliveries++; DictationDelivery("ok") }, backgroundScope)
        review.start(DictationTarget.Message(SessionId("one")), emptyList())
        capture.events.value = CaptureState(text = "bonjour", complete = true)
        runCurrent()
        review.send(); runCurrent()
        assertEquals(0, deliveries)
        review.finish()
        review.send(); runCurrent()
        assertEquals(1, deliveries)
    }

    @Test fun `release consumes final result even before the collector resumes`() = runTest {
        val capture = TestCapture()
        val sent = mutableListOf<String>()
        val review = ReviewDictation(capture, { _, text -> sent += text; DictationDelivery("ok") }, backgroundScope)
        review.start(DictationTarget.Message(SessionId("one")), emptyList())
        capture.events.value = CaptureState(text = "Dans Passage", recording = true)
        runCurrent()
        capture.events.value = CaptureState(text = "Dans PassageCheck.ts", complete = true)
        review.finish()
        runCurrent()
        assertEquals("Dans PassageCheck.ts", review.state.value.text)
        review.send(); runCurrent()
        assertEquals(listOf("Dans PassageCheck.ts"), sent)
    }

    @Test fun `release consumes correction failure even before the collector resumes`() = runTest {
        val capture = TestCapture()
        val review = ReviewDictation(capture, { _, _ -> DictationDelivery("ok") }, backgroundScope)
        review.start(DictationTarget.Message(SessionId("one")), emptyList())
        capture.events.value = CaptureState(text = "Bonjour", complete = true)
        runCurrent(); review.finish()
        review.correct(); review.record()
        capture.events.value = CaptureState(text = "partiel erroné", recording = true)
        runCurrent()
        capture.events.value = CaptureState(error = "Hors ligne")
        review.finish()
        runCurrent()
        assertEquals("Bonjour", review.state.value.text)
        assertEquals("Hors ligne", review.state.value.notice)
        assertEquals(DictationPhase.REVIEW, review.state.value.phase)
    }

    @Test fun `Y appends and X replaces only the word chosen with the dpad`() = runTest {
        val capture = TestCapture()
        val review = ReviewDictation(capture, { _, _ -> DictationDelivery("ok") }, backgroundScope)
        review.start(DictationTarget.Message(SessionId("one")), emptyList())
        capture.events.value = CaptureState(text = "Modifie mauvais.kt demain", complete = true)
        runCurrent()
        review.finish()
        review.correct()
        review.moveWord(1)
        review.record()
        capture.events.value = CaptureState(text = "PassageCheck.ts", complete = true)
        runCurrent()
        review.finish()
        assertEquals("Modifie PassageCheck.ts demain", review.state.value.text)
        review.append()
        review.record()
        capture.events.value = CaptureState(text = "puis teste", complete = true)
        runCurrent()
        review.finish()
        assertEquals("Modifie PassageCheck.ts demain puis teste", review.state.value.text)
    }

    @Test fun `failed correction preserves reviewed text and never sends a partial`() = runTest {
        val capture = TestCapture()
        val sent = mutableListOf<String>()
        val review = ReviewDictation(capture, { _, text -> sent += text; DictationDelivery("ok") }, backgroundScope)
        review.start(DictationTarget.Message(SessionId("one")), emptyList())
        capture.events.value = CaptureState(text = "Bonjour", complete = true)
        runCurrent()
        review.finish()
        review.correct(); review.record()
        capture.events.value = CaptureState(text = "erreur", recording = true)
        runCurrent()
        review.finish()
        review.send()
        assertTrue(sent.isEmpty())
        capture.events.value = CaptureState(error = "Hors ligne")
        runCurrent()
        review.finish()
        assertEquals("Bonjour", review.state.value.text)
        review.discard()
        assertFalse(review.state.value.visible)
    }

    @Test fun `delivery failure keeps a draft but uncertain delivery cannot be resent`() = runTest {
        val capture = TestCapture()
        var count = 0
        val review = ReviewDictation(capture, { target, _ ->
            count++
            if (count == 1) DictationDelivery("Non envoyé", target) else DictationDelivery("Livraison incertaine")
        }, backgroundScope)
        review.start(DictationTarget.Message(SessionId("one")), emptyList())
        capture.events.value = CaptureState(text = "Bonjour", complete = true)
        runCurrent()
        review.finish()
        review.send(); runCurrent()
        assertTrue(review.state.value.editable)
        review.send(); runCurrent()
        review.send(); runCurrent()
        assertEquals(2, count)
        assertEquals(DictationPhase.SENT, review.state.value.phase)
    }

    @Test fun `a delivered message closes the review by itself and reports its delivery once`() = runTest {
        val capture = TestCapture()
        val delivered = mutableListOf<Pair<DictationTarget, Delivery>>()
        val target: DictationTarget = DictationTarget.Message(SessionId("one"))
        for (delivery in listOf(Delivery.PROMPT, Delivery.STEERING, Delivery.QUEUED)) {
            val review = ReviewDictation(capture, { _, _ -> DictationDelivery("ok", delivery = delivery) }, backgroundScope) { t, d -> delivered += t to d }
            review.start(target, emptyList())
            capture.events.value = CaptureState(text = "Lance les tests", complete = true)
            runCurrent(); review.finish()
            review.send(); runCurrent()
            assertFalse(review.state.value.visible, "$delivery")
            assertEquals(DictationPhase.CLOSED, review.state.value.phase)
            review.send(); runCurrent()
        }
        assertEquals(listOf(target to Delivery.PROMPT, target to Delivery.STEERING, target to Delivery.QUEUED), delivered)
    }

    @Test fun `away from its session a queued or steering message keeps its notice, a new turn still closes`() = runTest {
        val capture = TestCapture()
        val delivered = mutableListOf<Delivery>()
        val target: DictationTarget = DictationTarget.Message(SessionId("one"))
        val phases = listOf(Delivery.PROMPT, Delivery.QUEUED, Delivery.STEERING).map { delivery ->
            val review = ReviewDictation(capture, { _, _ -> DictationDelivery("Message mis en attente. B pour revenir.", delivery = delivery) },
                backgroundScope, shown = { false }) { _, d -> delivered += d }
            review.start(target, emptyList())
            capture.events.value = CaptureState(text = "Continue", complete = true)
            runCurrent(); review.finish()
            review.send(); runCurrent()
            review.state.value.phase
        }
        assertEquals(listOf(DictationPhase.CLOSED, DictationPhase.SENT, DictationPhase.SENT), phases)
        assertEquals(listOf(Delivery.PROMPT), delivered)
    }

    @Test fun `uncertain refused and other targets keep their notice until B`() = runTest {
        val capture = TestCapture()
        val delivered = mutableListOf<Delivery>()
        fun sendWith(target: DictationTarget, result: DictationDelivery): DictationReview {
            val review = ReviewDictation(capture, { _, _ -> result }, backgroundScope) { _, d -> delivered += d }
            review.start(target, emptyList())
            capture.events.value = CaptureState(text = "Bonjour", complete = true)
            testScheduler.runCurrent(); review.finish()
            review.send(); testScheduler.runCurrent()
            return review.state.value
        }
        val message = DictationTarget.Message(SessionId("one"))
        assertEquals(DictationPhase.SENT, sendWith(message, DictationDelivery("Envoi non confirmé.", delivery = Delivery.UNKNOWN)).phase)
        assertEquals(DictationPhase.REVIEW, sendWith(message, DictationDelivery("Non livré.", message, Delivery.NOT_SENT)).phase)
        val comment = DictationTarget.DiffComment(SessionId("one"), "a.kt", 3)
        assertEquals("Commentaire envoyé.", sendWith(comment, DictationDelivery("Commentaire envoyé.", delivery = Delivery.PROMPT)).notice)
        assertTrue(delivered.isEmpty())
    }
}

private class TestCapture : DictationCapture {
    val events = MutableStateFlow(CaptureState())
    override val state = events
    var terms = emptyList<String>()
    override fun start(vocabulary: List<String>) { terms = vocabulary; events.value = CaptureState(recording = true) }
    override fun finish() { events.value = events.value.copy(recording = false) }
    override fun cancel() = Unit
}
