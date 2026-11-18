package app.regie.game.ui.house

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Neighbours
import app.regie.domain.model.Session
import app.regie.domain.model.SessionSettingsReview
import app.regie.game.ui.R
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.delay

/**
 * Le carnet du bas d'une maison : les voisines (L1 / R1), le fil brut de la session, l'avis du
 * moment, la table des réglages exposés par l'agent, le message écrit de secours et les touches
 * (B, X, L2, Start). Droite ou un toucher sur un réglage passe la table en modification
 * ([settings] visible) : la croix choisit, A applique, B referme. Une validation en attente
 * ([approval]) prend la place des touches.
 *
 * [compact] : mode un seul écran, le carnet occupe la droite de l'écran du haut ; [threadApproval]
 * termine alors le fil.
 */
@Composable
fun HouseBottomScreen(
    live: LiveSession?,
    gone: Boolean,
    neighbours: Neighbours?,
    settings: SessionSettingsReview,
    settingsHint: String?,
    draft: String,
    notice: HouseNotice?,
    onNoticeSeen: () -> Unit,
    scroll: ScrollInput,
    callbacks: HouseCallbacks,
    modifier: Modifier = Modifier,
    format: AgentTextFormat = AgentTextFormat.Plain,
    approval: (@Composable () -> Unit)? = null,
    /** Une autre validation attend : R1 la montre. */
    approvalQueued: Boolean = false,
    threadApproval: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    /** Mode console (ticket 25) : une autre appli occupe le haut, le carnet nomme la session suivie. */
    console: Boolean = false,
    /**
     * Un seul écran, pendant une dictée de la session : le fil reste, la dictée prend la place de la
     * table, du champ et des touches (ADR 17).
     */
    dictation: (@Composable () -> Unit)? = null,
) {
    val metrics = Game.metrics
    val colors = Game.colors
    // Clavier ouvert : le carnet n'a plus que la moitié de sa hauteur. L'avis passe au-dessus du champ ;
    // la table et les touches s'effacent le temps de la saisie (la manette les garde).
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val editing = settings.visible && settings.session != null
    Column(modifier.fillMaxSize().background(colors.paper).imePadding()) {
        NeighbourBar(neighbours, approval != null, approvalQueued, callbacks)
        if (console && live != null) {
            BasicText(
                sessionName(live.session),
                style = GameText.body(26, colors.ink, weight = 700),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.dp(32), vertical = metrics.dp(10)).testTag("console-session"),
            )
        }
        if (dictation != null) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (live != null) androidx.compose.runtime.key(live.session.id) { HouseThread(live, scroll, format, Modifier.fillMaxSize(), threadApproval) }
            }
            Box(Modifier.fillMaxWidth().height(metrics.dp(DICTATION_PANEL_PX)).testTag("dictation-panel")) { dictation() }
            return@Column
        }
        if (keyboardOpen) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = metrics.dp(32), vertical = metrics.dp(12)), contentAlignment = Alignment.BottomStart) {
                notice?.let { NoticeLine(it, onNoticeSeen) }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (live == null) {
                    CenteredNote(stringResource(if (gone) R.string.game_session_gone else R.string.game_thread_opening))
                } else {
                    // Une liste par session : passer à la voisine repart de la fin de son fil.
                    androidx.compose.runtime.key(live.session.id) { HouseThread(live, scroll, format, Modifier.fillMaxSize(), threadApproval) }
                }
            }
            val hint = settingsHint.takeIf { editing }
            Box(Modifier.padding(horizontal = metrics.dp(32))) {
                if (hint != null) BasicText(hint, style = GameText.body(24, colors.muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
                else notice?.let { NoticeLine(it, onNoticeSeen) }
            }
            SettingsGrid(live?.session, settings, callbacks, columns = if (compact) 3 else 4)
        }
        MessageField(
            draft, callbacks, sendWithA = approval == null && !editing,
            modifier = Modifier.padding(horizontal = metrics.dp(24), vertical = metrics.dp(10)),
        )
        if (!keyboardOpen) {
            when {
                editing -> PadActionBar(settingsActions(settings, callbacks), compact = compact)
                approval != null -> Box(Modifier.padding(start = metrics.dp(24), end = metrics.dp(24), bottom = metrics.dp(20))) { approval() }
                else -> PadActionBar(sessionActions(live, callbacks), compact = compact)
            }
        }
    }
}

