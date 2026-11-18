package app.regie.game.ui.menu

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.regie.domain.model.Agent
import app.regie.domain.model.AgentAuthentication
import app.regie.domain.model.AgentAuthenticationReview
import app.regie.domain.model.AuthenticationPhase
import app.regie.domain.model.Overview
import app.regie.game.engine.menu.GameMenu
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.Choice
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.kit.WorkDots
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Les gestes de la connexion des agents : la manette passe par l'app, le doigt par ces rappels. */
class AgentsCallbacks(
    val select: (Int) -> Unit = {},
    val confirm: () -> Unit = {},
    val back: () -> Unit = {},
    val refresh: () -> Unit = {},
    val submitKey: (Agent, String) -> Unit = { _, _ -> },
    /** A, sur une clé à saisir, l'envoie : l'app garde ce geste tant que le champ est affiché. */
    val bindKeyAction: ((() -> Unit)?) -> Unit = {},
)

/** L'habitant d'un agent : son image dans l'atlas et son nom de rôle. */
fun inhabitantSprite(agent: Agent, pose: String = "repos"): String =
    "${InhabitantKind.of(agent).name.lowercase()}.$pose"

@Composable
fun inhabitantRole(agent: Agent): String = stringResource(
    when (InhabitantKind.of(agent)) {
        InhabitantKind.SCRIBE -> R.string.game_role_scribe
        InhabitantKind.BRICOLEUR -> R.string.game_role_bricoleur
        InhabitantKind.CARTOGRAPHE -> R.string.game_role_cartographe
    },
)

/** Le portrait d'un habitant, à l'échelle du jeu, sur un fond de papier foncé. */
@Composable
fun Portrait(agent: Agent, assets: GameAssets, modifier: Modifier = Modifier, pose: String = "repos", sizePx: Int = 128) {
    Box(modifier.size(Game.metrics.dp(sizePx)).background(Game.colors.paperDeep).clipToBounds(), contentAlignment = Alignment.BottomCenter) {
        SpriteImage(inhabitantSprite(agent, pose), assets, placeholderPixels = 24)
    }
}

private fun connectionState(connected: Boolean?, pending: Boolean = false): TagState = when {
    pending -> TagState.WORKING
    connected == true -> TagState.WORKING
    connected == false -> TagState.WAITING
    else -> TagState.IDLE
}

@Composable
private fun connectionLabel(connected: Boolean?, pending: Boolean = false): String = stringResource(
    when {
        pending -> R.string.game_agents_pending
        connected == true -> R.string.game_agents_connected
        connected == false -> R.string.game_agents_disconnected
        else -> R.string.game_agents_unknown
    },
)

/** Une ligne d'agent : portrait, nom, rôle et état de connexion. [focused] l'encadre en vert. */
@Composable
private fun AgentRow(agent: Agent, connected: Boolean?, pending: Boolean, assets: GameAssets, focused: Boolean, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    Box(modifier.fillMaxWidth()) {
        if (focused) PixelArrow(ArrowDirection.RIGHT, Modifier.align(Alignment.CenterStart), color = Game.colors.focus, blink = true)
        PixelFrame(
            Modifier.fillMaxWidth().padding(start = metrics.dp(36)).semantics { selected = focused },
            colors = if (focused) FrameDefaults.focus() else FrameDefaults.panel(),
            contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(10)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(24))) {
                Portrait(agent, assets)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
                    BasicText(agent.label, style = GameText.display(44), maxLines = 1)
                    BasicText(inhabitantRole(agent), style = GameText.body(26, Game.colors.muted), maxLines = 1)
                }
                StateTag(connectionState(connected, pending), label = connectionLabel(connected, pending))
            }
        }
    }
}

/** Les agents annoncés par le pont et leur connexion, d'après la vue d'ensemble. */
@Composable
fun AgentRoster(overview: Overview, assets: GameAssets) {
    if (overview.agents.isEmpty()) {
        BasicText(stringResource(R.string.game_agents_reading), style = GameText.body(30, Game.colors.muted))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Game.metrics.dp(12))) {
        overview.agents.forEach { AgentRow(it.agent, it.connected, false, assets, focused = false) }
    }
}

/**
 * La connexion des agents (ADR 18), dans le menu du jeu. En haut : les agents annoncés et leur
 * état, le menu à gauche s'il est ouvert. En bas : l'agent choisi, son code d'appareil (adresse,
 * code, QR), ses étapes ou sa clé, et les touches. Sur un seul écran, tout est en haut.
 */
