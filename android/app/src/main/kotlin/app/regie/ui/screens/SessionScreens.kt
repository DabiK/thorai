package app.regie.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import app.regie.domain.usecase.ApprovalReview
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.DiffKind
import app.regie.ui.markdown.highlightCode
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import app.regie.R
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.Delivery
import app.regie.domain.model.LineChanges
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ModifiedFile
import app.regie.domain.model.Neighbours
import app.regie.domain.model.PlanStep
import app.regie.domain.model.PlanStepStatus
import app.regie.domain.model.Session
import app.regie.domain.model.SessionSettingsReview
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import app.regie.domain.model.ThreadLine
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.session.SessionControlState
import app.regie.session.SessionNotice
import app.regie.ui.components.AgentTag
import app.regie.ui.components.BarAction
import app.regie.ui.components.StateMessage
import app.regie.ui.components.KeyChip
import app.regie.ui.components.MicButton
import app.regie.ui.components.StatusDot
import app.regie.ui.components.agentName
import app.regie.ui.components.bottomDivider
import app.regie.ui.components.focusOutline
import app.regie.ui.components.startDivider
import app.regie.ui.components.statusColor
import app.regie.ui.components.statusLabel
import app.regie.ui.components.tappable
import app.regie.ui.components.topDivider
import app.regie.ui.formatAge
import app.regie.ui.markdown.Markdown
import app.regie.ui.markdown.MdBlock
import app.regie.ui.theme.Palette
import app.regie.ui.theme.mono
import app.regie.ui.theme.monoFamily
import app.regie.ui.theme.sans
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.time.Instant

/** Ce que l'écran d'une session peut demander, au doigt comme à la manette. */
interface SessionCallbacks {
    fun onButton(button: Button)
    fun onOverview() = onButton(Button.B)
    fun onOptions() = onButton(Button.RIGHT)

    /** Toucher une ligne de la table des réglages : la table passe en modification sur cette ligne. */
    fun onSettingRow(row: String) = onOptions()
    fun onTrigger(trigger: Trigger, held: Boolean)
    fun onDraftChange(text: String)

    /** Envoi tactile explicite, même quand un autre panneau utilise A. */
    fun onSend()

    /** L'avis est resté affiché assez longtemps. */
    fun onNoticeSeen(notice: SessionNotice)
}

/** Défilement du fil demandé à la manette : pas de la croix, vitesse du stick droit. */
class ThreadScroll(val steps: Flow<Int>, val velocity: StateFlow<Float>)

/**
 * Écran du haut d'une session : le fil brut de l'agent, et son plan et ses fichiers modifiés quand il
 * en expose. Rien n'y est ajouté par Régie : ni point, ni bilan (ticket 22). Il ne change pas pendant
 * la dictée. En mode un seul écran, la zone du bas devient un panneau en bas : [options] ou
 * [dictation] y prennent la place des actions.
 */
@Composable
fun SessionTopScreen(
    live: LiveSession?,
    /** La session n'est plus dans la vue d'ensemble. */
    gone: Boolean,
    control: SessionControlState,
    draft: String,
    singleScreen: Boolean,
    scroll: ThreadScroll,
    callbacks: SessionCallbacks,
    approval: ApprovalReview = ApprovalReview(),
    onDecision: (ApprovalId, ApprovalDecision) -> Unit = { _, _ -> },
    onNextApproval: () -> Unit = {},
    onDismissApproval: () -> Unit = {},
    options: (@Composable () -> Unit)? = null,
    dictation: (@Composable () -> Unit)? = null,
    /** Une dictée capte A, B et X : la carte de validation du fil garde ses touchers, sans puces. */
    dictating: Boolean = false,
) {
    val now by rememberNow()
    Column(Modifier.fillMaxSize().background(Palette.background).imePadding()) {
        SessionHeader(live?.session, live, now, callbacks)
        // Sur deux écrans, les réglages sont dans la table du bas ; seul, l'écran les résume ici.
        if (singleScreen) live?.session?.let { session ->
            val labels = listOf(app.regie.domain.model.approvalModeLabel(session.approvalMode)) +
                session.configOptions.filter { it.category in listOf("model", "thought_level") }.map { it.valueLabel } + "→ Régler"
            BasicText(labels.joinToString(" · "), style = sans(12.sp, color = Palette.textMuted), maxLines = 2,
                modifier = Modifier.fillMaxWidth().tappable { callbacks.onOptions() }.bottomDivider().padding(horizontal = 20.dp, vertical = 7.dp))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    live == null -> StateMessage(stringResource(if (gone) R.string.session_gone else R.string.thread_opening))
                    // Une liste par session : passer à la voisine repart de la fin de son fil.
                    else -> key(live.session.id) { ThreadList(live, scroll, approval, onDecision, onNextApproval, onDismissApproval, shortcutsEnabled = options == null && !dictating) }
                }
            }
            // Le plan et les fichiers seulement quand l'agent en expose : pas de cadre vide.
            if (live != null && (live.plan != null || live.modifiedFiles.isNotEmpty())) SidePanel(live)
        }
        if (singleScreen) {
            control.notice?.let {
                NoticeText(it, callbacks, Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            if (dictation != null) Box(Modifier.height(260.dp).fillMaxWidth().topDivider()) { dictation() }
            else if (options != null) Box(Modifier.height(260.dp).fillMaxWidth()) { options() }
            else if (approval.request != null) InlineApproval(approval, onDecision, onNextApproval, details = false, onDismiss = onDismissApproval)
            else CompactSessionPanel(live, control, draft, callbacks)
        }
    }
}

