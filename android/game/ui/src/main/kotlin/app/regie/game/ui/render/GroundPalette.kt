package app.regie.game.ui.render

import app.regie.game.engine.world.Ground

/** Trois tons d'une matière, éclairée en haut à gauche (`da-jeu.md`), en ARGB. */
data class Tones(val light: Int, val base: Int, val dark: Int)

/**
 * Les rôles de couleur du sol, lus dans `palette.json` (ADR 21) :
 * `<sol>.light`, `<sol>.base`, `<sol>.dark` pour `grass`, `path`, `plaza`, `water`, `bridge`, et `outline`.
 * Un rôle absent garde sa couleur par défaut, prise dans la palette de 32 couleurs du hameau.
 */
data class GroundPalette(
    val grass: Tones,
    val path: Tones,
    val plaza: Tones,
    val water: Tones,
    val bridge: Tones,
    val outline: Int,
    /** Les 32 couleurs figées ; vide si `palette.json` ne les donne pas. */
    val colors: List<Int> = emptyList(),
) {
    fun tones(ground: Ground): Tones = when (ground) {
        Ground.GRASS -> grass
        Ground.PATH -> path
        Ground.PLAZA -> plaza
        Ground.WATER -> water
        Ground.BRIDGE -> bridge
    }

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
            outline = roles["outline"] ?: outline,
        )
    }

    companion object {
        val ROLES: List<String> = listOf("grass", "path", "plaza", "water", "bridge")
            .flatMap { ground -> listOf("light", "base", "dark").map { "$ground.$it" } } + "outline"

        val Default = GroundPalette(
            grass = Tones(light = argb(0xA0D944), base = argb(0x71C645), dark = argb(0x58A949)),
            path = Tones(light = argb(0xF0DDD1), base = argb(0xF1D8A6), dark = argb(0xD8AFA1)),
            plaza = Tones(light = argb(0xF0DDD1), base = argb(0xA0A0A0), dark = argb(0x605751)),
            water = Tones(light = argb(0x53E6DE), base = argb(0x27D1CB), dark = argb(0x05ACAB)),
            bridge = Tones(light = argb(0xE5A669), base = argb(0xA16948), dark = argb(0x615039)),
            outline = argb(0x1F583A),
        )

        private fun argb(rgb: Int) = (0xFF shl 24) or rgb
    }
}
