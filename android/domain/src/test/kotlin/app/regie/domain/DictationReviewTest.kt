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
        review.send()
        review.send()
        runCurrent()
        assertEquals(listOf(target to "Dans PassageCheck.ts, ajoute un test."), sent)
        assertEquals(listOf("PassageCheck.ts"), capture.terms)
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

}

private class TestCapture : DictationCapture {
    val events = MutableStateFlow(CaptureState())
    override val state = events
    var terms = emptyList<String>()
    override fun start(vocabulary: List<String>) { terms = vocabulary; events.value = CaptureState(recording = true) }
    override fun finish() { events.value = events.value.copy(recording = false) }
    override fun cancel() = Unit
}
