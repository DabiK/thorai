package app.regie.game.engine.village

import app.regie.game.engine.world.RoofColors

/** Les couleurs de toit tirées pour les sessions, du clair au sombre. */
object RoofPalette {
    private fun roof(light: Long, base: Long, shadow: Long) =
        RoofColors(light.toInt(), base.toInt(), shadow.toInt())

    /**
     * Huit toits relevés sur le hameau de l'artefact, tous pris dans les 32 couleurs de la palette Aube
     * (da-jeu.md, « Palette ») : assez différents pour que deux maisons voisines ne se confondent pas.
     */
    val AUBE: List<RoofColors> = listOf(
        // Terre cuite.
        roof(0xFFDF724A, 0xFFDB6633, 0xFF9E5A37),
        // Ardoise bleue.
        roof(0xFF718EA0, 0xFF5A718C, 0xFF203648),
        // Mousse.
        roof(0xFF8FB237, 0xFF63A53A, 0xFF1F583A),
        // Sarcelle.
        roof(0xFF27D1CB, 0xFF05ACAB, 0xFF1B5F53),
        // Tournesol.
        roof(0xFFDCDA5F, 0xFFDDDC2C, 0xFFA19E5C),
        // Corail rosé.
        roof(0xFFF0DDD1, 0xFFD8AFA1, 0xFFA16948),
        // Chaume.
        roof(0xFFF1D8A6, 0xFFE5A669, 0xFFA16948),
        // Bardeau brun.
        roof(0xFFA16948, 0xFF9E5A37, 0xFF503835),
    )
}
