package app.regie.game.ui.approval

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalAnswer
import app.regie.domain.model.ApprovalChange
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.ApprovalOption
import app.regie.domain.model.ApprovalOptionKind
import app.regie.domain.model.ApprovalReply
import app.regie.domain.model.DictationReview
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionSettingsReview
import app.regie.domain.model.SessionStatus
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.house.HouseBottomScreen
import app.regie.game.ui.house.HouseTopScreen
import app.regie.game.ui.house.InteriorScenes
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.RecordingCallbacks
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.house.live
import app.regie.game.ui.house.session
import app.regie.game.ui.theme.GameTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class ApprovalScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private val assets: GameAssets by lazy { GameAssetLoader.load(app.assets) }
    private val scenes by lazy { InteriorScenes.load(app.assets) }

    private fun show(content: @Composable () -> Unit) {
        val clock = mutableLongStateOf(0L)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().apply {
            setContent { GameTheme(clock = clock) { content() } }
        }
    }

    private val steps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val scroll = ScrollInput(steps, MutableStateFlow(0f))

    @Test
    fun `the inhabitant holds out the parchment, the request is shown verbatim`() {
        show { ApprovalScene(review(command()), session(SessionStatus.AWAITING_APPROVAL), assets, scenes, scroll) }
        compose.onNode(hasTestTag("approval-room") and hasContentDescription("scribe.attente")).assertExists()
        compose.onNode(hasTestTag("approval-marker"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Claude").assertIsDisplayed()
        compose.onNodeWithText("Exécuter une commande").assertIsDisplayed()
        compose.onNodeWithText(COMMAND).assertIsDisplayed()
        compose.onNodeWithText("dans /home/dev/regie").assertIsDisplayed()
        compose.onNodeWithText(REASON).assertIsDisplayed()
        compose.onNodeWithText("android/app/build").assertIsDisplayed()
        capture("47-validation-haut.png")
    }

    @Test
    fun `the cross scrolls the whole parchment on the top screen, which is not touch`() {
        val request = command(plan = LONG_PLAN)
        show { ApprovalScene(review(request), session(SessionStatus.AWAITING_APPROVAL), assets, scenes, scroll) }
        compose.onNodeWithText(LONG_PLAN, substring = true).assertExists()
        compose.onNodeWithText(FILE_AFTER).assertIsNotDisplayed()
        repeat(12) { steps.tryEmit(1) }
        compose.waitForIdle()
        compose.onNodeWithText(FILE_AFTER).assertIsDisplayed()
    }

    @Test
    fun `a file change shows its before and after, missing data is announced`() {
        val request = Approval(ApprovalId("f"), SessionId("s1"), ApprovalKind.FILE_CHANGE, "Overview.kt",
            changes = listOf(ApprovalChange("android/domain/Overview.kt", "fun sessions() = store.all()", "fun sessions() = store.all().sorted()")))
        show { ApprovalScene(review(request), session(SessionStatus.AWAITING_APPROVAL), assets, scenes, scroll) }
        compose.onNodeWithText("Modifier un fichier").assertIsDisplayed()
        compose.onNodeWithText("Justification non communiquée.").assertIsDisplayed()
        compose.onNodeWithText("android/domain/Overview.kt").assertIsDisplayed()
        compose.onNodeWithText("fun sessions() = store.all()").assertIsDisplayed()
        compose.onNodeWithText("fun sessions() = store.all().sorted()").assertIsDisplayed()
        compose.onNodeWithText("Fichiers concernés").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `the bottom menu offers every choice with its key, by touch too`() {
        val events = mutableListOf<String>()
        val other = Approval(ApprovalId("b"), SessionId("s2"), ApprovalKind.COMMAND, "rm -rf build", options = OPTIONS)
        val callbacks = object : ApprovalCallbacks {
            override fun onKey(key: PadKey) { events += key.name }
            override fun onSpeak(held: Boolean) { events += "L2 $held" }
            override fun onSelect(id: ApprovalId) { events += "open ${id.value}" }
        }
        val request = command()
        show { ApprovalNotebook(ApprovalReview(request, listOf(request, other), pending = true), listOf(session()), callbacks) }
        compose.onNodeWithText("Validation").assertIsDisplayed()
        compose.onNodeWithText("! Claude · regie").assertIsDisplayed()
        compose.onNodeWithText("! Attend ta validation").assertIsDisplayed()
        val labels = listOf("Autoriser une fois", "Refuser", "Autoriser pour la session", "Refuser et dicter une consigne")
        val tops = labels.map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops, "A, B, X, L2")
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasAnyDescendant(hasText("Autoriser une fois"))).assertExists()
        compose.onNodeWithText("Maintiens L2 pour dicter.").assertIsDisplayed()
        labels.take(3).forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("Refuser et dicter une consigne").performTouchInput { down(center); up() }
        compose.onNodeWithText("rm -rf build", substring = true).performClick()
        compose.onNodeWithText("Revenir").performClick()
        compose.onNodeWithText("Voir la suivante").performClick()
        assertEquals(listOf("A", "B", "X", "L2 true", "L2 false", "open b", "L1", "R1"), events)
        capture("47-validation-bas.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `without a session option X is absent and said so, an answer disables the choices`() {
        val decisions = mutableListOf<ApprovalDecision>()
        val request = command(options = listOf(OPTIONS[0], OPTIONS[2]))
        val review = mutableStateOf(ApprovalReview(request, listOf(request), pending = true))
        show { ApprovalMenu(review.value, standalone = true, onChoose = { decisions += it.decision }, onSpeak = {}) }
        compose.onNodeWithText("Autoriser pour la session").assertDoesNotExist()
        compose.onNodeWithText("Autorisation pour la session indisponible.").assertIsDisplayed()
        compose.runOnIdle { review.value = review.value.copy(pending = false, answer = ApprovalAnswer(ApprovalReply.ACCEPTED)) }
        compose.onNodeWithText("Réponse reçue.").assertIsDisplayed()
        compose.onNodeWithText("Autoriser une fois").performClick()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        assertEquals(emptyList(), decisions)
    }

    @Test
    fun `on a single screen the room is behind the parchment and the notebook takes the right`() {
        val request = command()
        val review = ApprovalReview(request, listOf(request), pending = true)
        val callbacks = object : ApprovalCallbacks {
            override fun onKey(key: PadKey) = Unit
            override fun onSpeak(held: Boolean) = Unit
            override fun onSelect(id: ApprovalId) = Unit
        }
        show {
            ApprovalScene(review, session(SessionStatus.AWAITING_APPROVAL), assets, scenes, scroll, single = {
                ApprovalNotebook(review, listOf(session()), callbacks, compact = true)
            })
        }
        compose.onNodeWithText(COMMAND).assertIsDisplayed()
        compose.onNodeWithText("Autoriser une fois").assertIsDisplayed()
        compose.onNodeWithText("Refuser et dicter une consigne").assertIsDisplayed()
        capture("47-validation-un-ecran.png")
    }

    @Test
    fun `nothing selected says the review is over`() {
        show { ApprovalScene(ApprovalReview(), null, assets, scenes, scroll) }
        compose.onNodeWithText("Validation terminée.").assertIsDisplayed()
    }

    @Test
    fun `in the house the parchment is held out and says the rest is in the thread`() {
        val request = command(plan = LONG_PLAN)
        show {
            HouseTopScreen(live(SessionStatus.AWAITING_APPROVAL), false, assets, scenes, null, DictationReview(), approval = {
                ApprovalParchment(request, session().agent, assets, scroll = rememberScrollState(), overflowNote = true)
            })
        }
        compose.onNode(hasContentDescription("scribe.attente")).assertExists()
        compose.onNode(hasTestTag("approval-marker"), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(COMMAND).assertIsDisplayed()
        compose.onNodeWithText("La suite se lit dans le fil.").assertIsDisplayed()
        capture("47-maison-validation-haut.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `in the house the whole parchment ends the thread and the cross reads it to the end`() {
        val request = command(plan = LONG_PLAN)
        val review = ApprovalReview(request, listOf(request), pending = true)
        val decisions = mutableListOf<ApprovalDecision>()
        val threadSteps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        show {
            HouseBottomScreen(
                live(SessionStatus.AWAITING_APPROVAL), false, null, SessionSettingsReview(), null, "", null, {},
                ScrollInput(threadSteps, MutableStateFlow(0f)), RecordingCallbacks(),
                approval = { ApprovalMenu(review, standalone = false, onChoose = { decisions += it.decision }) },
                threadApproval = { ApprovalParchment(request, session().agent, assets) },
            )
        }
        // Le parchemin se lit depuis son début ; la croix descend jusqu'à sa fin.
        compose.onNodeWithText("Exécuter une commande").assertIsDisplayed()
        compose.onNodeWithText(FILE_AFTER).assertIsNotDisplayed()
        // Dans une maison, L2 parle à l'agent : pas de refus dicté dans le menu.
        compose.onNodeWithText("Refuser et dicter une consigne").assertDoesNotExist()
        compose.onNodeWithText("Parler").assertDoesNotExist()
        capture("47-maison-validation-bas.png")
        repeat(20) { threadSteps.tryEmit(1) }
        compose.waitForIdle()
        compose.onNodeWithText(FILE_AFTER).assertIsDisplayed()
        compose.onNodeWithText("Refuser").performClick()
        assertEquals(listOf(ApprovalDecision.REJECT), decisions)
    }

    /** L'écran entier, dessiné par Robolectric ; gardé dans `build/` pour le relire. */
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/jeu-47").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun review(request: Approval) = ApprovalReview(request, listOf(request), pending = true)

    private fun command(plan: String? = null, options: List<ApprovalOption> = OPTIONS) = Approval(
        ApprovalId("a"), SessionId("s1"), ApprovalKind.COMMAND, COMMAND, REASON, "/home/dev/regie",
        paths = listOf("android/app/build", "android/domain/Overview.kt"), plan = plan,
        changes = if (plan == null) emptyList() else listOf(ApprovalChange("android/domain/Overview.kt", null, FILE_AFTER)),
        options = options,
    )

    private companion object {
        const val COMMAND = "./gradlew :app:connectedCheck"
        const val REASON = "Vérifier le tri dans l'app, sur la console."
        const val FILE_AFTER = "fun sessions() = store.all().sortedByDescending { it.updatedAt }"
        val LONG_PLAN = (1..14).joinToString("\n") { "$it. Étape du plan numéro $it, à lire en entier avant de valider." }
        val OPTIONS = listOf(
            ApprovalOption("yes", ApprovalOptionKind.ALLOW_ONCE, "Yes"),
            ApprovalOption("allow_for_session", ApprovalOptionKind.ALLOW_ALWAYS, "Always"),
            ApprovalOption("no", ApprovalOptionKind.REJECT_ONCE, "No"),
        )
    }
}
