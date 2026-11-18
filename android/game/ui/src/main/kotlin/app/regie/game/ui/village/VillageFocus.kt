package app.regie.game.ui.village

import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.village.Village
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.render.GroundPainter
import app.regie.game.ui.scene.VillageSceneState
import kotlin.math.abs

/**
 * La maison sur laquelle porte la vue d'ensemble (ticket 45) : celle où le coursier se rend, sinon
 * celle dont il regarde la porte, sinon la plus proche de lui. C'est la ligne choisie du carnet, le
 * panneau affiché en haut, et la session que vise L2 (dictée) depuis le hameau.
 */
fun VillageSceneState.focusedHouse(): SessionId? = destination ?: facingDoor ?: nearestHouse(village, courierTile)

/** La tuile où se trouvent les pieds du coursier. */
val VillageSceneState.courierTile: TilePos get() = TilePos(foot.x / GroundPainter.TILE, foot.y / GroundPainter.TILE)

/** Le coursier marche sous la main du joueur, sans marche automatique : A sera joué à l'arrivée. */
val VillageSceneState.walkingByHand: Boolean get() = action == CourierAction.WALK && destination == null

/**
 * La maison dont l'entrée est la plus proche de [tile], en pas sur la grille (la même mesure que le
 * coursier pour L1 et R1) ; à égalité, la première dans l'ordre du hameau. `null` sans maison.
 */
fun nearestHouse(village: Village, tile: TilePos): SessionId? =
    village.houses.minByOrNull { abs(it.entry.x - tile.x) + abs(it.entry.y - tile.y) }?.session?.id
