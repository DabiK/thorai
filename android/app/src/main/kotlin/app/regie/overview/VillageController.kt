package app.regie.overview

import app.regie.domain.model.Overview
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.village.HouseCatalog
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.engine.village.VillageLayoutStore
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.village.focusedHouse
import app.regie.game.ui.village.walkingByHand
import app.regie.input.Button
import app.regie.input.GamepadInput
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
 * - L1, R1 : maison précédente ou suivante ; R1 va d'abord à la validation en attente, et y entre.
 * - Une ligne du carnet touchée ([goTo]) : il y marche et y entre.
 * - Le reste (X, Y, B, Start, L2, R2, le stick droit, « Quitter ») suit la vue d'ensemble classique,
 *   qui vise la maison choisie.
 *
 * Sans hameau, ou quand l'écran du haut n'est pas visible (mode console), tout revient à
 * [classic] : un coursier qu'on ne voit pas ne marche pas pour rien.
 */
class VillageController(
    private val classic: OverviewController,
    private val store: VillageLayoutStore,
    private val catalog: () -> HouseCatalog,
    private val shown: () -> Boolean,
    private val open: (SessionId) -> Unit,
) {
    private var generator: VillageGenerator? = null
    private var lastKey: List<HouseKey>? = null

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
     * garde sa place ([VillageSceneState.updateVillage]).
     */
    fun update(overview: Overview) {
        val key = overview.sessions.map { HouseKey(it.id, it.project, it.agent.id, it.title, it.status, it.pendingApproval != null) }
        if (key == lastKey) return
        lastKey = key
        val village = (generator ?: VillageGenerator(store, catalog()).also { generator = it }).generate(overview)
        val current = _scene.value
        if (current == null) _scene.value = VillageSceneState(village) else current.updateVillage(village)
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
            Button.R1 -> scene.send(CourierCommand.Next)
            else -> classic.handle(input)
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
        val scene = _scene.value
        if (scene == null || !shown() || scene.village.houseOf(id) == null) open(id) else scene.send(CourierCommand.GoTo(id))
    }

    /** L2 tenu sur le hameau : le coursier parle tant que la dictée est ouverte. */
    fun talk(active: Boolean) {
        _scene.value?.send(CourierCommand.Talk(active))
    }

    /** Retour sur la vue d'ensemble : sorti d'une maison, le coursier se tient devant sa porte. */
    fun leaveHouse() {
        _scene.value?.leaveHouse()
    }
}
