package app.regie.game.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import app.regie.domain.model.Agent
import app.regie.domain.model.DiffKind
import app.regie.domain.model.Delivery
import app.regie.domain.model.LineChanges
import app.regie.domain.model.Session
import app.regie.domain.usecase.DiffExplanation
import app.regie.domain.usecase.DiffReview
import app.regie.domain.usecase.DiffSelection
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.house.AgentTextBlocks
import app.regie.game.ui.house.AgentTextFormat
import app.regie.game.ui.house.HouseLook
import app.regie.game.ui.house.HouseScene
import app.regie.game.ui.house.InteriorScene
import app.regie.game.ui.house.InteriorSize
import app.regie.game.ui.house.PadAction
import app.regie.game.ui.house.PadActionBar
import app.regie.game.ui.house.PadActionHint
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.FrameColors
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelDiffLine
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.render.Sprite
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.flow.collectLatest

/** Ce que les écrans des modifications peuvent demander. La racine de composition le relie à `ReviewDiff`. */
interface DiffCallbacks {
    /** A, B, Y, L1, R1, Start : au doigt comme à la manette. */
    fun onKey(key: PadKey)

    /** L2 tenu au doigt : commenter la ligne choisie à la voix. */
    fun onSpeak(held: Boolean)

    /** Un toucher sur un fichier de la liste. */
    fun onFile(index: Int)
}

/**
 * L'écran du haut des modifications : dans la maison de la session, le tableau de liège et la feuille
 * épinglée du fichier choisi, ligne par ligne. Ajouts en vert pâle, retraits en rose, la ligne choisie
 * encadrée et ramenée en vue ; le stick droit fait défiler ([scroll]). Pendant une explication,
 * la feuille montre la demande puis la réponse de l'agent, telle quelle ; la croix la fait défiler.
 *
 * [single] : un seul écran, le carnet ([single]) prend la droite.
 */
@Composable
fun CorkBoardScreen(
    review: DiffReview,
    session: Session?,
    assets: GameAssets,
    scenes: Map<String, InteriorScene>,
    scroll: ScrollInput,
    modifier: Modifier = Modifier,
    format: AgentTextFormat = AgentTextFormat.Plain,
    single: (@Composable () -> Unit)? = null,
) {
    val metrics = Game.metrics
    Row(modifier.fillMaxSize().background(InteriorSize.NIGHT)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            RoomBehind(session?.agent, session?.let { HouseLook.pose(it) }, assets, scenes)
            Box(Modifier.fillMaxSize().background(Game.colors.scrim))
            PixelFrame(
                Modifier.fillMaxSize().padding(horizontal = metrics.dp(if (single != null) 32 else 80), vertical = metrics.dp(if (single != null) 32 else 48))
                    .testTag("cork-board"),
                thick = true,
                colors = CORK,
            ) {
                Box(Modifier.fillMaxSize().drawBehind { drawCork(metrics.pixelPx.toFloat()) }) {
                    val explanation = review.explanation
                    Sheet(Modifier.padding(start = metrics.dp(48), end = metrics.dp(48), top = metrics.dp(if (explanation == null) 120 else 48), bottom = metrics.dp(40))) {
                        if (explanation != null) Explanation(explanation, format, scroll) else Lines(review, scroll)
                    }
                    if (explanation == null) FileLabel(review, Modifier.padding(start = metrics.dp(40), top = metrics.dp(30)))
                }
            }
        }
        if (single != null) Box(Modifier.width(metrics.dp(SINGLE_NOTEBOOK_PX)).fillMaxHeight()) { single() }
    }
}

/** La pièce derrière le tableau : l'habitant de la session à son poste, le coursier à l'entrée. */
@Composable
private fun RoomBehind(agent: Agent?, pose: app.regie.game.ui.house.Pose?, assets: GameAssets, scenes: Map<String, InteriorScene>) {
    val kind = agent?.let { InhabitantKind.of(it) } ?: InhabitantKind.FALLBACK
    val background = HouseLook.interior(kind)
    val scene = scenes[background] ?: InteriorScene.DEFAULT
    HouseScene(
        assets,
        background,
        sprites = listOfNotNull(
            pose?.let { Sprite(HouseLook.inhabitant(kind, it), scene.inhabitant) },
            Sprite(HouseLook.courier(false), scene.entrance),
        ),
        focus = scene.inhabitant,
        modifier = Modifier.fillMaxSize(),
    )
}

