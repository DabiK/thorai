package app.regie.game.ui.house

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ThreadLine
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.theme.GameTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Le fil du carnet défile à la croix et au stick, suit le bas, et « Revenir en bas » l'y ramène. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
class HouseThreadTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val steps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val velocity = MutableStateFlow(0f)
    private val follow = "↓ Revenir en bas"

    private fun live(count: Int) = LiveSession(
        session = session(),
        lines = (0 until count).map { ThreadLine.UserMessage(it.toLong(), "Message $it", dictated = false) },
        plan = null, modifiedFiles = emptyList(), context = null, loading = false,
    )

    private val clock = mutableLongStateOf(0)

    private fun show(count: Int) = mutableStateOf(live(count)).also { state ->
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            GameTheme(clock = clock) {
                HouseThread(state.value, ScrollInput(steps, velocity), AgentTextFormat.Plain, Modifier.fillMaxSize())
            }
        }
    }

    private fun visible(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess

    private fun cross(direction: Int, times: Int = 1) {
        repeat(times) { compose.runOnIdle { steps.tryEmit(direction) } }
        compose.waitForIdle()
    }

    @Test
    fun `the thread opens on its end and the cross scrolls up, then back down with zero`() {
        show(40)
        assertTrue(visible("Message 39"))
        assertFalse(visible(follow))
        cross(-1, times = 4)
        assertFalse(visible("Message 39"), "la croix remonte le fil")
        assertTrue(visible(follow))
        cross(1, times = 8)
        cross(0)
        assertTrue(visible("Message 39"))
        assertFalse(visible(follow))
    }

    @Test
    fun `follow the end tapped brings the thread back to its last line`() {
        show(40)
        cross(-1, times = 4)
        compose.onNodeWithText(follow).performClick()
        compose.waitForIdle()
        assertTrue(visible("Message 39"))
        assertFalse(visible(follow))
    }

    @Test
    fun `the right stick scrolls while it is held`() {
        show(40)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { velocity.value = -1f }
        repeat(60) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { velocity.value = 0f }
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse(visible("Message 39"), "le stick remonte le fil")
        assertTrue(visible(follow))
    }

    @Test
    fun `a new line is followed at the end, not while reading higher up`() {
        val state = show(40)
        compose.runOnIdle { state.value = live(41) }
        compose.waitForIdle()
        assertTrue(visible("Message 40"), "en bas, le fil suit la nouvelle ligne")
        cross(-1, times = 4)
        compose.runOnIdle { state.value = live(42) }
        compose.waitForIdle()
        assertFalse(visible("Message 41"), "plus haut, la lecture n'est pas déplacée")
        assertTrue(visible(follow))
    }
}
