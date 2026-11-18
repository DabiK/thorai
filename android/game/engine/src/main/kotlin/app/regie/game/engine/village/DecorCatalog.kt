package app.regie.game.engine.village

/** L'empreinte au sol d'un objet de décor, en tuiles ; son ancre est la tuile en bas à gauche. */
data class DecorSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Empreinte vide" }
    }

    companion object {
        val ONE = DecorSize(1, 1)
    }
}

/**
 * Les clés de l'atlas `decor` que le hameau pose. Un objet occupe une tuile au sol, sauf ceux de
 * [sizes] (la fontaine en 3 × 3, le puits en 2 × 2, une jardinière en 2 × 1…) ; le sprite peut déborder
 * au-dessus. Les clés suivent l'atlas du ticket 42 ; un autre atlas passe un autre catalogue.
 *
 * Les premières listes (arbres, buissons, fleurs, rochers, clôture, fontaine, banc, lanterne, panneau)
 * sont celles du ticket 43. Les suivantes (ticket 52) meublent les jardins, les bords des rues, la place
 * et la rivière.
 */
data class DecorCatalog(
    val trees: List<String> = listOf("decor.arbre.0", "decor.arbre.1", "decor.arbre.2"),
    val bushes: List<String> = listOf("decor.buisson"),
    val flowers: List<String> = listOf("decor.fleurs.0", "decor.fleurs.1", "fleurs.blanches"),
    val rocks: List<String> = listOf("decor.rocher"),
    /** Morceau droit, horizontal, des clôtures de jardin. */
    val fence: String = "decor.cloture",
    val fountain: String = "decor.fontaine",
    val bench: String = "decor.banc",
    val lantern: String = "decor.lanterne",
    /** Le panneau planté au début de chaque rue ; l'interface y écrit le projet. */
    val sign: String = "decor.panneau",
    /** Le bout gauche d'une clôture (une voisine à l'est), son bout droit (à l'ouest), un poteau seul. */
    val fenceStart: String = "cloture.e",
    val fenceEnd: String = "cloture.o",
    val fencePost: String = "cloture.poteau",
    /** Petits arbres des jardins. */
    val saplings: List<String> = listOf("arbre.jeune"),
    /** Buissons en fleurs, de deux tuiles. */
    val floweringBushes: List<String> = listOf("buisson.fleuri"),
    /** Herbes hautes et roseaux : on les traverse, comme les fleurs. */
    val grasses: List<String> = listOf("herbe.haute"),
    /** Accessoires d'une tuile, posés contre les murs : tonneau, caisse. */
    val props: List<String> = listOf("tonneau", "caisse"),
    /** Accessoires de deux tuiles de large : jardinière, tas de bois. */
    val wideProps: List<String> = listOf("jardiniere", "bois"),
    /** Petites choses des prés : souche, rondin, gros rocher, cailloux. */
    val fieldProps: List<String> = listOf("souche", "rondin", "rocher.gros", "rocher.petit"),
    /** Le banc de jardin, vu de côté (1 × 2). */
    val gardenBench: String = "banc.cote",
    val well: String = "puits",
    /** Le poteau indicateur du carrefour sous la place. */
    val signpost: String = "panneau.fleches",
    val lowLantern: String = "lanterne.basse",
    val sizes: Map<String, DecorSize> = DEFAULT_SIZES,
) {
    init {
        require(trees.isNotEmpty() && bushes.isNotEmpty() && flowers.isNotEmpty() && rocks.isNotEmpty())
        require(saplings.isNotEmpty() && floweringBushes.isNotEmpty() && grasses.isNotEmpty())
        require(props.isNotEmpty() && wideProps.isNotEmpty() && fieldProps.isNotEmpty())
    }

    /** Les fleurs et les herbes se traversent ; le reste bloque. */
    fun isBlocking(objectId: String): Boolean = objectId !in flowers && objectId !in grasses

    /** L'empreinte de [objectId] : une tuile s'il n'est pas dans [sizes]. */
    fun sizeOf(objectId: String): DecorSize = sizes[objectId] ?: DecorSize.ONE

    /** Tout ce que le hameau peut poser, pour vérifier qu'un atlas le connaît. */
    val all: List<String>
        get() = (
            trees + bushes + flowers + rocks + saplings + floweringBushes + grasses + props + wideProps + fieldProps +
                listOf(fence, fountain, bench, lantern, sign, fenceStart, fenceEnd, fencePost, gardenBench, well, signpost, lowLantern)
            ).distinct()

    companion object {
        /** Les empreintes de l'atlas `decor` du ticket 42 (`decor.json`, `objects.*.footprint`). */
        val DEFAULT_SIZES: Map<String, DecorSize> = mapOf(
            "decor.fontaine" to DecorSize(3, 3),
            "puits" to DecorSize(2, 2),
            "banc.cote" to DecorSize(1, 2),
            "buisson.fleuri" to DecorSize(2, 1),
            "jardiniere" to DecorSize(2, 1),
            "bois" to DecorSize(2, 1),
            "souche" to DecorSize(2, 1),
            "rondin" to DecorSize(2, 1),
            "rocher.gros" to DecorSize(2, 1),
        )

        val DEFAULT = DecorCatalog()
    }
}
