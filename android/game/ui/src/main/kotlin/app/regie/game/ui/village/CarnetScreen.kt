package app.regie.game.ui.village

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.SessionId
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.KeyChipColors
import app.regie.game.ui.kit.KeyOrder
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.kit.tagLabel
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * L'écran du bas de la vue d'ensemble (maquette 03) : le carnet. En tête, la première validation en
 * attente ; puis une ligne par session, celle du coursier choisie ; puis le micro et les actions.
 *
 * - [focused] : la maison où va ou devant laquelle se tient le coursier ([focusedHouse]).
 * - [atDoor] : le coursier regarde une porte ; A y entre (« Entrer ») au lieu d'y aller.
 * - [scrollVelocity] : le stick droit fait défiler la liste.
 */
@Composable
fun CarnetBottomScreen(
    carnet: Carnet,
    focused: SessionId?,
    atDoor: Boolean,
    assets: GameAssets,
    callbacks: CarnetCallbacks,
    modifier: Modifier = Modifier,
    scrollVelocity: StateFlow<Float> = MutableStateFlow(0f),
) {
    val metrics = Game.metrics
    Box(modifier.fillMaxSize().background(Game.colors.paper)) {
        Column(Modifier.fillMaxSize()) {
            CarnetHead(carnet, callbacks, Modifier.padding(start = metrics.dp(40), end = metrics.dp(24), top = metrics.dp(20)))
            Column(
                Modifier.padding(horizontal = metrics.dp(28)).padding(top = metrics.dp(12)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(14)),
            ) {
                carnet.notices.forEach { NoticeCard(it) }
                carnet.approval?.let { ApprovalCard(it, assets, callbacks) }
            }
            SessionRows(carnet, focused, assets, callbacks, scrollVelocity, Modifier.weight(1f).fillMaxWidth())
            MicRow(carnet, callbacks, Modifier.padding(horizontal = metrics.dp(40), vertical = metrics.dp(14)))
            ActionBar(atDoor, callbacks)
        }
        carnet.quit?.let { CarnetQuitPrompt(it, callbacks) }
    }
}

/**
 * Le carnet en bande, sous le hameau, en mode un seul écran : la validation en attente ou la maison
 * choisie sur une ligne, le micro et les actions. La confirmation de « Quitter » est à poser par-dessus
 * tout l'écran ([CarnetQuitPrompt]).
 */
@Composable
fun CompactCarnet(
    carnet: Carnet,
    focused: SessionId?,
    atDoor: Boolean,
    assets: GameAssets,
    callbacks: CarnetCallbacks,
    modifier: Modifier = Modifier,
) {
    val metrics = Game.metrics
    Column(modifier.fillMaxWidth().background(Game.colors.paper)) {
        Column(
            Modifier.padding(horizontal = metrics.dp(24), vertical = metrics.dp(12)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(10)),
        ) {
            carnet.notices.forEach { NoticeCard(it) }
            val approval = carnet.approval
            val row = carnet.rows.firstOrNull { it.session == focused }
            when {
                approval != null -> ApprovalCard(approval, assets, callbacks, compact = true)
                row != null -> SessionRowView(row, focused = true, assets, compact = true) { callbacks.onGoTo(row.session) }
                carnet.empty != null -> EmptyText(carnet.empty, Modifier.fillMaxWidth())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(28))) {
                MicHints(carnet, callbacks, textSizePx = 24)
                Spacer(Modifier.weight(1f))
                val header = carnet.header
                BasicText(header.count, style = GameText.body(24, Game.colors.muted), maxLines = 1)
                header.bridge?.let { BasicText(it, style = GameText.body(24, toneColor(header.bridgeTone), weight = 600), maxLines = 1) }
                Shortcuts(carnet, callbacks, textSizePx = 24)
                QuitLink(callbacks, textSizePx = 24)
            }
        }
        ActionBar(atDoor, callbacks, compact = true)
    }
}

/** L'en-tête : « Hameau », le nombre de sessions, l'état du pont, les raccourcis tactiles et les versions. */
@Composable
private fun CarnetHead(carnet: Carnet, callbacks: CarnetCallbacks, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val header = carnet.header
    Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(24))) {
            BasicText(stringResource(R.string.hameau_title), style = GameText.display(60, colors.line), maxLines = 1)
            BasicText(header.count, style = GameText.body(28, colors.muted), maxLines = 1)
            header.bridge?.let { BasicText(it, style = GameText.body(26, toneColor(header.bridgeTone), weight = 600), maxLines = 1) }
            Spacer(Modifier.weight(1f))
            Shortcuts(carnet, callbacks, textSizePx = 26)
        }
        if (header.versions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(20))) {
                header.versions.forEach { version ->
                    BasicText(version.label, style = GameText.code(28, if (version.tested) colors.muted else colors.alertLine), maxLines = 1)
                }
            }
        }
    }
}

