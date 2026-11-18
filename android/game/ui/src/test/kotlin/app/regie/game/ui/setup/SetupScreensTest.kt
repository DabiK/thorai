package app.regie.game.ui.setup

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentAuthentication
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupChecks
import app.regie.domain.model.SetupPermission
import app.regie.domain.model.SetupReview
import app.regie.domain.model.SetupStep
import app.regie.domain.model.VoiceModel
import app.regie.game.engine.menu.GameMenu
import app.regie.game.engine.menu.MenuKey
import app.regie.game.engine.setup.ArrivalRoad
import app.regie.game.ui.render.GroundCache
import app.regie.game.ui.render.GroundKey
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.menu.MenuCallbacks
import app.regie.game.ui.menu.PauseMenuBottom
import app.regie.game.ui.menu.PauseMenuTop
import app.regie.domain.model.VoiceSettings
import app.regie.game.ui.menu.Samples
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ticket 53 : le parcours d'accueil dans le style du jeu garde les étapes, les vérifications et les
 * actions du ticket 28. Chaque touche de l'étape (A, B, X, Y) est montrée avec son libellé et un
 * toucher joue exactement l'action que le domaine lui donne ; rien n'est accordé par l'écran.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_BOTTOM)
class SetupScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ComponentActivity
    private val assets by lazy { Samples.realAssets() }
    private val pressed = mutableListOf<SetupAction?>()
    private val review = mutableStateOf(SetupReview(true))

    private fun show(start: SetupReview, bottom: Boolean, single: Boolean = false) {
        review.value = start
        showContent { GameSetupScreen(review.value, bottom, single, "app.thorai", assets, { pressed += it }) }
    }

    private fun showContent(content: @Composable () -> Unit) {
        // Le sol de la route, peint d'avance : une capture ne montre jamais l'herbe d'attente.
        GroundCache.prepare(GroundKey.of(ArrivalRoad.build().world.map), assets.palette)
        val clock = mutableLongStateOf(0)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.actionBar?.hide()
        activity.setContent { GameTheme(clock = clock) { content() } }
    }

    private val connected = SetupChecks(termux = true, runCommand = true, bridge = BridgeStatus.Connected)

    @Test
    fun `every step is reachable at the bottom, with its title, its counter and every key of the domain`() {
        show(SetupReview(true), bottom = true)
        SetupStep.entries.forEach { step ->
            val shown = SetupReview(true, step, connected)
            review.value = shown
            compose.waitForIdle()
            compose.onNodeWithTag("game-setup-bottom").assertIsDisplayed()
            val counter = "Étape ${step.ordinal + 1} sur 9" + if (step.optional) " · facultative" else ""
            compose.onNodeWithText(counter).assertIsDisplayed()
            // Chaque touche de l'étape, et seulement elles : un toucher joue l'action du domaine.
            val keys = shown.keys
            listOfNotNull(keys.a, keys.b, keys.x, keys.y).forEach { action ->
                pressed.clear()
                val label = label(action, shown)
                compose.onNode(hasText(label) and isTappable()).performClick()
                assertEquals(listOf<SetupAction?>(action), pressed, "$step, $label")
            }
        }
    }

    @Test
    fun `a touch plays the action of each key, and Reprendre plus tard closes`() {
        show(SetupReview(true, SetupStep.CONSOLE_MODE, SetupChecks(consoleMode = false)), bottom = true)
        Captures.save(compose, activity, "mode-console-bas")
        compose.onNodeWithText("Désactivé").assertIsDisplayed()
        compose.onNodeWithText("Ouvrir Accessibilité").performClick()
        compose.onNodeWithText("Passer").performClick()
        compose.onNodeWithText("Revenir").performClick()
        compose.onNodeWithText("Ouvrir les infos de l’appli").performClick()
        compose.onNodeWithTag("setup-later").performClick()
        assertEquals(
            listOf<SetupAction?>(SetupAction.OPEN_ACCESSIBILITY, SetupAction.SKIP, SetupAction.BACK, SetupAction.OPEN_APP_INFO, SetupAction.CLOSE),
            pressed,
        )
    }

    @Test
    fun `the welcome closes with B, without a second way to leave`() {
        show(SetupReview(true), bottom = true)
        Captures.save(compose, activity, "bienvenue-bas")
        listOf("Bienvenue dans ThorAI", "Il te faut", "Claude, ChatGPT ou OpenCode", "Commencer", "Reprendre plus tard").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        // B « Reprendre plus tard » est la seule sortie : pas de lien en double dans l'en-tête.
        assertEquals(0, compose.onAllNodes(hasTestTagMatcher("setup-later")).fetchSemanticsNodes().size)
    }

    @Test
    fun `while the bridge starts, A says what it waits for and does nothing`() {
        show(SetupReview(true, SetupStep.BRIDGE, SetupChecks(termux = true, runCommand = true, bridge = BridgeStatus.Connecting)), bottom = true)
        compose.onNodeWithTag("setup-primary").assertTextContains("Connexion au pont…").performClick()
        compose.onNodeWithText("Connexion…").assertIsDisplayed()
        assertEquals(emptyList<SetupAction?>(), pressed)
    }

    @Test
    fun `the bridge asks RUN_COMMAND through Android, then the app info once refused for good`() {
        show(SetupReview(true, SetupStep.BRIDGE, SetupChecks(termux = true)), bottom = true)
        Captures.save(compose, activity, "pont-bas")
        compose.onNodeWithText("Autoriser les commandes Termux").performClick()
        review.value = SetupReview(true, SetupStep.BRIDGE, SetupChecks(termux = true, refused = setOf(SetupPermission.RUN_COMMAND)))
        compose.onNodeWithText("Refusé").assertIsDisplayed()
        compose.onNodeWithText("Ouvrir les infos de l’appli").performClick()
        assertEquals(listOf<SetupAction?>(SetupAction.GRANT_RUN_COMMAND, SetupAction.OPEN_APP_INFO), pressed)
    }

    @Test
    fun `the voice step shows the size to download, then the progress`() {
        show(SetupReview(true, SetupStep.VOICE, SetupChecks(microphone = true, voiceModel = VoiceModel.Missing(487_650_000, 1_217_650_000))), bottom = true)
        compose.onNodeWithText("488 Mo à télécharger").assertIsDisplayed()
        compose.onNodeWithText("Il faut 1,2 Go libres. Une seule fois.").assertIsDisplayed()
        compose.onNodeWithText("Télécharger le modèle").performClick()
        review.value = SetupReview(true, SetupStep.VOICE, SetupChecks(microphone = true, voiceModel = VoiceModel.Downloading(0.42f)))
        compose.onNodeWithText("42 %").assertIsDisplayed()
        compose.onNodeWithTag("voice-progress").assertIsDisplayed()
        compose.onNodeWithText("Continuer").assertIsDisplayed()
        Captures.save(compose, activity, "voix-bas")
        assertEquals(listOf<SetupAction?>(SetupAction.DOWNLOAD_VOICE), pressed)
    }

    @Test
    fun `the agents step lists the agents announced by the bridge, opencode included`() {
        val agents = listOf(
            AgentAuthentication(Agent.CLAUDE, true),
            AgentAuthentication(Agent.CODEX, false),
            AgentAuthentication(Samples.OPENCODE, null),
        )
        show(SetupReview(true, SetupStep.AGENTS, connected.copy(agents = agents)), bottom = true)
        Captures.save(compose, activity, "agents-bas")
        listOf("Claude", "Codex", "opencode", "Connecté", "Non connecté", "État inconnu", "Continuer", "Gérer les comptes").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Gérer les comptes").performClick()
        assertEquals(listOf<SetupAction?>(SetupAction.OPEN_AGENTS), pressed)
    }

    @Test
    fun `the end lists what is left and A creates the first session`() {
        show(SetupReview(true, SetupStep.FINISH, connected), bottom = true)
        Captures.save(compose, activity, "fin-bas")
        listOf("C’est prêt", "Étapes prêtes", "Mode console", "Accès à Android", "Comptes des agents", "Terminer").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Créer une session").performClick()
        assertEquals(listOf<SetupAction?>(SetupAction.CREATE_SESSION), pressed)
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the top shows the road to the hamlet, the courier at the sign of the step, and the explanation`() {
        show(SetupReview(true), bottom = false)
        compose.onNodeWithTag("game-setup-top").assertIsDisplayed()
        compose.onNode(hasContentDescription("coursier.welcome")).assertIsDisplayed()
        compose.onNodeWithTag("setup-sign").assertTextContains("Bienvenue", substring = true)
        compose.onNodeWithText("ThorAI pilote Claude Code, Codex et opencode.").assertIsDisplayed()
        assertTrue(assets.problems.isEmpty(), assets.problems.toString())
        Captures.save(compose, activity, "bienvenue-haut")
        // A sur la bienvenue : le coursier marche jusqu'au panneau suivant.
        review.value = SetupReview(true, SetupStep.TERMUX)
        compose.onNode(hasContentDescription("coursier.termux")).assertIsDisplayed()
        compose.onNodeWithTag("setup-sign").assertTextContains("Termux et Debian", substring = true)
        compose.onNodeWithTag("setup-sign").assertTextContains("! À faire", substring = true)
        compose.onNodeWithTag("setup-commands").assertTextContains("bash thor/install-on-device.sh", substring = true)
        Captures.save(compose, activity, "termux-haut")
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the pairing commands name this app and fit beside the steps`() {
        show(SetupReview(true, SetupStep.ANDROID_ACCESS, connected), bottom = false)
        compose.onNodeWithText("L’accès des agents à Android").assertIsDisplayed()
        compose.onNodeWithTag("setup-commands").assertIsDisplayed()
            .assertTextContains("pm grant app.thorai android.permission.WRITE_SECURE_SETTINGS", substring = true)
        Captures.save(compose, activity, "acces-android-haut")
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the agents wait by their sign, the done steps are marked on the road`() {
        val agents = listOf(AgentAuthentication(Agent.CLAUDE, true), AgentAuthentication(Agent.CODEX, false), AgentAuthentication(Samples.OPENCODE, true))
        show(SetupReview(true, SetupStep.AGENTS, connected.copy(microphone = true, voiceModel = VoiceModel.Installed, notifications = true, agents = agents)), bottom = false)
        compose.onNodeWithTag("setup-sign").assertTextContains("Prête", substring = true)
        Captures.save(compose, activity, "agents-haut")
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `one screen shows the road, the explanation, the checks and the keys together`() {
        show(SetupReview(true, SetupStep.NOTIFICATIONS, connected), bottom = false, single = true)
        compose.onNodeWithTag("game-setup-single").assertIsDisplayed()
        compose.onNodeWithText("Les notifications").assertIsDisplayed()
        compose.onNodeWithText("À autoriser").assertIsDisplayed()
        compose.onNodeWithText("Autoriser les notifications").performClick()
        compose.onNodeWithText("Revenir").performClick()
        compose.onNodeWithTag("setup-later").performClick()
        compose.onNode(hasContentDescription("coursier.notifications")).assertIsDisplayed()
        Captures.save(compose, activity, "un-ecran")
        assertEquals(listOf<SetupAction?>(SetupAction.GRANT_NOTIFICATIONS, SetupAction.BACK, SetupAction.CLOSE), pressed)
    }

    @Test
    fun `the pause menu offers Préparer la console and flags an optional step left`() {
        val chosen = mutableListOf<Int>()
        val menu = GameMenu.pause().press(MenuKey.DOWN).menu.press(MenuKey.DOWN).menu.press(MenuKey.DOWN).menu
        showContent {
            PauseMenuBottom(menu, Samples.overview, Samples.courier, Samples.now, assets,
                callbacks = MenuCallbacks(onChoose = { chosen += it }), setupLeft = true)
        }
        compose.onNodeWithText("Préparer la console").assertIsDisplayed()
        compose.onNodeWithText("Étape facultative restante").assertIsDisplayed()
        Captures.save(compose, activity, "menu-pause-bas")
        compose.onNodeWithText("Préparer la console").performClick()
        assertEquals(listOf(3), chosen)
    }

    @Test
    @Config(qualifiers = THOR_TOP)
    fun `the pause menu page of Préparer la console says where the setup resumes`() {
        val menu = GameMenu.pause().press(MenuKey.DOWN).menu.press(MenuKey.DOWN).menu.press(MenuKey.DOWN).menu
        showContent {
            PauseMenuTop(menu, Samples.overview, Samples.courier, VoiceSettings(), Samples.now, assets, singleScreen = false, setupLeft = true)
        }
        compose.onNodeWithText("Reprend le parcours à la première étape restante.").assertIsDisplayed()
        compose.onNodeWithText("Une étape facultative reste à faire.").assertIsDisplayed()
        Captures.save(compose, activity, "menu-pause-haut")
    }

    private fun isTappable() = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick)

    private fun hasTestTagMatcher(tag: String) = SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, tag)

    /** Les libellés de `docs/textes.md`, comme dans la vue classique. */
    private fun label(action: SetupAction, review: SetupReview): String = when (action) {
        SetupAction.NEXT -> if (review.step == SetupStep.WELCOME) "Commencer" else "Continuer"
        SetupAction.BACK -> "Revenir"
        SetupAction.SKIP -> "Passer"
        SetupAction.CLOSE -> if (review.step == SetupStep.FINISH) "Terminer" else "Reprendre plus tard"
        SetupAction.OPEN_TERMUX_RELEASES -> "Ouvrir la page de Termux"
        SetupAction.COPY_INSTALL_COMMANDS, SetupAction.COPY_PAIRING_COMMANDS -> if (review.checks.termux) "Copier et ouvrir Termux" else "Copier les commandes"
        SetupAction.GRANT_RUN_COMMAND -> "Autoriser les commandes Termux"
        SetupAction.RETRY_BRIDGE -> "Relancer le pont"
        SetupAction.GRANT_MICROPHONE -> "Autoriser le micro"
        SetupAction.DOWNLOAD_VOICE -> "Télécharger le modèle"
        SetupAction.GRANT_NOTIFICATIONS -> "Autoriser les notifications"
        SetupAction.OPEN_ACCESSIBILITY -> "Ouvrir Accessibilité"
        SetupAction.OPEN_APP_INFO -> "Ouvrir les infos de l’appli"
        SetupAction.OPEN_DEVELOPER_OPTIONS -> "Ouvrir les options développeur"
        SetupAction.OPEN_AGENTS -> if (review.done) "Gérer les comptes" else "Connecter les agents"
        SetupAction.CREATE_SESSION -> "Créer une session"
    }
}

/**
 * Les captures du ticket 53, rangées dans `docs/images/jeu/` quand `-PgameCaptures` donne ce dossier
 * (`./gradlew :game-ui:testDebugUnitTest --rerun -PgameCaptures=$PWD/../docs/images/jeu`). Sans la
 * propriété, rien n'est écrit.
 */
private object Captures {
    private val directory: File? = System.getProperty("game.captures")?.let(::File)

    fun save(compose: ComposeTestRule, activity: Activity, name: String) {
        val dir = directory ?: return
        compose.waitForIdle()
        dir.mkdirs()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "53-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
