package app.regie.game.ui.render

import app.regie.game.engine.world.Ground

/** Trois tons d'une matière, éclairée en haut à gauche (`da-jeu.md`), en ARGB. */
data class Tones(val light: Int, val base: Int, val dark: Int)

/**
 * Les rôles de couleur du sol, lus dans `palette.json` (ADR 24) :
 * - `<sol>.light`, `<sol>.base`, `<sol>.dark` pour `grass`, `path`, `plaza`, `water`, `bridge` et `stone`
 *   (les pierres des berges et les joints de la place) ;
 * - `grass.deep` (le pied des touffes), `water.mid` (les remous), `outline` (les contours) ;
 * - `flower.0` à `flower.3` (les petites fleurs de l'herbe, la dernière étant leur cœur).
 * Un rôle absent garde sa couleur par défaut, relevée sur le hameau de l'artefact.
 */
data class GroundPalette(
    val grass: Tones,
    val path: Tones,
    val plaza: Tones,
    val water: Tones,
    val bridge: Tones,
    val stone: Tones,
    val grassDeep: Int,
    val waterMid: Int,
    val flowers: List<Int>,
    val outline: Int,
    /** Les 32 couleurs figées ; vide si `palette.json` ne les donne pas. */
    val colors: List<Int> = emptyList(),
) {
    init {
        require(flowers.size == FLOWER_ROLES) { "$FLOWER_ROLES couleurs de fleurs attendues" }
    }

    fun tones(ground: Ground): Tones = when (ground) {
        Ground.GRASS -> grass
        Ground.PATH -> path
        Ground.PLAZA -> plaza
        Ground.WATER -> water
        Ground.BRIDGE -> bridge
    }

    /** Toutes les couleurs que le sol peut employer. */
    val all: Set<Int>
        get() = (listOf(grass, path, plaza, water, bridge, stone).flatMap { listOf(it.light, it.base, it.dark) } +
            listOf(grassDeep, waterMid, outline) + flowers).toSet()

    /** Remplace les rôles donnés, sous la forme `grass.light` → couleur. */
    fun withRoles(roles: Map<String, Int>): GroundPalette {
        fun Tones.with(prefix: String) = Tones(
            light = roles["$prefix.light"] ?: light,
            base = roles["$prefix.base"] ?: base,
            dark = roles["$prefix.dark"] ?: dark,
        )
        return copy(
            grass = grass.with("grass"),
            path = path.with("path"),
            plaza = plaza.with("plaza"),
            water = water.with("water"),
            bridge = bridge.with("bridge"),
            stone = stone.with("stone"),
            grassDeep = roles["grass.deep"] ?: grassDeep,
            waterMid = roles["water.mid"] ?: waterMid,
            flowers = flowers.mapIndexed { i, color -> roles["flower.$i"] ?: color },
            outline = roles["outline"] ?: outline,
        )
    }

    companion object {
        const val FLOWER_ROLES = 4

        val ROLES: List<String> = listOf("grass", "path", "plaza", "water", "bridge", "stone")
            .flatMap { ground -> listOf("light", "base", "dark").map { "$ground.$it" } } +
            listOf("grass.deep", "water.mid", "outline") + (0 until FLOWER_ROLES).map { "flower.$it" }

        val Default = GroundPalette(
            grass = Tones(light = argb(0xDCDA5F), base = argb(0xA0D944), dark = argb(0x73C839)),
            path = Tones(light = argb(0xF0DDD1), base = argb(0xF1D8A6), dark = argb(0xD8AFA1)),
            plaza = Tones(light = argb(0xF0DDD1), base = argb(0xF1D8A6), dark = argb(0xD8AFA1)),
            water = Tones(light = argb(0x53E6DE), base = argb(0x05ACAB), dark = argb(0x1B5F53)),
            bridge = Tones(light = argb(0xE5A669), base = argb(0xA16948), dark = argb(0x615039)),
            stone = Tones(light = argb(0xF0DDD1), base = argb(0xA0A0A0), dark = argb(0x605751)),
            grassDeep = argb(0x58A949),
            waterMid = argb(0x27D1CB),
            flowers = listOf(argb(0xF0DDD1), argb(0xD8AFA1), argb(0xDF724A), argb(0xDDDC2C)),
            outline = argb(0x1F583A),
        )

        private fun argb(rgb: Int) = (0xFF shl 24) or rgb
    }
}
