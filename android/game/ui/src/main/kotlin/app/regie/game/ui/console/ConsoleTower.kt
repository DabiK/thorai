package app.regie.game.ui.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Session
import app.regie.domain.model.SessionStatus
import app.regie.domain.model.ThreadLine
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.menu.PaperScreen
import app.regie.game.ui.menu.TOWER_BACKGROUND
import app.regie.game.ui.menu.TapHint
import app.regie.game.ui.menu.inhabitantSprite
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** La tour, au pixel natif : la partie montrée en bas, et les pieds de l'habitant (`fonds.json`, `scenes`). */
object TowerScene {
    /** Les pieds de l'habitant, sur son tapis devant le miroir. */
    const val INHABITANT_X = 236
    const val INHABITANT_Y = 176

    /** La bande montrée : 310 pixels de large (l'écran du bas), 125 de haut, centrée sur l'habitant. */
    const val WIDTH = 310
    const val HEIGHT = 125
    const val LEFT = INHABITANT_X - WIDTH / 2
    const val TOP = INHABITANT_Y + 6 - HEIGHT

    /** La pose de l'habitant selon l'état de la session. */
    fun pose(status: SessionStatus): String = when (status) {
        SessionStatus.WORKING -> "travail"
        SessionStatus.AWAITING_APPROVAL -> "attente"
        SessionStatus.IDLE, SessionStatus.FAILED -> "repos"
        SessionStatus.STOPPED -> "sommeil"
    }
}

/** Les gestes de la tour : une touche de la manette (« A », « B », « X », « Start », « → ») ou L2 maintenu. */
class TowerCallbacks(val press: (String) -> Unit = {}, val hold: (Boolean) -> Unit = {})

/** Défilement du fil compact : la croix (pas) et le stick droit (vitesse). */
class TowerScroll(val steps: Flow<Int>, val velocity: StateFlow<Float>)

/**
 * La tour de la console (maquette 12), sur l'écran du bas quand une autre appli occupe le haut :
 * l'habitant devant son miroir, l'état de la session, puis le fil compact et les touches. Une
 * validation en attente ([approval]) met ses choix dans les touches, comme dans une maison.
 */
@Composable
fun ConsoleTowerScreen(
    live: LiveSession?,
    session: Session,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    approval: Approval? = null,
    scroll: TowerScroll? = null,
    callbacks: TowerCallbacks = TowerCallbacks(),
) {
    val metrics = Game.metrics
    PaperScreen(null, towerHints(live, session, approval, callbacks), modifier.testTag("game-tower")) {
        Box(Modifier.fillMaxWidth().height(metrics.art(TowerScene.HEIGHT)).clipToBounds()) {
            TowerBand(session, assets)
            PixelFrame(Modifier.padding(start = metrics.dp(8), top = metrics.dp(8)), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(12))) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                    BasicText(stringResource(R.string.game_tower_title), style = GameText.display(44), maxLines = 1)
                    BasicText(stringResource(R.string.game_tower_pilots, session.agent.label), style = GameText.body(24, Game.colors.muted), maxLines = 1)
                }
            }
            StateTag(TagState.of(session.status), Modifier.align(Alignment.TopEnd).padding(top = metrics.dp(16), end = metrics.dp(8)))
        }
        CompactThread(live, Modifier.weight(1f), scroll)
    }
}

/** L'image de la tour, recadrée sur l'habitant, et l'habitant dans la pose de son état. */
@Composable
private fun TowerBand(session: Session, assets: GameAssets) {
    val metrics = Game.metrics
    val clock = Game.clock
    val p = metrics.pixelPx
    val sky = Color(0xFF1B5F53)
    Canvas(Modifier.fillMaxSize().background(sky)) {
        assets.resolve(TOWER_BACKGROUND)?.let { tower ->
            val f = tower.frame
            drawImage(
                tower.atlas.image,
                srcOffset = IntOffset(f.x + TowerScene.LEFT, f.y + TowerScene.TOP),
                srcSize = IntSize(TowerScene.WIDTH, TowerScene.HEIGHT),
                dstSize = IntSize(TowerScene.WIDTH * p, TowerScene.HEIGHT * p),
                filterQuality = FilterQuality.None,
            )
        }
        val sprite = assets.resolve(inhabitantSprite(session.agent, TowerScene.pose(session.status)), clock.value) ?: return@Canvas
        val f = sprite.frame
        drawImage(
            sprite.atlas.image,
            srcOffset = IntOffset(f.x, f.y),
            srcSize = IntSize(f.w, f.h),
            dstOffset = IntOffset((TowerScene.INHABITANT_X - TowerScene.LEFT - f.ax) * p, (TowerScene.INHABITANT_Y - TowerScene.TOP - f.ay) * p),
            dstSize = IntSize(f.w * p, f.h * p),
            filterQuality = FilterQuality.None,
        )
    }
}

private val ActionStatus?.unfinished: Boolean get() = this == ActionStatus.PENDING || this == ActionStatus.IN_PROGRESS

