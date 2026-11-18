package app.regie.game.ui.courier

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.regie.game.engine.courier.Letter
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.demo.DemoTopScreen
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.menu.Captures
import app.regie.game.ui.menu.Samples
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class CourierToastTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ComponentActivity

    private val assets by lazy { Samples.realAssets() }

    private fun show(letter: Letter, now: Instant, world: Boolean = false) {
        val clock = mutableLongStateOf(0)
        compose.mainClock.autoAdvance = false
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Sans la barre de titre de l'activité de test : l'écran entier, comme sur la Thor.
        activity.actionBar?.hide()
        activity.setContent {
            GameTheme(clock = clock) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    if (world) DemoTopScreen(assets)
                    CourierToast(letter, { now }, assets, action = KeyHintSpec("R1", "Voir"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun `the bird brings a letter as it arrives, with the key that opens a validation`() {
        val letter = Samples.courier.latest!!
        show(letter, letter.receivedAt, world = true)
        compose.onNodeWithText("opencode attend ta validation").assertIsDisplayed()
        compose.onNodeWithText("api-meteo · npm run db:migrate").assertIsDisplayed()
        compose.onNodeWithText("Voir").assertIsDisplayed()
        Captures.save(compose, activity, "oiseau-haut")
        compose.mainClock.advanceTimeBy(CourierTiming.SHOWN.inWholeMilliseconds + 1_000)
        compose.onNodeWithText("opencode attend ta validation").assertDoesNotExist()
    }

    @Test
    fun `an old letter does not bring the bird back`() {
        val letter = Samples.courier.latest!!
        show(letter, Instant.fromEpochMilliseconds(letter.receivedAt.toEpochMilliseconds() + 60_000))
        compose.onNodeWithText("opencode attend ta validation").assertDoesNotExist()
    }

    @Test
    fun `only a validation shows the key that opens it`() {
        val letter = Samples.courier.letters.first { it.approval == null }
        show(letter, letter.receivedAt)
        compose.onNodeWithText(letter.title).assertIsDisplayed()
        compose.onNodeWithText("Voir").assertDoesNotExist()
    }
}
