package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import kotlin.test.assertTrue
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.WrittenSummary
import app.regie.ui.screens.SummaryPanel
import app.regie.ui.theme.RegieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w596dp-h300dp-land-mdpi")
class SummaryPanelTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `even long statements and a question occupy fewer than ten visible lines`() {
        val lines = listOf("Fait : ", "Tests : ", "Suite : ").map { it + "texte long ".repeat(24) }
        val question = "Question : " + "question longue ".repeat(20)
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                RegieTheme { SummaryPanel(WrittenSummary(lines, true, question.removePrefix("Question : "))) }
            }
            var count = 0
            for (text in lines + question.replace("Question :", "Question :") + "Point de session" + "Bilan de l’agent") {
                val results = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                count += results.single().lineCount
            }
            assertTrue(count < 10, "$count visible lines")
        }
    }

    @Test fun `short report shows work tests and next steps inside the compact panel`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                RegieTheme {
                    SummaryPanel(WrittenSummary(listOf("Fait : Minimum corrigé.", "Tests : 68 passent.", "Suite : Commit à valider."), true, question = "Veux-tu le commit ?"))
                }
            }
            compose.onNodeWithText("Fait : Minimum corrigé.").assertIsDisplayed()
            compose.onNodeWithText("Tests : 68 passent.").assertIsDisplayed()
            compose.onNodeWithText("Suite : Commit à valider.").assertIsDisplayed()
            compose.onNodeWithText("Fermer avec B ou Y").assertIsDisplayed()
            compose.onNodeWithText("Question : Veux-tu le commit ?").assertIsDisplayed()
        }
    }
}
