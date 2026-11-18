package app.regie.game.ui.atlas

import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.render.GroundPalette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Une image d'un atlas, en pixels natifs ; ([ax], [ay]) est l'ancrage, au niveau des pieds. */
data class AtlasFrame(val x: Int, val y: Int, val w: Int, val h: Int, val ax: Int, val ay: Int)

/** Une animation : des images qui se suivent, chacune [ms] millisecondes, en boucle. */
data class AtlasAnim(val frames: List<String>, val ms: Int) {
    /** L'image à montrer au temps [timeMillis]. */
    fun frameAt(timeMillis: Long): String = frames[((timeMillis / ms) % frames.size).toInt()]
}

/** Empreinte au sol, en tuiles de 16 px. */
data class Footprint(val w: Int, val h: Int)

/**
 * Un objet posable : son image, son empreinte, sa porte (tuile relative au coin haut gauche de
 * l'empreinte) et les trois couleurs clés de son toit, du clair au sombre, en ARGB.
 */
data class AtlasObject(
    val frame: String,
    val footprint: Footprint = Footprint(1, 1),
    val door: TilePos? = null,
    val roofKeys: List<Int> = emptyList(),
)

/** Le contenu d'un `<atlas>.json` (contrat de `docs/rebrand/plan-dev-jeu.md`). */
data class AtlasSpec(
    val pixel: Int,
    val frames: Map<String, AtlasFrame>,
    val anims: Map<String, AtlasAnim> = emptyMap(),
    val objects: Map<String, AtlasObject> = emptyMap(),
)

/** Lecture des fichiers JSON des assets ; une erreur de contrat lève [IllegalArgumentException]. */
object AssetJson {
    private val json = Json { isLenient = false }

    fun parseAtlas(text: String): AtlasSpec {
        val root = json.parseToJsonElement(text).jsonObject
        val pixel = root["pixel"]?.jsonPrimitive?.intOrNull ?: 1
        require(pixel >= 1) { "pixel doit valoir au moins 1 : $pixel" }
        val frames = root.objectOf("frames").mapValues { (id, value) ->
            val f = value.jsonObject
            val frame = AtlasFrame(f.int("x"), f.int("y"), f.int("w"), f.int("h"), f.int("ax"), f.int("ay"))
            require(frame.w > 0 && frame.h > 0) { "Image $id vide" }
            frame
        }
        val anims = root.objectOf("anims").mapValues { (id, value) ->
            val a = value.jsonObject
            val names = a["frames"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            require(names.isNotEmpty()) { "Animation $id sans image" }
            names.forEach { require(it in frames) { "Animation $id : image $it inconnue" } }
            val ms = a.int("ms")
            require(ms > 0) { "Animation $id : ms doit être positif" }
            AtlasAnim(names, ms)
        }
        val objects = root.objectOf("objects").mapValues { (id, value) ->
            val o = value.jsonObject
            val frame = o["frame"]?.jsonPrimitive?.content ?: id
            require(frame in frames || frame in anims) { "Objet $id : image $frame inconnue" }
            val footprint = o["footprint"]?.jsonObject?.let { Footprint(it.int("w"), it.int("h")) } ?: Footprint(1, 1)
            require(footprint.w > 0 && footprint.h > 0) { "Objet $id : empreinte vide" }
            val door = o["door"]?.jsonObject?.let { TilePos(it.int("tx"), it.int("ty")) }
            val keys = (o["roofKeys"] as? JsonArray)?.map { color(it.jsonPrimitive.content) }.orEmpty()
            require(keys.isEmpty() || keys.size == 3) { "Objet $id : trois couleurs clés attendues" }
            AtlasObject(frame, footprint, door, keys)
        }
        return AtlasSpec(pixel, frames, anims, objects)
    }

    /**
     * `palette.json` : `{"colors": ["#RRGGBB", …], "roles": {"grass.light": "#RRGGBB", …}}`.
     * Les rôles absents gardent leur valeur par défaut ([GroundPalette.ROLES]).
     */
    fun parsePalette(text: String, base: GroundPalette = GroundPalette.Default): GroundPalette {
        val root = json.parseToJsonElement(text).jsonObject
        val colors = (root["colors"] as? JsonArray)?.map { color(it.jsonPrimitive.content) }.orEmpty()
        val roles = root.objectOf("roles").mapValues { (_, value) -> color(value.jsonPrimitive.content) }
        return base.withRoles(roles).copy(colors = colors)
    }

    /** `#RRGGBB` ou `#AARRGGBB`, en ARGB. */
    fun color(text: String): Int {
        val hex = text.removePrefix("#")
        require(text.startsWith("#") && (hex.length == 6 || hex.length == 8)) { "Couleur invalide : $text" }
        val value = hex.toLong(16).toInt()
        return if (hex.length == 6) (0xFF shl 24) or value else value
    }

    /** La couleur clé d'un toit pour un rôle de [RoofColors]. */
    internal fun roofTargets(roof: RoofColors): List<Int> = listOf(roof.light, roof.base, roof.shadow)

    private fun JsonObject.objectOf(key: String): Map<String, JsonElement> = (this[key] as? JsonObject).orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: throw IllegalArgumentException("Champ $key manquant")
}
