package app.regie.game.ui.scene

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.IntOffset
import app.regie.game.ui.THOR_TOP
import app.regie.game.ui.theme.GameTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = THOR_TOP)
class IrisTransitionTest {
    @get:Rule val compose = createEmptyComposeRule()

    // Horloge du thème figée : les animations du décor restent à leur première image.
    private val frozen = mutableLongStateOf(0L)

    private val paper = Color(0xFFFBE6B2)
    private val center = IntOffset(800, 400)

    private fun show(opening: Boolean, onFinished: () -> Unit): ComponentActivity {
        compose.mainClock.autoAdvance = false
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            GameTheme(clock = frozen) {
                Box(Modifier.fillMaxSize().background(paper)) {
                    IrisTransition(center, Modifier.fillMaxSize(), opening = opening, onFinished = onFinished)
                }
            }
        }
        return activity
    }

    @Test
    fun `the iris closes on its centre in true pixels, then calls back once`() {
        var finished = 0
        val activity = show(opening = false) { finished++ }
        compose.mainClock.advanceTimeBy(IRIS_MILLIS / 2L)
        assertEquals(0, finished)
        val half = snapshot(activity)
        assertEquals(IrisColor.toArgb(), half.getPixel(2, 2), "les coins sont déjà noirs")
        assertEquals(paper.toArgb(), half.getPixel(center.x, center.y), "le centre est encore ouvert")
        // Le bord suit des rangées de 4 pixels : chaque rangée est uniforme sur sa hauteur.
        for (y in 0 until half.height - 4 step 4) {
            val row = (0 until 4).map { half.getPixel(center.x - 300, y + it) }.toSet()
            assertEquals(1, row.size, "rangée $y floue")
        }

        compose.mainClock.advanceTimeBy(IRIS_MILLIS.toLong())
        compose.waitForIdle()
        assertEquals(1, finished)
        val closed = snapshot(activity)
        assertEquals(IrisColor.toArgb(), closed.getPixel(center.x, center.y), "fermé, l'iris reste noir")
    }

    @Test
    fun `the iris opens back and calls back`() {
        var finished = false
        val activity = show(opening = true) { finished = true }
        compose.mainClock.advanceTimeBy(32)
        val start = snapshot(activity)
        assertEquals(IrisColor.toArgb(), start.getPixel(2, 2))
        assertFalse(finished)
        compose.mainClock.advanceTimeBy(IRIS_MILLIS.toLong() + 32)
        compose.waitForIdle()
        assertTrue(finished)
        assertEquals(paper.toArgb(), snapshot(activity).getPixel(2, 2))
    }

    private fun snapshot(activity: ComponentActivity): Bitmap {
        compose.waitForIdle()
        val view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
}
