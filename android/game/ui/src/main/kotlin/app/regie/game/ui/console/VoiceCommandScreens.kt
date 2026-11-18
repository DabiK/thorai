package app.regie.game.ui.console

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.regie.domain.model.CommandPhase
import app.regie.domain.model.VoiceCommandReview
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.DialogBox
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.MicMeter
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.menu.MenuBackdrop
import app.regie.game.ui.menu.PaperScreen
import app.regie.game.ui.menu.TapHint
import app.regie.game.ui.menu.TapHintBar
import app.regie.game.ui.menu.tapped
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Les gestes de l'ordre vocal : A confirme, B annule, R2 maintenu redit, et le lien vers la voix. */
class CommandCallbacks(
    val confirm: () -> Unit = {},
    val cancel: () -> Unit = {},
    val hold: (Boolean) -> Unit = {},
    val settings: () -> Unit = {},
)

/** Défilement du texte au stick et à la croix, comme le fil. */
class CommandScroll(val steps: Flow<Int>, val velocity: StateFlow<Float>)

@Composable
private fun rememberCommandScroll(state: VoiceCommandReview, scroll: CommandScroll?): ScrollState {
    val position = rememberScrollState()
    if (scroll != null) {
        LaunchedEffect(scroll) { scroll.steps.collect { position.scrollTo(position.value + it * 150) } }
        LaunchedEffect(scroll.velocity) {
            while (true) {
                delay(16)
                if (scroll.velocity.value != 0f) position.scrollTo(position.value + (scroll.velocity.value * 20).toInt())
            }
        }
    }
    LaunchedEffect(state.phase) { position.scrollTo(0) }
    return position
}

/** Le niveau du micro en escalier, sur les dernières mesures. */
@Composable
private fun LevelHistory(level: Float) {
    val levels = remember { mutableStateListOf<Float>().apply { repeat(LEVEL_BARS) { add(0f) } } }
    LaunchedEffect(level) {
        levels.removeAt(0)
        levels.add(level)
    }
    MicMeter(levels.toList(), heightPx = 64)
}

private const val LEVEL_BARS = 12

@Composable
private fun commandHints(state: VoiceCommandReview, callbacks: CommandCallbacks): List<TapHint> = buildList {
    if (state.canConfirm) add(TapHint("A", stringResource(R.string.game_command_confirm), callbacks.confirm))
    if (state.phase != CommandPhase.RUNNING) {
        add(TapHint("B", stringResource(if (state.phase == CommandPhase.DONE) R.string.game_hint_back else R.string.game_command_cancel), callbacks.cancel))
    }
    if (state.phase in setOf(CommandPhase.REVIEW, CommandPhase.UNKNOWN, CommandPhase.DONE)) {
        add(TapHint(KeyHintSpec("R2", stringResource(R.string.game_command_redo)), onHold = callbacks.hold))
    }
}

@Composable
private fun ColumnScope.Recording(state: VoiceCommandReview) {
    if (state.phase != CommandPhase.RECORDING && state.phase != CommandPhase.FINALIZING) return
    val metrics = Game.metrics
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(28))) {
        LevelHistory(state.level)
        Column {
            BasicText("${state.durationMs / 1000} s", style = GameText.code(36, Game.colors.muted))
            BasicText(stringResource(R.string.game_command_release), style = GameText.body(26, Game.colors.muted))
        }
    }
}

/**
 * L'ordre vocal (R2) dans le jeu. En haut : la phrase entendue dans une boîte de dialogue, puis ce
 * que le domaine en a compris. En bas : la réponse, le niveau du micro et les touches.
 * Sur un seul écran, tout est en haut.
 */
@Composable
fun GameVoiceCommandScreen(
    state: VoiceCommandReview,
    bottom: Boolean,
    singleScreen: Boolean,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    scroll: CommandScroll? = null,
    callbacks: CommandCallbacks = CommandCallbacks(),
) {
    val position = rememberCommandScroll(state, scroll)
    val metrics = Game.metrics
    if (bottom) {
        PaperScreen(
            stringResource(R.string.game_command_title),
            commandHints(state, callbacks),
            modifier.testTag("game-command-bottom"),
            headEnd = { SettingsLink(callbacks.settings) },
        ) {
            Column(Modifier.weight(1f).verticalScroll(position), verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                BasicText(state.notice, style = GameText.body(36, weight = 600))
            }
            Recording(state)
        }
        return
    }
    Box(modifier.fillMaxSize().testTag("game-command-top")) {
        MenuBackdrop(assets)
        Column(Modifier.fillMaxSize()) {
            // Une réplique : la boîte de dialogue en bas de la scène, comme dans les maisons.
            Spacer(Modifier.weight(1f))
            DialogBox(
                Modifier.fillMaxWidth().heightIn(max = metrics.dp(760)).padding(metrics.dp(72)),
                speaker = stringResource(R.string.game_command_title),
                speakerColor = Game.colors.focus,
                showNext = false,
            ) {
                Column(Modifier.verticalScroll(position), verticalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
                    BasicText("« ${state.text.ifEmpty { "…" }} »", style = GameText.body(44))
                    BasicText(
                        stringResource(if (state.phase == CommandPhase.REVIEW) R.string.game_command_proposed else R.string.game_command_title),
                        style = GameText.body(24, Game.colors.muted),
                    )
                    BasicText(state.notice, style = GameText.body(34))
                    if (singleScreen) Recording(state)
                }
            }
            if (singleScreen) TapHintBar(commandHints(state, callbacks))
        }
    }
}

/** L'ordre vocal par-dessus une autre appli (mode console) : un cadre compact, sans toucher. */
@Composable
fun GameCommandOverlay(state: VoiceCommandReview, modifier: Modifier = Modifier, scroll: CommandScroll? = null) {
    val metrics = Game.metrics
    val position = rememberCommandScroll(state, scroll)
    PixelFrame(
        modifier.size(metrics.dp(1240), metrics.dp(600)).testTag("game-command-overlay"),
        thick = true,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = metrics.dp(40), vertical = metrics.dp(28)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(16)),
            ) {
                BasicText(stringResource(R.string.game_command_title), style = GameText.display(56))
                Column(Modifier.weight(1f).verticalScroll(position), verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    BasicText("« ${state.text.ifEmpty { "…" }} »", style = GameText.body(34))
                    BasicText(state.notice, style = GameText.body(32, weight = 600))
                }
                Recording(state)
            }
            TapHintBar(commandHints(state, CommandCallbacks()))
        }
    }
}

@Composable
private fun SettingsLink(onTap: () -> Unit) {
    BasicText(stringResource(R.string.game_voice_settings_link), Modifier.tapped(onTap), style = GameText.body(26, Game.colors.muted))
}
