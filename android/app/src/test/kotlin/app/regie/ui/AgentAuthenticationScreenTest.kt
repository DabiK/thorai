package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import app.regie.domain.model.*
import app.regie.ui.screens.AgentAuthenticationScreen
import app.regie.ui.screens.authenticationQr
import app.regie.ui.theme.RegieTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-mdpi")
class AgentAuthenticationScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun `compact connection shows a readable code, URL, QR and exit together`() {
        val review = AgentAuthenticationReview(visible = true, agents = listOf(AgentAuthentication(Agent.CODEX, false, phase = AuthenticationPhase.WAITING, url = "https://auth.openai.com/codex/device", code = "ABCD-EFGHI")))
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { AgentAuthenticationScreen(review, false, true, {}, {}, {}) } }
            compose.onNodeWithText("ABCD-EFGHI").assertIsDisplayed()
            compose.onNodeWithText("https://auth.openai.com/codex/device").assertIsDisplayed()
            compose.onNodeWithContentDescription("QR code de connexion").assertIsDisplayed()
            compose.onNodeWithText("Annuler et fermer").assertIsDisplayed()
            compose.onNodeWithText("Actualiser").assertIsDisplayed()
        }
    }
    @Test fun `a future API key agent uses a masked transient field and clears it after hardware A`() {
        val future = Agent("future", "Agent futur")
        val review = AgentAuthenticationReview(visible = true, selected = 3, agents = listOf(AgentAuthentication(Agent.CLAUDE, true), AgentAuthentication(Agent.CODEX, true), AgentAuthentication(Agent("third", "Troisième agent"), true), AgentAuthentication(future, false, login = AgentLogin("api_key", "Clé du fournisseur", "Aide", "provider"))))
        val sent = mutableListOf<String>()
        var action: (() -> Unit)? = null
        Robolectric.buildActivity(ComponentActivity::class.java).use { host ->
            host.setup().get().setContent { RegieTheme { AgentAuthenticationScreen(review, false, true, {}, {}, {}, { _, key -> sent += key }, { action = it }) } }
            compose.onNodeWithText("AGENT FUTUR").assertIsDisplayed()
            kotlin.test.assertTrue(compose.onNodeWithText("AGENT FUTUR").getUnclippedBoundsInRoot().right <= compose.onRoot().getUnclippedBoundsInRoot().right)
            compose.onNodeWithContentDescription("Clé de connexion").performTextInput("test-only-key")
            compose.onNodeWithContentDescription("Clé de connexion").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Password))
            compose.runOnIdle { action!!.invoke() }
            compose.waitForIdle()
            assertEquals(listOf("test-only-key"), sent)
            compose.onNodeWithContentDescription("Clé de connexion").assertTextEquals("")
            compose.onNodeWithText("Enregistrer la clé").assertDoesNotExist()
        }
        assertEquals(null, action)
    }

    @Test fun `QR decodes to the public URL without any code or token`() {
        val bitmap = authenticationQr("https://auth.openai.com/codex/device")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals("https://auth.openai.com/codex/device", result.text)
    }
}
