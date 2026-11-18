package app.regie.game.ui.console

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import app.regie.domain.model.Agent
import app.regie.domain.model.CommandPhase
import app.regie.domain.model.FloatingCard
import app.regie.domain.model.FloatingKind
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.VoiceCommandReview
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.menu.Captures
import app.regie.game.ui.menu.Samples
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
class ConsoleScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ComponentActivity

    private val assets by lazy { Samples.realAssets() }
    private val t = Instant.fromEpochMilliseconds(0)
    private val console = Session(SessionId("console"), Agent.CLAUDE, "console", null, null, SessionStatus.WORKING, true, t, t)

    private fun show(content: @Composable () -> Unit) {
        val clock = mutableLongStateOf(0)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Sans la barre de titre de l'activité de test : l'écran entier, comme sur la Thor.
        activity.actionBar?.hide()
        activity.setContent { GameTheme(clock = clock) { content() } }
    }

    /** Une appli Android quelconque sous la carte, comme dans la maquette 12. */
    @Composable
    private fun OverOtherApp(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF3F4F6)), contentAlignment = Alignment.TopEnd) {
            Box(Modifier.padding(20.dp)) { content() }
        }
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `a floating message offers to answer with L2 and to close with B`() {
        val card = FloatingCard("k", console.id, Agent.CLAUDE, "console", FloatingKind.MESSAGE, "J'ai ouvert l'affichage. Je baisse la luminosité à 40 % ?")
        show { OverOtherApp { PixelFloatingCard(card, assets) } }
        listOf("Claude", "console", "J'ai ouvert l'affichage. Je baisse la luminosité à 40 % ?", "Répondre", "Fermer").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        Captures.save(compose, activity, "carte-volante")
    }

    @Test
    fun `a floating validation offers only the choices the agent gives`() {
        val card = FloatingCard("p1", SessionId("meteo"), Samples.OPENCODE, "api-meteo", FloatingKind.APPROVAL, "npm run db:migrate", Samples.approval)
        show { PixelFloatingCard(card, assets) }
        listOf("Autoriser une fois", "Fermer", "Refuser en dictant", "! Attend ta validation", "npm run db:migrate").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Répondre").assertDoesNotExist()
        val bare = card.copy(approval = Samples.approval.copy(options = emptyList()))
        assertEquals(1, cardHintsCount(bare))
    }

    private fun cardHintsCount(card: FloatingCard): Int {
        var count = 0
        show { count = cardHints(card).size }
        compose.waitForIdle()
        return count
    }

    @Test
    fun `the bubble follows a working session with its last sentence`() {
        show { PixelConsoleBubble(console, "J'ouvre YouTube.") }
        compose.onNodeWithText("Au travail").assertIsDisplayed()
        compose.onNodeWithText("J'ouvre YouTube.").assertIsDisplayed()
    }

    @Test
    fun `the tower header shows the inhabitant at the mirror, who pilots Android and the session state`() {
        show { Column { ConsoleTowerHeader(console, assets); Box(Modifier.fillMaxSize().background(Color(0xFFFBE6B2))) } }
        listOf("Tour de la console", "Claude pilote Android", "Au travail").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        assertTrue(assets.resolve("fond.tour") != null)
        assertEquals("travail", TowerScene.pose(SessionStatus.WORKING))
        assertEquals("attente", TowerScene.pose(SessionStatus.AWAITING_APPROVAL))
        assertEquals("sommeil", TowerScene.pose(SessionStatus.STOPPED))
        Captures.save(compose, activity, "tour-entete")
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the voice command on top quotes what was heard and what is proposed`() {
        val review = VoiceCommandReview(CommandPhase.REVIEW, text = "lance Claude sur météo", notice = "Lancer Claude sur météo")
        show { GameVoiceCommandScreen(review, bottom = false, singleScreen = false, assets = assets) }
        compose.onNodeWithText("« lance Claude sur météo »").assertIsDisplayed()
        compose.onNodeWithText("Action proposée").assertIsDisplayed()
        Captures.save(compose, activity, "ordre-vocal-haut")
    }

    @Test
    fun `the voice command bottom confirms with A, cancels with B and redoes with R2`() {
        var confirmed = false
        var cancelled = false
        val review = VoiceCommandReview(CommandPhase.REVIEW, text = "lance Claude sur météo", command = app.regie.domain.model.VoiceCommand.ShowOverview, notice = "Lancer Claude sur météo")
        show { GameVoiceCommandScreen(review, bottom = true, singleScreen = false, assets = assets, callbacks = CommandCallbacks(confirm = { confirmed = true }, cancel = { cancelled = true })) }
        listOf("Ordre vocal", "Lancer Claude sur météo", "Confirmer", "Annuler", "Redire", "Régler la voix").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        Captures.save(compose, activity, "ordre-vocal-bas")
        compose.onNodeWithText("Confirmer").performClick()
        compose.onNodeWithText("Annuler").performClick()
        assertTrue(confirmed && cancelled)
    }

    // Repris de l'ordre vocal classique (ticket 51) : « Redire » démarre au toucher et ne s'arrête qu'au relâché.
    @Test
    fun `Redire starts on touch down and ends only on release`() {
        val holds = mutableListOf<Boolean>()
        val review = VoiceCommandReview(CommandPhase.REVIEW, text = "lance Claude sur météo", command = app.regie.domain.model.VoiceCommand.ShowOverview, notice = "Lancer Claude sur météo")
        show { GameVoiceCommandScreen(review, bottom = true, singleScreen = false, assets = assets, callbacks = CommandCallbacks(hold = { holds += it })) }
        compose.onNodeWithText("Redire").performTouchInput { down(center) }
        compose.runOnIdle { kotlin.test.assertEquals(listOf(true), holds) }
        compose.onNodeWithText("Redire").performTouchInput { up() }
        compose.runOnIdle { kotlin.test.assertEquals(listOf(true, false), holds) }
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the voice command over another app shows the level while R2 is held`() {
        val review = VoiceCommandReview(CommandPhase.RECORDING, text = "où en est", level = 0.6f, durationMs = 2_000)
        show { OverOtherApp { GameCommandOverlay(review) } }
        compose.onNodeWithTag("game-command-overlay").assertIsDisplayed()
        compose.onNodeWithText("Relâche R2 quand tu as fini").assertIsDisplayed()
        compose.onNodeWithText("2 s").assertIsDisplayed()
        Captures.save(compose, activity, "ordre-vocal-console")
    }
}
