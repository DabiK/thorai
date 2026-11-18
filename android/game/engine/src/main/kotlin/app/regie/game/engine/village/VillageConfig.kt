package app.regie.game.engine.village

/**
 * Réglages du hameau.
 *
 * - [seed] : la graine de tout tirage (modèle, toit, décor).
 * - [initialPlots] : les terrains d'une rue neuve.
 * - [minFreePlots] : une rue garde toujours au moins ce nombre de terrains libres ; sinon elle s'allonge.
 * - [water] : une rivière au nord du hameau.
 * - [minWidth] × [minHeight] : la carte couvre au moins l'écran du haut (30 × 17 tuiles).
 */
data class VillageConfig(
    val seed: Long = 0L,
    val initialPlots: Int = 3,
    val minFreePlots: Int = 1,
    val water: Boolean = true,
    val minWidth: Int = 30,
    val minHeight: Int = 17,
) {
    init {
        require(initialPlots >= 1) { "Une rue a au moins un terrain" }
        require(minFreePlots >= 0)
        require(minWidth > 0 && minHeight > 0)
    }
}
