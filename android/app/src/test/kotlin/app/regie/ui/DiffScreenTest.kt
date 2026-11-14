package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            compose.onNodeWithText("Explique-moi ce bloc").assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Maintenir pour dicter").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf(1), files); assertEquals(listOf(Button.Y), buttons) }
        }
    }
}