/** L'étiquette épinglée en haut du tableau : le chemin du fichier, ses comptes, sa place dans la liste. */
@Composable
private fun FileLabel(review: DiffReview, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(modifier, contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(8))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
            BasicText(
                review.file?.path.orEmpty(), style = GameText.code(36), maxLines = 1, overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Counts(review.file?.changes, review.file != null)
            BasicText(if (review.files.isEmpty()) "0 / 0" else "${review.fileIndex + 1} / ${review.files.size}", style = GameText.code(30, colors.muted))
        }
    }
}

/** La feuille de papier épinglée, avec son ombre et sa punaise. */
@Composable
private fun Sheet(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val metrics = Game.metrics
    val p = metrics.pixelPx.toFloat()
    Box(
        modifier.fillMaxSize()
            .drawBehind {
                drawRect(CORK_LINE, Offset(2 * p, 2 * p), size)
                drawRect(SHEET, Offset.Zero, size)
            }
            .padding(vertical = metrics.dp(SHEET_PADDING_PX)),
    ) {
        content()
        // La punaise dépasse de 10 px au-dessus de la feuille (28 px de marge intérieure).
        Box(
            Modifier.align(Alignment.TopCenter).offset { IntOffset(0, -(SHEET_PADDING_PX + 10)) }.size(metrics.art(6))
                .drawBehind {
                    drawRect(PIN, Offset.Zero, size)
                    drawRect(PIN_SHADE, Offset(size.width / 2, size.height / 2), Size(size.width / 2, size.height / 2))
                },
        )
    }
}

/** Les lignes du fichier choisi ; la sélection est ramenée en vue à chaque geste de la croix. */
@Composable
private fun Lines(review: DiffReview, scroll: ScrollInput) {
    val metrics = Game.metrics
    val colors = Game.colors
    val rows = review.rows
    if (rows.isEmpty()) {
        Note(
            stringResource(
                when {
                    review.loading -> R.string.game_diff_loading
                    review.file != null -> R.string.game_diff_not_shared
                    else -> R.string.game_diff_none
                },
            ),
        )
        return
    }
    val list = rememberLazyListState()
    LaunchedEffect(review.file?.path, review.lineIndex, review.selectionRevision) {
        val index = review.lineIndex
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == index } || visible.lastOrNull()?.index == index) list.scrollToItem(index)
    }
    LaunchedEffect(list, scroll) {
        scroll.velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                list.scrollBy(value * STICK_PX * (now - last) / 1e9f)
                last = now
            }
        }
    }
    val file = review.file
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("diff-lines")) {
        items(rows.size) { index ->
            val row = rows[index]
            if (row.lineIndex == 0 && file != null) {
                val number = file.blocks.indexOf(row.block) + 1
                BasicText(
                    if (row.block.fileCoordinates) stringResource(R.string.game_diff_edit, number) else stringResource(R.string.game_diff_excerpt, number),
                    style = GameText.code(28, colors.muted),
                    modifier = Modifier.padding(horizontal = metrics.dp(24), vertical = metrics.dp(6)),
                )
            }
            PixelDiffLine(row.line, Modifier.semantics { selected = index == review.lineIndex }, selected = index == review.lineIndex)
        }
    }
}

/** La demande d'explication, puis la réponse de l'agent sans réécriture ; la croix et le stick font défiler. */
@Composable
private fun Explanation(explanation: DiffExplanation, format: AgentTextFormat, scroll: ScrollInput) {
    val metrics = Game.metrics
    val list = rememberLazyListState()
    LaunchedEffect(list, scroll) { scroll.steps.collect { list.animateScrollBy(it * STEP_PX.toFloat()) } }
    LaunchedEffect(list, scroll) {
        scroll.velocity.collectLatest { value ->
            if (value == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                list.scrollBy(value * STICK_PX * (now - last) / 1e9f)
                last = now
            }
        }
    }
    val status = explanationStatus(explanation)
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("diff-explanation"), contentPadding = PaddingValues(horizontal = metrics.dp(40))) {
        item {
            BasicText(stringResource(R.string.game_diff_explain), style = GameText.display(48), modifier = Modifier.padding(bottom = metrics.dp(16)))
        }
        item {
            when {
                explanation.reply.isNotBlank() -> AgentTextBlocks(explanation.reply, !explanation.ended, format, sizePx = 34)
                explanation.delivery == null && !explanation.sending -> BasicText(explanation.prompt, style = GameText.code(32))
                else -> BasicText(status, style = GameText.body(32, Game.colors.muted))
            }
        }
    }
}

