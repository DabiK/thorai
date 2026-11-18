package app.regie.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupChecks
import app.regie.domain.model.SetupStep
import app.regie.domain.port.SetupMemory
import app.regie.domain.usecase.ReviewSetup
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.setup.GameSetupScreen
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ticket 53 : le parcours d'accueil du jeu est branché comme le classique, sur `ReviewSetup.press`
 * (`GamepadActivity`). Au doigt, il joue les mêmes actions du cas d'usage ; une permission n'est
 * jamais accordée par l'écran : seule la demande part, vers Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w538dp-h468dp-land-369dpi")
class SetupWiringTest {
    @get:Rule val compose = createEmptyComposeRule()

    private class Memory(override var finished: Boolean = false) : SetupMemory {
        override fun finish() { finished = true }
    }

    private val memory = Memory()
    private val performed = mutableListOf<SetupAction>()
    private val setup = ReviewSetup(memory) { performed += it }

    private fun show() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            val review by setup.state.collectAsStateWithLifecycle()
            if (review.visible) GameTheme {
                GameSetupScreen(review, bottom = true, singleScreen = false, appId = "app.thorai", assets = GameAssets.Placeholder, onAction = setup::press)
            }
        }
    }

    @Test fun `the game screen walks the same use case, step by step`() {
        setup.offerOnLaunch()
        show()
        compose.onNodeWithText("Commencer").performClick()
        assertEquals(SetupStep.TERMUX, setup.state.value.step)
        compose.onNodeWithText("Revenir").performClick()
        assertEquals(SetupStep.WELCOME, setup.state.value.step)
        // B sur la bienvenue referme : le parcours ne se rouvrira plus seul.
        compose.onNodeWithText("Reprendre plus tard").performClick()
        assertFalse(setup.state.value.visible)
        assertTrue(memory.finished)
        assertEquals(emptyList(), performed)
    }

    @Test fun `a permission is only asked, through the use case, never granted by the screen`() {
        setup.update(SetupChecks(termux = true, bridge = BridgeStatus.Connecting))
        setup.open(at = SetupStep.BRIDGE)
        show()
        compose.onNodeWithText("Autoriser les commandes Termux").performClick()
        assertEquals(listOf(SetupAction.GRANT_RUN_COMMAND), performed)
        // L'écran ne change rien à l'état vérifié : seul Android, relu par l'app, l'accorde.
        assertFalse(setup.state.value.checks.runCommand)
        assertEquals(SetupStep.BRIDGE, setup.state.value.step)
        compose.onNodeWithTag("setup-later").performClick()
        assertFalse(setup.state.value.visible)
    }
}