/** « Configurer » (un point s'il reste une étape) et « Régler la voix », au doigt. */
@Composable
private fun Shortcuts(carnet: Carnet, callbacks: CarnetCallbacks, textSizePx: Int) {
    val metrics = Game.metrics
    val colors = Game.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(28))) {
        Row(
            Modifier.testTag("open-setup").clickable(onClick = callbacks::onSetup).padding(vertical = metrics.dp(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
        ) {
            if (carnet.setupLeft) Box(Modifier.size(metrics.art(2)).background(colors.wait).testTag("setup-left"))
            BasicText(stringResource(R.string.hameau_setup), style = GameText.body(textSizePx, colors.muted), maxLines = 1)
        }
        BasicText(
            stringResource(R.string.hameau_voice_settings),
            style = GameText.body(textSizePx, colors.muted),
            maxLines = 1,
            modifier = Modifier.clickable(onClick = callbacks::onVoiceSettings).padding(vertical = metrics.dp(8)),
        )
    }
}

@Composable
private fun NoticeCard(notice: CarnetNotice) {
    val metrics = Game.metrics
    PixelFrame(
        Modifier.fillMaxWidth(),
        colors = if (notice.tone == CarnetTone.WAIT || notice.tone == CarnetTone.ERROR) FrameDefaults.alert() else FrameDefaults.panel(),
        contentPadding = PaddingValues(horizontal = metrics.dp(24), vertical = metrics.dp(14)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
            BasicText(notice.title, style = GameText.body(28, toneColor(notice.tone), weight = 600), maxLines = 1)
            BasicText(notice.hint, style = GameText.body(24, Game.colors.ink))
        }
    }
}

/** La première validation : l'habitant qui attend, la session, la commande ou le fichier, et R1. */
@Composable
private fun ApprovalCard(approval: CarnetApproval, assets: GameAssets, callbacks: CarnetCallbacks, compact: Boolean = false) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(
        Modifier.fillMaxWidth().testTag("carnet-approval").clickable { callbacks.onKey(CarnetKey.R1) },
        colors = FrameDefaults.alert(),
        contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(if (compact) 10 else 16)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(22))) {
            Portrait(approval.inhabitant, TagState.WAITING, stopped = false, assets, colors.wait, if (compact) 72 else 96)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                BasicText("! ${approval.title}", style = GameText.body(26, colors.waitInk, weight = 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(approval.where, style = GameText.body(26, colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) approval.subject?.let {
                    BasicText(it, style = GameText.code(32, colors.waitInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            CarnetHint(listOf("R1"), stringResource(R.string.hameau_see), onTap = { callbacks.onKey(CarnetKey.R1) })
        }
    }
}

/** La liste des sessions : la ligne du coursier suivie, le reste au stick droit. */
@Composable
private fun SessionRows(
    carnet: Carnet,
    focused: SessionId?,
    assets: GameAssets,
    callbacks: CarnetCallbacks,
    velocity: StateFlow<Float>,
    modifier: Modifier,
) {
    val metrics = Game.metrics
    val rows = carnet.rows
    if (rows.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { carnet.empty?.let { EmptyText(it, Modifier.padding(metrics.dp(40))) } }
        return
    }
    val list = rememberLazyListState()
    val index = rows.indexOfFirst { it.session == focused }
    LaunchedEffect(index, rows.size) {
        if (index < 0) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.index == index }
        val end = list.layoutInfo.viewportEndOffset
        if (item == null || item.offset < 0 || item.offset + item.size > end) list.animateScrollToItem(index)
    }
    val speed = metrics.pixelPx * STICK_PIXELS_PER_SECOND
    LaunchedEffect(list, velocity) {
        velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                list.scrollBy(value * speed * (frame - last) / 1e9f)
                last = frame
            }
        }
    }
    LazyColumn(
        state = list,
        modifier = modifier.testTag("carnet-sessions"),
        contentPadding = PaddingValues(horizontal = metrics.dp(28), vertical = metrics.dp(12)),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        items(rows, key = { it.session.value }) { row ->
            SessionRowView(row, row.session == focused, assets) { callbacks.onGoTo(row.session) }
        }
    }
}

/** Le stick droit fait défiler de 220 pixels du dessin par seconde à fond. */
private const val STICK_PIXELS_PER_SECOND = 220f

/** Une ligne : l'habitant, projet et branche, session et agent, la demande ou la dernière action, l'état et l'ancienneté. */
@Composable
private fun SessionRowView(row: CarnetRow, focused: Boolean, assets: GameAssets, compact: Boolean = false, onTap: () -> Unit) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(
        Modifier.fillMaxWidth().semantics { selected = focused }.testTag("carnet-row-${row.session.value}").clickable(onClick = onTap),
        colors = if (focused) FrameDefaults.focus() else FrameDefaults.panel(),
        contentPadding = PaddingValues(start = metrics.dp(14), end = metrics.dp(22), top = metrics.dp(8), bottom = metrics.dp(8)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
            Box(Modifier.width(metrics.art(3))) {
                if (focused) PixelArrow(ArrowDirection.RIGHT, color = colors.focus, blink = true)
            }
            Portrait(row.inhabitant, row.state, row.stopped, assets, colors.paperDeep, if (compact) 72 else 88)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(2))) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
                    BasicText(
                        row.project,
                        style = GameText.display(46, colors.ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    row.branch?.let { BasicText(it, style = GameText.code(28, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                BasicText(
                    listOfNotNull(row.title, row.agent.label).joinToString(" · "),
                    style = GameText.body(24, colors.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) row.detail?.let {
                    BasicText(it, style = GameText.code(28, toneColor(row.detailTone)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
                StateTag(row.state, label = stateLabel(row.state, row.stopped))
                BasicText(
                    listOfNotNull(row.step, row.age).joinToString(" · "),
                    style = GameText.body(22, colors.muted).copy(textAlign = TextAlign.End),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Le libellé d'un état dans le jeu ; une session arrêtée le dit, au lieu de « Au repos ». */
@Composable
fun stateLabel(state: TagState, stopped: Boolean): String =
    if (stopped) stringResource(R.string.hameau_state_stopped) else tagLabel(state)

/** L'habitant en buste, cadré dans un carré : son attitude suit l'état de la session. */
@Composable
private fun Portrait(kind: InhabitantKind, state: TagState, stopped: Boolean, assets: GameAssets, background: Color, sizePx: Int) {
    val metrics = Game.metrics
    Box(Modifier.size(metrics.dp(sizePx)).background(background).clipToBounds()) {
        SpriteImage(
            inhabitantSprite(kind, state, stopped),
            assets,
            Modifier.wrapContentSize(align = Alignment.TopCenter, unbounded = true).align(Alignment.TopCenter).padding(top = metrics.art(1)),
        )
    }
}

/** L'animation de l'habitant (atlas `personnages`) : au travail, en attente, au repos ou endormi. */
fun inhabitantSprite(kind: InhabitantKind, state: TagState, stopped: Boolean): String {
    val prefix = when (kind) {
        InhabitantKind.SCRIBE -> "scribe"
        InhabitantKind.BRICOLEUR -> "bricoleur"
        InhabitantKind.CARTOGRAPHE -> "cartographe"
    }
    return prefix + when {
        stopped -> ".sommeil"
        state == TagState.WORKING -> ".travail"
        state == TagState.WAITING -> ".attente"
        else -> ".repos"
    }
}

/** Près du micro : la phrase de L2, les gâchettes à tenir, L1 R1, puis « Quitter ». */
@Composable
private fun MicRow(carnet: Carnet, callbacks: CarnetCallbacks, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        BasicText(
            carnet.mic.title,
            style = GameText.body(30, if (carnet.mic.held.isEmpty()) colors.ink else colors.focus, weight = 600),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(30))) {
            MicHints(carnet, callbacks, textSizePx = 26)
            CarnetHint(listOf("L1", "R1"), stringResource(R.string.hameau_change_house), textSizePx = 26)
            Spacer(Modifier.weight(1f))
            QuitLink(callbacks, textSizePx = 26)
        }
    }
}

/** « Quitter », tactile seulement : un simple libellé, sans touche. */
@Composable
private fun QuitLink(callbacks: CarnetCallbacks, textSizePx: Int) {
    BasicText(
        stringResource(R.string.hameau_quit),
        style = GameText.body(textSizePx, Game.colors.muted),
        maxLines = 1,
        modifier = Modifier.testTag("carnet-quit").clickable(onClick = callbacks::onQuit).padding(vertical = Game.metrics.dp(8)),
    )
}

/** L2 et R2, à tenir au doigt comme à la manette. */
@Composable
private fun MicHints(carnet: Carnet, callbacks: CarnetCallbacks, textSizePx: Int) {
    val held = carnet.mic.held
    CarnetHint(
        listOf("L2"), stringResource(R.string.hameau_dictate), textSizePx = textSizePx, active = CarnetTrigger.L2 in held,
        onHold = { callbacks.onTrigger(CarnetTrigger.L2, it) },
    )
    CarnetHint(
        listOf("R2"), stringResource(R.string.hameau_order), textSizePx = textSizePx, active = CarnetTrigger.R2 in held,
        onHold = { callbacks.onTrigger(CarnetTrigger.R2, it) },
    )
}

/** La barre des touches : A va à la maison choisie (ou y entre), B les agents, X bâtir, Y la console, Start. */
@Composable
private fun ActionBar(atDoor: Boolean, callbacks: CarnetCallbacks, compact: Boolean = false) {
    val colors = Game.colors
    val metrics = Game.metrics
    val chip = KeyChipColors(colors.keyBackground, colors.keyInk)
    val actions = listOf(
        "A" to stringResource(if (atDoor) R.string.hameau_enter else R.string.hameau_go) to CarnetKey.A,
        "B" to stringResource(R.string.hameau_agents) to CarnetKey.B,
        "X" to stringResource(R.string.hameau_build) to CarnetKey.X,
        "Y" to stringResource(R.string.hameau_console) to CarnetKey.Y,
        "Start" to stringResource(R.string.hameau_pause_all) to CarnetKey.START,
    ).sortedBy { (hint, _) -> KeyOrder.rank(hint.first) }
    Row(
        Modifier.fillMaxWidth().height(metrics.dp(96)).background(colors.bar).padding(horizontal = metrics.dp(if (compact) 28 else 40)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(if (compact) 40 else 30)),
    ) {
        actions.forEach { (hint, key) ->
            CarnetHint(listOf(hint.first), hint.second, chip = chip, ink = colors.barInk, textSizePx = 26, onTap = { callbacks.onKey(key) })
        }
    }
}

@Composable
private fun EmptyText(text: String, modifier: Modifier) {
    BasicText(
        text,
        style = GameText.body(28, Game.colors.muted).copy(textAlign = TextAlign.Center),
        modifier = modifier.testTag("carnet-empty"),
    )
}

/**
 * Une aide touchable : ses touches, puis son libellé. [onHold] remplace [onTap] pour une gâchette
 * tenue au doigt ; [active] la montre tenue.
 */
@Composable
fun CarnetHint(
    keys: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    chip: KeyChipColors? = null,
    ink: Color = Game.colors.ink,
    textSizePx: Int = 28,
    active: Boolean = false,
    onTap: (() -> Unit)? = null,
    onHold: ((Boolean) -> Unit)? = null,
) {
    val metrics = Game.metrics
    val hold by rememberUpdatedState(onHold)
    val touch = when {
        onHold != null -> Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                hold?.invoke(true)
                try {
                    tryAwaitRelease()
                } finally {
                    hold?.invoke(false)
                }
            })
        }
        onTap != null -> Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
        else -> Modifier
    }
    val colors = if (active) KeyChipColors(Game.colors.focus, Game.colors.workInk) else chip
    Row(
        modifier.then(touch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        keys.forEach { KeyChip(it, colors = colors) }
        BasicText(label, style = GameText.body(textSizePx, if (active) Game.colors.focus else ink), maxLines = 1)
    }
}

/** La confirmation de « Quitter » (A confirme, B annule, au doigt aussi), puis l'arrêt en cours. */
@Composable
fun CarnetQuitPrompt(quit: CarnetQuit, callbacks: CarnetCallbacks) {
    val metrics = Game.metrics
    Box(
        Modifier
            .fillMaxSize()
            .background(Game.colors.scrim)
            // Le voile avale les touchers : rien d'autre ne réagit pendant la confirmation.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        QuitFrame {
            when (quit) {
                is CarnetQuit.Confirm -> Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(28))) {
                    BasicText(quit.question, style = GameText.body(32, weight = 600))
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(44))) {
                        CarnetHint(listOf("A"), quit.confirm, onTap = callbacks::onConfirmQuit)
                        CarnetHint(listOf("B"), quit.cancel, onTap = callbacks::onCancelQuit)
                    }
                }
                is CarnetQuit.Stopping -> BasicText(quit.text, style = GameText.body(32, weight = 600))
            }
        }
    }
}

@Composable
private fun QuitFrame(content: @Composable BoxScope.() -> Unit) {
    val metrics = Game.metrics
    PixelFrame(
        Modifier.widthIn(max = metrics.dp(1000)),
        thick = true,
        contentPadding = PaddingValues(horizontal = metrics.dp(48), vertical = metrics.dp(40)),
        content = content,
    )
}

@Composable
private fun toneColor(tone: CarnetTone): Color = Game.colors.let {
    when (tone) {
        CarnetTone.NORMAL -> it.ink
        CarnetTone.MUTED -> it.muted
        CarnetTone.WAIT -> it.alertLine
        CarnetTone.ERROR -> it.error
    }
}