/**
 * Écran du bas d'une session : les voisines, la table des réglages exposés par l'agent, le message de
 * secours et les touches utiles (B, X, L2, Start). Droite ou un toucher sur une ligne fait passer la
 * table en modification ([settings] visible) : la croix choisit, A applique, B referme.
 * Une validation en attente prend la place des touches.
 */
@Composable
fun SessionBottomScreen(
    live: LiveSession?,
    neighbours: Neighbours?,
    control: SessionControlState,
    draft: String,
    callbacks: SessionCallbacks,
    approval: ApprovalReview = ApprovalReview(),
    onDecision: (ApprovalId, ApprovalDecision) -> Unit = { _, _ -> },
    onNextApproval: () -> Unit = {},
    onDismissApproval: () -> Unit = {},
    settings: SessionSettingsReview = SessionSettingsReview(),
    onSelectSetting: (Int) -> Unit = {},
    /**
     * Mode console (ticket 25) : une autre appli occupe l'écran du haut. Le fil compact de la session
     * prend la place de la table, qui reste à un geste (Droite, ou toucher « Modifier »).
     */
    console: ThreadScroll? = null,
) {
    // Clavier ouvert, l'écran du bas n'a plus que la moitié de sa hauteur : l'avis passe juste au-dessus
    // du champ, la table et les touches s'effacent le temps de la saisie (la manette les garde).
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val editing = settings.visible
    Column(Modifier.fillMaxSize().background(Palette.background).imePadding()) {
        NeighbourBar(neighbours, callbacks, approval)
        if (keyboardOpen) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.BottomStart) {
                control.notice?.let { NoticeText(it, callbacks, Modifier) }
            }
        } else if (console != null && !editing) {
            ConsoleThread(live, console, callbacks, Modifier.weight(1f).fillMaxWidth())
            control.notice?.let { NoticeText(it, callbacks, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) }
        } else {
            SettingsTable(live?.session, settings, callbacks, onSelectSetting, Modifier.weight(1f).fillMaxWidth())
            val hint = if (editing) settingsHint(settings) else null
            if (hint != null) BasicText(hint, style = sans(12.sp, color = Palette.textMuted, lineHeight = 16.sp), maxLines = 2,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
            else control.notice?.let { NoticeText(it, callbacks, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) }
        }
        MessageField(draft, callbacks, compact = false, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = if (keyboardOpen) 10.dp else 8.dp),
            sendWithA = approval.request == null && !editing)
        if (!keyboardOpen) {
            when {
                editing -> SettingsKeys(settings, callbacks)
                approval.request != null -> Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    InlineApproval(approval, onDecision, onNextApproval, details = false, onDismiss = onDismissApproval)
                }
                else -> SessionKeys(live, callbacks)
            }
        }
    }
}

/**
 * Les réglages de la session, présentés de façon générique : le mode de validation, puis chaque
 * option que l'agent expose (modèle, réflexion, mode rapide…), avec ses noms. Hors modification,
 * la table montre les valeurs confirmées ; en modification, la ligne choisie porte le focus et une
 * valeur pas encore appliquée est en ambre.
 */
