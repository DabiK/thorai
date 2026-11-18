package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.overview.ControlState
import app.regie.ui.screens.ControlCallbacks
import app.regie.ui.screens.OverviewTopScreen
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Ticket 22 : la vue d'ensemble est une liste dense, une ligne par session, et elle défile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class OverviewListTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val now = Clock.System.now()
    private val callbacks = object : ControlCallbacks {
        override fun onButton(button: Button) {}
        override fun onTrigger(trigger: Trigger, held: Boolean) {}
        override fun onOpenApproval() {}
        override fun onQuit() {}
        override fun onConfirmQuit() {}
        override fun onCancelQuit() {}
    }

    private fun session(index: Int, status: SessionStatus = SessionStatus.IDLE) = Session(
        SessionId("s$index"), if (index % 2 == 0) Agent.CODEX else Agent.CLAUDE, "projet-$index", "branche-$index", "Titre $index",
        status, true, now - index.minutes, now - index.minutes)

    @Test fun `each line carries agent project branch title state step last action and age`() {
        val approval = Approval(ApprovalId("a"), SessionId("s1"), ApprovalKind.COMMAND, "pip install open_clip_torch")
        val sessions = listOf(
            session(0, SessionStatus.WORKING).copy(progress = Progress(1, 5, "Lire"), lastAction = LastAction("npm test", ActionStatus.IN_PROGRESS)),
            session(1, SessionStatus.AWAITING_APPROVAL).copy(pendingApproval = approval),
            session(2, SessionStatus.FAILED).copy(lastError = "Codex s’est arrêté"),
        )
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                OverviewTopScreen(Overview.of(sessions, emptyList(), BridgeStatus.Connected), ControlState(), false, callbacks)
            } }
            compose.onAllNodesWithText("CODEX").assertCountEquals(2)
            compose.onNodeWithText("CLAUDE").assertIsDisplayed()
            listOf("projet-0", "branche-0", "Titre 0", "En cours · étape 2 sur 5", "› npm test · en cours", "2\u00a0min").forEach {
                compose.onNodeWithText(it, substring = true).assertIsDisplayed()
            }
            compose.onNodeWithText("Attend ta validation").assertIsDisplayed()
            compose.onNodeWithText("Commande à autoriser", substring = true).assertIsDisplayed()
            compose.onNodeWithText("pip install open_clip_torch", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Codex s’est arrêté").assertIsDisplayed()
            val first = compose.onNodeWithText("projet-0").getUnclippedBoundsInRoot()
            val second = compose.onNodeWithText("projet-1").getUnclippedBoundsInRoot()
            assertTrue(first.left == second.left && first.bottom < second.top, "Une ligne par session, l'une sous l'autre")
        }
    }

    @Test fun `the list scrolls to keep the focused line visible`() {
        val sessions = (0 until 20).map { session(it) }
        val control = mutableStateOf(ControlState())
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                OverviewTopScreen(Overview.of(sessions, emptyList(), BridgeStatus.Connected), control.value, false, callbacks)
            } }
            compose.onNodeWithText("projet-0").assertIsDisplayed()
            compose.onNodeWithText("projet-19").assertDoesNotExist()
            compose.runOnIdle { control.value = ControlState(focusedIndex = 19, focusedId = SessionId("s19")) }
            compose.onNodeWithText("projet-19").assertIsDisplayed()
            compose.runOnIdle { control.value = ControlState(focusedIndex = 0, focusedId = SessionId("s0")) }
            compose.onNodeWithText("projet-0").assertIsDisplayed()
        }
    }
}
