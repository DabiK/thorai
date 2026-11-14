package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
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
    @Test fun `short report shows work tests and next steps inside the compact panel`() {
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent {
                RegieTheme {
                    SummaryPanel(WrittenSummary(listOf("Fait : Minimum corrigé.", "Tests : 68 passent.", "Suite : Commit à valider."), true))
                }
            }
            compose.onNodeWithText("Fait : Minimum corrigé.").assertIsDisplayed()
            compose.onNodeWithText("Tests : 68 passent.").assertIsDisplayed()
            compose.onNodeWithText("Suite : Commit à valider.").assertIsDisplayed()
            compose.onNodeWithText("Y ou B pour fermer").assertIsDisplayed()
        }
    }
}
