package app.regie.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.DictationProvider
import app.regie.domain.model.Overview
import app.regie.domain.model.VoiceSettings
import app.regie.game.engine.courier.Courier
import app.regie.game.engine.menu.GameMenu
import app.regie.game.engine.menu.MenuEntry
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.courier.CourierRows
import app.regie.game.ui.courier.CourierScreen
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.Choice
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlin.time.Instant

/** « Quitter » en cours : [busy] sessions à confirmer, ou l'arrêt déjà lancé. */
data class QuitPrompt(val busy: Int, val stopping: Boolean = false)

/** Les gestes d'un menu, au doigt comme à la manette. */
class MenuCallbacks(
    val onChoose: (Int) -> Unit = {},
    val onClose: () -> Unit = {},
    val onConfirmQuit: () -> Unit = {},
    val onCancelQuit: () -> Unit = {},
    val onOpenLetter: (Int) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onLeaveCourier: () -> Unit = {},
)

@Composable
internal fun entryLabel(entry: MenuEntry): String = stringResource(
    when (entry) {
        MenuEntry.CONTINUE -> R.string.game_menu_continue
        MenuEntry.NEW_SESSION -> R.string.game_menu_new_session
        MenuEntry.COURIER -> R.string.game_menu_courier
        MenuEntry.AGENTS -> R.string.game_menu_agents
        MenuEntry.SETTINGS -> R.string.game_menu_settings
        MenuEntry.PAUSE_ALL -> R.string.game_menu_pause_all
        MenuEntry.QUIT -> R.string.game_menu_quit
    },
)

/** Le détail d'une entrée, tiré de ce que le domaine expose : sessions, validations, agents. */
@Composable
private fun entryDetail(entry: MenuEntry, overview: Overview, unread: Int): String? = when (entry) {
    MenuEntry.CONTINUE -> {
        val sessions = overview.sessions.size
        val approvals = overview.sessions.count { it.pendingApproval != null }
        when {
            sessions == 0 -> stringResource(R.string.game_menu_no_session)
            approvals == 0 -> pluralStringResource(R.plurals.game_menu_sessions, sessions, sessions)
            else -> pluralStringResource(R.plurals.game_menu_sessions, sessions, sessions) + " · " +
                pluralStringResource(R.plurals.game_menu_approvals, approvals, approvals)
        }
    }
    MenuEntry.NEW_SESSION -> stringResource(R.string.game_menu_new_session_detail)
    MenuEntry.COURIER -> if (unread > 0) pluralStringResource(R.plurals.game_menu_unread, unread, unread) else null
    MenuEntry.AGENTS -> overview.agents.takeIf { it.isNotEmpty() }?.let { agents ->
        val connected = agents.count { it.connected == true }
        pluralStringResource(R.plurals.game_menu_agents_connected, connected, connected, agents.size)
    }
    MenuEntry.SETTINGS -> stringResource(R.string.game_menu_settings_detail)
    MenuEntry.PAUSE_ALL -> overview.busyCount.let { busy ->
        if (busy == 0) stringResource(R.string.game_menu_nothing_to_pause) else pluralStringResource(R.plurals.game_menu_busy, busy, busy)
    }
    MenuEntry.QUIT -> stringResource(R.string.game_menu_quit_detail)
}

@Composable
private fun choices(menu: GameMenu, overview: Overview, unread: Int, details: Boolean = true): List<Choice> =
    menu.entries.map { Choice(entryLabel(it), detail = if (details) entryDetail(it, overview, unread) else null) }

/** L'état du pont, en une pastille. */
@Composable
fun BridgePill(bridge: BridgeStatus, modifier: Modifier = Modifier) {
    val colors = Game.colors
    when (bridge) {
        BridgeStatus.Connected -> Pill(stringResource(R.string.game_bridge_ready), colors.work, modifier, background = colors.mine)
        BridgeStatus.Connecting -> Pill(stringResource(R.string.game_bridge_connecting), colors.wait, modifier)
        is BridgeStatus.Unreachable -> Pill(stringResource(R.string.game_bridge_unreachable), colors.error, modifier)
    }
}

/**
 * Le logo du titre, en Jersey 10 : un contour de papier de deux pixels et une ombre vert profond,
 * comme `text-shadow` dans la maquette 01.
 */
@Composable
fun TitleLogo(name: String, modifier: Modifier = Modifier, sizePx: Int = 300) {
    val metrics = Game.metrics
    val step = metrics.dp(8)
    val outline = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
    Box(modifier) {
        BasicText(name, Modifier.offset(y = metrics.dp(16)), style = GameText.display(sizePx, Color(0xFF1F583A)))
        outline.forEach { (x, y) -> BasicText(name, Modifier.offset(step * x, step * y), style = GameText.display(sizePx, Game.colors.paper)) }
        BasicText(name, style = GameText.display(sizePx, Game.colors.line))
    }
}

/**
 * L'écran titre, en haut : le hameau au lever du soleil et le logo. Sur deux écrans, le menu est en
 * bas et le haut invite à commencer ; sur un seul écran, le menu s'affiche ici.
 */