@Composable
fun AgentsScreen(
    review: AgentAuthenticationReview,
    bottom: Boolean,
    singleScreen: Boolean,
    menu: GameMenu,
    overview: Overview,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    callbacks: AgentsCallbacks = AgentsCallbacks(),
) {
    val controls = bottom || singleScreen
    var key by remember(review.visible, review.current?.agent) { mutableStateOf("") }
    val sendKey = {
        val expected = review.current?.agent
        if (key.isNotBlank() && !review.busy && expected != null) {
            val entered = key
            key = ""
            callbacks.submitKey(expected, entered)
        }
    }
    DisposableEffect(key, review.busy, review.current?.agent, controls) {
        if (controls) callbacks.bindKeyAction(sendKey)
        onDispose { if (controls) callbacks.bindKeyAction(null) }
    }
    val hints = agentHints(review, key, menu.open, callbacks, sendKey)
    if (bottom) {
        val current = review.current
        PaperScreen(current?.agent?.label ?: stringResource(R.string.game_agents_title), hints, modifier.testTag("game-agents-bottom")) {
            if (review.agents.size > 1) AgentTabs(review, callbacks.select)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Game.metrics.dp(20))) {
                AgentDetail(review, showQr = true, key = key, onKey = { if (!review.busy) key = it })
            }
        }
        return
    }
    val metrics = Game.metrics
    Box(modifier.fillMaxSize().testTag("game-agents-top")) {
        MenuBackdrop(assets)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(metrics.dp(72)), horizontalArrangement = Arrangement.spacedBy(metrics.dp(60))) {
                if (menu.open) {
                    MenuList(
                        menu.entries.map { Choice(entryLabel(it)) },
                        menu.selected,
                        Modifier.width(metrics.dp(MENU_WIDTH)),
                        title = stringResource(R.string.game_menu_title),
                    )
                }
                PixelFrame(Modifier.weight(1f).fillMaxSize(), thick = true, contentPadding = PaddingValues(horizontal = metrics.dp(40), vertical = metrics.dp(32))) {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
                        BasicText(stringResource(R.string.game_agents_title), style = GameText.display(64))
                        if (review.agents.isEmpty()) BasicText(stringResource(R.string.game_agents_reading), style = GameText.body(30, Game.colors.muted))
                        review.agents.forEachIndexed { index, agent ->
                            AgentRow(agent.agent, agent.connected, agent.pending, assets, focused = index == review.selected)
                        }
                        if (singleScreen) {
                            AgentDetail(review, showQr = true, key = key, onKey = { if (!review.busy) key = it })
                        } else {
                            review.current?.account?.let { BasicText(it, style = GameText.body(28, Game.colors.muted)) }
                            if (review.notice.isNotBlank()) BasicText(review.notice, style = GameText.body(28, Game.colors.alertLine))
                        }
                    }
                }
            }
            if (singleScreen) TapHintBar(hints)
        }
    }
}

/** Les agents en onglets touchables, sur l'écran du bas. */
@Composable
private fun AgentTabs(review: AgentAuthenticationReview, select: (Int) -> Unit) {
    val metrics = Game.metrics
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        review.agents.forEachIndexed { index, agent ->
            PixelFrame(
                Modifier.tapped { select(index) },
                colors = if (index == review.selected) FrameDefaults.focus() else FrameDefaults.panel(),
                contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(10)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    Box(Modifier.size(metrics.art(3)).background(
                        when (connectionState(agent.connected, agent.pending)) {
                            TagState.WORKING -> Game.colors.work
                            TagState.WAITING -> Game.colors.wait
                            else -> Game.colors.idle
                        },
                    ))
                    BasicText(agent.agent.label, style = GameText.body(30, weight = 600), maxLines = 1)
                }
            }
        }
    }
}

