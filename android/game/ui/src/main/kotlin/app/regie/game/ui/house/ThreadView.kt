package app.regie.game.ui.house

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import app.regie.domain.model.ActionStatus
import app.regie.domain.model.DiffKind
import app.regie.domain.model.LineChanges
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ThreadEntry
import app.regie.domain.model.ThreadLine
import app.regie.game.ui.R
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.FrameColors
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Instant

/**
 * Le fil brut de la session, dans le carnet du bas : messages, réponses de l'agent, actions sur une
 * ligne (un toucher déplie leur détail), texte en cours. Rien n'y est ajouté par Régie (ADR 17).
 * La fin reste visible tant qu'on ne remonte pas ; la croix et le stick droit font défiler.
 * [approval] s'ajoute en fin de fil (mode un seul écran).
 */
@Composable
fun HouseThread(
    live: LiveSession,
    scroll: ScrollInput,
    format: AgentTextFormat,
    modifier: Modifier = Modifier,
    approval: (@Composable () -> Unit)? = null,
) {
    val metrics = Game.metrics
    val lines = live.lines.filterNot { it is ThreadLine.AwaitingApproval && approval != null }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val step = STEP_PX.toFloat()
    val lastIndex = lines.size - 1 + if (approval != null) 1 else 0
    suspend fun bottom() {
        if (lastIndex < 0) return
        listState.scrollToItem(lastIndex)
        listState.scrollBy(listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }?.size?.toFloat() ?: 0f)
    }
    LaunchedEffect(listState, lastIndex) {
        scroll.steps.collect { direction ->
            if (direction == 0) {
                follow = true
                bottom()
            } else {
                if (direction < 0) follow = false
                listState.animateScrollBy(direction * step)
            }
        }
    }
    LaunchedEffect(listState) {
        scroll.velocity.collectLatest { velocity ->
            if (velocity == 0f) return@collectLatest
            if (velocity < 0f) follow = false
            var last = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                listState.scrollBy(velocity * STICK_PX * (frame - last) / 1e9f)
                last = frame
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect { follow = !listState.canScrollForward }
    }
    LaunchedEffect(lines, approval != null, viewportHeight) { if (follow) bottom() }
    Box(modifier) {
        when {
            live.loading && approval == null -> CenteredNote(stringResource(R.string.game_thread_loading))
            lines.isEmpty() && approval == null -> CenteredNote(stringResource(R.string.game_thread_empty))
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = metrics.dp(32), vertical = metrics.dp(16)),
                modifier = Modifier.fillMaxSize().testTag("house-thread").onSizeChanged { viewportHeight = it.height },
            ) {
                items(count = lines.size, key = { lines[it].key }) { position ->
                    val line = lines[position]
                    Column(Modifier.padding(top = metrics.dp(threadGapBefore(lines.getOrNull(position - 1), line)))) {
                        // L'heure n'apparaît que lorsqu'elle change d'une ligne à l'autre.
                        val time = minuteOf(live.timestamps[line.key])
                        if (time != null && time != minuteOf(lines.getOrNull(position - 1)?.let { live.timestamps[it.key] })) {
                            BasicText(time, style = GameText.code(24, Game.colors.muted), modifier = Modifier.padding(bottom = metrics.dp(4)))
                        }
                        val action = live.actionDetails[line.key]
                        if (action != null) ExpandableAction(line, action) else ThreadLineView(line, format)
                    }
                }
                if (approval != null) item(key = "inline-approval") {
                    Box(Modifier.padding(top = metrics.dp(24))) { approval() }
                }
            }
        }
        if (!follow) {
            BasicText(
                "↓ " + stringResource(R.string.game_thread_follow),
                style = GameText.body(26, Game.colors.barInk, weight = 600),
                modifier = Modifier.align(Alignment.BottomEnd).padding(metrics.dp(24))
                    .background(Game.colors.bar)
                    .clickable { scope.launch { follow = true; bottom() } }
                    .padding(horizontal = metrics.dp(24), vertical = metrics.dp(14)),
            )
        }
    }
}

