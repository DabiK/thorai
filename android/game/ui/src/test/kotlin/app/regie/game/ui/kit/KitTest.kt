package app.regie.game.ui.kit

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import app.regie.domain.model.Agent
import app.regie.domain.model.DiffKind
import app.regie.domain.model.DiffLine
import app.regie.domain.model.SessionStatus
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.theme.GameColors
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class KitTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun show(content: @Composable () -> Unit) {
        // Horloge figée : les animations restent à leur première image.
        val clock = mutableLongStateOf(0)
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            GameTheme(clock = clock) { Column { content() } }
        }
    }

    /** Sur la Thor, 48 pixels de l'écran à 369 dpi. */
    private val px48 = (48 / (369f / 160f)).dp

    @Test
    fun `the hint bar orders its keys as in the interface texts`() {
        show {
            KeyHintBar(
                listOf(
                    KeyHintSpec("Start", "Menu"),
                    KeyHintSpec(listOf("L1", "R1"), "Changer de maison"),
                    KeyHintSpec("B", "Agents"),
                    KeyHintSpec("A", "Y aller"),
                    KeyHintSpec("Croix", "Choisir"),
                ),
            )
        }
        val lefts = listOf("Choisir", "Y aller", "Agents", "Changer de maison", "Menu")
            .map { compose.onNodeWithText(it).getUnclippedBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts)
    }

    @Test
    fun `key order follows the interface texts`() {
        val sorted = KeyOrder.sort(listOf(KeyHintSpec("R2", "b"), KeyHintSpec("Y", "a"), KeyHintSpec("Stick", "c"), KeyHintSpec("X", "d")))
        assertEquals(listOf("Stick", "X", "Y", "R2"), sorted.map { it.keys.first() })
    }

    @Test
    fun `state tags read the session state from afar`() {
        show {
            TagState.entries.forEach { StateTag(it) }
            StateTag(TagState.WORKING, label = "Étape 3 sur 5")
        }
        listOf("Au travail", "! Attend ta validation", "Au repos", "Échec", "Étape 3 sur 5").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        assertEquals(TagState.IDLE, TagState.of(SessionStatus.STOPPED))
        assertEquals(TagState.WAITING, TagState.of(SessionStatus.AWAITING_APPROVAL))
    }

    @Test
    fun `the sign, the bubble, the toast and the dialog show their texts`() {
        show {
            SignPanel("regie", subtitle = "tri des sessions", state = TagState.WORKING)
            SignPanel("api-meteo", subtitle = "migration", state = TagState.WAITING)
            Bubble { BubbleText("Modification · Overview.kt") }
            Toast("La cartographe attend ta validation", subtitle = "api-meteo · migration", alert = true, action = KeyHintSpec("R1", "Voir"))
            DialogBox(speaker = "Claude") { DialogText("Les tests passent.") }
        }
        listOf(
            "regie", "tri des sessions", "Au travail", "api-meteo", "! Attend ta validation",
            "Modification · Overview.kt", "La cartographe attend ta validation", "Voir", "Claude", "Les tests passent.",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        // Le cartouche est à cheval sur le haut du cadre, au-dessus de la réplique.
        val name = compose.onNodeWithText("Claude").getUnclippedBoundsInRoot()
        val line = compose.onNodeWithText("Les tests passent.").getUnclippedBoundsInRoot()
        assertTrue(name.bottom < line.top)
    }

    @Test
    fun `the choice menu marks the selected option and shows each key`() {
        show {
            ChoiceMenu(
                listOf(Choice("Autoriser une fois", "A"), Choice("Refuser", "B"), Choice("Refuser et dicter une consigne", "L2", enabled = false)),
                selected = 1,
            )
        }
        val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        compose.onAllNodes(selected).assertCountEquals(1)
        compose.onNode(selected and hasAnyDescendant(hasText("Refuser"))).assertIsDisplayed()
        compose.onNodeWithText("Autoriser une fois").assertIsDisplayed()
        compose.onNodeWithText("L2").assertIsDisplayed()
    }

    @Test
    fun `diff lines keep the agent text with number and sign`() {
        show {
            PixelDiffLine(DiffLine(DiffKind.REMOVED, "    fun sessions() = store.all()", 42, null))
            PixelDiffLine(DiffLine(DiffKind.ADDED, "        .sortedByDescending { it.updatedAt }", null, 43), selected = true)
        }
        compose.onNodeWithText("    fun sessions() = store.all()").assertIsDisplayed()
        compose.onNodeWithText("42").assertIsDisplayed()
        compose.onNodeWithText("−").assertIsDisplayed()
        compose.onNodeWithText("43").assertIsDisplayed()
    }

    @Test
    fun `the mic meter is a staircase of whole pixels, never empty`() {
        assertEquals(listOf(1, 8, 16, 16), MicLevels.steps(listOf(0f, 0.5f, 1f, 3f), 16))
    }

    @Test
    fun `blinks are frank steps`() {
        assertEquals(1f, Blink.pulse(0))
        assertEquals(0.35f, Blink.pulse(400))
        assertEquals(1f, Blink.dot(0, 0))
        assertEquals(0.15f, Blink.dot(0, 1))
        assertEquals(1f, Blink.dot(400, 1))
        assertTrue(Blink.bobUp(500))
    }

    @Test
    fun `known agents have their colour, an unknown agent a neutral one`() {
        val colors = GameColors()
        assertEquals(colors.claude, colors.agent(Agent.CLAUDE))
        assertEquals(colors.codex, colors.agent(Agent.CODEX))
        assertEquals(colors.opencode, colors.agent(Agent("opencode", "opencode")))
        assertEquals(colors.otherAgent, colors.agent(Agent("aider")))
    }

    @Test
    fun `a chip is exactly 48 screen pixels high, A B X Y are square`() {
        show {
            KeyChip("X", Modifier.testTag("x"))
            KeyChip("Start", Modifier.testTag("start"))
        }
        compose.onNodeWithTag("x").assertHeightIsEqualTo(px48).assertWidthIsEqualTo(px48)
        compose.onNodeWithTag("start").assertHeightIsEqualTo(px48)
        val start = compose.onNodeWithTag("start").getUnclippedBoundsInRoot()
        assertTrue(start.right - start.left > px48, "Start : ${start.right - start.left}")
    }
}
