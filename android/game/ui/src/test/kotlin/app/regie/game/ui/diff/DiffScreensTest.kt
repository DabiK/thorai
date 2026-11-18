package app.regie.game.ui.diff

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import app.regie.domain.model.Delivery
import app.regie.domain.model.DiffBlock
import app.regie.domain.model.DiffFile
import app.regie.domain.model.DiffKind
import app.regie.domain.model.DiffLine
import app.regie.domain.model.LineChanges
import app.regie.domain.model.SessionId
import app.regie.domain.usecase.DiffExplanation
import app.regie.domain.usecase.DiffReview
import app.regie.game.ui.THOR_BOTTOM
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.house.AgentBlock
import app.regie.game.ui.house.AgentSpan
import app.regie.game.ui.house.AgentTextFormat
import app.regie.game.ui.house.InteriorScenes
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.ScrollInput
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
class DiffScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private val assets: GameAssets by lazy { GameAssetLoader.load(app.assets) }
    private val scenes by lazy { InteriorScenes.load(app.assets) }
    private val steps = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val scroll = ScrollInput(steps, MutableStateFlow(0f))
    private var activity: ComponentActivity? = null

    private fun show(content: @Composable () -> Unit) {
        val clock = mutableLongStateOf(0L)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get().apply {
            setContent { GameTheme(clock = clock) { content() } }
        }
    }

    private class Recorder : DiffCallbacks {
        val events = mutableListOf<String>()
        override fun onKey(key: PadKey) { events += key.name }
        override fun onSpeak(held: Boolean) { events += "L2 $held" }
        override fun onFile(index: Int) { events += "file $index" }
    }

    @Test
    fun `the pinned sheet shows the file line by line, the chosen line framed`() {
        show { CorkBoardScreen(review(lineIndex = 4), session(), assets, scenes, scroll) }
        compose.onNode(hasTestTag("cork-board")).assertIsDisplayed()
        compose.onNodeWithText("android/domain/Overview.kt").assertIsDisplayed()
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithText("Édition 1").assertIsDisplayed()
        compose.onNodeWithText("        .sortedByDescending { it.updatedAt }").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasAnyDescendant(hasText("        .sortedByDescending { it.updatedAt }")), useUnmergedTree = true)
            .assertExists()
        capture("47-modifications-haut.png")
    }

    @Test
    fun `a long line wraps instead of being cut, an excerpt says its position is unknown`() {
        val long = "val message = \"" + "x".repeat(200) + "\""
        val file = DiffFile("a.kt", listOf(DiffBlock("1", listOf(DiffLine(DiffKind.ADDED, long, null, 1)), fileCoordinates = false)), LineChanges(1, 0))
        show { CorkBoardScreen(DiffReview(SessionId("s1"), "regie", listOf(file), loading = false), session(), assets, scenes, scroll) }
        compose.onNodeWithText(long).assertIsDisplayed()
        compose.onNodeWithText("Édition 1 · extrait · position dans le fichier non communiquée").assertIsDisplayed()
    }

    @Test
    fun `a repeated selection brings an offscreen first line back into view`() {
        val lines = (0..70).map { DiffLine(DiffKind.ADDED, "row$it", null, it + 1) }
        val state = mutableStateOf(DiffReview(SessionId("s1"), "regie", listOf(DiffFile("a.kt", listOf(DiffBlock("1", lines, true)), LineChanges(71, 0))), loading = false))
        show { CorkBoardScreen(state.value, session(), assets, scenes, scroll) }
        compose.onNodeWithText("row0").assertIsDisplayed()
        compose.onNode(hasTestTag("diff-lines")).performScrollToIndex(60)
        compose.onNodeWithText("row0").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(selectionRevision = 1) }
        compose.onNodeWithText("row0").assertIsDisplayed()
    }

    @Test
    fun `empty states say what is missing`() {
        val state = mutableStateOf(DiffReview(SessionId("s1"), "regie"))
        show { CorkBoardScreen(state.value, session(), assets, scenes, scroll) }
        compose.onNodeWithText("Chargement des modifications…").assertIsDisplayed()
        compose.runOnIdle { state.value = DiffReview(SessionId("s1"), "regie", listOf(DiffFile("b.kt", emptyList(), null)), loading = false) }
        compose.onNodeWithText("Modifications non communiquées pour ce fichier.").assertIsDisplayed()
        compose.onNodeWithText("+/− ?").assertIsDisplayed()
        compose.runOnIdle { state.value = DiffReview(SessionId("s1"), "regie", loading = false) }
        compose.onNodeWithText("Aucune modification suivie. Les écritures par commande peuvent manquer.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `the notebook lists the files, comments the line with L2 and explains it with Y`() {
        val recorder = Recorder()
        show { DiffNotebook(review(lineIndex = 4), recorder) }
        compose.onNodeWithText("Modifications").assertIsDisplayed()
        compose.onNodeWithText("3 fichiers · modifications suivies").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasAnyDescendant(hasText("android/domain/Overview.kt")), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithText("Ligne 43").assertIsDisplayed()
        compose.onNodeWithText("android/app/SessionList.kt").performClick()
        compose.onNodeWithText("Commenter à la voix").performTouchInput { down(center); up() }
        compose.onNodeWithText("Expliquer le bloc").performClick()
        compose.onNodeWithText("Revenir").performClick()
        assertEquals(listOf("file 2", "L2 true", "L2 false", "Y", "B"), recorder.events)
        capture("47-modifications-bas.png")
    }

    @Test
    @Config(qualifiers = THOR_BOTTOM)
    fun `a removed line of an excerpt says so, no line offers no comment`() {
        val file = DiffFile("a.kt", listOf(DiffBlock("1", listOf(DiffLine(DiffKind.REMOVED, "old", 7, null)), fileCoordinates = false)), LineChanges(0, 1))
        val state = mutableStateOf(DiffReview(SessionId("s1"), "regie", listOf(file), loading = false))
        show { DiffNotebook(state.value, Recorder()) }
        compose.onNodeWithText("Ligne 7 · supprimée · extrait").assertIsDisplayed()
        compose.runOnIdle { state.value = DiffReview(SessionId("s1"), "regie", listOf(DiffFile("b.kt", emptyList(), null)), loading = false) }
        compose.onNodeWithText("Aucune ligne à commenter").assertIsDisplayed()
        compose.onNodeWithText("Commenter à la voix").assertDoesNotExist()
        compose.onNodeWithText("Expliquer le bloc").assertDoesNotExist()
    }

    @Test
    fun `an explanation shows the request, then the agent reply rendered, and the cross scrolls it`() {
        val markdown = AgentTextFormat { text -> listOf(AgentBlock.Text(listOf(AgentSpan(text.replace("**", ""))))) }
        val state = mutableStateOf(DiffReview(SessionId("s1"), "regie", explanation = DiffExplanation("Explique-moi ce bloc.\na.kt · ligne 1", "a.kt · ligne 1")))
        val recorder = Recorder()
        show {
            CorkBoardScreen(state.value, session(), assets, scenes, scroll, format = markdown, single = { DiffNotebook(state.value, recorder, compact = true) })
        }
        compose.onNodeWithText("Explique-moi ce bloc.\na.kt · ligne 1").assertIsDisplayed()
        compose.onNodeWithText("Relis le bloc, puis demande une explication.").assertIsDisplayed()
        compose.onNodeWithText("Demander").performClick()
        compose.onNodeWithText("Revenir aux modifications").performClick()
        assertEquals(listOf("A", "B"), recorder.events)
        val reply = (1..30).joinToString("\n") { "Ligne **$it** de la réponse." }
        compose.runOnIdle {
            state.value = state.value.copy(explanation = state.value.explanation!!.copy(delivery = Delivery.PROMPT, reply = reply, ended = true))
        }
        compose.onNodeWithText("Demander").assertDoesNotExist()
        compose.onNodeWithText("Réponse de l’agent").assertIsDisplayed()
        compose.onNodeWithText("Ligne 1 de la réponse.", substring = true).assertIsDisplayed()
        capture("47-modifications-explication.png")
        repeat(10) { steps.tryEmit(1) }
        compose.waitForIdle()
        compose.onNodeWithText("Expliquer le bloc").assertDoesNotExist()
    }

    @Test
    fun `on a single screen the notebook takes the right of the cork board`() {
        val recorder = Recorder()
        show { CorkBoardScreen(review(lineIndex = 4), session(), assets, scenes, scroll, single = { DiffNotebook(review(lineIndex = 4), recorder, compact = true) }) }
        compose.onNode(hasTestTag("cork-board")).assertIsDisplayed()
        compose.onNodeWithText("Ligne 43").assertIsDisplayed()
        compose.onNodeWithText("Expliquer le bloc").assertIsDisplayed()
        capture("47-modifications-un-ecran.png")
    }

    /** L'écran entier, dessiné en logiciel par Robolectric (graphismes natifs) ; gardé dans `build/`. */
    private fun capture(name: String) {
        compose.waitForIdle()
        val view = requireNotNull(activity).findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/jeu-47").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun review(lineIndex: Int = 0) = DiffReview(
        SessionId("s1"), "regie",
        listOf(
            DiffFile(
                "android/domain/Overview.kt",
                listOf(
                    DiffBlock(
                        "1",
                        listOf(
                            DiffLine(DiffKind.CONTEXT, "class Overview(private val store: SessionStore) {", 40, 40),
                            DiffLine(DiffKind.CONTEXT, "", 41, 41),
                            DiffLine(DiffKind.REMOVED, "    fun sessions() = store.all()", 42, null),
                            DiffLine(DiffKind.ADDED, "    fun sessions() = store.all()", null, 42),
                            DiffLine(DiffKind.ADDED, "        .sortedByDescending { it.updatedAt }", null, 43),
                            DiffLine(DiffKind.ADDED, "        .sortedBy { it.isPaused }", null, 44),
                            DiffLine(DiffKind.CONTEXT, "}", 45, 47),
                        ),
                        fileCoordinates = true,
                    ),
                ),
                LineChanges(12, 3),
            ),
            DiffFile("android/domain/OverviewTest.kt", emptyList(), LineChanges(18, 0)),
            DiffFile("android/app/SessionList.kt", emptyList(), LineChanges(1, 1)),
        ),
        lineIndex = lineIndex,
        loading = false,
    )
}
