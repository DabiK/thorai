package app.regie.domain

import app.regie.domain.model.*
import app.regie.domain.usecase.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReviewDiffTest {
    private val line = DiffLine(DiffKind.ADDED, "const limit = 70", null, 40)
    private fun edit(id: Long, status: ActionStatus = ActionStatus.COMPLETED) = ThreadEntry.ToolCall(
        id, ToolKind.EDIT, "Edit", status = status,
        files = listOf(ModifiedFile("a.kt", LineChanges(1, 0))),
        diffs = listOf(DiffFragment("a.kt", listOf(line), true)),
    )
    @Test fun `evicting an earlier block preserves the selected block and its inner line`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s"))
        val selectedEdit = edit(2).copy(diffs = listOf(DiffFragment("a.kt", listOf(
            DiffLine(DiffKind.REMOVED, "old limit", 41, null),
            DiffLine(DiffKind.ADDED, "new limit", null, 41),
        ), true)))
        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(1), selectedEdit, edit(3)), loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.moveLine(2)

        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(selectedEdit, edit(3)), loaded = true)
        runCurrent()

        assertEquals(1, review.state.value.lineIndex)
        assertEquals("2:0", review.state.value.selected?.block?.id)
        assertEquals(1, review.state.value.selected?.lineIndex)
        assertEquals(DictationTarget.DiffComment(SessionId("s"), "a.kt", 41,
            selectedText = "new limit"), review.commentTarget())
        review.close()
    }
    @Test fun `completing an earlier edit preserves the selected removed line and explanation`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s"))
        val selectedEdit = edit(2).copy(diffs = listOf(DiffFragment("a.kt", listOf(
            DiffLine(DiffKind.CONTEXT, "context", 40, 40),
            DiffLine(DiffKind.REMOVED, "old limit", 41, null),
        ), false)))
        gateway.threadOf(SessionId("s")).value = SessionThread(
            listOf(edit(1, ActionStatus.PENDING), selectedEdit, edit(3)), loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.moveLine(1)

        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(1), selectedEdit, edit(3)), loaded = true)
        runCurrent()

        assertEquals(2, review.state.value.lineIndex)
        assertEquals("2:0", review.state.value.selected?.block?.id)
        assertEquals(1, review.state.value.selected?.lineIndex)
        assertEquals(DictationTarget.DiffComment(SessionId("s"), "a.kt", 41,
            removed = true, fileCoordinates = false, selectedText = "old limit"), review.commentTarget())
        review.explain()
        assertEquals("Explique-moi ce bloc, sans modifier les fichiers. Réponds en français par écrit.\n" +
            "a.kt · ligne 41 de l’extrait supprimée\nÉdition historique exposée par l’agent ; numéros ancien/nouveau" +
            " relatifs à l’extrait, position dans le fichier non communiquée.\n" +
            "Extrait autour de la sélection :\n40/40   context\n41/- - old limit", review.state.value.explanation?.prompt)
        review.close()
    }

    @Test fun `review keeps completed edits separately and names files without exposed diff`() {
        val files = sessionDiff(session("s").copy(modifiedFiles = listOf("unknown.bin", "a.kt")),
            SessionThread(listOf(edit(1), edit(2, ActionStatus.FAILED), edit(3), edit(4, ActionStatus.PENDING)), loaded = true))
        assertEquals(listOf("unknown.bin", "a.kt"), files.map { it.path })
        assertTrue(files[0].blocks.isEmpty())
        assertNull(files[0].changes)
        assertEquals(LineChanges(2, 0), files[1].changes)
        assertEquals(listOf("1:0", "3:0"), files[1].blocks.map { it.id })
        assertEquals(line, files[1].blocks[0].lines.single())
    }
    @Test fun `navigation clamps and freezes the selected comment including removed lines`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s"))
        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(1).copy(diffs = listOf(
            DiffFragment("a.kt", listOf(line, DiffLine(DiffKind.REMOVED, "old", 41, null)), true),
            DiffFragment("b.kt", listOf(line), true),
        ))), loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        assertEquals("a.kt", review.state.value.file?.path)
        review.moveLine(50)
        val target = review.commentTarget()!!
        assertEquals(41, target.line)
        assertTrue(target.removed)
        assertTrue(target.fileCoordinates)
        assertEquals("old", target.selectedText)
        assertEquals("a.kt", target.path)
        review.moveFile(1)
        assertEquals("b.kt", review.state.value.file?.path)
        assertEquals(0, review.state.value.lineIndex)
        assertEquals("a.kt", target.path)
        review.moveFile(1)
        assertEquals("b.kt", review.state.value.file?.path)
        review.close()
    }

    @Test fun `Y prepares a frozen block A sends once and only its reply is shown`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s"))
        val initial = listOf(edit(1), ThreadEntry.AgentText(2, "Ancienne réponse"))
        gateway.threadOf(SessionId("s")).value = SessionThread(initial, loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.explain()
        val request = review.state.value.explanation!!
        assertEquals("Explique-moi ce bloc, sans modifier les fichiers. Réponds en français par écrit.\n" +
            "a.kt · ligne 40\nÉdition historique exposée par l’agent ; numéros ancien/nouveau.\n" +
            "Extrait autour de la sélection :\n-/40 + const limit = 70", request.prompt)
        assertTrue(gateway.sent.isEmpty())
        review.confirmExplanation(); review.confirmExplanation(); runCurrent()
        assertEquals(listOf(SessionId("s") to request.prompt), gateway.sent)
        assertEquals("", review.state.value.explanation!!.reply)
        gateway.threadOf(SessionId("s")).value = SessionThread(initial + listOf(
            ThreadEntry.AgentText(3, "Autre réponse avant la demande"),
            ThreadEntry.UserMessage(4, request.prompt),
            ThreadEntry.AgentText(5, "Ce seuil"),
        ), loaded = true)
        runCurrent()
        assertEquals("Ce seuil", review.state.value.explanation!!.reply)
        gateway.threadOf(SessionId("s")).value = SessionThread(initial + listOf(
            ThreadEntry.UserMessage(4, request.prompt), ThreadEntry.AgentText(5, "Ce seuil vaut 70."),
            ThreadEntry.UserMessage(6, "Autre question"), ThreadEntry.AgentText(7, "Autre réponse"),
        ), loaded = true)
        runCurrent()
        assertEquals("Ce seuil vaut 70.", review.state.value.explanation!!.reply)
        review.close()
    }

    @Test fun `missing diff cannot be commented and unknown delivery cannot be retried`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s").copy(modifiedFiles = listOf("missing.bin")))
        gateway.threadOf(SessionId("s")).value = SessionThread(loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        assertNull(review.commentTarget())
        review.explain(); review.confirmExplanation(); runCurrent()
        assertTrue(gateway.sent.isEmpty())
        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(1)), loaded = true)
        runCurrent(); review.moveFile(1)
        gateway.delivery = Delivery.UNKNOWN
        review.explain(); review.confirmExplanation(); runCurrent()
        review.confirmExplanation(); runCurrent()
        assertEquals(1, gateway.sent.size)
        assertEquals(Delivery.UNKNOWN, review.state.value.explanation!!.delivery)
        review.close()
    }
    @Test fun `definite delivery failure retries the same block even if new edits arrive`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s"))
        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(1)), loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        review.explain()
        gateway.delivery = Delivery.NOT_SENT
        review.confirmExplanation(); runCurrent()
        val prompt = gateway.sent.single().second
        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(2).copy(diffs = emptyList())), loaded = true)
        runCurrent()
        gateway.delivery = Delivery.QUEUED
        review.confirmExplanation(); runCurrent()
        assertEquals(listOf(SessionId("s") to prompt, SessionId("s") to prompt), gateway.sent)
        review.confirmExplanation(); runCurrent()
        assertEquals(2, gateway.sent.size)
        review.close()
    }

    @Test fun `navigation at the first line still requests that selection be revealed`() = runTest {
        val gateway = StubGateway()
        gateway.sessions.value = listOf(session("s"))
        gateway.threadOf(SessionId("s")).value = SessionThread(listOf(edit(1)), loaded = true)
        val review = ReviewDiff(gateway, backgroundScope)
        review.open(SessionId("s")); runCurrent()
        val before = review.state.value.selectionRevision
        review.moveLine(-1)
        assertEquals(0, review.state.value.lineIndex)
        assertTrue(review.state.value.selectionRevision > before)
        review.close()
    }

}
