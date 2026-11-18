package app.regie.game.engine.village

import app.regie.domain.model.Agent
import app.regie.domain.model.Overview
import app.regie.game.engine.world.RoofColors

/**
 * Le hameau de la vue d'ensemble : une rue par projet, une maison par session.
 *
 * À chaque appel de [generate], la disposition enregistrée dans [store] est complétée (nouvelles rues,
 * nouveaux terrains), enregistrée si elle a changé, puis dessinée. Mêmes sessions, même disposition et
 * même graine donnent le même hameau, tuile pour tuile.
 *
 * Une session qui disparaît de la vue d'ensemble libère son terrain à l'affichage. Elle garde ce
 * terrain en réserve : si elle revient avant qu'une session nouvelle n'en ait besoin, sa maison
 * réapparaît au même endroit, avec le même modèle et le même toit.
 *
 * À appeler depuis un seul fil à la fois.
 */
class VillageGenerator(
    private val store: VillageLayoutStore,
    catalog: HouseCatalog = HouseCatalog.DEFAULT,
    decor: DecorCatalog = DecorCatalog.DEFAULT,
    roofs: List<RoofColors> = RoofPalette.AUBE,
    config: VillageConfig = VillageConfig(),
    inhabitants: (Agent) -> InhabitantKind = InhabitantKind::of,
) {
    private val planner = VillagePlanner(catalog, roofs, config)
    private val builder = VillageBuilder(catalog, decor, config, inhabitants)

    fun generate(overview: Overview): Village {
        val previous = store.load()
        val layout = planner.plan(overview.sessions, previous)
        if (layout != previous) store.save(layout)
        return builder.build(overview.sessions, layout)
    }
}