@Composable
private fun SettingsTable(session: Session?, settings: SessionSettingsReview, callbacks: SessionCallbacks, onSelect: (Int) -> Unit, modifier: Modifier) {
    val editing = settings.visible && settings.session != null
    val shown = if (editing) settings else SessionSettingsReview(session = session)
    val rows = shown.rows
    val list = rememberLazyListState()
    LaunchedEffect(editing, settings.selected) {
        if (!editing || settings.selected !in rows.indices) return@LaunchedEffect
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == settings.selected && it.offset >= 0 && it.offset + it.size <= list.layoutInfo.viewportEndOffset }) {
            list.animateScrollToItem(settings.selected)
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                session?.let { stringResource(R.string.settings_title, agentName(it.agent)) }.orEmpty(),
                style = sans(12.sp, color = Palette.textSubtle),
                modifier = Modifier.weight(1f),
            )
            if (!editing && session != null) BarAction("→", stringResource(R.string.settings_edit)) { callbacks.onOptions() }
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("session-settings"),
            contentPadding = PaddingValues(horizontal = 14.dp)) {
            items(count = rows.size, key = { rows[it].id }) { index ->
                val row = rows[index]
                val selected = editing && index == settings.selected
                val value = if (selected) settings.value else settings.replacements[row.id] ?: row.current
                val pending = editing && value != row.current && row.id != "resume"
                val label = row.choices.firstOrNull { it.value == value }?.name ?: value
                    ?: if (row.id == "resume") (if (selected) "A pour reprendre" else "") else "Non communiqué"
                Row(
                    Modifier.fillMaxWidth().height(40.dp)
                        .focusOutline(selected, 8.dp)
                        .background(if (selected) Palette.surface else Color.Transparent, RoundedCornerShape(8.dp))
                        .then(if (selected) Modifier else Modifier.bottomDivider())
                        .then(if (settings.busy) Modifier else Modifier.tappable { if (editing) onSelect(index) else callbacks.onSettingRow(row.id) })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText(row.label, style = sans(13.sp, color = Palette.textMuted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(if (row.id == "resume") 200.dp else 128.dp))
                    BasicText(label, style = sans(14.sp, weight = 600, color = if (pending) Palette.waiting else Palette.text),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (selected && row.choices.size > 1) BasicText("‹ ›", style = mono(13.sp, color = Palette.textSubtle))
                }
            }
        }
    }
}

/**
 * Le fil de la session en version compacte, sur l'écran du bas, quand une autre appli occupe le haut :
 * les derniers messages et actions défilent seuls ; la validation en attente reste dans les touches.
 */
@Composable
private fun ConsoleThread(live: LiveSession?, scroll: ThreadScroll, callbacks: SessionCallbacks, modifier: Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                live?.session?.let { sessionName(it) }.orEmpty(),
                style = sans(12.sp, color = Palette.textSubtle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (live != null) BarAction("→", stringResource(R.string.action_session_settings)) { callbacks.onOptions() }
        }
        Box(Modifier.weight(1f).fillMaxWidth().testTag("console-thread")) {
            if (live == null) StateMessage(stringResource(R.string.thread_opening))
            else key(live.session.id) { ThreadList(live, scroll, ApprovalReview(), { _, _ -> }, {}, {}, shortcutsEnabled = false) }
        }
    }
}

/** Touches de la table en modification : A applique, B referme, la croix choisit et change. */
@Composable
private fun SettingsKeys(settings: SessionSettingsReview, callbacks: SessionCallbacks) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).topDivider().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        app.regie.ui.components.ButtonChip("A", if (settings.busy) "Application…" else "Appliquer",
            Modifier.alpha(if (settings.canApply) 1f else 0.4f).then(if (settings.canApply) Modifier.tappable { callbacks.onButton(Button.A) } else Modifier))
        app.regie.ui.components.ButtonChip("B", "Fermer",
            Modifier.alpha(if (settings.busy) 0.4f else 1f).then(if (settings.busy) Modifier else Modifier.tappable { callbacks.onButton(Button.B) }))
        BarAction("↑↓", "Choisir") { callbacks.onButton(Button.DOWN) }
        BarAction("←→", "Changer") { callbacks.onButton(Button.RIGHT) }
        if (settings.canInterrupt) BarAction("Start", stringResource(R.string.action_interrupt)) { callbacks.onButton(Button.START) }
    }
}

/** Touches d'une session, dans l'ordre B, X, L2, Start ; Start seulement quand il y a un tour à arrêter. */
@Composable
private fun SessionKeys(live: LiveSession?, callbacks: SessionCallbacks) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).topDivider().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BarAction("B", stringResource(R.string.action_back_to_overview)) { callbacks.onButton(Button.B) }
        BarAction("X", stringResource(R.string.action_see_diff)) { callbacks.onButton(Button.X) }
        HoldAction("L2", stringResource(R.string.action_speak), onPress = { callbacks.onTrigger(Trigger.L2, held = true) },
            onRelease = { callbacks.onTrigger(Trigger.L2, held = false) })
        if (live?.canInterrupt == true) BarAction("Start", stringResource(R.string.action_interrupt)) { callbacks.onButton(Button.START) }
    }
}