/** Les touches d'une session, dans l'ordre B, X, L2, Start ; Start seulement quand un tour peut s'arrêter. */
@Composable
private fun sessionActions(live: LiveSession?, callbacks: HouseCallbacks): List<PadAction> = listOfNotNull(
    PadAction("B", stringResource(R.string.game_action_back)) { callbacks.onKey(PadKey.B) },
    PadAction("X", stringResource(R.string.game_action_see_diff)) { callbacks.onKey(PadKey.X) },
    PadAction(listOf("L2"), stringResource(R.string.game_action_speak), onHold = callbacks::onSpeak),
    PadAction("Start", stringResource(R.string.game_action_interrupt)) { callbacks.onKey(PadKey.START) }.takeIf { live?.canInterrupt == true },
)

/** Les touches de la table en modification : la croix choisit et change, A applique, B referme. */
@Composable
private fun settingsActions(settings: SessionSettingsReview, callbacks: HouseCallbacks): List<PadAction> = listOfNotNull(
    PadAction("↑↓", stringResource(R.string.game_settings_choose)) { callbacks.onKey(PadKey.DOWN) },
    PadAction("←→", stringResource(R.string.game_settings_change)) { callbacks.onKey(PadKey.RIGHT) },
    PadAction("A", stringResource(if (settings.busy) R.string.game_settings_applying else R.string.game_settings_apply), enabled = settings.canApply) {
        callbacks.onKey(PadKey.A)
    },
    PadAction("B", stringResource(R.string.game_settings_close), enabled = !settings.busy) { callbacks.onKey(PadKey.B) },
    PadAction("Start", stringResource(R.string.game_action_interrupt)) { callbacks.onKey(PadKey.START) }.takeIf { settings.canInterrupt },
)