@Composable
fun TitleTopScreen(
    name: String,
    menu: GameMenu,
    overview: Overview,
    assets: GameAssets,
    singleScreen: Boolean,
    modifier: Modifier = Modifier,
    quit: QuitPrompt? = null,
    callbacks: MenuCallbacks = MenuCallbacks(),
) {
    val metrics = Game.metrics
    val clock = Game.clock
    Box(modifier.fillMaxSize().testTag("game-title")) {
        MenuBackdrop(assets, dim = false)
        TitleLogo(name, Modifier.align(Alignment.TopCenter).padding(top = metrics.dp(if (singleScreen) 24 else 70)), sizePx = if (singleScreen) 200 else 300)
        if (singleScreen) {
            MenuList(
                choices(menu, overview, 0),
                menu.selected,
                Modifier.align(Alignment.BottomCenter).padding(bottom = metrics.dp(40)).width(metrics.dp(980)),
                onTap = callbacks.onChoose,
            )
            quit?.let { QuitDialog(it, callbacks, Modifier.align(Alignment.Center)) }
        } else {
            PixelFrame(
                Modifier.align(Alignment.BottomCenter).padding(bottom = metrics.dp(72)).graphicsLayer { alpha = if (Blink.pulse(clock.value) < 1f) 0.6f else 1f },
                contentPadding = PaddingValues(horizontal = metrics.dp(28), vertical = metrics.dp(14)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                    KeyChip("A")
                    BasicText(stringResource(R.string.game_hint_start), style = GameText.body(34))
                }
            }
        }
    }
}

/** Le menu principal, en bas de l'écran titre (maquette 01). */
@Composable
fun MainMenuScreen(
    menu: GameMenu,
    overview: Overview,
    modifier: Modifier = Modifier,
    quit: QuitPrompt? = null,
    callbacks: MenuCallbacks = MenuCallbacks(),
) {
    val metrics = Game.metrics
    Box(modifier.fillMaxSize().testTag("game-main-menu")) {
        PaperScreen(
            stringResource(R.string.game_menu_title),
            listOf(
                TapHint("A", stringResource(R.string.game_hint_choose)) { callbacks.onChoose(menu.selected) },
                TapHint("L2", stringResource(R.string.game_hint_dictate_hold)),
                TapHint("R2", stringResource(R.string.game_hint_command)),
            ),
            headEnd = { BridgePill(overview.bridge) },
        ) {
            MenuList(choices(menu, overview, 0), menu.selected, Modifier.fillMaxWidth(), onTap = callbacks.onChoose)
            BasicText(
                stringResource(R.string.game_menu_keep_running),
                Modifier.fillMaxWidth(),
                style = GameText.body(24, Game.colors.muted).copy(textAlign = TextAlign.Center),
            )
        }
        quit?.let { QuitDialog(it, callbacks, Modifier.align(Alignment.BottomCenter).padding(bottom = metrics.dp(120))) }
    }
}

/**
 * Le menu de pause en haut (maquette 02) : le hameau assombri, le menu à gauche, et à droite ce que
 * montre l'entrée choisie. Sur un seul écran, il porte aussi le courrier, la confirmation et les touches.
 */
@Composable
fun PauseMenuTop(
    menu: GameMenu,
    overview: Overview,
    courier: Courier,
    voice: VoiceSettings,
    now: Instant,
    assets: GameAssets,
    singleScreen: Boolean,
    modifier: Modifier = Modifier,
    quit: QuitPrompt? = null,
    callbacks: MenuCallbacks = MenuCallbacks(),
) {
    val metrics = Game.metrics
    Box(modifier.fillMaxSize().testTag("game-pause-menu")) {
        MenuBackdrop(assets)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(metrics.dp(72)),
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(60)),
            ) {
                MenuList(
                    choices(menu, overview, courier.unread, details = false),
                    menu.selected,
                    Modifier.width(metrics.dp(MENU_WIDTH)),
                    title = stringResource(R.string.game_menu_title),
                    showCursor = !menu.courierOpen,
                )
                PixelFrame(
                    Modifier.weight(1f).fillMaxSize(),
                    thick = true,
                    contentPadding = PaddingValues(horizontal = metrics.dp(40), vertical = metrics.dp(32)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
                        PausePage(menu, overview, courier, voice, now, assets)
                    }
                }
            }
            if (singleScreen) TapHintBar(pauseHints(menu, callbacks))
        }
        if (singleScreen) quit?.let { QuitDialog(it, callbacks, Modifier.align(Alignment.Center)) }
    }
}