@Composable
internal fun BoxScope.CenteredNote(text: String) {
    BasicText(text, style = GameText.body(32, Game.colors.muted), modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun ExpandableAction(line: ThreadLine, action: ThreadEntry.ToolCall) {
    val metrics = Game.metrics
    val colors = Game.colors
    var expanded by remember(action.persistentId) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (expanded) "▾" else "▸", style = GameText.code(28, colors.muted))
            Box(Modifier.weight(1f)) { ThreadLineView(line, AgentTextFormat.Plain) }
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = metrics.dp(20), top = metrics.dp(6)).background(colors.paperDeep).padding(metrics.dp(16)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(6)),
            ) {
                action.output?.takeIf { it.isNotEmpty() }?.let { BasicText(it, style = GameText.code(28)) }
                action.diffs.forEach { diff ->
                    BasicText(diff.path, style = GameText.code(26, colors.muted))
                    diff.lines.forEach { part ->
                        val (sign, background) = when (part.kind) {
                            DiffKind.ADDED -> "+" to colors.diffAdded
                            DiffKind.REMOVED -> "−" to colors.diffRemoved
                            DiffKind.CONTEXT -> " " to Color.Transparent
                        }
                        BasicText(sign + part.text, style = GameText.code(28), modifier = Modifier.fillMaxWidth().background(background))
                    }
                }
                if (action.output.isNullOrEmpty() && action.diffs.isEmpty()) {
                    BasicText(stringResource(R.string.game_thread_no_detail), style = GameText.body(24, colors.muted))
                }
            }
        }
    }
}

