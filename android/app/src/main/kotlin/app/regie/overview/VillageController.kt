package app.regie.overview

import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.courier.Direction
import app.regie.domain.model.Session
import app.regie.domain.model.SessionThread
import app.regie.game.engine.village.Construction
import app.regie.game.engine.village.ConstructionStage
import app.regie.game.engine.village.HouseCatalog
import app.regie.game.engine.village.MapLot
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.engine.village.VillageLayoutStore
import app.regie.game.engine.village.map
import app.regie.game.ui.village.courierTile
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.village.focusedHouse
import app.regie.game.ui.village.walkingByHand
import app.regie.input.Button
import app.regie.input.GamepadInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La vue d'ensemble en hameau (ticket 45, ADR 24) : le hameau est régénéré à chaque vue d'ensemble, et
 * la manette pilote le coursier tant que le hameau est à l'écran ([shown]).
 *
 * - Croix et stick gauche, tenus : le coursier marche ([onDirection]) ; les appuis répétés sont ignorés.
 * - A : entrer dans la maison dont il regarde la porte ; sinon, marcher jusqu'à la maison choisie
 *   ([focusedHouse]) et y entrer, comme A ouvrait la ligne qui avait le focus.
 * - L1, R1 : maison précédente ou suivante. S'il y a une validation en attente, R1 va d'abord à la
 *   maison de la première ([Overview.firstPendingApproval], comme la vue d'ensemble), et y entre ; devant
 *   sa porte (au retour de la maison, par exemple), il y rentre.
 * - Une ligne du carnet touchée ([goTo]) : il y marche et y entre.
 * - Le reste (X, Y, L2, R2, le stick droit, « Quitter ») suit la vue d'ensemble classique, qui vise
 *   la maison choisie. B (agents), Start (menu de pause) et « R1 Voir » de l'oiseau sont pris avant,
 *   par `AppContainer`.
 *
 * Sans hameau, ou quand l'écran du haut n'est pas visible (mode console), tout revient à
 * [classic] : un coursier qu'on ne voit pas ne marche pas pour rien.
 *
 * Bâtir (ticket 48) : pendant la fiche de nouvelle session, le coursier marche jusqu'au terrain visé
 * ([planning]) ; A réserve ce terrain avant la création ([launch]), la session créée le prend
 * ([created]), un échec le libère ([failed]). Le chantier ([construction]) suit ensuite la session
 * ([observe]) ; à la première réponse, le coursier entre dans la maison finie s'il est resté devant.
 * Pendant le chantier, l'écran du bas montre la carte du hameau ([mapOpen]).
 */
class VillageController(
    private val overview: StateFlow<Overview>,
    private val classic: OverviewController,
    private val store: VillageLayoutStore,
    private val catalog: () -> HouseCatalog,
    private val shown: () -> Boolean,
    private val open: (SessionId) -> Unit,
    private val worker: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) {
    private var generator: VillageGenerator? = null
    private var lastKey: Any? = null

    private val _construction = MutableStateFlow<Construction?>(null)

    /** Le chantier de la nouvelle session, `null` sans fiche ni chantier. Il fait partie de ce qui régénère le hameau. */
    val construction: StateFlow<Construction?> = _construction.asStateFlow()

    /** La disposition a changé hors de la vue d'ensemble (réservation, session liée, terrain libéré). */
    private val layoutVersion = MutableStateFlow(0)

    private val changesFlow = MutableStateFlow<Any>(Unit)

    /** Ce qui fait régénérer le hameau, en plus de la vue d'ensemble : le chantier et la disposition. */
    val changes: StateFlow<Any> = changesFlow.asStateFlow()

    /** Le coursier doit marcher jusqu'au chantier dès que le hameau le montre. */
    private var walkToSite = false

    /** La maison finie où entrer dès que le hameau la montre. */
    private var enterWhenBuilt: SessionId? = null

    private val _mapOpen = MutableStateFlow(false)

    /** La carte du hameau est à l'écran du bas : la croix choisit une rue, A y va, B la ferme. */
    val mapOpen: StateFlow<Boolean> = _mapOpen.asStateFlow()

    private val _mapStreet = MutableStateFlow(0)

    /** La rue choisie sur la carte. */
    val mapStreet: StateFlow<Int> = _mapStreet.asStateFlow()

    private val _scene = MutableStateFlow<VillageSceneState?>(null)

    /** Le hameau et son coursier, `null` avant la première vue d'ensemble. */
    val scene: StateFlow<VillageSceneState?> = _scene.asStateFlow()

    /** Le coursier répond à la manette : le hameau existe et l'écran du haut le montre. */
    val active: Boolean get() = _scene.value != null && shown()

    /** Ce que le hameau montre d'une session : une vue d'ensemble qui ne change rien d'autre ne le refait pas. */
    private data class HouseKey(
        val id: SessionId,
        val project: String,
        val agent: String,
        val title: String?,
        val status: SessionStatus,
        val pending: Boolean,
    )

    /**
     * À chaque vue d'ensemble : le hameau est régénéré dès qu'une maison peut avoir changé (session
     * ajoutée ou retirée, projet, agent, titre, état), avec la disposition enregistrée ; le coursier y
     * garde sa place ([VillageSceneState.updateVillage]). La génération tourne sur [worker], un seul
     * fil à la fois, hors du fil principal ; le coursier est mis à jour par l'appelant, sur le sien.
     */
    suspend fun update(overview: Overview) {
        // Le hameau ne dépend pas de l'ordre de la vue d'ensemble : une session qui passe devant ne le refait pas.
        val houses = overview.sessions.map { HouseKey(it.id, it.project, it.agent.id, it.title, it.status, it.pendingApproval != null) }
            .sortedBy { it.id.value }
        val construction = _construction.value
        val key = Triple(houses, construction, layoutVersion.value)
        if (key == lastKey) return
        val village = withContext(worker) { generator().generate(overview, construction) }
        lastKey = key
        val current = _scene.value
        val scene = if (current == null) VillageSceneState(village).also { _scene.value = it } else current.also { it.updateVillage(village) }
        if (walkToSite && village.site != null && shown()) {
            walkToSite = false
            scene.send(CourierCommand.GoToSite)
        }
        enterWhenBuilt?.let { id ->
            if (village.houseOf(id) == null || village.site?.stage != ConstructionStage.FINISHED) return@let
            enterWhenBuilt = null
            // Resté devant son chantier : il entre dans la maison finie. Parti ailleurs : on le laisse.
            val site = village.site
            val tile = scene.courierTile
            if (shown() && site != null && !scene.walkingByHand && scene.destination == null &&
                kotlin.math.abs(tile.x - site.entry.x) + kotlin.math.abs(tile.y - site.entry.y) <= NEAR_SITE
            ) {
                scene.send(CourierCommand.GoTo(id, enter = true))
            }
        }
    }

    /** Une seule génération à la fois, sur [worker] ; la réservation d'un lancement précédent tombe. */
    private fun generator(): VillageGenerator =
        generator ?: VillageGenerator(store, catalog()).also {
            it.forgetUnboundReservation()
            generator = it
        }

    private fun changed() {
        changesFlow.value = Triple(_construction.value, layoutVersion.value, _mapOpen.value)
    }

    /**
     * La fiche est ouverte ou modifiée : [project] est le dossier choisi (`null` sans projet, ou pour un
     * nouveau projet encore sans nom valide). Le coursier marche jusqu'au terrain visé ; un nouveau
     * projet montre la rue qu'il ouvrirait. Rien n'est réservé. Ignoré pendant un lancement en cours.
     */
    fun planning(project: String?, newProject: Boolean) {
        val current = _construction.value
        val next = if (project == null && !newProject) null else Construction(project.orEmpty(), newProject)
        val same = current != null && next != null && current.project == next.project && current.newProject == next.newProject
        // Un lancement en cours ne bouge pas ; un échec reste lu tant que la fiche vise le même projet.
        if (current?.stage != null && (!current.failed || same)) return
        if (same || next == null && current == null) return
        _construction.value = next
        _mapOpen.value = false
        walkToSite = next != null
        changed()
    }

    /**
     * A sur la fiche : le terrain de [project] est réservé et enregistré avant la création. [prompted] :
     * une consigne part avec la session.
     */
    suspend fun launch(overview: Overview, project: String, newProject: Boolean, prompted: Boolean) {
        withContext(worker) { generator().reserve(overview, project) }
        _construction.value = Construction(project, newProject).launched(prompted)
        layoutVersion.value++
        walkToSite = true
        changed()
    }

    /** Le pont a créé la session : sa maison prendra le terrain réservé. La carte du hameau s'ouvre en bas. */
    suspend fun created(id: SessionId) {
        withContext(worker) { generator().bind(id) }
        _construction.value = _construction.value?.created(id)
        layoutVersion.value++
        _mapOpen.value = shown()
        _mapStreet.value = 0
        changed()
    }

    /** La création a échoué ou reste incertaine : l'étape s'arrête, le message reste lisible, le terrain se libère. */
    suspend fun failed(message: String) {
        withContext(worker) { generator().release() }
        _construction.value = _construction.value?.failedWith(message)
        layoutVersion.value++
        changed()
    }

    /** La fiche est fermée sans lancement (B) : rien n'est réservé, le chantier disparaît. */
    suspend fun cancel() {
        val current = _construction.value ?: return
        if (current.session != null && !current.failed) return
        withContext(worker) { generator().release() }
        _construction.value = null
        walkToSite = false
        layoutVersion.value++
        changed()
    }

    /** Ce que la vue d'ensemble et le fil disent de la session du chantier. */
    fun observe(session: Session?, thread: SessionThread?) {
        val current = _construction.value ?: return
        val next = current.observed(session, thread)
        if (next == current) return
        _construction.value = next
        if (next.finished && !current.finished) enterWhenBuilt = next.session
        changed()
    }

    /** Le coursier est entré dans une maison : un chantier fini ou arrêté s'efface, la carte se ferme. */
    fun settle() {
        val current = _construction.value
        if (current != null && (current.finished || current.failed)) {
            _construction.value = null
            enterWhenBuilt = null
        }
        _mapOpen.value = false
        changed()
    }

    /** B sur la carte : le carnet revient ; un chantier en cours continue en haut, un chantier fini ou arrêté s'efface. */
    fun closeMap() = settle()

    /** Croix sur la carte : la rue précédente ou suivante. */
    fun moveOnMap(delta: Int) {
        val streets = _scene.value?.village?.streets?.size ?: return
        if (streets == 0) return
        _mapStreet.value = (_mapStreet.value + delta).coerceIn(0, streets - 1)
    }

    /** A sur la carte, ou une rue touchée : le coursier va au chantier de cette rue, sinon à sa première maison. */
    fun goToStreet(index: Int) {
        val scene = _scene.value ?: return
        _mapStreet.value = index
        if (!shown()) return
        val street = scene.village.map().streets.getOrNull(index) ?: return
        when {
            street.lots.any { it is MapLot.Building } -> scene.send(CourierCommand.GoToSite)
            else -> street.lots.firstNotNullOfOrNull { (it as? MapLot.Built)?.house?.session?.id }
                ?.let { scene.send(CourierCommand.GoTo(it, enter = false)) }
        }
    }

    /** La maison choisie, quand le hameau est à l'écran. */
    fun focus(): SessionId? = _scene.value?.takeIf { shown() }?.focusedHouse()

    /** La ligne qui a le focus suit le coursier : L2, R2 et le mode console visent sa maison. */
    fun syncFocus() {
        focus()?.let(classic::focus)
    }

    fun handle(input: GamepadInput) {
        val scene = _scene.value
        if (scene == null || !shown() || classic.state.value.quit != null) return classic.handle(input)
        syncFocus()
        if (input !is GamepadInput.Pressed) return classic.handle(input)
        when (input.button) {
            // Le coursier marche tant que la direction est tenue (onDirection), pas à chaque répétition.
            Button.UP, Button.DOWN, Button.LEFT, Button.RIGHT -> Unit
            Button.A ->
                if (scene.facingDoor != null || scene.walkingByHand) {
                    scene.send(CourierCommand.Interact)
                } else {
                    scene.focusedHouse()?.let { scene.send(CourierCommand.GoTo(it)) }
                }
            Button.L1 -> scene.send(CourierCommand.Previous)
            Button.R1 -> nextOrPending(scene)
            else -> classic.handle(input)
        }
    }

    /**
     * R1 : la première validation en attente d'abord, celle de la vue d'ensemble et du bandeau du
     * carnet. Devant sa porte, le coursier y entre ; ailleurs, il y marche. Si sa maison n'est pas
     * encore bâtie (hameau en cours de régénération), la validation s'ouvre directement. Sans
     * validation, la maison suivante.
     */
    private fun nextOrPending(scene: VillageSceneState) {
        val pending = overview.value.firstPendingApproval?.session?.id ?: return scene.send(CourierCommand.Next)
        when {
            scene.village.houseOf(pending) == null -> classic.openPendingApproval()
            scene.facingDoor == pending -> scene.send(CourierCommand.Interact)
            else -> scene.send(CourierCommand.GoTo(pending))
        }
    }

    /**
     * Une direction tenue ([held]) ou relâchée. [free] : la vue d'ensemble a la manette (rien n'est
     * ouvert par-dessus). Un relâché passe toujours, pour que le coursier ne marche pas seul.
     */
    fun onDirection(button: Button, held: Boolean, free: Boolean) {
        val scene = _scene.value ?: return
        val direction = when (button) {
            Button.UP -> Direction.UP
            Button.DOWN -> Direction.DOWN
            Button.LEFT -> Direction.LEFT
            Button.RIGHT -> Direction.RIGHT
            else -> return
        }
        if (held && !(free && shown() && classic.state.value.quit == null)) return
        scene.send(CourierCommand.Move(direction, held))
    }

    /** Une ligne du carnet touchée : le coursier marche jusqu'à la maison et y entre. */
    fun goTo(id: SessionId) {
        if (!walkTo(id)) open(id)
    }

    /**
     * Le coursier marche jusqu'à la maison de [id] et y entre, si le hameau est à l'écran et que la
     * maison y est. Faux sinon : à l'appelant d'ouvrir directement.
     */
    fun walkTo(id: SessionId): Boolean {
        val scene = _scene.value
        if (scene == null || !shown() || scene.village.houseOf(id) == null) return false
        scene.send(if (scene.facingDoor == id) CourierCommand.Interact else CourierCommand.GoTo(id))
        return true
    }

    /** L2 tenu sur le hameau : le coursier parle tant que la dictée est ouverte. */
    fun talk(active: Boolean) {
        _scene.value?.send(CourierCommand.Talk(active))
    }

    /** Retour sur la vue d'ensemble : sorti d'une maison, le coursier se tient devant sa porte. */
    fun leaveHouse() {
        _scene.value?.leaveHouse()
    }

    private companion object {
        /** À une ou deux tuiles du devant du chantier, le coursier y est resté. */
        const val NEAR_SITE = 2
    }
}
