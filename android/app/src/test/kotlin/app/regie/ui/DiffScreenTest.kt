package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.domain.usecase.*
import app.regie.input.Button
import app.regie.ui.screens.DiffScreen
import app.regie.ui.theme.RegieTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w832dp-h468dp-land-mdpi")
class DiffScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `single screen shows selected line files and reachable actions`() {
        val buttons = mutableListOf<Button>()
        val files = mutableListOf<Int>()
        val review = DiffReview(SessionId("s"), "demo", listOf(
            DiffFile("src/a.kt", listOf(DiffBlock("1", listOf(DiffLine(DiffKind.ADDED, "const limit = 70", null, 40)), true)), LineChanges(1, 0)),
            DiffFile("src/b.kt", emptyList(), null),
        ), loading = false)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                DiffScreen(review, false, true, MutableStateFlow(0f), { buttons += it }, { files += it }, {})
            } }
            compose.onNodeWithText("const limit = 70", substring = true).assertIsDisplayed()
            compose.onNodeWithText("src/b.kt").assertIsDisplayed().performClick()
            compose.onNodeWithText("Expliquer le bloc").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Maintenir pour dicter").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf(1), files); assertEquals(listOf(Button.Y), buttons) }
        }
    }
    @Test fun `file count agrees at one and several files`() {
        val review = mutableStateOf(DiffReview(files = listOf(DiffFile("a.kt", emptyList(), null)), loading = false))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                DiffScreen(review.value, true, false, MutableStateFlow(0f), {}, {}, {})
            } }
            compose.onNodeWithText("1 fichier · modifications suivies").assertIsDisplayed()
            compose.runOnIdle { review.value = review.value.copy(files = review.value.files + DiffFile("b.kt", emptyList(), null)) }
            compose.onNodeWithText("2 fichiers · modifications suivies").assertIsDisplayed()
        }
    }

    @Test fun `a repeated selection brings an offscreen first line back into view`() {
        val review = mutableStateOf(DiffReview(SessionId("s"), "demo", listOf(
            DiffFile("a.kt", listOf(DiffBlock("1", (0..70).map { DiffLine(DiffKind.ADDED, "row$it", null, it + 1) }, true)), LineChanges(71, 0)),
        ), loading = false))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                DiffScreen(review.value, false, false, MutableStateFlow(0f), {}, {}, {})
            } }
            compose.onNodeWithText("row0").assertIsDisplayed()
            compose.onNode(hasScrollAction()).performScrollToIndex(45)
            compose.onNodeWithText("row0").assertDoesNotExist()
            compose.runOnIdle { review.value = review.value.copy(selectionRevision = 1) }
            compose.onNodeWithText("row0").assertIsDisplayed()
        }
    }

    @Test fun `explanation renders agent Markdown and keeps return action on a single screen`() {
        val review = DiffReview(explanation = DiffExplanation("Question", "a.kt ligne 1", delivery = Delivery.PROMPT, reply = "Le seuil **80** remplace `70`.", ended = true))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme {
                DiffScreen(review, false, true, MutableStateFlow(0f), {}, {}, {})
            } }
            compose.onNodeWithText("Le seuil 80 remplace 70.").assertIsDisplayed()
            compose.onNodeWithText("Revenir aux modifications").assertIsDisplayed()
            compose.onNodeWithText("Demander").assertDoesNotExist()
        }
    }

}
