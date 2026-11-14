package app.regie.ui

import android.view.inputmethod.EditorInfo
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.ThreadLine
import app.regie.ui.screens.gapBefore
import app.regie.ui.screens.withoutFullscreen
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadLayoutTest {

    private val read = ThreadLine.Read(1, listOf("a.ts"), inProgress = false)
    private val edit = ThreadLine.Edit(2, "a.ts", null, ActionStatus.COMPLETED)
    private val run = ThreadLine.Run(3, "npm test", ActionStatus.IN_PROGRESS, null)
    private val text = ThreadLine.AgentText(4, "Je lance les tests", streaming = false)

    @Test
    fun `consecutive actions form a tight block, other lines keep their room`() {
        assertEquals(0, gapBefore(null, read))
        assertEquals(2, gapBefore(read, edit))
        assertEquals(2, gapBefore(edit, run))
        assertEquals(11, gapBefore(run, text))
        assertEquals(11, gapBefore(text, read))
    }

    @Test
    fun `the keyboard is asked never to go full screen`() {
        val options = withoutFullscreen(EditorInfo.IME_ACTION_DONE)

        assertEquals(EditorInfo.IME_ACTION_DONE, options and EditorInfo.IME_MASK_ACTION)
        assertEquals(EditorInfo.IME_FLAG_NO_EXTRACT_UI, options and EditorInfo.IME_FLAG_NO_EXTRACT_UI)
        assertEquals(EditorInfo.IME_FLAG_NO_FULLSCREEN, options and EditorInfo.IME_FLAG_NO_FULLSCREEN)
    }
}
