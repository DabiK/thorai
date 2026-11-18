package app.regie.game.ui.atlas

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.regie.game.engine.world.RoofColors
import app.regie.game.ui.render.GroundPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/** Un atlas chargé : sa description et son image. Les toits recolorés sont gardés en cache. */
class Atlas(val name: String, val spec: AtlasSpec, private val bitmap: Bitmap) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    private val recoloured = HashMap<Pair<String, RoofColors>, ImageBitmap>()

    init {
        spec.frames.forEach { (id, f) ->
            require(f.x >= 0 && f.y >= 0 && f.x + f.w <= bitmap.width && f.y + f.h <= bitmap.height) {
                "$name : l'image $id sort de l'atlas (${bitmap.width} × ${bitmap.height})"
            }
        }
    }

    /** L'image [frameId] seule, toit repeint aux couleurs [roof]. Calculée une fois par couple. */
    fun recoloured(frameId: String, keys: List<Int>, roof: RoofColors): ImageBitmap =
        recoloured.getOrPut(frameId to roof) {
            val f = spec.frames.getValue(frameId)
            val pixels = IntArray(f.w * f.h)
            bitmap.getPixels(pixels, 0, f.w, f.x, f.y, f.w, f.h)
            Bitmap.createBitmap(Recolor.roof(pixels, keys, roof), f.w, f.h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }

    /** Nombre de toits recolorés en cache (pour les tests). */
    internal val cacheSize: Int get() = recoloured.size
}

/** Ce qu'il faut dessiner pour un identifiant à un instant donné. */
class ResolvedSprite(
    val atlas: Atlas,
    val frameId: String,
    val frame: AtlasFrame,
    val footprint: Footprint,
    val roofKeys: List<Int>,
)

/**
 * Les assets du jeu : la palette du sol et les atlas présents. Un atlas absent ou invalide est
 * simplement manquant : le rendu dessine alors des formes de remplacement, et [problems] dit pourquoi.
 */
class GameAssets(
    val palette: GroundPalette = GroundPalette.Default,
    val atlases: List<Atlas> = emptyList(),
    val problems: List<String> = emptyList(),
) {
    /**
     * Cherche [id] dans les atlas : d'abord un objet, puis une animation, puis une image.
     * Une animation donne son image au temps [timeMillis].
     */
    fun resolve(id: String, timeMillis: Long = 0): ResolvedSprite? {
        for (atlas in atlases) {
            val spec = atlas.spec
            val obj = spec.objects[id]
            val ref = obj?.frame ?: id
            val frameId = spec.anims[ref]?.frameAt(timeMillis) ?: ref
            val frame = spec.frames[frameId] ?: continue
            return ResolvedSprite(atlas, frameId, frame, obj?.footprint ?: Footprint(1, 1), obj?.roofKeys.orEmpty())
        }
        return null
    }

    /** L'empreinte d'un objet, une tuile s'il est inconnu. */
    fun footprint(id: String): Footprint =
        atlases.firstNotNullOfOrNull { it.spec.objects[id]?.footprint } ?: Footprint(1, 1)

    /** Les données d'un objet (porte, toit…), s'il existe. */
    fun objectSpec(id: String): AtlasObject? = atlases.firstNotNullOfOrNull { it.spec.objects[id] }

    companion object {
        /** Aucun atlas : tout est dessiné en formes de remplacement. */
        val Placeholder = GameAssets()
    }
}

/** Lit `assets/game/` selon le contrat des assets (`docs/rebrand/plan-dev-jeu.md`). */
object GameAssetLoader {
    const val DIRECTORY = "game"
    val ATLASES = listOf("personnages", "maisons", "decor", "icones", "fonds")

    fun load(assets: AssetManager, directory: String = DIRECTORY, names: List<String> = ATLASES): GameAssets =
        load({ assets.open(it) }, directory, names)

    /** [open] ouvre un fichier par son chemin, ou lève [IOException] s'il n'existe pas. */
    fun load(open: (String) -> InputStream, directory: String = DIRECTORY, names: List<String> = ATLASES): GameAssets {
        val problems = mutableListOf<String>()
        val palette = try {
            open("$directory/palette.json").use { AssetJson.parsePalette(it.readBytes().decodeToString()) }
        } catch (_: IOException) {
            problems += "palette.json absent : palette par défaut"
            GroundPalette.Default
        } catch (e: IllegalArgumentException) { // y compris un JSON mal formé
            problems += "palette.json invalide : ${e.message}"
            GroundPalette.Default
        }
        val atlases = names.mapNotNull { name ->
            try {
                val spec = open("$directory/$name.json").use { AssetJson.parseAtlas(it.readBytes().decodeToString()) }
                val bitmap = open("$directory/$name.png").use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                } ?: throw IllegalArgumentException("$name.png illisible")
                Atlas(name, spec, bitmap)
            } catch (_: IOException) {
                problems += "$name absent : formes de remplacement"
                null
            } catch (e: IllegalArgumentException) {
                problems += "$name invalide : ${e.message}"
                null
            }
        }
        return GameAssets(palette, atlases, problems)
    }
}

/** Charge les assets hors du fil principal ; en attendant, tout est en formes de remplacement. */
@Composable
fun rememberGameAssets(directory: String = GameAssetLoader.DIRECTORY): GameAssets {
    val assets = LocalContext.current.applicationContext.assets
    return produceState(GameAssets.Placeholder, assets, directory) {
        value = withContext(Dispatchers.IO) { GameAssetLoader.load(assets, directory) }
    }.value
}
