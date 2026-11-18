package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import app.regie.domain.model.*
import app.regie.domain.usecase.ApprovalReview
import app.regie.domain.usecase.DiffReview
import app.regie.input.Button
import app.regie.session.SessionControlState
import app.regie.session.SessionNotice
import app.regie.ui.game.GameApprovalBottom
import app.regie.ui.game.GameApprovalTop
import app.regie.ui.game.GameDiff
import app.regie.ui.game.GameSessionBottom
import app.regie.ui.game.GameSessionTop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Ticket 47 : le parchemin, le menu de choix et le tableau de liège rendent la main aux contrôleurs de l'app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-369dpi")
class ValidationWiringTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val session = Session(SessionId("s"), Agent.CODEX, "demo", "main", null, SessionStatus.AWAITING_APPROVAL, true,
        Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), approvalMode = "ask")
    private val overview = Overview(listOf(session), emptyList())
    private val options = listOf(
        ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"),
        ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Always"),
        ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
    )
    private val lastPath = "val fin = \"dernière ligne du parchemin\""
    private val plan = (1..24).joinToString("\n") { "$it. Étape du plan, à lire avant de valider." }
    private val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "touch proof", reason = "Créer la preuve", plan = plan,
        changes = listOf(ApprovalChange("android/app/Fin.kt", null, lastPath)), options = options)

    private class Recorder : SessionCallbacks {
        override fun onButton(button: Button) = Unit
        override fun onOptions() = Unit
        override fun onSettingRow(row: String) = Unit
        override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) = Unit
        override fun onDraftChange(text: String) = Unit
        override fun onSend() = Unit
        override fun onNoticeSeen(notice: SessionNotice) = Unit
    }

    private fun host(content: @androidx.compose.runtime.Composable () -> Unit) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent { content() }
    }

    @Test fun `on two screens the whole request is read with the cross in the bottom thread`() {
        val steps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val decisions = mutableListOf<ApprovalDecision>()
        host {
            GameSessionBottom(LiveSession.of(session, SessionThread(listOf(ThreadEntry.UserMessage(1, "Lance la preuve")), loaded = true)), false, null,
                SessionControlState(), "", SessionSettingsReview(), ThreadScroll(steps, MutableStateFlow(0f)), Recorder(),
                ApprovalReview(request, listOf(request), pending = true), { _, decision -> decisions += decision }, {}, {})
        }
        compose.onNodeWithText("touch proof").assertIsDisplayed()
        compose.onNodeWithText(lastPath).assertIsNotDisplayed()
        compose.onNodeWithText("Autoriser pour la session").performClick()
        // La croix, comme `SessionController` la relaie au fil : le parchemin se lit jusqu'à la dernière étape.
        repeat(20) { steps.tryEmit(1) }
        compose.waitForIdle()
        compose.onNodeWithText(lastPath).assertIsDisplayed()
        assertEquals(listOf(ApprovalDecision.SESSION), decisions)
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `on a single screen the whole request ends the thread of the notebook and the cross reads it to the end`() {
        val steps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        host {
            GameSessionTop(LiveSession.of(session, SessionThread(emptyList(), loaded = true)), false, null, SessionControlState(), "", SessionSettingsReview(), true,
                ThreadScroll(steps, MutableStateFlow(0f)), Recorder(),
                ApprovalReview(request, listOf(request), pending = true), { _, _ -> }, {}, {}, DictationReview(), null)
        }
        compose.onNodeWithText("touch proof").assertIsDisplayed()
        compose.onNodeWithText("Autoriser une fois").assertIsDisplayed()
        compose.onNodeWithText(lastPath).assertIsNotDisplayed()
        // La croix, relayée au fil du carnet comme sur deux écrans : la demande se lit jusqu'au bout.
        repeat(30) { steps.tryEmit(1) }
        compose.waitForIdle()
        compose.onNodeWithText(lastPath).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `the approval screen top scrolls its parchment with the approval controller steps`() {
        val steps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        host { GameApprovalTop(ApprovalReview(request, listOf(request), pending = true), overview, false, steps, MutableStateFlow(0f), {}, {}, {}) }
        compose.onNodeWithText("touch proof").assertIsDisplayed()
        compose.onNodeWithText(lastPath).assertIsNotDisplayed()
        repeat(12) { steps.tryEmit(1) }
        compose.waitForIdle()
        compose.onNodeWithText(lastPath).assertIsDisplayed()
    }

    @Test fun `the approval menu relays the same buttons as the gamepad, L2 held to refuse with a consigne`() {
        val buttons = mutableListOf<Button>()
        val held = mutableListOf<Boolean>()
        val selected = mutableListOf<ApprovalId>()
        val other = request.copy(id = ApprovalId("b"), subject = "rm -rf build", plan = null)
        host { GameApprovalBottom(ApprovalReview(request, listOf(request, other), pending = true), overview, { buttons += it }, { held += it }, { selected += it }) }
        listOf("Autoriser une fois", "Refuser", "Autoriser pour la session").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Refuser et dicter une consigne").performTouchInput { down(center); up() }
        compose.onNodeWithText("demo · rm -rf build").performClick()
        compose.onNodeWithText("Revenir").performClick()
        compose.onNodeWithText("Voir la suivante").performClick()
        assertEquals(listOf(Button.A, Button.B, Button.X, Button.L1, Button.R1), buttons)
        assertEquals(listOf(true, false), held)
        assertEquals(listOf(ApprovalId("b")), selected)
    }

    @Test fun `the diff notebook relays files, Y, B and L2 like the classic screen`() {
        val buttons = mutableListOf<Button>()
        val files = mutableListOf<Int>()
        val held = mutableListOf<Boolean>()
        val review = DiffReview(session.id, "demo", listOf(
            DiffFile("src/a.kt", listOf(DiffBlock("1", listOf(DiffLine(DiffKind.ADDED, "const limit = 70", null, 40)), true)), LineChanges(1, 0)),
            DiffFile("src/b.kt", emptyList(), null),
        ), loading = false)
        host { GameDiff(review, overview, bottom = true, single = false, MutableStateFlow(0f), { buttons += it }, { files += it }, { held += it }) }
        compose.onNodeWithText("2 fichiers · modifications suivies").assertIsDisplayed()
        compose.onNodeWithText("Ligne 40").assertIsDisplayed()
        compose.onNodeWithText("src/b.kt").performClick()
        compose.onNodeWithText("Expliquer le bloc").performClick()
        compose.onNodeWithText("Commenter à la voix").performTouchInput { down(center); up() }
        compose.onNodeWithText("Revenir").performClick()
        assertEquals(listOf(1), files)
        assertEquals(listOf(Button.Y, Button.B), buttons)
        assertEquals(listOf(true, false), held)
    }

    @Test @Config(qualifiers = "w832dp-h468dp-land-369dpi")
    fun `on a single screen the cork board keeps the line and the notebook`() {
        val review = DiffReview(session.id, "demo", listOf(
            DiffFile("src/a.kt", listOf(DiffBlock("1", listOf(DiffLine(DiffKind.ADDED, "const limit = 70", null, 40)), true)), LineChanges(1, 0)),
        ), loading = false)
        host { GameDiff(review, overview, bottom = false, single = true, MutableStateFlow(0f), {}, {}, {}) }
        compose.onNodeWithText("const limit = 70").assertIsDisplayed()
        compose.onNodeWithText("Expliquer le bloc").assertIsDisplayed()
        compose.onNodeWithText("Commenter à la voix").assertIsDisplayed()
    }
}
