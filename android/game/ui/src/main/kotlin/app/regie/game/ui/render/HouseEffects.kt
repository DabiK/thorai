package app.regie.game.ui.render

import app.regie.game.ui.atlas.WindowLight
import app.regie.game.ui.kit.TagState

/**
 * La vie d'une maison selon l'état de sa session (ticket 52), en pixels du dessin et sans allocation :
 * - au travail : fenêtres allumées qui vacillent, fumée qui monte de la cheminée ;
 * - en attente d'une validation : fenêtres allumées et lumière qui sort de la porte, en pulsant ;
 * - au repos, arrêtée ou en échec : fenêtres éteintes, pas de fumée.
 * Une maison sans état connu garde l'atlas tel quel.
 */
object HouseEffects {
    /** Un panache de fumée met [SMOKE_PERIOD] ms à monter de [SMOKE_RISE] pixels ; [SMOKE_PUFFS] se suivent. */
    const val SMOKE_PERIOD = 3000L
    const val SMOKE_PUFFS = 3
    const val SMOKE_RISE = 24
    const val SMOKE_DRIFT = 7

    /** Le vacillement : une image plus vive [FLICKER_ON] ms toutes les [FLICKER_PERIOD] ms. */
    const val FLICKER_PERIOD = 2600L
    const val FLICKER_ON = 260L

    fun light(state: TagState?): WindowLight = when (state) {
        null, TagState.WORKING, TagState.WAITING -> WindowLight.LIT
        TagState.IDLE, TagState.FAILED -> WindowLight.DARK
    }

    fun smokes(state: TagState?): Boolean = state == TagState.WORKING

    fun flickers(state: TagState?): Boolean = state == TagState.WORKING

    fun doorGlows(state: TagState?): Boolean = state == TagState.WAITING

    /** Vrai pendant l'image vive du vacillement. */
    fun glowing(timeMillis: Long, phase: Long): Boolean = Math.floorMod(timeMillis + phase, FLICKER_PERIOD) < FLICKER_ON

    /** L'avancée du panache [index], de 0 (à la cheminée) à 999 (dissipé). */
    fun puff(timeMillis: Long, phase: Long, index: Int): Int =
        (Math.floorMod(timeMillis + phase + index * SMOKE_PERIOD / SMOKE_PUFFS, SMOKE_PERIOD) * PER_MILLE / SMOKE_PERIOD).toInt()

    /** Décalage horizontal d'un panache : il dérive du côté [drift] (−1 ou 1), avec une petite ondulation. */
    fun puffDx(progress: Int, drift: Int): Int {
        val wobble = if ((progress / WOBBLE_STEP) % 2 == 0) 0 else drift
        return drift * (progress * SMOKE_DRIFT / PER_MILLE) + wobble
    }

    fun puffDy(progress: Int): Int = -(progress * SMOKE_RISE / PER_MILLE)

    /** L'image du panache : petit, moyen, grand. */
    fun puffFrame(progress: Int): Int = when {
        progress < 380 -> 0
        progress < 760 -> 1
        else -> 2
    }

    /** L'opacité, par paliers francs. */
    fun puffAlpha(progress: Int): Float = when {
        progress < 560 -> 0.95f
        progress < 820 -> 0.7f
        else -> 0.4f
    }

    /** La lumière de la porte, qui pulse en deux paliers. */
    fun doorAlpha(timeMillis: Long): Float = if (Math.floorMod(timeMillis, DOOR_PERIOD) < DOOR_PERIOD / 2) 0.8f else 0.55f

    /**
     * La flaque de lumière d'une porte ouverte : largeur de chaque rangée, en pixels, à partir de
     * [DOOR_LIGHT_TOP] pixels au-dessus du bas de l'empreinte (sur les marches) jusqu'au seuil.
     */
    val DOOR_LIGHT = intArrayOf(8, 10, 12, 14, 14, 14, 12, 10, 8)
    const val DOOR_LIGHT_TOP = -2

    private const val PER_MILLE = 1000
    private const val WOBBLE_STEP = 170
    private const val DOOR_PERIOD = 1000L
}
