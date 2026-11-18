package app.regie.game.ui.atlas

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.regie.game.engine.village.HouseCatalog
import app.regie.game.engine.village.HouseModel
import app.regie.game.engine.village.PixelPoint
import app.regie.game.engine.village.PixelRect
import app.regie.game.engine.world.RoofColors
import app.regie.game.ui.render.GroundPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException
import java.io.InputStream

/** Un atlas chargé : sa description et son image. Les images recolorées sont gardées en cache. */
class Atlas(val name: String, val spec: AtlasSpec, private val bitmap: Bitmap) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    private val recoloured = HashMap<Pair<String, RoofColors>, ImageBitmap>()
    private val houses = HashMap<HouseKey, ImageBitmap>()
    private val mapped = HashMap<Pair<String, Map<Int, Int>>, ImageBitmap>()

    private data class HouseKey(val frameId: String, val roof: RoofColors?, val light: WindowLight)

    init {
        spec.frames.forEach { (id, f) ->
            require(f.x >= 0 && f.y >= 0 && f.x + f.w <= bitmap.width && f.y + f.h <= bitmap.height) {
                "$name : l'image $id sort de l'atlas (${bitmap.width} × ${bitmap.height})"
            }
        }
    }

    /** L'image [frameId] seule, toit repeint aux couleurs [roof]. Calculée une fois par couple. */
    fun recoloured(frameId: String, keys: List<Int>, roof: RoofColors): ImageBitmap = synchronized(recoloured) {
        recoloured.getOrPut(frameId to roof) {
            val f = spec.frames.getValue(frameId)
            Bitmap.createBitmap(Recolor.roof(pixels(f), keys, roof), f.w, f.h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    /**
     * L'image [frameId] d'une maison : toit aux couleurs [roof] (si l'objet a des [keys]) et vitres de
     * [windows] selon [light]. Calculée une fois par triplet (image, toit, lumière).
     */
    fun house(frameId: String, keys: List<Int>, roof: RoofColors?, windows: List<PixelRect>, light: WindowLight): ImageBitmap {
        if (light == WindowLight.LIT || windows.isEmpty()) {
            return if (roof != null && keys.isNotEmpty()) recoloured(frameId, keys, roof) else mapped(frameId, emptyMap())
        }
        return synchronized(houses) {
            houses.getOrPut(HouseKey(frameId, roof, light)) {
                val f = spec.frames.getValue(frameId)
                val pixels = pixels(f)
                val painted = if (roof != null && keys.isNotEmpty()) Recolor.roof(pixels, keys, roof) else pixels
                Recolor.windows(painted, f.w, windows, light)
                Bitmap.createBitmap(painted, f.w, f.h, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }

    /** L'image [frameId] seule, couleurs remplacées selon [mapping] (une bulle d'état ambre…). */
    fun mapped(frameId: String, mapping: Map<Int, Int>): ImageBitmap = synchronized(mapped) {
        mapped.getOrPut(frameId to mapping) {
            val f = spec.frames.getValue(frameId)
            Bitmap.createBitmap(Recolor.colours(pixels(f), mapping), f.w, f.h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    /** La première rangée opaque de la colonne [column] de l'image [frameId] (le haut du toit), ou `null`. */
    fun columnTop(frameId: String, column: Int): Int? {
        val f = spec.frames[frameId] ?: return null
        if (column !in 0 until f.w) return null
        return (0 until f.h).firstOrNull { y -> (bitmap.getPixel(f.x + column, f.y + y) ushr 24) != 0 }
    }

    private fun pixels(f: AtlasFrame): IntArray {
        val pixels = IntArray(f.w * f.h)
        bitmap.getPixels(pixels, 0, f.w, f.x, f.y, f.w, f.h)
        return pixels
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

/** Les images d'une animation, chacune [ms] millisecondes, en boucle. */
class ResolvedAnimation(val frames: List<ResolvedSprite>, val ms: Int) {
    init {
        require(frames.isNotEmpty() && ms > 0)
    }

    /** L'image au temps [timeMillis], décalé de [phase] ; sans allocation. */
    fun at(timeMillis: Long, phase: Long = 0): ResolvedSprite =
        if (frames.size == 1) frames[0] else frames[(Math.floorMod(timeMillis + phase, ms.toLong() * frames.size) / ms).toInt()]
}

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

    /**
     * Toutes les images de [id] résolues d'avance, pour dessiner une animation sans rien allouer à
     * chaque image ; une image fixe donne une animation d'une seule image. `null` si [id] est inconnu.
     */
    fun animation(id: String): ResolvedAnimation? {
        for (atlas in atlases) {
            val spec = atlas.spec
            val obj = spec.objects[id]
            val ref = obj?.frame ?: id
            val names = spec.anims[ref]?.frames ?: listOf(ref)
            if (names.any { it !in spec.frames }) continue
            val footprint = obj?.footprint ?: Footprint(1, 1)
            val frames = names.map { ResolvedSprite(atlas, it, spec.frames.getValue(it), footprint, obj?.roofKeys.orEmpty()) }
            return ResolvedAnimation(frames, spec.anims[ref]?.ms ?: Int.MAX_VALUE)
        }
        return null
    }

    /** Vrai si [id] désigne une animation, directement ou par son objet. */
    fun isAnimated(id: String): Boolean = atlases.any { atlas ->
        val ref = atlas.spec.objects[id]?.frame ?: id
        ref in atlas.spec.anims
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

/**
 * Les assets du processus : décodés une seule fois, hors du fil principal, et partagés par les
 * deux écrans (les deux activités vivent dans le même processus).
 */
object SharedGameAssets {
    private val loaded = ConcurrentHashMap<String, GameAssets>()
    private val mutex = Mutex()

    /** Les assets déjà chargés depuis [directory], sinon `null`. */
    fun peek(directory: String = GameAssetLoader.DIRECTORY): GameAssets? = loaded[directory]

    suspend fun get(assets: AssetManager, directory: String = GameAssetLoader.DIRECTORY): GameAssets =
        loaded[directory] ?: mutex.withLock {
            loaded[directory] ?: withContext(Dispatchers.IO) { GameAssetLoader.load(assets, directory) }
                .also { loaded[directory] = it }
        }
}

/** Les assets partagés du processus ; en attendant le premier chargement, tout est en formes de repli. */
@Composable
fun rememberGameAssets(directory: String = GameAssetLoader.DIRECTORY): GameAssets {
    val assets = LocalContext.current.applicationContext.assets
    return produceState(SharedGameAssets.peek(directory) ?: GameAssets.Placeholder, assets, directory) {
        value = SharedGameAssets.get(assets, directory)
    }.value
}

/**
 * Les modèles de maison de l'atlas `maisons` (objets `maison.*`), pour le générateur du hameau :
 * empreinte, porte, cheminée et fenêtres viennent de l'atlas. `null` si l'atlas n'en donne aucun de valide.
 * La cheminée et les fenêtres, données dans l'atlas depuis le coin de l'image, passent au repère de
 * [HouseModel] : le coin haut gauche de l'empreinte.
 */
fun GameAssets.houseCatalog(): HouseCatalog? = houseCatalogOf(atlases.map { it.spec })

/**
 * Les modèles de maison lus dans `maisons.json` seul, sans décoder d'image : la vue d'ensemble
 * génère le hameau dès la première vue d'ensemble, avant que les atlas ne soient chargés (ticket 45).
 * `null` si le fichier manque ou ne donne aucun modèle valide.
 */
fun GameAssetLoader.houseCatalog(assets: AssetManager, directory: String = DIRECTORY): HouseCatalog? = try {
    val spec = assets.open("$directory/maisons.json").use { AssetJson.parseAtlas(it.readBytes().decodeToString()) }
    houseCatalogOf(listOf(spec))
} catch (_: IOException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun houseCatalogOf(specs: List<AtlasSpec>): HouseCatalog? {
    val models = specs.flatMap { spec ->
        spec.objects.filterKeys { it.startsWith("maison.") }.mapNotNull { (id, house) ->
            val door = house.door ?: return@mapNotNull null
            val frame = spec.frames[house.frame] ?: return@mapNotNull null
            // Le coin de l'image, depuis le coin haut gauche de l'empreinte (ancre au milieu de la dernière rangée).
            val dx = house.footprint.w * HOUSE_TILE / 2 - frame.ax
            val dy = house.footprint.h * HOUSE_TILE - 1 - frame.ay
            try {
                HouseModel(
                    id, house.footprint.w, house.footprint.h, door.x, door.y,
                    chimney = house.chimney?.let { PixelPoint(it.x + dx, it.y + dy) },
                    windows = house.windows.map { PixelRect(it.x + dx, it.y + dy, it.width, it.height) },
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }.distinctBy { it.id }
    return if (models.isEmpty()) null else HouseCatalog(models)
}

private const val HOUSE_TILE = 16