/** Le fil en version compacte : messages, réponses et actions, sans rien ajouter (ADR 17). */
@Composable
fun CompactThread(live: LiveSession?, modifier: Modifier = Modifier, scroll: TowerScroll? = null) {
    val metrics = Game.metrics
    val colors = Game.colors
    val lines = live?.lines.orEmpty()
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1) }
    if (scroll != null) {
        // Un pas de 0 ramène à la fin du fil (Gauche, comme dans une session).
        LaunchedEffect(scroll, lines.size) {
            scroll.steps.collect {
                val last = (lines.size - 1).coerceAtLeast(0)
                list.scrollToItem(if (it == 0) last else (list.firstVisibleItemIndex + it).coerceIn(0, last))
            }
        }
        LaunchedEffect(scroll.velocity) {
            while (true) {
                delay(16)
                if (scroll.velocity.value != 0f) list.scrollBy(scroll.velocity.value * 20)
            }
        }
    }
    when {
        live == null || live.loading -> BasicText(stringResource(R.string.game_thread_loading), modifier, style = GameText.body(28, colors.muted))
        lines.isEmpty() -> BasicText(stringResource(R.string.game_thread_empty), modifier, style = GameText.body(28, colors.muted))
        else -> LazyColumn(modifier.fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            items(lines, key = { it.key }) { line -> CompactLine(line) }
        }
    }
}

@Composable
private fun CompactLine(line: ThreadLine) {
    val metrics = Game.metrics
    val colors = Game.colors
    when (line) {
        is ThreadLine.UserMessage -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.widthIn(max = metrics.dp(1000)).background(colors.mine).padding(horizontal = metrics.dp(22), vertical = metrics.dp(16))) {
                BasicText(stringResource(R.string.game_thread_you), style = GameText.body(22, colors.muted))
                BasicText(line.text, style = GameText.body(30))
            }
        }
        is ThreadLine.AgentText -> BasicText(line.text, style = GameText.body(30))
        is ThreadLine.Read -> Tool(stringResource(if (line.inProgress) R.string.game_thread_reading else R.string.game_thread_read), line.files.joinToString(", "), line.inProgress)
        is ThreadLine.Edit -> Tool(stringResource(if (line.status.unfinished) R.string.game_thread_editing else R.string.game_thread_edited), line.path, line.status.unfinished)
        is ThreadLine.Run -> Tool(stringResource(if (line.status.unfinished) R.string.game_thread_running else R.string.game_thread_ran), line.command, line.status.unfinished)
        is ThreadLine.Other -> Tool(stringResource(R.string.game_thread_action), line.title, line.status.unfinished)
        is ThreadLine.TurnFailed -> BasicText(line.message ?: stringResource(R.string.game_thread_failed), style = GameText.body(28, colors.error))
        is ThreadLine.Interrupted -> BasicText(stringResource(R.string.game_thread_interrupted), style = GameText.body(26, colors.muted))
        is ThreadLine.AwaitingApproval -> StateTag(TagState.WAITING, label = listOfNotNull(stringResource(R.string.game_state_waiting), line.subject).joinToString(" : "))
    }
}

/** Une action en une ligne : un carré, le verbe, et ce que l'agent a exposé, en VT323. */
@Composable
private fun Tool(verb: String, subject: String, active: Boolean) {
    val metrics = Game.metrics
    val colors = Game.colors
    Row(Modifier.padding(start = metrics.dp(8)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
        Box(Modifier.size(metrics.art(3)).background(if (active) colors.work else colors.muted))
        BasicText(verb, style = GameText.body(24, colors.muted), maxLines = 1)
        BasicText(subject, style = GameText.code(30), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun towerHints(live: LiveSession?, session: Session, approval: Approval?, callbacks: TowerCallbacks): List<TapHint> = buildList {
    if (approval != null) {
        if (approval.optionFor(ApprovalDecision.ONCE) != null) add(TapHint("A", stringResource(R.string.game_card_allow_once)) { callbacks.press("A") })
        if (approval.optionFor(ApprovalDecision.SESSION) != null) add(TapHint("X", stringResource(R.string.game_approval_session)) { callbacks.press("X") })
        if (approval.optionFor(ApprovalDecision.REJECT) != null) add(TapHint("B", stringResource(R.string.game_approval_refuse)) { callbacks.press("B") })
    } else {
        add(TapHint("B", stringResource(R.string.game_hint_back)) { callbacks.press("B") })
        add(TapHint(KeyHintSpec("→", stringResource(R.string.game_hint_settings)), onTap = { callbacks.press("→") }))
    }
    add(TapHint(KeyHintSpec("L2", stringResource(R.string.game_hint_speak)), onHold = callbacks.hold))
    if ((live?.canInterrupt ?: session.status.isBusy) && approval == null) {
        add(TapHint("Start", stringResource(R.string.game_hint_interrupt)) { callbacks.press("Start") })
    }
}
