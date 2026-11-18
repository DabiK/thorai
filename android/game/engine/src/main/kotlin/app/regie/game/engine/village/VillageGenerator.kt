package app.regie.game.engine.village

import app.regie.domain.model.Agent
import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
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

    /**
     * Le hameau de [overview]. [construction] : le chantier d'une nouvelle session (ticket 48). Pendant la
     * fiche ([Construction.stage] `null`), le terrain visé est montré sans être réservé ni enregistré : le
     * coursier s'y rend, et un nouveau projet y montre la rue qu'il ouvrirait.
     */
    fun generate(overview: Overview, construction: Construction? = null): Village {
        val previous = store.load()
        val layout = planner.plan(overview.sessions, previous)
        if (layout != previous) store.save(layout)
        val shown = if (construction != null && construction.stage == null && layout.reservation == null) {
            planner.reserve(overview.sessions, layout, construction.project)
        } else {
            layout
        }
        return builder.build(overview.sessions, shown, construction)
    }

    /**
     * Réserve et enregistre, avant la création de la session, le terrain de la rue de [project] où sa
     * maison sera bâtie : le même que celui que [generate] montrait pendant la fiche. Remplace une
     * réservation précédente.
     */
    fun reserve(overview: Overview, project: String): PlotReservation {
        val layout = planner.reserve(overview.sessions, store.load(), project)
        store.save(layout)
        return checkNotNull(layout.reservation)
    }

    /** Le pont a créé la session [id] : elle prendra le terrain réservé dès qu'elle paraît. */
    fun bind(id: SessionId) {
        val layout = store.load()
        val reservation = layout.reservation ?: return
        store.save(layout.copy(reservation = reservation.copy(session = id)))
    }

    /** La création a échoué ou a été abandonnée : le terrain redevient libre. */
    fun release() {
        val layout = store.load()
        if (layout.reservation != null) store.save(planner.release(layout))
    }

    /**
     * Au lancement de l'app : une réservation sans session connue vient d'une création dont on ne saura
     * plus rien ; elle tombe. Une réservation liée à une session attend celle-ci.
     */
    fun forgetUnboundReservation() {
        if (store.load().reservation?.session == null) release()
    }
}
