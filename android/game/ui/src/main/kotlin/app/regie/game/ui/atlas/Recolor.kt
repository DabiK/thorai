package app.regie.game.ui.atlas

import app.regie.game.engine.village.PixelRect
import app.regie.game.engine.world.RoofColors

/** L'éclairage des fenêtres d'une maison, selon l'état de sa session. */
enum class WindowLight {
    /** Telles que l'atlas les peint : jaune chaud. */
    LIT,

    /** Plus vives : l'image d'un vacillement, pour une maison au travail. */
    GLOW,

    /** Éteintes : un verre bleu ardoise, pour une maison au repos. */
    DARK,
}

/** Le remplacement de couleurs d'une image : toit d'une session, fenêtres, bulles d'état. */
object Recolor {
    /**
     * Copie de [pixels] (ARGB) où chaque couleur de [keys] (clair, moyen, sombre) devient la couleur
     * correspondante de [roof]. La comparaison ignore l'alpha, qui est conservé.
     */
    fun roof(pixels: IntArray, keys: List<Int>, roof: RoofColors): IntArray {
        if (keys.isEmpty()) return pixels.copyOf()
        return colours(pixels, keys.zip(AssetJson.roofTargets(roof)).toMap())
    }

    /** Copie de [pixels] où chaque couleur de [mapping] (clé → valeur, alpha ignoré) est remplacée. */
    fun colours(pixels: IntArray, mapping: Map<Int, Int>): IntArray {
        val from = mapping.keys.map { it and RGB }.toIntArray()
        val to = mapping.values.map { it and RGB }.toIntArray()
        return IntArray(pixels.size) { i -> swap(pixels[i], from, to) }
    }

    /**
     * Rallume ou éteint les vitres : dans les rectangles [windows] d'une image de [width] pixels de large,
     * les deux jaunes des vitres de l'atlas prennent les couleurs de [light]. Le reste (cadres, murs) ne
     * bouge pas. Modifie [pixels] sur place.
     */
    fun windows(pixels: IntArray, width: Int, windows: List<PixelRect>, light: WindowLight) {
        val targets = when (light) {
            WindowLight.LIT -> return
            WindowLight.GLOW -> GLOW
            WindowLight.DARK -> DARK
        }
        val height = pixels.size / width
        for (window in windows) {
            for (y in maxOf(0, window.y) until minOf(height, window.y + window.height)) {
                for (x in maxOf(0, window.x) until minOf(width, window.x + window.width)) {
                    pixels[y * width + x] = swap(pixels[y * width + x], GLASS, targets)
                }
            }
        }
    }

    private fun swap(pixel: Int, from: IntArray, to: IntArray): Int {
        if ((pixel ushr 24) == 0) return pixel
        val rgb = pixel and RGB
        for (i in from.indices) if (from[i] == rgb) return (pixel and ALPHA) or to[i]
        return pixel
    }

    /** Les deux jaunes des vitres de l'atlas `maisons`, du plus vif au plus doux. */
    private val GLASS = intArrayOf(0xDDDC2C, 0xDCDA5F)
    private val GLOW = intArrayOf(0xDCDA5F, 0xF1D8A6)
    private val DARK = intArrayOf(0x5A718C, 0x718EA0)

    private const val RGB = 0x00FFFFFF
    private const val ALPHA = -0x1000000
}