/** Ce qui sert à connecter l'agent choisi. Les textes de l'agent et du pont restent tels quels. */
@Composable
private fun ColumnScope.AgentDetail(review: AgentAuthenticationReview, showQr: Boolean, key: String, onKey: (String) -> Unit) {
    val metrics = Game.metrics
    val colors = Game.colors
    val current: AgentAuthentication? = review.current
    if (current == null) {
        BasicText(stringResource(R.string.game_agents_reading), style = GameText.body(30, colors.muted))
    } else {
        current.account?.let { BasicText(it, style = GameText.body(28, colors.muted)) }
        val url = current.url
        val code = current.code
        if (url != null && code != null) {
            DeviceCode(current.agent, url, code, showQr)
        }
        current.message?.takeIf { it != current.login.help }?.let { BasicText(it, style = GameText.body(30)) }
        if (current.phase == AuthenticationPhase.STARTING) BasicText(stringResource(R.string.game_agents_preparing), style = GameText.body(30, colors.muted))
        if (current.connected != true && current.login.help.isNotEmpty()) BasicText(current.login.help, style = GameText.body(28, colors.muted))
        if (current.login.method == "api_key" && current.connected != true) {
            val fieldName = stringResource(R.string.game_agents_key_field)
            BasicText(current.login.label, style = GameText.body(28, weight = 600))
            PixelFrame(Modifier.fillMaxWidth(), colors = FrameDefaults.focus(), contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(14))) {
                BasicTextField(
                    value = key,
                    onValueChange = onKey,
                    enabled = !review.busy,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = GameText.code(40),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = metrics.dp(48)).semantics { contentDescription = fieldName },
                )
            }
        }
        if (current.connected == true) BasicText(stringResource(R.string.game_agents_account_kept), style = GameText.body(28, colors.muted))
    }
    if (review.busy) BasicText(stringResource(R.string.game_agents_checking), style = GameText.body(28, colors.muted))
    if (review.notice.isNotBlank()) BasicText(review.notice, style = GameText.body(28, colors.alertLine))
}

/** Le code d'appareil de Codex : QR de l'adresse, adresse, code et attente (maquette 02). */
@Composable
private fun DeviceCode(agent: Agent, url: String, code: String, showQr: Boolean) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(Modifier.fillMaxWidth(), thick = true, contentPadding = PaddingValues(metrics.dp(32))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(40))) {
            if (showQr) {
                val qr = remember(url) { authenticationQr(url).asImageBitmap() }
                Image(
                    qr,
                    stringResource(R.string.game_agents_qr),
                    Modifier.size(metrics.dp(288)),
                    filterQuality = FilterQuality.None,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(22))) {
                Step(1) {
                    BasicText(stringResource(R.string.game_agents_open_address), style = GameText.body(30))
                    BasicText(url, style = GameText.code(34))
                }
                Step(2) {
                    BasicText(stringResource(R.string.game_agents_enter_code), style = GameText.body(30))
                    BasicText(code, style = GameText.code(72))
                }
                Step(3) { BasicText(stringResource(R.string.game_agents_wait_here), style = GameText.body(30)) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        WorkDots(colors.work)
        BasicText(stringResource(R.string.game_agents_waiting_for, agent.label), style = GameText.body(28, colors.muted))
    }
}

@Composable
private fun Step(number: Int, content: @Composable ColumnScope.() -> Unit) {
    val metrics = Game.metrics
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        Box(Modifier.size(metrics.dp(44)).background(Game.colors.line), contentAlignment = Alignment.Center) {
            BasicText("$number", style = GameText.body(26, Game.colors.light))
        }
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(6)), content = content)
    }
}

/** Les touches de la connexion, mêmes libellés que la vue classique. */
@Composable
private fun agentHints(
    review: AgentAuthenticationReview,
    key: String,
    fromMenu: Boolean,
    callbacks: AgentsCallbacks,
    sendKey: () -> Unit,
): List<TapHint> {
    val current = review.current
    val list = mutableListOf<TapHint>()
    if (review.agents.size > 1) list += TapHint(KeyHintSpec("Croix", stringResource(R.string.game_hint_choose_agent)))
    if (!review.busy && current?.login?.method == "api_key" && current.connected != true && key.isNotBlank()) {
        list += TapHint("A", stringResource(R.string.game_agents_save_key), sendKey)
    }
    if (!review.busy && current != null && !current.pending && (current.login.method != "api_key" || current.connected == true)) {
        val label = when {
            review.confirmingLogout -> stringResource(R.string.game_agents_confirm_logout)
            current.connected == true -> stringResource(R.string.game_agents_logout)
            else -> current.login.label
        }
        list += TapHint("A", label, callbacks.confirm)
    }
    if (!review.busy) {
        val back = when {
            current?.pending == true -> stringResource(R.string.game_agents_cancel_close)
            review.confirmingLogout -> stringResource(R.string.game_agents_keep)
            fromMenu -> stringResource(R.string.game_hint_back)
            else -> stringResource(R.string.game_hint_close)
        }
        list += TapHint("B", back, callbacks.back)
        list += TapHint("Y", stringResource(R.string.game_hint_refresh), callbacks.refresh)
    }
    return list
}

/** Le QR contient seulement l'adresse publique de vérification, avec sa marge de quatre modules. */
internal fun authenticationQr(url: String): Bitmap {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 384, 384)
    val ink = 0xFF22343A.toInt()
    val paper = 0xFFFFFFFF.toInt()
    val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) ink else paper }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