/** Ce que montre l'entrée choisie, dans le grand cadre du menu de pause. */
@Composable
private fun PausePage(menu: GameMenu, overview: Overview, courier: Courier, voice: VoiceSettings, now: Instant, assets: GameAssets) {
    val entry = if (menu.courierOpen) MenuEntry.COURIER else menu.current ?: return
    val colors = Game.colors
    BasicText(entryLabel(entry), style = GameText.display(64))
    when (entry) {
        MenuEntry.COURIER -> if (courier.letters.isEmpty()) {
            BasicText(stringResource(R.string.game_courier_empty), style = GameText.body(30, colors.muted))
        } else {
            CourierRows(courier, if (menu.courierOpen) menu.letter else -1, now, assets, limit = 5)
        }
        MenuEntry.AGENTS -> AgentRoster(overview, assets)
        MenuEntry.SETTINGS -> {
            BasicText(stringResource(if (voice.provider == DictationProvider.LOCAL) R.string.game_voice_local else R.string.game_voice_cloud), style = GameText.body(34, weight = 600))
            BasicText(stringResource(if (voice.keyConfigured) R.string.game_voice_key_saved else R.string.game_voice_key_missing), style = GameText.body(28, colors.muted))
            BasicText(stringResource(if (voice.commandKeyConfigured) R.string.game_voice_mistral_saved else R.string.game_voice_mistral_missing), style = GameText.body(28, colors.muted))
        }
        MenuEntry.PAUSE_ALL -> {
            BasicText(entryDetail(entry, overview, 0).orEmpty(), style = GameText.body(30, colors.muted))
            overview.sessions.filter { it.status.isBusy }.take(6).forEach {
                BasicText("${it.agent.label} · ${it.project}", style = GameText.body(30), maxLines = 1)
            }
        }
        MenuEntry.QUIT -> {
            BasicText(entryDetail(entry, overview, 0).orEmpty(), style = GameText.body(30))
            BasicText(stringResource(R.string.game_menu_keep_running), style = GameText.body(28, colors.muted))
        }
        MenuEntry.CONTINUE, MenuEntry.NEW_SESSION -> BasicText(entryDetail(entry, overview, 0).orEmpty(), style = GameText.body(30))
    }
}

@Composable
private fun pauseHints(menu: GameMenu, callbacks: MenuCallbacks): List<TapHint> = if (menu.courierOpen) {
    listOf(
        TapHint("A", stringResource(R.string.game_hint_open)) { callbacks.onOpenLetter(menu.letter) },
        TapHint("X", stringResource(R.string.game_hint_mark_read), callbacks.onMarkAllRead),
        TapHint("B", stringResource(R.string.game_hint_back), callbacks.onLeaveCourier),
    )
} else {
    listOf(
        TapHint("A", stringResource(R.string.game_hint_choose)) { callbacks.onChoose(menu.selected) },
        TapHint(KeyHintSpec(listOf("B", "Start"), stringResource(R.string.game_hint_close_menu)), onTap = callbacks.onClose),
    )
}

/** Le menu de pause en bas : la liste et ses détails, ou la page du courrier. */
@Composable
fun PauseMenuBottom(
    menu: GameMenu,
    overview: Overview,
    courier: Courier,
    now: Instant,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    quit: QuitPrompt? = null,
    callbacks: MenuCallbacks = MenuCallbacks(),
) {
    val metrics = Game.metrics
    Box(modifier.fillMaxSize()) {
        if (menu.courierOpen) {
            CourierScreen(courier, menu.letter, now, assets, pauseHints(menu, callbacks), onTap = callbacks.onOpenLetter)
        } else {
            PaperScreen(stringResource(R.string.game_menu_title), pauseHints(menu, callbacks), Modifier.testTag("game-pause-bottom"), headEnd = { BridgePill(overview.bridge) }) {
                MenuList(choices(menu, overview, courier.unread), menu.selected, Modifier.fillMaxWidth(), onTap = callbacks.onChoose)
            }
        }
        quit?.let { QuitDialog(it, callbacks, Modifier.align(Alignment.BottomCenter).padding(bottom = metrics.dp(120))) }
    }
}

/** La confirmation de « Quitter » : A confirme, B annule, au doigt aussi ; puis l'arrêt en cours. */
@Composable
fun QuitDialog(prompt: QuitPrompt, callbacks: MenuCallbacks, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    PixelFrame(
        modifier.padding(horizontal = metrics.dp(44)).testTag("game-quit"),
        thick = true,
        colors = FrameDefaults.alert(),
        contentPadding = PaddingValues(horizontal = metrics.dp(40), vertical = metrics.dp(32)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(24))) {
            if (prompt.stopping) {
                BasicText(stringResource(R.string.game_quit_stopping), style = GameText.body(34, weight = 600))
            } else {
                BasicText(pluralStringResource(R.plurals.game_quit_busy, prompt.busy, prompt.busy), style = GameText.body(34, weight = 600))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(36))) {
                    QuitAction("A", stringResource(R.string.game_quit_confirm), callbacks.onConfirmQuit)
                    QuitAction("B", stringResource(R.string.game_quit_cancel), callbacks.onCancelQuit)
                }
            }
        }
    }
}

@Composable
private fun QuitAction(key: String, label: String, onTap: () -> Unit) {
    val metrics = Game.metrics
    Row(
        Modifier.background(Game.colors.panel).tapped(onTap).padding(horizontal = metrics.dp(12), vertical = metrics.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        KeyChip(key)
        BasicText(label, style = GameText.body(30))
    }
}

/** Un toucher, sans effet d'encre : le jeu n'a pas d'ondulation Material. */
fun Modifier.tapped(onTap: () -> Unit): Modifier = clickable(interactionSource = null, indication = null, onClick = onTap)