@Composable
private fun ThreadLineView(line: ThreadLine, format: AgentTextFormat) {
    val colors = Game.colors
    val metrics = Game.metrics
    when (line) {
        is ThreadLine.UserMessage -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            // Le message envoyé, tel quel, à droite, sans étiquette ajoutée par Régie.
            PixelFrame(
                Modifier.widthIn(max = metrics.dp(960)),
                colors = FrameColors(colors.line, colors.mine, colors.light, colors.shade),
                contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(16)),
            ) { BasicText(line.text, style = GameText.body(30)) }
        }
        is ThreadLine.AgentText -> PixelFrame(
            Modifier.fillMaxWidth(),
            colors = FrameDefaults.panel(),
            contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(16)),
        ) { AgentTextBlocks(line.text, line.streaming, format, sizePx = 30) }
        is ThreadLine.Read -> ActionRow(
            verb = stringResource(if (line.inProgress) R.string.game_thread_reading else R.string.game_thread_read),
            subject = if (line.files.size > 1) pluralStringResource(R.plurals.game_thread_files, line.files.size, line.files.size) else line.files.single(),
            active = line.inProgress,
            detail = line.files.takeIf { it.size > 1 }?.joinToString(", "),
        )
        is ThreadLine.Edit -> ActionRow(
            verb = stringResource(if (line.status.isUnfinished) R.string.game_thread_editing else R.string.game_thread_edited),
            subject = line.path,
            active = line.status == ActionStatus.IN_PROGRESS,
            failed = line.status == ActionStatus.FAILED,
            trailing = {
                Changes(line.changes)
                StatusWord(line.status.takeIf { it == ActionStatus.FAILED || it == ActionStatus.INTERRUPTED })
            },
        )
        is ThreadLine.Run -> ActionRow(
            verb = stringResource(if (line.status.isUnfinished) R.string.game_thread_running else R.string.game_thread_ran),
            subject = line.command,
            active = line.status == ActionStatus.IN_PROGRESS,
            failed = line.status == ActionStatus.FAILED,
            detail = line.result,
            trailing = { StatusWord(line.status) },
        )
        is ThreadLine.Other -> ActionRow(
            verb = stringResource(R.string.game_thread_action),
            subject = line.title,
            active = line.status == ActionStatus.IN_PROGRESS,
            failed = line.status == ActionStatus.FAILED,
            trailing = { StatusWord(line.status) },
        )
        is ThreadLine.TurnFailed -> BasicText(
            line.message?.let { stringResource(R.string.game_thread_turn_failed_with, it) } ?: stringResource(R.string.game_thread_turn_failed),
            style = GameText.body(28, colors.error),
        )
        is ThreadLine.Interrupted -> BasicText(stringResource(R.string.game_thread_interrupted), style = GameText.body(26, colors.muted))
        is ThreadLine.AwaitingApproval -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            Box(Modifier.size(metrics.art(3)).background(colors.wait))
            BasicText(
                line.subject?.let { stringResource(R.string.game_thread_awaiting, it) } ?: stringResource(R.string.game_thread_awaiting_no_subject),
                style = GameText.body(26, colors.waitInk, weight = 600),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal val ActionStatus?.isUnfinished get() = this == ActionStatus.PENDING || this == ActionStatus.IN_PROGRESS

/** Une action sur une ligne : un carré, le verbe, le sujet en code, puis détail et statut. */
@Composable
private fun ActionRow(
    verb: String,
    subject: String,
    active: Boolean = false,
    failed: Boolean = false,
    detail: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = Game.colors
    val metrics = Game.metrics
    Row(
        Modifier.fillMaxWidth().background(if (active) colors.paperDeep else Color.Transparent).padding(horizontal = metrics.dp(8), vertical = metrics.dp(4)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        Box(Modifier.size(metrics.art(3)).background(if (failed) colors.error else if (active) colors.work else colors.muted))
        BasicText(verb, style = GameText.body(24, colors.muted), modifier = Modifier.width(metrics.dp(110)), maxLines = 1)
        BasicText(
            subject,
            style = GameText.code(30),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = detail == null),
        )
        detail?.let {
            BasicText(it, style = GameText.code(28, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        trailing()
    }
}

/** « +12 −3 » ; rien sans diff. */
@Composable
internal fun Changes(changes: LineChanges?) {
    if (changes == null) return
    val colors = Game.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Game.metrics.dp(8))) {
        if (changes.added > 0) BasicText("+${changes.added}", style = GameText.code(28, colors.work))
        if (changes.removed > 0) BasicText("−${changes.removed}", style = GameText.code(28, colors.error))
    }
}

@Composable
private fun StatusWord(status: ActionStatus?) {
    status ?: return
    val colors = Game.colors
    val (label, color) = when (status) {
        ActionStatus.PENDING -> stringResource(R.string.game_run_pending) to colors.muted
        ActionStatus.IN_PROGRESS -> stringResource(R.string.game_run_in_progress) to colors.work
        ActionStatus.COMPLETED -> stringResource(R.string.game_run_succeeded) to colors.muted
        ActionStatus.FAILED -> stringResource(R.string.game_run_failed) to colors.error
        ActionStatus.INTERRUPTED -> stringResource(R.string.game_run_interrupted) to colors.muted
    }
    BasicText(label, style = GameText.body(24, color), maxLines = 1)
}

/**
 * Le texte de l'agent, son markdown rendu lisible par [format] : jamais réécrit. Tant qu'il s'écrit,
 * un curseur termine son dernier bloc.
 */
@Composable
fun AgentTextBlocks(text: String, streaming: Boolean, format: AgentTextFormat, sizePx: Int, color: Color = Game.colors.ink) {
    val metrics = Game.metrics
    val colors = Game.colors
    val blocks = remember(text, format) { format.blocks(text) }
    val style = GameText.body(sizePx, color)
    val codeFamily = Game.fonts.code
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        if (blocks.isEmpty() && streaming) BasicText(withCursor(AnnotatedString(""), true, colors.focus), style = style)
        blocks.forEachIndexed { index, block ->
            val cursor = streaming && index == blocks.lastIndex
            when (block) {
                is AgentBlock.Code -> BasicText(
                    withCursor(AnnotatedString(block.code), cursor, colors.focus),
                    style = GameText.code(sizePx),
                    modifier = Modifier.fillMaxWidth().background(colors.paperDeep).padding(horizontal = metrics.dp(14), vertical = metrics.dp(10)),
                )
                is AgentBlock.Text -> Row(Modifier.padding(start = metrics.dp(INDENT_PX * (block.depth - 1).coerceAtLeast(0)))) {
                    val textStyle = if (block.quote) style.copy(color = colors.muted) else style
                    block.marker?.let { BasicText(it, style = textStyle, modifier = Modifier.width(metrics.dp(INDENT_PX))) }
                    BasicText(withCursor(spansText(block, codeFamily, textStyle), cursor, colors.focus), style = textStyle)
                }
            }
        }
    }
}

private fun spansText(block: AgentBlock.Text, code: androidx.compose.ui.text.font.FontFamily, base: TextStyle): AnnotatedString = buildAnnotatedString {
    for (span in block.spans) {
        val style = SpanStyle(
            fontWeight = if (span.bold || block.heading) FontWeight(700) else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) code else null,
            fontSize = if (span.code) base.fontSize * 1.15f else androidx.compose.ui.unit.TextUnit.Unspecified,
        )
        withStyle(style) { append(span.text) }
    }
}

private fun withCursor(text: AnnotatedString, streaming: Boolean, color: Color): AnnotatedString =
    if (!streaming) text else buildAnnotatedString {
        append(text)
        withStyle(SpanStyle(color = color)) { append("▌") }
    }

/** « 15:18 » dans le fuseau de l'appareil ; rien pour un élément sans date. */
private fun minuteOf(time: Instant?): String? = time?.let {
    java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(it.toEpochMilliseconds()))
}

private const val INDENT_PX = 36
private const val STEP_PX = 280
private const val STICK_PX = 2000f
