package app.regie.game.ui.house

import android.content.res.AssetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.render.ArtPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Où se tiennent les personnages dans un intérieur, en pixels du fond (480 × 270) : la pièce, les
 * pieds de l'habitant sur son tapis et ceux du coursier à l'entrée (clé `scenes` de `fonds.json`,
 * ticket 42).
 */
data class InteriorScene(val roomTop: Int, val inhabitant: ArtPoint, val entrance: ArtPoint) {
    companion object {
        /** Sans `scenes` : la pièce centrée des fonds du ticket 42. */
        val DEFAULT = InteriorScene(roomTop = 33, inhabitant = ArtPoint(236, 170), entrance = ArtPoint(238, 215))
    }
}

/** Lecture de la clé `scenes` de l'atlas `fonds`, que le chargeur des atlas ignore. */
object InteriorScenes {
    fun parse(text: String): Map<String, InteriorScene> {
        val scenes = Json.parseToJsonElement(text).jsonObject["scenes"]?.jsonObject ?: return emptyMap()
        return scenes.mapNotNull { (id, value) ->
            try {
                val scene = value.jsonObject
                fun point(key: String) = scene.getValue(key).jsonObject.let { ArtPoint(it.getValue("x").jsonPrimitive.int, it.getValue("y").jsonPrimitive.int) }
                id to InteriorScene(
                    roomTop = scene["piece"]?.jsonObject?.get("y")?.jsonPrimitive?.int ?: InteriorScene.DEFAULT.roomTop,
                    inhabitant = point("habitant"),
                    entrance = point("entree"),
                )
            } catch (_: NoSuchElementException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }.toMap()
    }

    /** Les scènes de `assets/game/fonds.json` ; aucune si le fichier manque ou ne se lit pas. */
    fun load(assets: AssetManager, directory: String = GameAssetLoader.DIRECTORY): Map<String, InteriorScene> = try {
        assets.open("$directory/fonds.json").use { parse(it.readBytes().decodeToString()) }
    } catch (_: IOException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }

    @Volatile private var loaded: Map<String, InteriorScene>? = null

    /** Lues une fois par processus, hors du fil principal, comme les atlas. */
    suspend fun shared(assets: AssetManager): Map<String, InteriorScene> =
        loaded ?: withContext(Dispatchers.IO) { load(assets) }.also { loaded = it }

    fun peek(): Map<String, InteriorScene>? = loaded
}

@Composable
fun rememberInteriorScenes(): Map<String, InteriorScene> {
    val assets = LocalContext.current.applicationContext.assets
    return produceState(InteriorScenes.peek() ?: emptyMap(), assets) { value = InteriorScenes.shared(assets) }.value
}
