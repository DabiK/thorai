package app.regie.game.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierAction
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.courier.CourierController
import app.regie.game.engine.courier.CourierEvent
import app.regie.game.engine.courier.Direction
import app.regie.game.engine.village.Village
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.GroundPainter
import kotlinx.coroutines.channels.Channel

/**
 * L'état de la scène du hameau : le coursier ([CourierController]) et ce que l'écran en montre.
 * L'app lui envoie les commandes ([send]) et chaque nouveau hameau ([updateVillage]) ; la scène fait
 * tourner l'horloge ([runFrames]) tant que le coursier marche, et rien d'autre.
 *
 * - [foot] : les pieds du coursier, au pixel près, glissant d'une tuile à l'autre pendant un pas.
 * - [facingDoor] : la maison dont il regarde la porte, à l'arrêt (A y entre ; panneau « A Entrer »).
 * - [destination] : la maison visée par une marche automatique.
 * - [entering] : la maison où il entre ; l'iris se ferme sur sa porte, les commandes sont ignorées
 *   jusqu'à [leaveHouse].
 *
 * Les valeurs sont des états Compose lus au dessin : marcher ne recompose pas la scène.
 */
@Stable
class VillageSceneState(
    village: Village,
    start: TilePos = village.spawn,
    facing: Direction = Direction.DOWN,
) {
    private val controller = CourierController(village, start, facing, ::onCourierEvent)

    var village: Village by mutableStateOf(village)
        private set

    var foot: ArtPoint by mutableStateOf(footOf(controller))
        private set

    /** Le point que vise la caméra : le milieu de la tuile du coursier, au-dessus de ses pieds. */
    var cameraFocus: ArtPoint by mutableStateOf(ArtPoint(foot.x, foot.y - FOCUS_ABOVE_FEET))
        private set

    var action: CourierAction by mutableStateOf(controller.action)
        private set

    var facing: Direction by mutableStateOf(controller.facing)
        private set

    private val actionMillisState = mutableLongStateOf(0L)

    /** Depuis combien de temps dure [action] : la phase de la marche. */
    val actionMillis: Long get() = actionMillisState.longValue

    var facingDoor: SessionId? by mutableStateOf(null)
        private set

    var destination: SessionId? by mutableStateOf(null)
        private set

    var entering: SessionId? by mutableStateOf(null)
        private set

    /** Vrai juste après [leaveHouse], le temps que l'iris se rouvre. */
    var leaving: Boolean by mutableStateOf(false)
        private set

    /** Réveille l'horloge quand le coursier se met en marche. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var lastFrameMillis = NO_FRAME
    private val frameCallback: (Long) -> Unit = { nanos -> onFrame(nanos / 1_000_000L) }

    init {
        sync()
    }

    /** Une commande du joueur. Pendant l'entrée dans une maison, seuls les relâchements passent. */
    fun send(command: CourierCommand) {
        if (entering != null && !(command is CourierCommand.Move && !command.pressed)) return
        controller.handle(command)
        sync()
        wakeIfBusy()
    }

    /** Le hameau régénéré : le coursier garde sa place par rapport à la place, la marche reprend. */
    fun updateVillage(next: Village) {
        if (next === village) return
        controller.setVillage(next)
        village = next
        sync()
        wakeIfBusy()
    }

    /** Retour d'une maison : le coursier ressort devant la porte, face au hameau, et l'iris se rouvre. */
    fun leaveHouse() {
        if (entering == null) return
        entering = null
        leaving = true
        controller.rest(Direction.DOWN)
        sync()
    }

    internal fun irisOpened() {
        leaving = false
    }

    /** Fait avancer le coursier jusqu'à l'image de [frameMillis] ; un écart trop long est borné. */
    fun onFrame(frameMillis: Long) {
        val delta = if (lastFrameMillis == NO_FRAME) 0L else (frameMillis - lastFrameMillis).coerceIn(0L, MAX_FRAME_MILLIS)
        lastFrameMillis = frameMillis
        controller.advance(delta)
        sync()
    }

    /** L'horloge de la scène : une image après l'autre tant que le coursier marche, rien à l'arrêt. */
    suspend fun runFrames() {
        while (true) {
            if (!controller.busy) wake.receive()
            lastFrameMillis = NO_FRAME
            while (controller.busy) withFrameNanos(frameCallback)
        }
    }

    private fun wakeIfBusy() {
        if (controller.busy) wake.trySend(Unit)
    }

    private fun onCourierEvent(event: CourierEvent) {
        if (event is CourierEvent.EnterHouse) entering = event.sessionId
    }

    private fun sync() {
        val x = footX(controller)
        val y = footY(controller)
        if (x != foot.x || y != foot.y) {
            foot = ArtPoint(x, y)
            cameraFocus = ArtPoint(x, y - FOCUS_ABOVE_FEET)
        }
        action = controller.action
        facing = controller.facing
        actionMillisState.longValue = controller.actionMillis
        destination = controller.destination
        facingDoor = if (controller.moving) {
            null
        } else {
            controller.village.houseAtEntry(controller.tile)?.takeIf { controller.facing.next(controller.tile) == it.door }?.session?.id
        }
    }

    companion object {
        private const val NO_FRAME = Long.MIN_VALUE

        /** Au-delà, l'app a sans doute été suspendue : on ne rattrape pas des secondes de marche. */
        const val MAX_FRAME_MILLIS = 100L

        private const val TILE = GroundPainter.TILE

        /** Des pieds (dernière rangée de la tuile) au milieu de la tuile. */
        private const val FOCUS_ABOVE_FEET = TILE / 2 - 1

        /** Les pieds au milieu du bas de la tuile, comme un objet d'une tuile ([app.regie.game.ui.render.WorldLayout.footOf]). */
        private fun footX(c: CourierController): Int =
            c.tile.x * TILE + TILE / 2 + (c.stepDirection?.dx ?: 0) * c.stepMillis * TILE / CourierController.STEP_MILLIS

        private fun footY(c: CourierController): Int =
            c.tile.y * TILE + TILE - 1 + (c.stepDirection?.dy ?: 0) * c.stepMillis * TILE / CourierController.STEP_MILLIS

        private fun footOf(c: CourierController) = ArtPoint(footX(c), footY(c))
    }
}

/** L'état de la scène, gardé tant que la composition vit ; [village] le met à jour à chaque régénération. */
@Composable
fun rememberVillageSceneState(village: Village, start: TilePos = village.spawn): VillageSceneState {
    val state = remember { VillageSceneState(village, start) }
    SideEffect { state.updateVillage(village) }
    return state
}