/** Le carnet des modifications : les fichiers, la ligne choisie, L2 pour la commenter, Y pour l'expliquer. */
@Composable
fun DiffNotebook(review: DiffReview, callbacks: DiffCallbacks, modifier: Modifier = Modifier, compact: Boolean = false) {
    val metrics = Game.metrics
    val colors = Game.colors
    val explanation = review.explanation
    Column(modifier.fillMaxSize().background(colors.paper)) {
        Row(
            Modifier.fillMaxWidth().height(metrics.dp(88)).background(colors.paperDeep).padding(horizontal = metrics.dp(28)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(18)),
        ) {
            BasicText(stringResource(R.string.game_diff_title), style = GameText.display(52))
            BasicText(
                pluralStringResource(R.plurals.game_diff_files, review.files.size, review.files.size),
                style = GameText.body(24, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (explanation == null) {
            FileList(review, callbacks, Modifier.weight(1f))
            SelectedLine(review, callbacks)
            // Un seul écran : le carnet est étroit, L1 / R1 gardent leur effet sans aide.
            PadActionBar(
                listOfNotNull(
                    PadAction(listOf("↑↓"), stringResource(R.string.game_diff_lines)),
                    PadAction(listOf("←→"), stringResource(R.string.game_diff_file)),
                    PadAction("B", stringResource(R.string.game_diff_back)) { callbacks.onKey(PadKey.B) },
                    PadAction(listOf("L1", "R1"), stringResource(R.string.game_diff_navigate)).takeIf { !compact },
                ),
                compact = compact,
            )
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = metrics.dp(32), vertical = metrics.dp(20)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(14)),
            ) {
                BasicText(explanation.label, style = GameText.code(32))
                BasicText(explanationStatus(explanation), style = GameText.body(28, colors.muted))
            }
            PadActionBar(
                listOfNotNull(
                    PadAction("A", stringResource(R.string.game_diff_ask)) { callbacks.onKey(PadKey.A) }
                        .takeIf { !explanation.sending && (explanation.delivery == null || explanation.delivery?.canRetry == true) },
                    PadAction("B", stringResource(R.string.game_diff_back_to_diff)) { callbacks.onKey(PadKey.B) },
                ),
                compact = compact,
            )
        }
    }
}

/** Les fichiers suivis, chacun avec ses comptes ; le fichier choisi est encadré, un toucher en choisit un autre. */
@Composable
private fun FileList(review: DiffReview, callbacks: DiffCallbacks, modifier: Modifier) {
    val metrics = Game.metrics
    val list = rememberLazyListState()
    LaunchedEffect(review.fileIndex) { if (review.files.isNotEmpty()) list.scrollToItem(review.fileIndex) }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxWidth().testTag("diff-files"),
        contentPadding = PaddingValues(horizontal = metrics.dp(24), vertical = metrics.dp(16)),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        items(review.files.size) { index ->
            val file = review.files[index]
            val on = index == review.fileIndex
            PixelFrame(
                Modifier.fillMaxWidth().semantics { selected = on }.clickable { callbacks.onFile(index) },
                thick = on,
                colors = if (on) FrameDefaults.focus() else FrameDefaults.panel(),
                contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(12)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                    if (on) PixelArrow(ArrowDirection.RIGHT, color = Game.colors.focus, blink = true)
                    BasicText(file.path, style = GameText.code(32), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Counts(file.changes, true)
                }
            }
        }
    }
}

