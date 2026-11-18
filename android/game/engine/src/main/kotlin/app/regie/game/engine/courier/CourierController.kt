package app.regie.game.engine.courier

import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.game.engine.pathfinding.findPath
import app.regie.game.engine.village.House
import app.regie.game.engine.village.Village
import app.regie.game.engine.world.TilePos
import kotlin.math.abs

/**
 * Le coursier dans le hameau : la marche sur la grille, les collisions, les raccourcis et l'entrée
 * dans les maisons. Pur et piloté par le temps : l'interface envoie les commandes ([handle]) et fait
 * avancer l'horloge ([advance]) ; rien n'alloue d'une image à l'autre.
 *
 * Marche :
 * - un pas fait une tuile en [STEP_MILLIS], à vitesse constante. Le coursier ne s'arrête jamais
 *   entre deux tuiles : [tile] change à la fin du pas ;
 * - une direction tenue fait marcher ; la dernière enfoncée l'emporte ;
 * - une direction enfoncée pendant un pas est mise en réserve et jouée juste après, même relâchée
 *   entre-temps ; A pendant un pas est joué à son arrivée ;
 * - une tuile bloquée (ou hors de la carte) tourne le coursier sans le déplacer.
 *
 * Marche automatique ([CourierCommand.GoTo], L1, R1, [CourierCommand.GoToSite]) : le plus court chemin
 * jusqu'à l'entrée de la maison (ou le devant du chantier), depuis la fin du pas en cours ; puis le coursier regarde la porte et entre, ou s'arrête
 * devant. Toute entrée manuelle (direction, A) l'interrompt, après le pas commencé.
 *
 * À chaque régénération du hameau, [setVillage] reporte la position avec [Village.relocate] et
 * recalcule une marche automatique en cours. À appeler depuis un seul fil.
 */
