package app.regie.game.ui.house

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import app.regie.domain.model.Agent
import app.regie.domain.model.DictationPhase
import app.regie.domain.model.DictationReview
import app.regie.domain.model.DictationTarget
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionSettingsReview
import app.regie.domain.model.SessionStatus
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class HouseScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private val assets: GameAssets by lazy { GameAssetLoader.load(app.assets) }
    private val scenes by lazy { InteriorScenes.load(app.assets) }

    /** Horloge figée à [time] : les animations restent sur une image. */
    private fun show(time: Long = 0, content: @Composable () -> Unit): ComponentActivity {
        val clock = mutableLongStateOf(time)
        return Robolectric.buildActivity(ComponentActivity::class.java).setup().get().apply {
            setContent { GameTheme(clock = clock) { content() } }
        }
    }

    @Test
    fun `the real atlases give every inhabitant, its room and its animations`() {
        assertTrue(assets.problems.isEmpty(), assets.problems.toString())
        listOf("scribe", "bricoleur", "cartographe").forEach { who ->
            val scene = scenes.getValue("fond.interieur.$who")
            assertTrue(scene.entrance.y > scene.inhabitant.y, "$who : le coursier entre par le bas")
            listOf("travail", "attente", "repos", "sommeil").forEach { assertTrue(assets.resolve("$who.$it") != null, "$who.$it") }
        }
        assertTrue(assets.resolve("coursier.parle") != null && assets.resolve("coursier.debout.haut") != null)
    }

    @Test
    fun `the top shows the house, the activity, the plan and the latest reply verbatim`() {
        val activity = show(time = 400) {
            HouseTopScreen(live(), false, assets, scenes, ageLabel = "4 min", dictation = DictationReview())
        }
        compose.onNode(hasTestTag("house-scene") and hasContentDescription("scribe.travail")).assertExists()
        compose.onNodeWithText("regie").assertIsDisplayed()
        compose.onNodeWithText("ticket/22-tri").assertIsDisplayed()
        compose.onNodeWithText("Au travail · 4 min").assertIsDisplayed()
        // La bulle reprend les mots du fil : la dernière action, le nom du fichier.
        compose.onNode(hasTestTag("house-activity") and hasAnyDescendant(hasText("Modifie")) and hasAnyDescendant(hasText("Overview.kt")), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Ajouter le tri par date").assertIsDisplayed()
        compose.onNodeWithText("Fichiers modifiés").assertExists()
        // La réplique, telle quelle (texte brut sans analyse du markdown), avec le curseur pendant l'écriture.
        compose.onNode(hasTestTag("house-reply")).assertIsDisplayed()
        compose.onNodeWithText("$REPLY▌", useUnmergedTree = true).assertIsDisplayed()
        save(activity, "46-maison-travail.png")
    }

    @Test
    fun `an idle Codex rests in the workshop, without bubble, and the reply has no cursor`() {
        val activity = show {
            HouseTopScreen(live(SessionStatus.IDLE, Agent.CODEX, plan = false), false, assets, scenes, ageLabel = "1 h", dictation = DictationReview())
        }
        compose.onNode(hasContentDescription("bricoleur.repos")).assertExists()
        compose.onNode(hasTestTag("house-activity")).assertDoesNotExist()
        compose.onNode(hasTestTag("quest-log")).assertExists()
        compose.onNodeWithText(REPLY, useUnmergedTree = true).assertIsDisplayed()
        save(activity, "46-maison-repos.png")
    }

    @Test
    fun `while dictating to this session the courier speaks, the room stays`() {
        val dictation = DictationReview(DictationTarget.Message(SessionId("s1")), DictationPhase.RECORDING, "Oui", level = 0.6f, durationMs = 7_000)
        val activity = show {
            HouseTopScreen(live(), false, assets, scenes, ageLabel = "4 min", dictation = dictation)
        }
        compose.onNode(hasTestTag("courier-bubble")).assertIsDisplayed()
        compose.onNode(hasTestTag("house-reply")).assertIsDisplayed()
        save(activity, "46-dictee-haut.png")
    }

    @Test
    fun `a dictation to another session leaves the courier silent`() {
        val dictation = DictationReview(DictationTarget.Message(SessionId("autre")), DictationPhase.RECORDING, "Oui")
        show { HouseTopScreen(live(), false, assets, scenes, null, dictation) }
        compose.onNode(hasTestTag("courier-bubble")).assertDoesNotExist()
    }

    @Test
    fun `the approval card is hosted over the room`() {
        show {
            HouseTopScreen(live(SessionStatus.AWAITING_APPROVAL), false, assets, scenes, null, DictationReview(), approval = { BasicText("Carte de validation") })
        }
        compose.onNodeWithText("Carte de validation").assertIsDisplayed()
        compose.onNode(hasContentDescription("scribe.attente")).assertExists()
    }

    @Test
    fun `an unknown or opening session says so`() {
        show { HouseTopScreen(null, gone = true, assets, scenes, null, DictationReview()) }
        compose.onNodeWithText("Session indisponible.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `the bottom shows the raw thread, the settings table and the session keys`() {
        val callbacks = RecordingCallbacks()
        val activity = show {
            HouseBottomScreen(live(), false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), callbacks)
        }
        compose.onNodeWithText("Ajoute un tri par date dans la liste des sessions.").assertExists()
        compose.onNodeWithText("2 fichiers", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("lancé", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Réglages de Claude").assertIsDisplayed()
        listOf("Mode", "Je valide chaque action", "Modèle", "Opus 5.5", "Réflexion", "Moyenne", "Mode rapide", "Non").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        val keys = listOf("Revenir", "Voir les modifications", "Parler", "Interrompre")
        val lefts = keys.map { compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts, "B X L2 Start, dans l'ordre")
        compose.onNodeWithText("Revenir").performClick()
        compose.onNodeWithText("Voir les modifications").performClick()
        compose.onNodeWithText("Parler").performTouchInput { down(center); up() }
        compose.onNodeWithText("Interrompre").performClick()
        compose.onNodeWithText("→ Modifier").performClick()
        compose.onNodeWithText("Opus 5.5", useUnmergedTree = true).performClick()
        assertEquals(listOf("B", "X", "L2 true", "L2 false", "START", "settings null", "settings model"), callbacks.events)
        save(activity, "46-fil-bas.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `an expanded action shows its output, an idle session hides Start`() {
        show { HouseBottomScreen(live(SessionStatus.IDLE), false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks()) }
        compose.onNodeWithText("Interrompre").assertDoesNotExist()
        compose.onNodeWithText("OverviewTest PASSED\nBUILD SUCCESSFUL in 41s").assertDoesNotExist()
        compose.onNodeWithText("./gradlew :domain:test", useUnmergedTree = true).performClick()
        compose.onNodeWithText("OverviewTest PASSED\nBUILD SUCCESSFUL in 41s").assertExists()
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `editing the table frames the chosen setting, shows the pending value and the editing keys`() {
        val callbacks = RecordingCallbacks()
        val settings = SessionSettingsReview(visible = true, session = session(), selected = 1, value = "sonnet")
        val activity = show {
            HouseBottomScreen(live(), false, null, settings, "Un avis de réglage.", "", null, {}, scrollInput(), callbacks)
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasAnyDescendant(hasText("Sonnet 5")), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Un avis de réglage.").assertIsDisplayed()
        compose.onNodeWithText("Appliquer").performClick()
        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithText("Moyenne", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Voir les modifications").assertDoesNotExist()
        assertEquals(listOf("A", "B", "select 2"), callbacks.events)
        save(activity, "46-reglages-bas.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `a pending approval takes the place of the keys and R1 points to the next one`() {
        show {
            HouseBottomScreen(live(SessionStatus.AWAITING_APPROVAL), false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks(),
                approval = { BasicText("Décision") }, approvalQueued = true)
        }
        compose.onNodeWithText("Décision").assertIsDisplayed()
        compose.onNodeWithText("Voir la suivante").assertIsDisplayed()
        compose.onNodeWithText("Parler").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `a written message is sent with A and a final notice goes away by itself`() {
        val callbacks = RecordingCallbacks()
        var seen = 0
        show {
            HouseBottomScreen(live(), false, null, SessionSettingsReview(), null, "Lance les tests", HouseNotice("Message envoyé.", NoticeTone.NORMAL), { seen++ },
                scrollInput(), callbacks)
        }
        compose.onNodeWithText("Message envoyé.").assertIsDisplayed()
        compose.onNodeWithText("Envoyer").performClick()
        assertEquals(listOf("send"), callbacks.events)
        compose.mainClock.advanceTimeBy(7_000)
        compose.runOnIdle { assertEquals(1, seen) }
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `the dictation shows the live transcript, the repo terms and A B X Y`() {
        val callbacks = RecordingCallbacks()
        val text = "Garde updatedAt pour le reste"
        val state = DictationReview(DictationTarget.Message(SessionId("s1")), DictationPhase.REVIEW, text, vocabulary = listOf("updatedAt"), level = 0.4f, durationMs = 7_000)
        val activity = show { HouseDictationScreen(state, session(), listOf(6..14), scrollInput(), callbacks) }
        compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Message pour").assertIsDisplayed()
        compose.onNodeWithText("0:07").assertIsDisplayed()
        compose.onNodeWithText("Relis avant d’envoyer").assertIsDisplayed()
        val labels = listOf("Envoyer", "Effacer", "Corriger un mot", "Compléter")
        val lefts = labels.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts, "A B X Y")
        labels.forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Régler la voix").performClick()
        assertEquals(listOf("A", "B", "X", "Y", "voice"), callbacks.events)
        save(activity, "46-dictee-bas.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `while recording only B is offered, the recorded transcript says how to review`() {
        val state = DictationReview(DictationTarget.Message(SessionId("s1")), DictationPhase.RECORDING, "Oui, et range", level = 0.8f, durationMs = 3_000)
        show { HouseDictationScreen(state, session(), emptyList(), scrollInput(), RecordingCallbacks()) }
        compose.onNodeWithText("Relâche L2 pour relire").assertIsDisplayed()
        compose.onNodeWithText("Effacer").assertIsDisplayed()
        compose.onNodeWithText("Envoyer").assertDoesNotExist()
    }

    @Test
    fun `on a single screen the house shrinks to the left and the notebook keeps everything`() {
        val activity = show {
            HouseTopScreen(live(), false, assets, scenes, "4 min", DictationReview(), single = {
                HouseBottomScreen(live(), false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks(), compact = true)
            })
        }
        compose.onNode(hasTestTag("house-scene")).assertIsDisplayed()
        compose.onNode(hasTestTag("house-reply")).assertDoesNotExist()
        compose.onNodeWithTag("house-thread").assertIsDisplayed()
        compose.onNodeWithText("Interrompre").assertIsDisplayed()
        compose.onNodeWithText("Réglages de Claude").assertIsDisplayed()
        // Le journal de quête passe sous les pieds du coursier : il ne recouvre ni l'habitant ni le coursier.
        val log = compose.onNode(hasTestTag("quest-log")).assertIsDisplayed().getUnclippedBoundsInRoot()
        val bubble = compose.onNode(hasTestTag("house-activity"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val scene = scenes.getValue("fond.interieur.scribe")
        // La bulle part de l'épaule, 16 pixels du dessin au-dessus des pieds de l'habitant ; ×4, à 369 dpi.
        val courierFeet = bubble.bottom + ((16 + scene.entrance.y - scene.inhabitant.y) * 4 / (369f / 160f)).dp
        assertTrue(log.top >= courierFeet, "journal ${log.top} sur le coursier $courierFeet")
        val sign = compose.onNode(hasTestTag("house-sign")).getUnclippedBoundsInRoot()
        assertTrue(bubble.left >= sign.right || bubble.top >= sign.bottom, "la bulle recouvre le panneau")
        save(activity, "46-un-ecran.png")
    }

    @Test
    fun `on a single screen a dictation keeps the thread and takes the place of the field and the keys`() {
        val state = DictationReview(DictationTarget.Message(SessionId("s1")), DictationPhase.REVIEW, "Garde updatedAt", durationMs = 4_000)
        val activity = show {
            HouseTopScreen(live(), false, assets, scenes, "4 min", state, single = {
                HouseBottomScreen(live(), false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks(), compact = true,
                    threadApproval = { BasicText("Carte de validation") },
                    dictation = { HouseDictationScreen(state, session(), emptyList(), scrollInput(), RecordingCallbacks(), compact = true) })
            })
        }
        compose.onNodeWithTag("house-thread").assertIsDisplayed()
        compose.onNodeWithText(REPLY + "▌", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Carte de validation").assertExists()
        compose.onNodeWithText("Garde updatedAt", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Envoyer").assertIsDisplayed()
        compose.onNodeWithText("Écrire un message").assertDoesNotExist()
        compose.onNodeWithText("Voir les modifications").assertDoesNotExist()
        val thread = compose.onNodeWithTag("house-thread").getUnclippedBoundsInRoot()
        val panel = compose.onNodeWithTag("dictation-panel").getUnclippedBoundsInRoot()
        assertTrue(thread.bottom <= panel.top, "le fil au-dessus, la dictée en bas")
        save(activity, "46-un-ecran-dictee.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `in console mode the notebook names the session it follows`() {
        show { HouseBottomScreen(live(), false, null, SessionSettingsReview(), null, "", null, {}, scrollInput(), RecordingCallbacks(), console = true) }
        compose.onNodeWithText("Claude · regie").assertIsDisplayed()
        compose.onNodeWithTag("house-thread").assertIsDisplayed()
        compose.onNodeWithTag("house-settings").assertIsDisplayed()
    }

    /** Le contenu de la fenêtre, dessiné en logiciel ; gardé dans `build/` pour le relire. */
    private fun save(activity: ComponentActivity, name: String) {
        compose.waitForIdle()
        val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/jeu-46").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