/** Une touche maintenue au doigt comme à la manette : L2 tant que le doigt reste posé. */
@Composable
private fun HoldAction(key: String, label: String, onPress: () -> Unit, onRelease: () -> Unit) {
    val press by androidx.compose.runtime.rememberUpdatedState(onPress)
    val release by androidx.compose.runtime.rememberUpdatedState(onRelease)
    app.regie.ui.components.ButtonChip(key, label, Modifier.pointerInput(Unit) {
        detectTapGestures(onPress = {
            press()
            try { tryAwaitRelease() } finally { release() }
        })
    })
}

// --- En-têtes ---

@Composable
private fun SessionHeader(session: Session?, live: LiveSession?, now: Instant, callbacks: SessionCallbacks) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).bottomDivider().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            stringResource(R.string.session_breadcrumb),
            style = sans(13.sp, color = Palette.textMuted),
            modifier = Modifier.tappable { callbacks.onOverview() },
        )
        if (session == null) return@Row
        AgentTag(session.agent)
        BasicText(session.project, style = sans(13.sp, weight = 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f)) {
            session.branch?.let { BasicText(it, style = mono(11.sp, color = Palette.textSubtle), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusDot(session.status)
            BasicText(
                stringResource(R.string.session_status_since, statusLabel(session.status), formatAge(now - session.updatedAt)),
                style = sans(12.sp, color = statusColor(session.status)),
            )
        }
        live?.context?.let { BasicText(stringResource(R.string.session_context, it.percent), style = mono(11.sp, color = Palette.textSubtle)) }
    }
}