class CourierController(
    village: Village,
    start: TilePos = village.spawn,
    facing: Direction = Direction.DOWN,
    private val onEvent: (CourierEvent) -> Unit,
) {
    var village: Village = village
        private set

    /** La tuile du coursier ; pendant un pas, celle qu'il quitte. */
    var tile: TilePos = start
        private set

    /** Où il regarde ; pendant un pas, où il va. */
    var facing: Direction = facing
        private set

    /** La direction du pas en cours, `null` à l'arrêt. */
    var stepDirection: Direction? = null
        private set

    /** Le temps déjà passé dans le pas en cours, de 0 à [STEP_MILLIS]. */
    var stepMillis: Int = 0
        private set

    /** L'animation à montrer, et depuis combien de temps elle dure ([actionMillis]). */
    var action: CourierAction = CourierAction.IDLE
        private set

    var actionMillis: Long = 0
        private set

    /** La maison visée par la marche automatique, `null` sans marche automatique. */
    var destination: SessionId? = null
        private set

    val moving: Boolean get() = stepDirection != null

    /** Le coursier a besoin de l'horloge : un pas est en cours. À l'arrêt, rien ne change. */
    val busy: Boolean get() = moving

    private val held = ArrayList<Direction>(Direction.entries.size)
    private var buffered: Direction? = null
    private var pendingInteract = false
    private var talking = false
    private val path = ArrayList<TilePos>()
    private var pathIndex = 0
    private var enterOnArrival = false

    /** La marche automatique va au chantier, pas à une maison. */
    private var toSite = false

    /** Le coursier marche seul jusqu'au chantier. */
    val walkingToSite: Boolean get() = toSite

    init {
        require(!village.world.map.isBlocked(start)) { "Le coursier ne peut pas partir de $start, bloquée" }
    }

    fun handle(command: CourierCommand) {
        when (command) {
            is CourierCommand.Move -> move(command.direction, command.pressed)
            CourierCommand.Interact -> {
                cancelAutoWalk()
                if (moving) pendingInteract = true else interact()
            }
            is CourierCommand.GoTo -> goTo(command.sessionId, command.enter)
            CourierCommand.GoToSite -> goToSite()
            CourierCommand.Next -> next()
            CourierCommand.Previous -> cycle(-1)
            is CourierCommand.Talk -> {
                talking = command.active
                refreshAction()
            }
        }
    }

    /**
     * Le coursier s'arrête et regarde vers [facing], en oubliant les directions tenues, la réserve et
     * la marche automatique : au retour d'une maison, les relâchements ont pu se perdre. Un pas
     * commencé se termine.
     */
    fun rest(facing: Direction) {
        held.clear()
        buffered = null
        pendingInteract = false
        cancelAutoWalk()
        if (!moving) this.facing = facing
        refreshAction()
    }

    /** Fait passer [deltaMillis] millisecondes : un long intervalle fait plusieurs pas. */
    fun advance(deltaMillis: Long) {
        require(deltaMillis >= 0) { "Le temps ne recule pas : $deltaMillis" }
        var remaining = deltaMillis
        while (moving) {
            val need = (STEP_MILLIS - stepMillis).toLong()
            if (remaining < need) {
                stepMillis += remaining.toInt()
                actionMillis += remaining
                return
            }
            remaining -= need
            actionMillis += need
            finishStep()
        }
        actionMillis += remaining
    }

    /**
     * Le hameau a été régénéré : la position est reportée par [Village.relocate], un pas en cours se
     * poursuit s'il mène encore à une tuile libre voisine, et une marche automatique repart vers
     * la maison, là où elle est désormais ; elle s'arrête si la maison a disparu.
     */
    fun setVillage(next: Village) {
        val previous = village
        village = next
        val from = next.relocate(tile, previous)
        val step = stepDirection
        if (step != null) {
            val to = next.relocate(step.next(tile), previous)
            val direction = Direction.between(from, to)
            if (direction != null) {
                tile = from
                stepDirection = direction
                facing = direction
            } else {
                // La tuile visée a changé de place ou s'est bloquée : le coursier s'y pose.
                tile = to
                stepDirection = null
                stepMillis = 0
            }
        } else {
            tile = from
        }
        destination?.let { id ->
            if (!planPath(id, enterOnArrival)) cancelAutoWalk()
        }
        if (toSite && !planSitePath()) cancelAutoWalk()
        if (!moving) continueWalking()
        refreshAction()
    }

    private fun move(direction: Direction, pressed: Boolean) {
        if (!pressed) {
            held.remove(direction)
            return
        }
        cancelAutoWalk()
        held.remove(direction)
        held.add(direction)
        if (moving) buffered = direction else startStep(direction)
    }

    /** Fin d'un pas : la tuile est atteinte, puis on enchaîne ou on s'arrête. */
    private fun finishStep() {
        val step = stepDirection ?: return
        tile = step.next(tile)
        stepDirection = null
        stepMillis = 0
        continueWalking()
        refreshAction()
    }

    /** À l'arrêt sur une tuile : suivre le chemin, jouer A ou la réserve, ou marcher selon la direction tenue. */
    private fun continueWalking() {
        if (toSite) {
            if (pathIndex < path.size) {
                val direction = Direction.between(tile, path[pathIndex])
                if (direction != null && startStep(direction)) {
                    pathIndex++
                    return
                }
                if (planSitePath()) continueWalking() else cancelAutoWalk()
                return
            }
            arriveAtSite()
            return
        }
        val target = destination
        if (target != null) {
            if (pathIndex < path.size) {
                val direction = Direction.between(tile, path[pathIndex])
                if (direction != null && startStep(direction)) {
                    pathIndex++
                    return
                }
                // Le chemin ne mène plus : le recalculer depuis ici.
                if (planPath(target, enterOnArrival)) {
                    continueWalking()
                } else {
                    cancelAutoWalk()
                }
                return
            }
            arrive(target)
            return
        }
        if (pendingInteract) {
            pendingInteract = false
            interact()
            return
        }
        val direction = buffered ?: held.lastOrNull() ?: return
        buffered = null
        startStep(direction)
    }

    /** Tourne vers [direction] et part si la tuile est libre. */
    private fun startStep(direction: Direction): Boolean {
        facing = direction
        val free = !village.world.map.isBlocked(direction.next(tile))
        if (free) {
            stepDirection = direction
            stepMillis = 0
        }
        refreshAction()
        return free
    }

    private fun interact() {
        val house = village.houseAtEntry(tile) ?: return
        if (facing.next(tile) == house.door) onEvent(CourierEvent.EnterHouse(house.session.id))
    }

    private fun goTo(id: SessionId, enter: Boolean) {
        buffered = null
        pendingInteract = false
        if (!planPath(id, enter)) return
        if (!moving) continueWalking()
    }

    private fun goToSite() {
        buffered = null
        pendingInteract = false
        if (!planSitePath()) return
        if (!moving) continueWalking()
    }

    /** Le chemin vers le devant du chantier, depuis la fin du pas en cours. Faux sans chantier ou sans chemin. */
    private fun planSitePath(): Boolean {
        val site = village.site ?: return false
        val from = stepDirection?.next(tile) ?: tile
        val route = findPath(village.world.map, from, site.entry) ?: return false
        path.clear()
        path.addAll(route)
        pathIndex = 1
        destination = null
        enterOnArrival = false
        toSite = true
        return true
    }

    private fun arriveAtSite() {
        cancelAutoWalk()
        facing = Direction.UP
        refreshAction()
        onEvent(CourierEvent.ArrivedAtSite)
    }

    /** Le chemin vers l'entrée de la maison de [id], depuis la fin du pas en cours. Faux s'il n'y en a pas. */
    private fun planPath(id: SessionId, enter: Boolean): Boolean {
        val house = village.houseOf(id) ?: return false
        val from = stepDirection?.next(tile) ?: tile
        val route = findPath(village.world.map, from, house.entry) ?: return false
        path.clear()
        path.addAll(route)
        pathIndex = 1 // route[0] est le point de départ.
        destination = id
        enterOnArrival = enter
        toSite = false
        return true
    }

    private fun arrive(id: SessionId) {
        val enter = enterOnArrival
        cancelAutoWalk()
        val house = village.houseOf(id) ?: return
        Direction.between(tile, house.door)?.let { facing = it }
        refreshAction()
        onEvent(if (enter) CourierEvent.EnterHouse(id) else CourierEvent.ArrivedAtDoor(id))
    }

    private fun cancelAutoWalk() {
        destination = null
        toSite = false
        path.clear()
        pathIndex = 0
        enterOnArrival = false
    }

    /** R1 : la validation en attente d'abord (comme la vue d'ensemble, `Overview.firstPendingApproval`). */
    private fun next() {
        val waiting = village.houses.map { it.session }.sortedWith(Overview.order).firstOrNull { it.pendingApproval != null }
        if (waiting != null && waiting.id != currentHouse()?.session?.id) {
            goTo(waiting.id, enter = true)
        } else {
            cycle(+1)
        }
    }

    /**
     * L1, R1 : la maison voisine, dans l'ordre stable du hameau (rue après rue, terrain après terrain),
     * en bouclant. Sans maison de départ, la plus proche. Le coursier s'arrête devant la porte.
     */
    private fun cycle(delta: Int) {
        val houses = village.houses
        if (houses.isEmpty()) return
        val current = currentHouse()
        val target = if (current == null) {
            nearestHouse()
        } else {
            houses[Math.floorMod(houses.indexOf(current) + delta, houses.size)]
        }
        goTo(target.session.id, enter = false)
    }

    /** La maison de départ de L1 et R1 : celle que vise la marche, sinon celle devant laquelle il se tient. */
    private fun currentHouse(): House? =
        destination?.let { village.houseOf(it) } ?: village.houseAtEntry(stepDirection?.next(tile) ?: tile)

    private fun nearestHouse(): House {
        val from = stepDirection?.next(tile) ?: tile
        return village.houses.minBy { abs(it.entry.x - from.x) + abs(it.entry.y - from.y) }
    }

    private fun refreshAction() {
        val next = when {
            moving -> CourierAction.WALK
            talking -> CourierAction.TALK
            else -> CourierAction.IDLE
        }
        if (next != action) {
            action = next
            actionMillis = 0
        }
    }

    companion object {
        /** Durée d'un pas d'une tuile : vitesse constante, environ six tuiles par seconde. */
        const val STEP_MILLIS = 160
    }
}
