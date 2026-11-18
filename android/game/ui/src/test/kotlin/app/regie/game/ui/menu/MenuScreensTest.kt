package app.regie.game.ui.menu

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentAuthenticationReview
import app.regie.domain.model.DictationProvider
import app.regie.domain.model.VoiceSettings
import app.regie.game.engine.menu.GameMenu
import app.regie.game.engine.menu.MenuKey
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.theme.GameTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
class MenuScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ComponentActivity

    private val assets by lazy { Samples.realAssets() }

    private fun show(content: @Composable () -> Unit) {
        val clock = mutableLongStateOf(0)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Sans la barre de titre de l'activité de test : l'écran entier, comme sur la Thor.
        activity.actionBar?.hide()
        activity.setContent { GameTheme(clock = clock) { content() } }
    }

    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the title shows the hamlet at dawn, the logo and the invitation to start`() {
        show { TitleTopScreen(GameMenu.title(), Samples.overview, assets, singleScreen = false) }
        compose.onNodeWithText("Commencer").assertIsDisplayed()
        // Le nom du produit, seul : le contour et l'ombre du logo répètent le même texte.
        assertTrue(compose.onAllNodes(hasText("ThorAI")).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, compose.onAllNodes(hasText("ThorAI jeu", substring = true)).fetchSemanticsNodes().size)
        assertEquals("ThorAI", activity.getString(app.regie.game.ui.R.string.game_title_logo))
        assertTrue(assets.problems.isEmpty(), assets.problems.toString())
        Captures.save(compose, activity, "titre-haut")
    }

    @Test
    fun `the main menu details come from the domain and a touch chooses an entry`() {
        val chosen = mutableListOf<Int>()
        show { MainMenuScreen(GameMenu.title(), Samples.overview, callbacks = MenuCallbacks(onChoose = { chosen += it })) }
        Captures.save(compose, activity, "titre-bas")
        listOf("Continuer", "3 sessions · 1 validation", "Bâtir une maison", "2 connectés sur 3", "Voix", "Arrête le pont et les sessions", "Pont prêt", "Choisir")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNode(selected and hasText("Continuer")).assertIsDisplayed()
        compose.onNodeWithText("Nouvelle session").performClick()
        compose.onNodeWithText("Choisir").performClick()
        assertEquals(listOf(1, 0), chosen)
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `on a single screen the title carries the menu and the quit confirmation`() {
        var cancelled = false
        show {
            TitleTopScreen(GameMenu.title().press(MenuKey.UP).menu, Samples.overview, assets, singleScreen = true,
                quit = QuitPrompt(2), callbacks = MenuCallbacks(onCancelQuit = { cancelled = true }))
        }
        compose.onNodeWithText("Continuer").assertIsDisplayed()
        compose.onNodeWithText("2 sessions travaillent encore. Quitter les arrête.").assertIsDisplayed()
        compose.onNodeWithText("Annuler").performClick()
        assertTrue(cancelled)
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the pause menu darkens the hamlet and previews the agents and their connection`() {
        val menu = GameMenu.pause().press(MenuKey.DOWN).menu
        show { PauseMenuTop(menu, Samples.overview, Samples.courier, VoiceSettings(), Samples.now, assets, singleScreen = false) }
        Captures.save(compose, activity, "pause-haut")
        listOf("Courrier", "Tout mettre en pause", "Quitter", "le scribe", "le bricoleur", "la cartographe", "! Non connecté").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        assertEquals(2, compose.onAllNodes(hasText("Connecté")).fetchSemanticsNodes().size)
    }

    @Test
    fun `the pause menu bottom keeps pausing everything and counts unread letters`() {
        val chosen = mutableListOf<Int>()
        var closed = false
        show { PauseMenuBottom(GameMenu.pause(), Samples.overview, Samples.courier, Samples.now, assets, callbacks = MenuCallbacks(onChoose = { chosen += it }, onClose = { closed = true })) }
        listOf("Courrier", "2 non lus", "Tout mettre en pause", "2 sessions travaillent", "Fermer le menu").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        Captures.save(compose, activity, "pause-bas")
        compose.onNodeWithText("Tout mettre en pause").performClick()
        compose.onNodeWithText("Fermer le menu").performClick()
        // « Préparer la console » (ticket 53) passe avant « Tout mettre en pause ».
        assertEquals(listOf(4), chosen)
        assertTrue(closed)
    }

    @Test
    fun `the courier page lists the letters, newest first, and opens one by touch`() {
        val opened = mutableListOf<Int>()
        var read = false
        val menu = GameMenu.pause().press(MenuKey.A).menu
        show {
            PauseMenuBottom(menu, Samples.overview, Samples.courier, Samples.now, assets,
                callbacks = MenuCallbacks(onOpenLetter = { opened += it }, onMarkAllRead = { read = true }))
        }
        listOf("opencode attend ta validation", "api-meteo · npm run db:migrate", "1 min", "L’agent te pose une question", "5 min", "Session en échec", "3 h", "Tout marquer lu")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        Captures.save(compose, activity, "courrier-bas")
        compose.onNodeWithText("Session terminée").performClick()
        compose.onNodeWithText("Tout marquer lu").performClick()
        assertEquals(listOf(2), opened)
        assertTrue(read)
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `agents on top show each announced agent with its state, the menu at the left`() {
        val review = AgentAuthenticationReview(visible = true, agents = Samples.authentication, selected = 1)
        show { AgentsScreen(review, bottom = false, singleScreen = false, menu = GameMenu.pause().press(MenuKey.DOWN).menu, overview = Samples.overview, assets = assets) }
        Captures.save(compose, activity, "agents-haut")
        listOf("Claude", "Codex", "opencode", "Connexion en cours", "Menu").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        assertEquals(2, compose.onAllNodes(hasText("Agents")).fetchSemanticsNodes().size, "le menu et le titre du cadre")
        compose.onNode(selected and hasAnyDescendant(hasText("Codex"))).assertExists()
    }

    @Test
    fun `the Codex device code shows address, code and a QR of the public address only`() {
        var backs = 0
        val review = AgentAuthenticationReview(visible = true, agents = Samples.authentication, selected = 1)
        show { AgentsScreen(review, bottom = true, singleScreen = false, menu = GameMenu.CLOSED, overview = Samples.overview, assets = assets, callbacks = AgentsCallbacks(back = { backs++ })) }
        Captures.save(compose, activity, "agents-bas")
        assertEquals(2, compose.onAllNodes(hasText("Codex")).fetchSemanticsNodes().size, "le titre et l'onglet")
        listOf(Samples.CODEX_URL, "WXYZ-1234", "Saisis ce code", "Attends la confirmation ici", "En attente de Codex", "Annuler et fermer", "Actualiser", "Choisir l’agent")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithContentDescription("QR code de connexion").assertIsDisplayed()
        compose.onNodeWithText("Annuler et fermer").performClick()
        assertEquals(1, backs)
        val qr = authenticationQr(Samples.CODEX_URL)
        val pixels = IntArray(qr.width * qr.height).also { qr.getPixels(it, 0, qr.width, 0, 0, qr.width, qr.height) }
        assertEquals(Samples.CODEX_URL, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(qr.width, qr.height, pixels)))).text)
    }

    @Test
    fun `a key typed for opencode is sent once and the field is emptied`() {
        val sent = mutableListOf<Pair<Agent, String>>()
        val review = AgentAuthenticationReview(visible = true, agents = Samples.authentication, selected = 2)
        show { AgentsScreen(review, bottom = true, singleScreen = false, menu = GameMenu.pause(), overview = Samples.overview, assets = assets, callbacks = AgentsCallbacks(submitKey = { a, k -> sent += a to k })) }
        compose.onNodeWithText("Colle ta clé OpenCode Go.").assertIsDisplayed()
        compose.onNodeWithText("Revenir").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clé de connexion").performTextInput("cle-de-test")
        compose.onNodeWithText("Enregistrer la clé").performClick()
        assertEquals(listOf(Samples.OPENCODE to "cle-de-test"), sent)
        compose.onNodeWithText("Enregistrer la clé").assertDoesNotExist()
    }

    // Repris du test classique (ticket 51) : un agent futur à clé, champ masqué, A de la manette l'envoie et le vide.
    @Test
    fun `a future API key agent gets a masked field that hardware A sends once and empties`() {
        val future = Agent("future", "Agent futur")
        val agents = Samples.authentication.take(2) + app.regie.domain.model.AgentAuthentication(future, false,
            login = app.regie.domain.model.AgentLogin("api_key", "Clé du fournisseur", "Colle ta clé.", provider = "provider"))
        val sent = mutableListOf<Pair<Agent, String>>()
        var action: (() -> Unit)? = null
        val review = AgentAuthenticationReview(visible = true, agents = agents, selected = 2)
        show {
            AgentsScreen(review, bottom = true, singleScreen = false, menu = GameMenu.CLOSED, overview = Samples.overview, assets = assets,
                callbacks = AgentsCallbacks(submitKey = { a, k -> sent += a to k }, bindKeyAction = { action = it }))
        }
        compose.onNodeWithText("Colle ta clé.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clé de connexion").performTextInput("test-only-key")
        compose.onNodeWithContentDescription("Clé de connexion").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.runOnIdle { action!!.invoke() }
        compose.waitForIdle()
        assertEquals(listOf(future to "test-only-key"), sent)
        compose.onNodeWithText("Enregistrer la clé").assertDoesNotExist()
    }

    @Test
    fun `a connected agent offers to disconnect, Claude keeps its manual steps`() {
        val review = AgentAuthenticationReview(visible = true, agents = Samples.authentication, selected = 0)
        show { AgentsScreen(review, bottom = true, singleScreen = false, menu = GameMenu.CLOSED, overview = Samples.overview, assets = assets) }
        compose.onNodeWithText("Déconnecter").assertIsDisplayed()
        compose.onNodeWithText("Le compte reste enregistré par l’agent sur la console.").assertIsDisplayed()
        compose.onNodeWithText("Ouvre Termux, puis lance claude auth login.").assertDoesNotExist()
    }

    @Test
    fun `voice settings keep their functions by touch`() {
        val chosen = mutableListOf<DictationProvider>()
        var permission = false
        var closed = false
        show {
            GameVoiceSettingsScreen(VoiceSettings(DictationProvider.LOCAL, keyConfigured = true),
                callbacks = VoiceSettingsCallbacks(select = { chosen += it }, permission = { permission = true }, close = { closed = true }))
        }
        compose.onNodeWithText("Clé ElevenLabs : enregistrée").assertIsDisplayed()
        compose.onNodeWithTag("game-voice-settings").assertIsDisplayed()
        Captures.save(compose, activity, "voix-bas")
        compose.onNodeWithText("ElevenLabs · en ligne").performClick()
        compose.onNodeWithText("Autoriser le microphone").performClick()
        compose.onNodeWithText("Revenir").performClick()
        assertEquals(listOf(DictationProvider.ELEVENLABS), chosen)
        assertTrue(permission)
        assertTrue(closed)
        assertFalse(compose.onAllNodes(hasText("Enregistrée")).fetchSemanticsNodes().isNotEmpty())
    }
}