/** « L1 Codex · notes   1 / 4   Claude · montage R1 », sans boucler aux bouts. */
@Composable
private fun NeighbourBar(neighbours: Neighbours?, callbacks: SessionCallbacks, approval: ApprovalReview) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).bottomDivider().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = sans(12.sp, color = Palette.textMuted)
        Row(
            modifier = Modifier.weight(1f).tappable { callbacks.onButton(Button.L1) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            neighbours?.previous?.let {
                KeyChip("L1")
                BasicText(sessionName(it), style = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        neighbours?.let { BasicText("${it.position} / ${it.count}", style = mono(11.sp, color = Palette.textSubtle)) }
        Row(
            modifier = Modifier.weight(1f).tappable { callbacks.onButton(Button.R1) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (approval.request != null) {
                if (approval.queue.any { it.id != approval.request?.id }) { KeyChip("R1"); BasicText("Voir la suivante", style = label) }
            } else neighbours?.next?.let {
                BasicText(sessionName(it), style = label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                KeyChip("R1")
            }
        }
    }
}

@Composable
private fun sessionName(session: Session) = "${agentName(session.agent)} · ${session.project}"

// --- Le fil ---

/**
 * The latest content stays visible until the reader scrolls up. Normal layout anchors the
 * top of the visible item while a paused streaming response grows below it.
 */
@Composable
private fun ThreadList(live: LiveSession, scroll: ThreadScroll, approval: ApprovalReview,
    onDecision: (ApprovalId, ApprovalDecision) -> Unit, onNextApproval: () -> Unit, onDismissApproval: () -> Unit, shortcutsEnabled: Boolean) {
    val lines = live.lines.filterNot { it is ThreadLine.AwaitingApproval && approval.request != null }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val step = with(LocalDensity.current) { STEP.toPx() }
    val speed = with(LocalDensity.current) { STICK_SPEED.toPx() }
    val lastIndex = lines.size - 1 + if (approval.request != null) 1 else 0
    suspend fun bottom() {
        if (lastIndex < 0) return
        listState.scrollToItem(lastIndex)
        listState.scrollBy(listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }?.size?.toFloat() ?: 0f)
    }
    LaunchedEffect(listState, lastIndex) {
        scroll.steps.collect { direction ->
            if (direction == 0) { follow = true; bottom() }
            else { if (direction < 0) follow = false; listState.animateScrollBy(direction * step) }
        }
    }
    LaunchedEffect(listState) {
        scroll.velocity.collectLatest { velocity ->
            if (velocity == 0f) return@collectLatest
            if (velocity < 0f) follow = false
            var last = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                listState.scrollBy(velocity * speed * (frame - last) / 1e9f)
                last = frame
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect { follow = !listState.canScrollForward }
    }
    // Normal order preserves the reader's top anchor even when the same streaming item grows.
    LaunchedEffect(lines, approval, viewportHeight) { if (follow) bottom() }
    Box(Modifier.fillMaxSize()) {
        when {
            live.loading && approval.request == null -> StateMessage(stringResource(R.string.thread_loading))
            lines.isEmpty() && approval.request == null -> StateMessage(stringResource(R.string.thread_empty))
            else -> LazyColumn(state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                modifier = Modifier.fillMaxSize().testTag("session-thread").onSizeChanged { viewportHeight = it.height }) {
                items(count = lines.size, key = { lines[it].key }) { position ->
                    val line = lines[position]
                    Column(Modifier.padding(top = gapBefore(lines.getOrNull(position - 1), line).dp)) {
                        // Discret : l'heure n'apparaît que lorsqu'elle change d'une ligne à l'autre.
                        val time = minuteOf(live.timestamps[line.key])
                        if (time != null && time != minuteOf(lines.getOrNull(position - 1)?.let { live.timestamps[it.key] })) {
                            BasicText(time, style = mono(9.sp, color = Palette.textSubtle), modifier = Modifier.padding(bottom = 3.dp))
                        }
                        val action = live.actionDetails[line.key]
                        if (action != null) ExpandableAction(line, action) else ThreadLineView(line)
                    }
                }
                if (approval.request != null) item(key = "inline-approval") {
                    Box(Modifier.padding(top = 12.dp)) { InlineApproval(approval, onDecision, onNextApproval, shortcutsEnabled = shortcutsEnabled, onDismiss = onDismissApproval) }
                }
            }
        }
        if (!follow) BasicText("↓ Revenir en bas", style = sans(12.sp, 600), modifier = Modifier
            .align(Alignment.BottomEnd).padding(12.dp).background(Palette.chipBackground, RoundedCornerShape(16.dp))
            .tappable { scope.launch { follow = true; bottom() } }.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun ExpandableAction(line: ThreadLine, action: ThreadEntry.ToolCall) {
    var expanded by remember(action.persistentId) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().tappable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (expanded) "▾" else "▸", style = mono(12.sp, color = Palette.textMuted))
            Box(Modifier.weight(1f)) { ThreadLineView(line) }
        }
        if (expanded) Column(Modifier.fillMaxWidth().background(Palette.codeBackground, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            action.output?.takeIf { it.isNotEmpty() }?.let { BasicText(it, style = mono(12.sp, lineHeight = 17.sp)) }
            action.diffs.forEach { diff ->
                BasicText(diff.path, style = mono(11.sp, color = Palette.textMuted))
                diff.lines.forEach { part -> BasicText(when (part.kind) { DiffKind.ADDED -> "+"; DiffKind.REMOVED -> "−"; DiffKind.CONTEXT -> " " } + part.text,
                    style = mono(12.sp, color = when (part.kind) { DiffKind.ADDED -> Palette.done; DiffKind.REMOVED -> Palette.failed; DiffKind.CONTEXT -> Palette.textMuted })) }
            }
            if (action.output.isNullOrEmpty() && action.diffs.isEmpty()) BasicText("Détail non communiqué.", style = sans(12.sp, color = Palette.textMuted))
        }
    }
}

@Composable
private fun ThreadLineView(line: ThreadLine) {
    when (line) {
        is ThreadLine.UserMessage -> UserMessage(line)
        is ThreadLine.AgentText -> AgentText(line)
        is ThreadLine.Read -> ActionRow(
            verb = stringResource(if (line.inProgress) R.string.thread_reading else R.string.thread_read),
            subject = if (line.files.size > 1) pluralStringResource(R.plurals.thread_files, line.files.size, line.files.size) else line.files.single(),
            detail = line.files.takeIf { it.size > 1 }?.joinToString(", "),
        )
        is ThreadLine.Edit -> ActionRow(
            verb = stringResource(if (line.status.isUnfinished) R.string.thread_editing else R.string.thread_edited),
            subject = line.path,
            trailing = { Changes(line.changes, actionStyle(Palette.text)); StatusWord(line.status.takeIf { it == ActionStatus.FAILED || it == ActionStatus.INTERRUPTED }) },
        )
        is ThreadLine.Run -> ActionRow(
            verb = stringResource(if (line.status.isUnfinished) R.string.thread_running else R.string.thread_ran),
            subject = line.command,
            active = line.status == ActionStatus.IN_PROGRESS,
            detail = line.result,
            detailAtEnd = true,
            trailing = { StatusWord(line.status) },
        )
        is ThreadLine.Other -> ActionRow(
            verb = stringResource(R.string.thread_action),
            subject = line.title,
            active = line.status == ActionStatus.IN_PROGRESS,
            trailing = { StatusWord(line.status) },
        )
        is ThreadLine.TurnFailed -> BasicText(
            line.message?.let { stringResource(R.string.thread_turn_failed_with, it) } ?: stringResource(R.string.thread_turn_failed),
            style = sans(13.sp, color = Palette.failed, lineHeight = 19.sp),
        )
        is ThreadLine.Interrupted -> BasicText(
            stringResource(R.string.thread_interrupted),
            style = sans(12.sp, color = Palette.textMuted),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        is ThreadLine.AwaitingApproval -> Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(Palette.waiting, CircleShape))
            BasicText(
                line.subject?.let { stringResource(R.string.thread_awaiting, it) } ?: stringResource(R.string.thread_awaiting_no_subject),
                style = sans(13.sp, color = Palette.waiting),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val ActionStatus?.isUnfinished get() = this == ActionStatus.PENDING || this == ActionStatus.IN_PROGRESS

/** Le message envoyé, tel quel, dans une bulle à droite, sans étiquette ajoutée par Régie. */
@Composable
private fun UserMessage(line: ThreadLine.UserMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        BasicText(
            line.text,
            style = sans(13.5.sp, lineHeight = 19.6.sp),
            modifier = Modifier
                .widthIn(max = 470.dp)
                .background(Palette.bubble, RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/**
 * Le texte de l'agent, son markdown rendu lisible (ADR 07). Tant qu'il s'écrit, un curseur bleu
 * termine son dernier bloc.
 */
@Composable
internal fun AgentText(line: ThreadLine.AgentText) {
    val blocks = remember(line.text) { Markdown.parse(line.text) }
    val style = sans(13.5.sp, color = Palette.agentText, lineHeight = 20.25.sp)
    val codeStyle = mono(12.sp, color = Palette.agentText, lineHeight = 17.sp)
    val mono = monoFamily()
    val cursor = remember {
        mapOf(
            CURSOR to InlineTextContent(Placeholder(5.sp, 15.sp, PlaceholderVerticalAlign.TextCenter)) {
                Box(Modifier.padding(start = 3.dp).width(2.dp).height(15.dp).background(Palette.running))
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (blocks.isEmpty() && line.streaming) BasicText(withCursor(AnnotatedString(""), true), style = style, inlineContent = cursor)
        blocks.forEachIndexed { index, block ->
            val streaming = line.streaming && index == blocks.lastIndex
            when (block) {
                is MdBlock.CodeBlock -> BasicText(
                    withCursor(remember(block.code) { highlightCode(block.code) }, streaming),
                    style = codeStyle,
                    inlineContent = cursor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Palette.codeBackground, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
                is MdBlock.Text -> Row(Modifier.padding(start = (MD_INDENT * (block.depth - 1).coerceAtLeast(0)))) {
                    val textStyle = if (block.quote) style.copy(color = Palette.textMuted) else style
                    block.marker?.let { BasicText(it, style = textStyle, modifier = Modifier.width(MD_INDENT)) }
                    BasicText(withCursor(spansText(block, mono), streaming), style = textStyle, inlineContent = cursor)
                }
            }
        }
    }
}

private fun spansText(block: MdBlock.Text, mono: FontFamily): AnnotatedString = buildAnnotatedString {
    for (span in block.spans) {
        val style = SpanStyle(
            fontWeight = if (span.bold || block.heading) FontWeight(650) else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) mono else null,
            fontSize = if (span.code) 12.5.sp else TextUnit.Unspecified,
            background = if (span.code) Palette.codeBackground else Color.Unspecified,
        )
        withStyle(style) { append(span.text) }
    }
}

private fun withCursor(text: AnnotatedString, streaming: Boolean): AnnotatedString =
    if (!streaming) {
        text
    } else {
        buildAnnotatedString {
            append(text)
            appendInlineContent(CURSOR, "▍")
        }
    }

/** Une action sur une ligne : verbe, sujet, puis détail ou résultat et statut. */
@Composable
private fun ActionRow(
    verb: String,
    subject: String,
    active: Boolean = false,
    detail: String? = null,
    detailAtEnd: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) Palette.activeRow else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(verb, style = actionStyle(if (active) Palette.running else Palette.textSubtle), modifier = Modifier.width(58.dp))
        BasicText(
            subject,
            style = actionStyle(if (active) Palette.text else Palette.footerText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = !detailAtEnd && detail == null),
        )
        detail?.let {
            BasicText(
                it,
                style = actionStyle(Palette.textSubtle).copy(textAlign = if (detailAtEnd) TextAlign.End else TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (detail == null && detailAtEnd) Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** « +12 −3 » ; rien sans diff. */
@Composable
private fun Changes(changes: LineChanges?, style: TextStyle) {
    if (changes == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (changes.added > 0) BasicText("+${changes.added}", style = style.copy(color = Palette.added))
        if (changes.removed > 0) BasicText("−${changes.removed}", style = style.copy(color = Palette.removed))
    }
}

@Composable
private fun StatusWord(status: ActionStatus?) {
    status ?: return
    val (label, color) = when (status) {
        ActionStatus.PENDING -> stringResource(R.string.run_pending) to Palette.textSubtle
        ActionStatus.IN_PROGRESS -> stringResource(R.string.run_in_progress) to Palette.running
        ActionStatus.COMPLETED -> stringResource(R.string.run_succeeded) to Palette.textSubtle
        ActionStatus.FAILED -> stringResource(R.string.run_failed) to Palette.failed
        ActionStatus.INTERRUPTED -> stringResource(R.string.run_interrupted) to Palette.textMuted
    }
    BasicText(label, style = actionStyle(color), maxLines = 1)
}

// --- Plan et fichiers ---

@Composable
private fun SidePanel(live: LiveSession) {
    Column(
        modifier = Modifier
            .width(236.dp)
            .fillMaxHeight()
            .startDivider()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val heading = sans(12.sp, color = Palette.textSubtle)
        // Seulement ce que l'agent expose : une section absente n'est pas remplacée par un texte de Régie.
        live.plan?.let { plan ->
            BasicText(stringResource(R.string.plan_title), style = heading)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { plan.forEach { PlanRow(it) } }
        }
        if (live.plan != null && live.modifiedFiles.isNotEmpty()) Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(Palette.divider))
        if (live.modifiedFiles.isNotEmpty()) {
            BasicText(stringResource(R.string.files_title), style = heading)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { live.modifiedFiles.forEach { FileRow(it) } }
        }
    }
}

@Composable
private fun PlanRow(step: PlanStep) {
    val (color, weight) = when (step.status) {
        PlanStepStatus.DONE -> Palette.textMuted to 400
        PlanStepStatus.IN_PROGRESS -> Palette.text to 600
        PlanStepStatus.PENDING -> Palette.textSubtle to 400
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        StepIcon(step.status, Modifier.padding(top = 1.dp).size(14.dp))
        BasicText(step.text, style = sans(12.5.sp, weight = weight, color = color, lineHeight = 16.9.sp))
    }
}

/** Fait : coche verte ; en cours : anneau bleu ouvert ; à faire : cercle gris. */
@Composable
private fun StepIcon(status: PlanStepStatus, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        when (status) {
            PlanStepStatus.DONE -> {
                val check = Path().apply {
                    moveTo(5 * unit, 12 * unit)
                    lineTo(10 * unit, 17 * unit)
                    lineTo(19 * unit, 7 * unit)
                }
                drawPath(check, Palette.done, style = Stroke(2.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            PlanStepStatus.IN_PROGRESS -> {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = Palette.running,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
            }
            PlanStepStatus.PENDING -> {
                val stroke = 1.5.dp.toPx()
                drawCircle(Palette.keyBorder, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
            }
        }
    }
}

@Composable
private fun FileRow(file: ModifiedFile) {
    val style = mono(11.5.sp, color = Palette.footerText)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(file.path.substringAfterLast('/'), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Changes(file.changes, style)
    }
}

// --- Le bas : avis, message de secours, actions ---

@Composable
private fun NoticeText(notice: SessionNotice, callbacks: SessionCallbacks, modifier: Modifier) {
    // Un avis final s'efface seul ; « Envoi… » attend la réponse du pont.
    if (notice != SessionNotice.Sending && notice != SessionNotice.Interrupting) {
        LaunchedEffect(notice) {
            delay(NOTICE_MS)
            callbacks.onNoticeSeen(notice)
        }
    }
    val (text, color) = noticeText(notice)
    BasicText(text, style = sans(13.sp, color = color, lineHeight = 18.85.sp), modifier = modifier)
}

@Composable
private fun noticeText(notice: SessionNotice): Pair<String, Color> = when (notice) {
    SessionNotice.Sending -> stringResource(R.string.notice_sending) to Palette.textMuted
    SessionNotice.Interrupting -> stringResource(R.string.notice_interrupting) to Palette.textMuted
    SessionNotice.NothingHeard -> stringResource(R.string.notice_nothing_heard) to Palette.waiting
    is SessionNotice.Interrupted ->
        if (notice.heard) stringResource(R.string.notice_interrupted) to Palette.text else stringResource(R.string.notice_interrupt_failed) to Palette.failed
    is SessionNotice.Delivered -> when (notice.delivery) {
        Delivery.PROMPT -> stringResource(R.string.delivery_prompt) to Palette.text
        Delivery.STEERING -> stringResource(R.string.delivery_steering) to Palette.text
        Delivery.QUEUED -> stringResource(R.string.delivery_queued) to Palette.waiting
        Delivery.CANCELLED -> stringResource(R.string.delivery_cancelled) to Palette.waiting
        Delivery.UNKNOWN -> stringResource(R.string.delivery_unknown) to Palette.waiting
        Delivery.NOT_SENT -> stringResource(R.string.delivery_not_sent) to Palette.failed
    }
}

/**
 * Le message écrit, en secours de la dictée : saisi au doigt, il ne part qu'avec A ou « Envoyer ».
 * La touche du clavier ferme le clavier, elle n'envoie pas.
 */
@Composable
private fun MessageField(draft: String, callbacks: SessionCallbacks, compact: Boolean, modifier: Modifier, sendWithA: Boolean = true) {
    val focus = LocalFocusManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 40.dp else 44.dp)
            .background(Palette.surface, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val style = sans(13.5.sp)
            if (draft.isEmpty()) BasicText(stringResource(R.string.message_placeholder), style = style.copy(color = Palette.textSubtle))
            NoFullscreenKeyboard {
                BasicTextField(
                    value = draft,
                    onValueChange = callbacks::onDraftChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(Palette.running),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (draft.isNotBlank()) {
            val send = {
                callbacks.onSend()
                focus.clearFocus()
            }
            if (sendWithA) BarAction("A", stringResource(R.string.message_send), send)
            else BasicText(stringResource(R.string.message_send), style = sans(13.sp),
                modifier = Modifier.tappable(send).padding(8.dp))
        }
    }
}

/**
 * Le clavier s'ouvre sous le champ de Régie, jamais en plein écran : l'écran du bas, en paysage et
 * peu haut, ferait sinon passer Gboard en mode « extract » (un grand champ et un bouton OK à la place
 * du nôtre). Les drapeaux sont ajoutés à l'`EditorInfo` que prépare le champ Compose.
 */
// InterceptPlatformTextInput est encore expérimental dans Compose 1.11 (BOM 2026.06.01) : à revoir en
// montant le BOM (ADR 07).
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoFullscreenKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                        request.createInputConnection(outAttributes).also { outAttributes.imeOptions = withoutFullscreen(outAttributes.imeOptions) }
                },
            )
        },
        content = content,
    )
}

/** Les options du clavier, sans plein écran ni mode « extract ». */
internal fun withoutFullscreen(imeOptions: Int): Int =
    imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

/** Panneau du mode un seul écran : micro, message de secours (ou gâchette tenue) et actions. */
@Composable
private fun CompactSessionPanel(live: LiveSession?, control: SessionControlState, draft: String, callbacks: SessionCallbacks) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).topDivider().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MicButton(
            active = control.heldTriggers.isNotEmpty(),
            onPress = { callbacks.onTrigger(Trigger.L2, held = true) },
            onRelease = { callbacks.onTrigger(Trigger.L2, held = false) },
            size = 40.dp,
            halo = 4.dp,
        )
        Box(Modifier.weight(1f)) {
            if (control.heldTriggers.isNotEmpty()) {
                BasicText(micTitle(control.heldTriggers), style = sans(14.sp, weight = 600, color = Palette.mic), maxLines = 1)
            } else {
                MessageField(draft, callbacks, compact = true, modifier = Modifier)
            }
        }
        BarAction("B", stringResource(R.string.action_back_short)) { callbacks.onButton(Button.B) }
        BarAction("X", stringResource(R.string.action_see_diff)) { callbacks.onButton(Button.X) }
        if (live?.canInterrupt == true) {
            Row(
                modifier = Modifier.tappable { callbacks.onButton(Button.START) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                KeyChip("Start")
                BasicText(stringResource(R.string.action_interrupt), style = sans(12.5.sp, color = Palette.footerText), maxLines = 1)
            }
        }
    }
}

@Composable
private fun micTitle(held: Set<Trigger>): String = stringResource(
    when {
        Trigger.L2 in held -> R.string.mic_l2_held
        Trigger.R2 in held -> R.string.mic_r2_held
        else -> R.string.mic_hold_l2
    },
)

/** Les lignes d'action : Geist Mono 12 sp, sur une hauteur fixe pour que le bloc reste serré. */
@Composable
private fun actionStyle(color: Color) = mono(12.sp, color = color, lineHeight = 16.sp)

/** « 15:18 » dans le fuseau de l'appareil ; rien pour un élément sans date. */
private fun minuteOf(time: Instant?): String? = time?.let {
    java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()))
}

private val MD_INDENT: Dp = 16.dp
private const val CURSOR = "cursor"
private const val NOTICE_MS = 6_000L
private val STEP = 120.dp
private val STICK_SPEED = 900.dp
