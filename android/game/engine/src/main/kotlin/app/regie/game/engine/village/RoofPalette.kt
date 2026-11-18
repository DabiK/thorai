package app.regie.game.engine.village

import app.regie.game.engine.world.RoofColors

/** Les couleurs de toit tirées pour les sessions, du clair au sombre. */
object RoofPalette {
    private fun roof(light: Long, base: Long, shadow: Long) =
        RoofColors(light.toInt(), base.toInt(), shadow.toInt())

    /** Sept toits pris dans les 32 couleurs de la palette Aube (da-jeu.md, « Palette »). */
    val AUBE: List<RoofColors> = listOf(
        // Tuile.
        roof(0xFFDF724A, 0xFFDB6633, 0xFF9E5A37),
        // Ardoise.
        roof(0xFF718EA0, 0xFF5A718C, 0xFF203648),
        // Mousse.
        roof(0xFF8FB237, 0xFF63A53A, 0xFF1F583A),
        // Lagon.
        roof(0xFF27D1CB, 0xFF05ACAB, 0xFF1B5F53),
        // Chaume.
        roof(0xFFF1D8A6, 0xFFE5A669, 0xFFA16948),
        // Bois.
        roof(0xFFA16948, 0xFF9E5A37, 0xFF503835),
        // Pierre.
        roof(0xFFA0A0A0, 0xFF605751, 0xFF222F36),
    )
}