/** « L1 Codex · notes   1 / 4   Claude · montage R1 », sans boucler aux bouts. */
@Composable
private fun NeighbourBar(neighbours: Neighbours?, approvalShown: Boolean, approvalQueued: Boolean, callbacks: HouseCallbacks) {
    val metrics = Game.metrics
    val colors = Game.colors
    val label = GameText.body(26, colors.muted)
    Row(
        Modifier.fillMaxWidth().height(metrics.dp(88)).background(colors.paperDeep).padding(horizontal = metrics.dp(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(16)),
    ) {
        Row(
            Modifier.weight(1f).clickable { callbacks.onKey(PadKey.L1) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
        ) {
            neighbours?.previous?.let {
                KeyChip("L1")
                BasicText(sessionName(it), style = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        neighbours?.let { BasicText("${it.position} / ${it.count}", style = GameText.code(30, colors.muted)) }
        Row(
            Modifier.weight(1f).clickable { callbacks.onKey(PadKey.R1) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(12), Alignment.End),
        ) {
            if (approvalShown) {
                if (approvalQueued) {
                    KeyChip("R1")
                    BasicText(stringResource(R.string.game_next_approval), style = label)
                }
            } else {
                neighbours?.next?.let {
                    BasicText(sessionName(it), style = label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    KeyChip("R1")
                }
            }
        }
    }
}

private fun sessionName(session: Session) = "${session.agent.label} · ${session.project}"

/**
 * La table des réglages, en cases : le mode de validation de Régie, puis chaque option que l'agent
 * expose, avec ses noms. Hors modification, les valeurs confirmées ; en modification, la case choisie
 * est encadrée et une valeur pas encore appliquée passe en ambre.
 */
@Composable
private fun SettingsGrid(session: Session?, settings: SessionSettingsReview, callbacks: HouseCallbacks, columns: Int) {
    val metrics = Game.metrics
    val colors = Game.colors
    val editing = settings.visible && settings.session != null
    val shown = if (editing) settings else SessionSettingsReview(session = session)
    val rows = shown.rows
    Column(
        Modifier.fillMaxWidth().padding(horizontal = metrics.dp(24), vertical = metrics.dp(8)).testTag("house-settings"),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(8)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(8)), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                session?.let { stringResource(R.string.game_settings_title, it.agent.label) }.orEmpty(),
                style = GameText.body(24, colors.muted),
                modifier = Modifier.weight(1f),
            )
            if (!editing && session != null) {
                BasicText(
                    "→ " + stringResource(R.string.game_settings_edit),
                    style = GameText.body(24, colors.ink, weight = 600),
                    modifier = Modifier.clickable { callbacks.onOpenSettings(null) }.padding(metrics.dp(6)),
                )
            }
        }
        rows.indices.chunked(columns).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                line.forEach { index ->
                    val row = rows[index]
                    val selected = editing && index == settings.selected
                    val value = if (selected) settings.value else settings.replacements[row.id] ?: row.current
                    val pending = editing && value != row.current && row.id != "resume"
                    val label = row.choices.firstOrNull { it.value == value }?.name ?: value
                        ?: if (row.id == "resume") (if (selected) stringResource(R.string.game_settings_resume) else "") else stringResource(R.string.game_settings_unknown)
                    PixelFrame(
                        Modifier.weight(1f)
                            .semantics { this.selected = selected }
                            .then(if (settings.busy) Modifier else Modifier.clickable { if (editing) callbacks.onSelectSetting(index) else callbacks.onOpenSettings(row.id) }),
                        thick = selected,
                        colors = if (selected) FrameDefaults.focus() else FrameDefaults.panel(),
                        contentPadding = PaddingValues(horizontal = metrics.dp(14), vertical = metrics.dp(8)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(2))) {
                            BasicText(row.label, style = GameText.body(20, colors.muted, lineHeightPercent = 110), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(
                                    label,
                                    style = GameText.body(28, if (pending) colors.waitInk else colors.ink, weight = 600, lineHeightPercent = 110),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).then(if (pending) Modifier.background(colors.wait) else Modifier),
                                )
                                if (selected && row.choices.size > 1) BasicText(" ‹ ›", style = GameText.code(28, colors.muted))
                            }
                        }
                    }
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NoticeLine(notice: HouseNotice, onSeen: () -> Unit) {
    // Un avis final s'efface seul ; « Envoi… » attend la réponse du pont.
    if (!notice.lasting) {
        LaunchedEffect(notice) {
            delay(NOTICE_MS)
            onSeen()
        }
    }
    val colors = Game.colors
    val color = when (notice.tone) {
        NoticeTone.NORMAL -> colors.ink
        NoticeTone.MUTED -> colors.muted
        NoticeTone.WARN -> colors.alertLine
        NoticeTone.ERROR -> colors.error
    }
    BasicText(notice.text, style = GameText.body(26, color, weight = 600), modifier = Modifier.padding(vertical = Game.metrics.dp(4)))
}

/**
 * Le message écrit, en secours de la dictée : saisi au doigt, il ne part qu'avec A ou « Envoyer ».
 * La touche du clavier ferme le clavier, elle n'envoie pas.
 */
@Composable
private fun MessageField(draft: String, callbacks: HouseCallbacks, sendWithA: Boolean, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val focus = LocalFocusManager.current
    PixelFrame(modifier.fillMaxWidth().height(metrics.dp(76)), contentPadding = PaddingValues(start = metrics.dp(20), end = metrics.dp(12))) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val style = GameText.body(28)
                if (draft.isEmpty()) BasicText(stringResource(R.string.game_message_placeholder), style = style.copy(color = colors.muted))
                NoFullscreenKeyboard {
                    BasicTextField(
                        value = draft,
                        onValueChange = callbacks::onDraftChange,
                        singleLine = true,
                        textStyle = style,
                        cursorBrush = SolidColor(colors.focus),
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
                if (sendWithA) PadActionHint(PadAction("A", stringResource(R.string.game_message_send), onTap = send))
                else BasicText(stringResource(R.string.game_message_send), style = GameText.body(28), modifier = Modifier.clickable(onClick = send).padding(metrics.dp(10)))
            }
        }
    }
}

/**
 * Le clavier s'ouvre sous le champ, jamais en plein écran : l'écran du bas, en paysage et peu haut,
 * ferait sinon passer le clavier en mode « extract ».
 */
// InterceptPlatformTextInput est encore expérimental dans Compose 1.11 (ADR 07).
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoFullscreenKeyboard(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                        request.createInputConnection(outAttributes).also {
                            outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                        }
                },
            )
        },
        content = content,
    )
}

private const val NOTICE_MS = 6_000L

/** La dictée en panneau, sous le fil : transcription, niveau du micro et touches. */
private const val DICTATION_PANEL_PX = 520
