package app.regie.game.ui.demo

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import app.regie.domain.model.SessionId
import app.regie.game.engine.courier.CourierCommand
import app.regie.game.engine.courier.Direction
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.scene.VillageScene
import app.regie.game.ui.scene.rememberVillageSceneState
import kotlinx.coroutines.delay

/**
 * Le coursier sur le banc (ticket 44) : la croix ou les flèches marchent, A ou Entrée entre, L1 et
 * R1 vont de maison en maison, X va à une maison et y entre. La maison n'existe pas encore ici : le
 * coursier ressort une seconde après l'iris. Le vrai branchement de la manette est celui de l'app.
 */
@Composable
fun CourierSandbox(assets: GameAssets, sessions: Int) {
    val village = remember(assets, sessions) { DemoScene.village(sessions, assets) }
    val state = rememberVillageSceneState(village)
    var inside by remember { mutableStateOf<SessionId?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(inside) {
        if (inside != null) {
            delay(1_000)
            inside = null
            state.leaveHouse()
        }
    }
    VillageScene(
        state,
        assets,
        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { event ->
            val pressed = event.type == KeyEventType.KeyDown
            if (!pressed && event.type != KeyEventType.KeyUp) return@onKeyEvent false
            // Une touche tenue se répète : seul le premier appui compte, la direction reste tenue.
            if (event.nativeKeyEvent.repeatCount > 0) return@onKeyEvent true
            val direction = when (event.key.nativeKeyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
                KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
                KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
                else -> null
            }
            if (direction != null) {
                state.send(CourierCommand.Move(direction, pressed))
                return@onKeyEvent true
            }
            if (!pressed) return@onKeyEvent false
            val command = when (event.key.nativeKeyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> CourierCommand.Interact
                KeyEvent.KEYCODE_BUTTON_L1 -> CourierCommand.Previous
                KeyEvent.KEYCODE_BUTTON_R1 -> CourierCommand.Next
                KeyEvent.KEYCODE_BUTTON_X -> state.village.houses.lastOrNull()?.let { CourierCommand.GoTo(it.session.id) }
                else -> null
            } ?: return@onKeyEvent false
            state.send(command)
            true
        },
        onEnterHouse = { inside = it },
    )
}
