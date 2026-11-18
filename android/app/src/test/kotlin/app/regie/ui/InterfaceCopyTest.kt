package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.R
import app.regie.domain.model.*
import app.regie.domain.usecase.ApprovalReview
import app.regie.input.Button
import app.regie.ui.game.GameApprovalBottom
import app.regie.ui.game.GameDictation
import app.regie.ui.game.GameOverviewBottom
import app.regie.ui.game.GameSessionBottom
import app.regie.ui.game.carnet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Les textes de l'interface (docs/textes.md), sur les écrans du jeu branchés dans l'app : depuis le
 * ticket 51, plus aucun écran classique ne les porte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-369dpi")
class InterfaceCopyTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.WORKING, true,
        kotlin.time.Instant.fromEpochMilliseconds(0), kotlin.time.Instant.fromEpochMilliseconds(0), approvalMode = "ask")
    private val scroll = ThreadScroll(MutableSharedFlow(), MutableStateFlow(0f))

    @Test fun `uncertain delivery explains the next action in short sentences`() {
        val text = RuntimeEnvironment.getApplication().getString(R.string.delivery_unknown)
        assertEquals("Envoi non confirmé. Vérifie le fil avant de renvoyer.", text)
        text.split(Regex("[.!?]\\s*" )).filter { it.isNotBlank() }.forEach {
            assertTrue(it.split(Regex("\\s+")).size <= 8, it)
        }
    }

    // Robolectric mesure les polices à sa façon : comme avant, la régression surveillée est verticale.
    @Test
    fun `the session authorization scope is entirely readable in the choice menu`() {
        val request = Approval(ApprovalId("a"), session.id, ApprovalKind.COMMAND, "touch proof", options = listOf(
            ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Session"),
            ApprovalOption("once", ApprovalOptionKind.ALLOW_ONCE, "Once"), ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No")))
        val overview = Overview.of(listOf(session.copy(pendingApproval = request)), emptyList(), BridgeStatus.Connected)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { GameApprovalBottom(ApprovalReview(request, listOf(request), pending = true), overview, {}, {}, {}) }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Autoriser pour la session", useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(!layouts.single().didOverflowHeight, "La portée tient dans la hauteur du choix")
        }
    }

    @Test fun `session keys follow B X L2 Start in reading order and no longer offer a report`() {
        val callbacks = object : SessionCallbacks {
            override fun onButton(button: Button) {}
            override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) {}
            override fun onDraftChange(text: String) {}
            override fun onSend() {}
            override fun onNoticeSeen(notice: app.regie.session.SessionNotice) {}
        }
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameSessionBottom(LiveSession.of(session, SessionThread(loaded = true)), false, null, app.regie.session.SessionControlState(), "",
                    SessionSettingsReview(), scroll, callbacks, ApprovalReview(), { _, _ -> }, {}, {})
            }
            val labels = listOf("Revenir", "Voir les modifications", "Parler", "Interrompre")
            val bounds = labels.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
            assertTrue(bounds.zipWithNext().all { (a, b) -> a.left < b.left && a.top < b.bottom && b.top < a.bottom }, "B X L2 Start, sur une ligne")
            compose.onNodeWithText("Faire le point").assertDoesNotExist()
        }
    }

    @Test fun `an empty village explains how to create a session`() {
        val callbacks = object : ControlCallbacks {
            override fun onButton(button: Button) {}
            override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) {}
            override fun onQuit() {}
            override fun onConfirmQuit() {}
            override fun onCancelQuit() {}
        }.carnet({}, {}, {})
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameOverviewBottom(null, false, Overview.EMPTY.copy(bridge = BridgeStatus.Connected), app.regie.overview.ControlState(), false, callbacks)
            }
            compose.onNodeWithText("Aucune session. Bâtis-en une avec X.").assertIsDisplayed()
        }
    }

    @Test fun `dictation actions follow A B X Y and keep their callbacks`() {
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                GameDictation(DictationReview(phase = DictationPhase.REVIEW, text = "Lis ce fichier"), null, { buttons += it }, {}, {}, scroll)
            }
            val labels = listOf("Envoyer", "Effacer", "Corriger un mot", "Compléter")
            val bounds = labels.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
            assertTrue(bounds.zipWithNext().all { (a, b) -> a.left < b.left }, "A B X Y, de gauche à droite")
            labels.forEach { compose.onNodeWithText(it).performClick() }
            assertEquals(listOf(Button.A, Button.B, Button.X, Button.Y), buttons)
            compose.onNodeWithText("Rien ne part sans ton A").assertDoesNotExist()
        }
    }
}
