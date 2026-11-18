package app.regie.game.ui.atlas

import app.regie.game.engine.world.RoofColors

/** Le remplacement des couleurs clés d'un toit par les couleurs de la session (contrat des assets). */
object Recolor {
    /**
     * Copie de [pixels] (ARGB) où chaque couleur de [keys] (clair, moyen, sombre) devient la couleur
     * correspondante de [roof]. La comparaison ignore l'alpha, qui est conservé.
     */
    fun roof(pixels: IntArray, keys: List<Int>, roof: RoofColors): IntArray {
        if (keys.isEmpty()) return pixels.copyOf()
        val targets = AssetJson.roofTargets(roof)
        val rgbKeys = keys.map { it and RGB }
        return IntArray(pixels.size) { i ->
            val pixel = pixels[i]
            val index = rgbKeys.indexOf(pixel and RGB)
            if (index < 0 || (pixel ushr 24) == 0) pixel else (pixel and ALPHA) or (targets[index] and RGB)
        }
    }

    private const val RGB = 0x00FFFFFF
    private const val ALPHA = -0x1000000
}
