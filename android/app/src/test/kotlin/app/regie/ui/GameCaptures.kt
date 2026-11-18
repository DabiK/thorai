package app.regie.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File

/**
 * Les captures des écrans du jeu branchés dans l'app, rangées dans `docs/images/jeu/` quand
 * `-PgameCaptures` donne ce dossier. Sans la propriété, rien n'est écrit.
 */
object GameCaptures {
    private val directory: File? = System.getProperty("game.captures")?.let(::File)

    fun save(compose: ComposeTestRule, activity: Activity, name: String) {
        val dir = directory ?: return
        compose.waitForIdle()
        dir.mkdirs()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "49-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
