package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.R
import app.regie.domain.model.*
import app.regie.input.Button
import app.regie.ui.screens.DictationBottomScreen
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class InterfaceCopyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun `uncertain delivery explains the next action in short sentences`() {
        val text = RuntimeEnvironment.getApplication().getString(R.string.delivery_unknown)
        assertEquals("Envoi non confirmé. Vérifie le fil avant de renvoyer.", text)
        text.split(Regex("[.!?]\\s*" )).filter { it.isNotBlank() }.forEach {
            assertTrue(it.split(Regex("\\s+")).size <= 8, it)
        }
    }

    @Test fun `session actions have verbs and use the same labels in compact mode`() {
        val resources = RuntimeEnvironment.getApplication().resources
        assertEquals("Créer une session", resources.getString(R.string.action_new_session))
        assertEquals("Voir les modifications", resources.getString(R.string.action_see_diff))
        assertEquals(resources.getString(R.string.action_back_to_overview), resources.getString(R.string.action_back_short))
        assertEquals("Parler", resources.getString(R.string.action_speak))
        assertEquals("Réglages de Codex", resources.getString(R.string.settings_title, "Codex"))
    }

    @Test fun `session authorization scope is entirely readable in a narrow button`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                androidx.compose.foundation.layout.Column {
                    app.regie.ui.screens.DecisionButton("X", "Autoriser pour la session", true, Modifier.width(163.dp)) {}
                }
            } }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Autoriser pour la session", useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            // Robolectric's legacy font widths differ from Android; the regression is vertical.
            assertTrue(!layouts.single().didOverflowHeight, "La portée doit tenir dans la hauteur du bouton")
        }
    }

    @Test fun `session keys follow B X L2 Start in reading order and no longer offer a report`() {
        val callbacks = object : app.regie.ui.screens.SessionCallbacks {
            override fun onButton(button: Button) {}
            override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) {}
            override fun onDraftChange(text: String) {}
            override fun onSend() {}
            override fun onNoticeSeen(notice: app.regie.session.SessionNotice) {}
        }
        val session = Session(SessionId("s"), Agent.CODEX, "demo", null, null, SessionStatus.WORKING, true,
            kotlin.time.Instant.fromEpochMilliseconds(0), kotlin.time.Instant.fromEpochMilliseconds(0), approvalMode = "ask")
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                app.regie.ui.screens.SessionBottomScreen(LiveSession.of(session, SessionThread(loaded = true)), null,
                    app.regie.session.SessionControlState(), "", callbacks)
            } }
            val labels = listOf("Revenir", "Voir les modifications", "Parler", "Interrompre")
            val bounds = labels.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
            assertTrue(bounds.zipWithNext().all { (a, b) -> a.left < b.left && a.top == b.top }, "B X L2 Start, sur une ligne")
            compose.onNodeWithText("Faire le point").assertDoesNotExist()
        }
    }

    @Test fun `empty overview explains how to create a session`() {
        val callbacks = object : app.regie.ui.screens.ControlCallbacks {
            override fun onButton(button: Button) {}
            override fun onTrigger(trigger: app.regie.input.Trigger, held: Boolean) {}
            override fun onOpenApproval() {}
            override fun onQuit() {}
            override fun onConfirmQuit() {}
            override fun onCancelQuit() {}
        }
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                app.regie.ui.screens.OverviewTopScreen(Overview.EMPTY.copy(bridge = BridgeStatus.Connected),
                    app.regie.overview.ControlState(), false, callbacks)
            } }
            compose.onNodeWithText("Aucune session. Crée une session avec X.").assertIsDisplayed()
        }
    }

    @Test fun `dictation actions follow A B X Y and keep their callbacks`() {
        val buttons = mutableListOf<Button>()
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                DictationBottomScreen(DictationReview(phase = DictationPhase.REVIEW, text = "Lis ce fichier"),
                    { buttons += it }, {}, {})
            } }
            val labels = listOf("Envoyer", "Effacer", "Corriger un mot", "Compléter")
            val bounds = labels.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
            assertTrue(bounds.zipWithNext().all { (a, b) -> a.left < b.left }, "A B X Y, de gauche à droite")
            labels.forEach { compose.onNodeWithText(it).performClick() }
            assertEquals(listOf(Button.A, Button.B, Button.X, Button.Y), buttons)
            compose.onNodeWithText("Rien ne part sans ton A").assertDoesNotExist()
        }
    }
}
