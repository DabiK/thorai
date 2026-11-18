package app.regie.game.engine.village

/**
 * Les clés de l'atlas `decor` que le hameau pose. Chaque objet occupe une tuile au sol, sauf la
 * fontaine (3 × 3) ; le sprite peut déborder au-dessus. Les clés suivent le contrat des assets ;
 * si l'atlas du ticket 42 en choisit d'autres, on passe un autre catalogue.
 */
data class DecorCatalog(
    val trees: List<String> = listOf("decor.arbre.0", "decor.arbre.1", "decor.arbre.2"),
    val bushes: List<String> = listOf("decor.buisson"),
    val flowers: List<String> = listOf("decor.fleurs.0", "decor.fleurs.1"),
    val rocks: List<String> = listOf("decor.rocher"),
    /** Morceau droit, horizontal, des clôtures de jardin. */
    val fence: String = "decor.cloture",
    val fountain: String = "decor.fontaine",
    val bench: String = "decor.banc",
    val lantern: String = "decor.lanterne",
    /** Le panneau planté au début de chaque rue ; l'interface y écrit le projet. */
    val sign: String = "decor.panneau",
) {
    init {
        require(trees.isNotEmpty() && bushes.isNotEmpty() && flowers.isNotEmpty() && rocks.isNotEmpty())
    }

    /** Les fleurs se traversent ; le reste bloque. */
    fun isBlocking(objectId: String): Boolean = objectId !in flowers

    companion object {
        val DEFAULT = DecorCatalog()
    }
}