/** La ligne choisie, et ce qu'on peut en faire : la commenter (L2 tenu), demander une explication (Y). */
@Composable
private fun SelectedLine(review: DiffReview, callbacks: DiffCallbacks) {
    val metrics = Game.metrics
    val colors = Game.colors
    val speak by rememberUpdatedState(callbacks::onSpeak)
    val selected = review.selected
    PixelFrame(
        Modifier.fillMaxWidth().padding(horizontal = metrics.dp(24), vertical = metrics.dp(10)).testTag("diff-selected"),
        thick = true,
        contentPadding = PaddingValues(horizontal = metrics.dp(24), vertical = metrics.dp(16)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
            BasicText(selectedLabel(selected), style = GameText.body(30, weight = 600))
            if (selected != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(40)), verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                    // L2 se maintient au doigt, comme la gâchette.
                    Box(
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                speak(true)
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    speak(false)
                                }
                            })
                        },
                    ) { PadActionHint(PadAction(listOf("L2"), stringResource(R.string.game_diff_comment))) }
                    PadActionHint(PadAction("Y", stringResource(R.string.game_diff_explain)) { callbacks.onKey(PadKey.Y) })
                }
            }
        }
    }
}

@Composable
private fun selectedLabel(selected: DiffSelection?): String {
    selected ?: return stringResource(R.string.game_diff_no_line)
    // « Ligne 7 · supprimée · extrait » : une ligne retirée, un numéro relatif à l'extrait (ADR 12).
    return listOfNotNull(
        stringResource(R.string.game_diff_line, selected.line.number ?: 0),
        stringResource(R.string.game_diff_removed).takeIf { selected.line.kind == DiffKind.REMOVED },
        stringResource(R.string.game_diff_from_excerpt).takeIf { !selected.block.fileCoordinates },
    ).joinToString(" · ")
}

/** « +12 −3 », ou « +/− ? » quand l'agent n'a pas communiqué les lignes. */
@Composable
private fun Counts(changes: LineChanges?, known: Boolean) {
    val colors = Game.colors
    if (!known) return
    if (changes == null) {
        BasicText(stringResource(R.string.game_diff_unknown_changes), style = GameText.code(30, colors.muted))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Game.metrics.dp(10))) {
        BasicText("+${changes.added}", style = GameText.code(30, colors.work))
        BasicText("−${changes.removed}", style = GameText.code(30, colors.error))
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxSize().padding(Game.metrics.dp(40)), contentAlignment = Alignment.Center) {
        BasicText(text, style = GameText.body(32, Game.colors.muted))
    }
}

/** Où en est la demande d'explication, avec les mots de l'écran classique. */
@Composable
private fun explanationStatus(value: DiffExplanation): String = stringResource(
    when {
        value.sending -> R.string.game_explain_sending
        value.delivery == null -> R.string.game_explain_review
        value.delivery?.canRetry == true -> R.string.game_explain_retry
        value.delivery == Delivery.UNKNOWN -> R.string.game_explain_unknown
        value.ended && value.reply.isBlank() -> R.string.game_explain_none
        value.reply.isNotBlank() -> R.string.game_explain_reply
        value.delivery == Delivery.QUEUED -> R.string.game_explain_queued
        else -> R.string.game_explain_waiting
    },
)

/** Le liège : un fond brun piqueté d'un point sombre tous les 4 pixels du dessin (maquette 09). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCork(p: Float) {
    val step = 4 * p
    var y = p
    while (y < size.height) {
        var x = p + if (((y / step).toInt() % 2) == 0) 0f else 2 * p
        while (x < size.width) {
            drawRect(CORK_DOT, Offset(x, y), Size(p, p))
            x += step
        }
        y += step
    }
}

private val CORK = FrameColors(line = Color(0xFF6E4A3F), fill = Color(0xFFB07A4A), light = Color(0xFFC99466), shade = Color(0xFF7A5030))
private val CORK_LINE = Color(0xFF6E4A3F)
private val CORK_DOT = Color(0xFF8E5E36)
private val SHEET = Color(0xFFFFF8E6)
private val PIN = Color(0xFFC2412D)
private val PIN_SHADE = Color(0xFF8A2A1E)

private const val SHEET_PADDING_PX = 28
private const val STEP_PX = 160
private const val STICK_PX = 1600f
private const val SINGLE_NOTEBOOK_PX = 800
