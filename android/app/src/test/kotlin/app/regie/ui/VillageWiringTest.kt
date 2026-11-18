package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentVersion
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.LastAction
import app.regie.domain.model.Overview
import app.regie.domain.model.Progress
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.overview.ControlState
import app.regie.overview.QuitStep
import app.regie.ui.game.GameOverviewBottom
import app.regie.ui.game.carnet
import app.regie.ui.screens.ControlCallbacks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Le carnet du hameau reçoit les mêmes informations et les mêmes actions que la vue d'ensemble
 * classique (ticket 45), rédigées avec les textes de l'app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-369dpi")
class VillageWiringTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val now = Clock.System.now()
    private val buttons = mutableListOf<Button>()
    private val triggers = mutableListOf<Pair<Trigger, Boolean>>()
    private val visited = mutableListOf<SessionId>()
    private var quit = 0
    private val callbacks = object : ControlCallbacks {
        override fun onButton(button: Button) { buttons += button }
        override fun onTrigger(trigger: Trigger, held: Boolean) { triggers += trigger to held }
        override fun onOpenApproval() {}
        override fun onQuit() { quit++ }
        override fun onConfirmQuit() {}
        override fun onCancelQuit() {}
    }.carnet(onGoTo = { visited += it }, onSetup = {}, onVoice = {})

    private val approval = Approval(ApprovalId("a1"), SessionId("meteo"), ApprovalKind.COMMAND, "npm run db:migrate")

    private val sessions = listOf(
        Session(SessionId("regie"), Agent.CLAUDE, "regie", "ticket/22-tri", "tri des sessions", SessionStatus.WORKING, true,
            now - 4.minutes, now - 4.minutes, progress = Progress(2, 5, "Tests"), lastAction = LastAction("Modification · Overview.kt", ActionStatus.COMPLETED)),
        Session(SessionId("meteo"), Agent.CODEX, "api-meteo", null, "migration", SessionStatus.AWAITING_APPROVAL, true,
            now - 6.minutes, now - 6.minutes, pendingApproval = approval),
        Session(SessionId("site"), Agent.CODEX, "site-perso", null, "refonte", SessionStatus.FAILED, true,
            now - 120.minutes, now - 120.minutes, lastError = "build cassé"),
    )

    private fun show(overview: Overview, control: ControlState = ControlState()) {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            GameOverviewBottom(scene = null, villageShown = false, overview, control, setupLeft = true, callbacks)
        }
        compose.waitForIdle()
    }

    @Test
    fun `the carnet shows what the classic overview showed`() {
        show(Overview.of(sessions, listOf(AgentVersion(Agent.CLAUDE, "2.1.322", true), AgentVersion(Agent.CODEX, "0.170.0", false)), BridgeStatus.Connected))

        compose.onNodeWithText("! Codex attend ta validation").assertIsDisplayed()
        compose.onNodeWithText("api-meteo · migration").assertIsDisplayed()
        compose.onNodeWithText("Commande à autoriser : npm run db:migrate").assertIsDisplayed()
        compose.onNodeWithText("ticket/22-tri").assertIsDisplayed()
        compose.onNodeWithText("› Modification · Overview.kt · terminée").assertIsDisplayed()
        compose.onNodeWithText("étape 3 sur 5 · 4 min").assertIsDisplayed()
        compose.onNodeWithText("build cassé").assertIsDisplayed()
        compose.onNodeWithText("Échec").assertIsDisplayed()
        compose.onNodeWithText("3 sessions actives").assertIsDisplayed()
        compose.onNodeWithText("Codex 0.170.0 (version non testée)").assertIsDisplayed()
        compose.onNodeWithText("Maintiens L2 et parle").assertIsDisplayed()
        compose.onNodeWithTag("setup-left").assertExists()
    }

    @Test
    fun `the actions reach the same buttons, a touched line walks there`() {
        show(Overview.of(sessions, emptyList(), BridgeStatus.Connected))

        listOf("Y aller", "Agents", "Bâtir", "Console", "Tout mettre en pause").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithTag("carnet-approval").performClick()
        compose.onNodeWithTag("carnet-row-site").performClick()
        compose.onNodeWithTag("carnet-quit").performClick()

        assertEquals(listOf(Button.A, Button.B, Button.X, Button.Y, Button.START, Button.R1), buttons)
        assertEquals(listOf(SessionId("site")), visited)
        assertEquals(1, quit)
    }

    @Test
    fun `an empty village says why, the unreachable bridge says what to do`() {
        show(Overview.of(emptyList(), emptyList(), BridgeStatus.Connected))
        compose.onNodeWithText("Aucune session. Bâtis-en une avec X.").assertIsDisplayed()
    }

    @Test
    fun `an unreachable bridge is a notice with its hint`() {
        show(Overview.of(sessions, emptyList(), BridgeStatus.Unreachable(BridgeHint.START_EXTERNAL)))
        compose.onNodeWithText("Démarre le pont sur le Mac.").assertIsDisplayed()
    }

    @Test
    fun `quitting while a session works asks first`() {
        show(Overview.of(sessions, emptyList(), BridgeStatus.Connected), ControlState(quit = QuitStep.Confirm(1)))
        compose.onNodeWithText("Annuler").assertIsDisplayed()
    }
}
