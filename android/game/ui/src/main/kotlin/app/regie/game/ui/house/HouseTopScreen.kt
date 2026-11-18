package app.regie.game.ui.house

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.DictationPhase
import app.regie.domain.model.DictationReview
import app.regie.domain.model.LiveSession
import app.regie.domain.model.ModifiedFile
import app.regie.domain.model.PlanStep
import app.regie.domain.model.PlanStepStatus
import app.regie.domain.model.ThreadLine
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.R
import app.regie.game.ui.approval.WaitingMarker
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.Bubble
import app.regie.game.ui.kit.BubbleTail
import app.regie.game.ui.kit.DialogBox
import app.regie.game.ui.kit.MicMeter
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.kit.WorkDots
import app.regie.game.ui.kit.tagLabel
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.Sprite
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.GameScale
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/**
 * L'écran du haut d'une session : l'intérieur de sa maison selon l'habitant (le scribe pour Claude,
 * le bricoleur pour Codex, la cartographe pour opencode), l'habitant animé par l'état de la session,
 * le coursier à l'entrée, la bulle d'activité de la dernière action, le plan de l'agent en journal
 * de quête quand il en expose un, et sa dernière réplique, telle quelle, dans la boîte de dialogue.
 *
 * Pendant la dictée de cette session, le coursier parle : une bulle montre le niveau du micro ;
 * la transcription reste en bas. [approval] (la validation en attente, avec son détail) s'affiche
 * par-dessus la pièce.
 *
 * [single] : mode un seul écran. L'intérieur se resserre à gauche autour de l'habitant ; le carnet
 * du bas ([single]) occupe la droite, avec le fil, les réglages et les touches.
 */
@Composable
fun HouseTopScreen(
    live: LiveSession?,
    gone: Boolean,
    assets: GameAssets,
    scenes: Map<String, InteriorScene>,
    ageLabel: String?,
    dictation: DictationReview,
    modifier: Modifier = Modifier,
    format: AgentTextFormat = AgentTextFormat.Plain,
    approval: (@Composable () -> Unit)? = null,
    single: (@Composable () -> Unit)? = null,
) {
    val metrics = Game.metrics
    Row(modifier.fillMaxSize().background(InteriorSize.NIGHT)) {
        Box(if (single != null) Modifier.width(metrics.dp(SINGLE_SCENE_PX)).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) {
            if (live == null) {
                BasicText(
                    stringResource(if (gone) R.string.game_session_gone else R.string.game_thread_opening),
                    style = GameText.body(32, Game.colors.paper),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Interior(live, assets, scenes, ageLabel, dictation, format, approval.takeIf { single == null }, compact = single != null)
            }
        }
        if (single != null) Box(Modifier.weight(1f).fillMaxHeight()) { single() }
    }
}

@Composable
private fun Interior(
    live: LiveSession,
    assets: GameAssets,
    scenes: Map<String, InteriorScene>,
    ageLabel: String?,
    dictation: DictationReview,
    format: AgentTextFormat,
    approval: (@Composable () -> Unit)?,
    compact: Boolean,
) {
    val metrics = Game.metrics
    val session = live.session
    val kind = InhabitantKind.of(session.agent)
    val background = HouseLook.interior(kind)
    val scene = scenes[background] ?: InteriorScene.DEFAULT
    val speaking = dictation.visible && dictation.target?.sessionId == session.id &&
        dictation.phase in setOf(DictationPhase.RECORDING, DictationPhase.FINALIZING)
    val pose = HouseLook.pose(session)
    val reply = if (compact) null else HouseLook.lastReply(live.lines)
    // Deux écrans : la pièce remonte juste assez pour que le coursier reste au-dessus de la réplique.
    val lift = if (compact) 0 else (scene.entrance.y + 4 - DIALOG_TOP_ART).coerceIn(0, scene.roomTop)
    Box(Modifier.fillMaxSize()) {
        HouseScene(
            assets,
            background,
            sprites = listOf(
                Sprite(HouseLook.inhabitant(kind, pose), scene.inhabitant),
                Sprite(HouseLook.courier(speaking && dictation.phase == DictationPhase.RECORDING), scene.entrance),
            ),
            focus = scene.inhabitant,
            modifier = Modifier.fillMaxSize().testTag("house-scene").semantics { contentDescription = HouseLook.inhabitant(kind, pose) },
            lift = lift,
        ) { projection ->
            HouseLook.activity(session, live.lines)?.let { action ->
                ActivityBubble(action, Modifier.anchorBottomCenter { projection.toScreen(ArtPoint(scene.inhabitant.x, scene.inhabitant.y - HEAD_ART)) })
            }
            if (pose == Pose.WAITING) {
                WaitingMarker(Modifier.anchorBottomCenter { projection.toScreen(ArtPoint(scene.inhabitant.x, scene.inhabitant.y - HEAD_ART)) })
            }
            if (speaking) {
                CourierBubble(dictation, assets, Modifier.anchorBottomStart { projection.toScreen(ArtPoint(scene.entrance.x + 8, scene.entrance.y - 14)) })
            }
        }
        Sign(live, ageLabel, Modifier.padding(start = metrics.dp(if (compact) 24 else 44), top = metrics.dp(if (compact) 24 else 40)))
        if (live.plan != null || live.modifiedFiles.isNotEmpty()) {
            QuestLog(
                live.plan.orEmpty(), live.modifiedFiles,
                if (compact) {
                    Modifier.align(Alignment.BottomCenter).padding(metrics.dp(24)).fillMaxWidth().heightIn(max = metrics.dp(400))
                } else {
                    Modifier.align(Alignment.TopEnd).padding(end = metrics.dp(52), top = metrics.dp(48)).width(metrics.dp(520)).heightIn(max = metrics.dp(660))
                },
            )
        }
        if (approval != null) {
            // Le parchemin tendu par l'habitant borne sa hauteur et dit où lire la suite (ticket 47).
            Box(Modifier.padding(start = metrics.dp(44), top = metrics.dp(290)).width(metrics.dp(900)).heightIn(max = metrics.dp(460))) { approval() }
        }
        reply?.let {
            Reply(
                live, it, format,
                Modifier.align(Alignment.BottomCenter).padding(start = metrics.dp(96), end = metrics.dp(96), bottom = metrics.dp(24))
                    .heightIn(max = metrics.dp(DIALOG_HEIGHT_PX)),
            )
        }
    }
}

/** Le panneau de la maison : projet, agent et branche, état depuis quand, contexte. */
@Composable
private fun Sign(live: LiveSession, ageLabel: String?, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val session = live.session
    val state = TagState.of(session.status)
    PixelFrame(modifier.widthIn(max = metrics.dp(720)), contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(14))) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
            BasicText(session.project, style = GameText.display(44), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                BasicText(session.agent.label, style = GameText.body(24, colors.agent(session.agent), weight = 700), maxLines = 1)
                session.branch?.let { BasicText(it, style = GameText.code(30, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                StateTag(state, label = listOfNotNull(tagLabel(state), ageLabel).joinToString(" · "))
                live.context?.let { BasicText(stringResource(R.string.game_session_context, it.percent), style = GameText.code(28, colors.muted)) }
            }
        }
    }
}

/** La bulle d'activité au-dessus de l'habitant : la dernière action, comme dans le fil. */
@Composable
private fun ActivityBubble(action: ThreadLine, modifier: Modifier) {
    val (verb, subject) = activityText(action) ?: return
    Bubble(modifier.widthIn(max = Game.metrics.dp(900)).testTag("house-activity")) {
        WorkDots(Game.colors.work)
        BasicText(verb, style = GameText.body(30), maxLines = 1)
        BasicText(subject, style = GameText.code(34), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Le verbe et le sujet d'une action, avec les mots du fil ; le nom du fichier seul pour un chemin. */
@Composable
private fun activityText(action: ThreadLine): Pair<String, String>? = when (action) {
    is ThreadLine.Read -> stringResource(if (action.inProgress) R.string.game_thread_reading else R.string.game_thread_read) to
        if (action.files.size > 1) pluralStringResource(R.plurals.game_thread_files, action.files.size, action.files.size) else action.files.single().substringAfterLast('/')
    is ThreadLine.Edit -> stringResource(if (action.status.isUnfinished) R.string.game_thread_editing else R.string.game_thread_edited) to action.path.substringAfterLast('/')
    is ThreadLine.Run -> stringResource(if (action.status.isUnfinished) R.string.game_thread_running else R.string.game_thread_ran) to action.command
    is ThreadLine.Other -> stringResource(R.string.game_thread_action) to action.title
    else -> null
}?.let { (verb, subject) -> verb.replaceFirstChar { it.uppercase() } to subject }

/** Le coursier parle à l'habitant : les derniers niveaux du micro, rien d'inventé quand il se tait. */
@Composable
private fun CourierBubble(dictation: DictationReview, assets: GameAssets, modifier: Modifier) {
    val levels = rememberLevels(dictation, MAX_BUBBLE_LEVELS)
    Bubble(modifier.testTag("courier-bubble"), tail = BubbleTail.LEFT) {
        SpriteImage("ui.micro", assets, scale = GameScale.ICON, placeholderPixels = 12)
        MicMeter(levels.ifEmpty { listOf(0f) }, heightPx = 48, color = if (dictation.phase == DictationPhase.RECORDING) Game.colors.work else Game.colors.idle)
    }
}

/** Les niveaux récents du micro, remis à zéro à chaque dictée. */
@Composable
internal fun rememberLevels(dictation: DictationReview, count: Int): List<Float> {
    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(dictation.durationMs, dictation.level) {
        if (dictation.durationMs == 0L) levels.clear()
        levels.add(dictation.level)
        while (levels.size > count) levels.removeAt(0)
    }
    return levels
}

/** Le journal de quête : le plan exposé par l'agent, puis ses fichiers modifiés. */
@Composable
private fun QuestLog(plan: List<PlanStep>, files: List<ModifiedFile>, modifier: Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val list = rememberLazyListState()
    val current = plan.indexOfFirst { it.status == PlanStepStatus.IN_PROGRESS }
    LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem(current) }
    PixelFrame(modifier.testTag("quest-log"), thick = true, contentPadding = PaddingValues(horizontal = metrics.dp(28), vertical = metrics.dp(22))) {
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            if (plan.isNotEmpty()) {
                item { BasicText(stringResource(R.string.game_plan_title), style = GameText.body(24, colors.muted)) }
                items(plan.size) { PlanRow(plan[it]) }
            }
            if (files.isNotEmpty()) {
                item {
                    BasicText(
                        stringResource(R.string.game_files_title), style = GameText.body(24, colors.muted),
                        modifier = Modifier.padding(top = metrics.dp(if (plan.isEmpty()) 0 else 12)),
                    )
                }
                items(files.size) { index ->
                    val file = files[index]
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                        BasicText(file.path.substringAfterLast('/'), style = GameText.code(30), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Changes(file.changes)
                    }
                }
            }
        }
    }
}

/** Fait : case verte ; en cours : case ambre qui clignote ; à faire : case vide. */
@Composable
private fun PlanRow(step: PlanStep) {
    val metrics = Game.metrics
    val colors = Game.colors
    val clock = Game.clock
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        val box = Modifier.padding(top = metrics.dp(8)).size(metrics.art(5))
        when (step.status) {
            PlanStepStatus.DONE -> Box(box.background(colors.work))
            PlanStepStatus.IN_PROGRESS -> Box(box.graphicsLayer { alpha = Blink.pulse(clock.value) }.background(colors.wait))
            PlanStepStatus.PENDING -> Box(
                box.drawBehind {
                    val p = metrics.pixelPx.toFloat()
                    drawRect(colors.idle, Offset(p / 2, p / 2), Size(size.width - p, size.height - p), style = Stroke(p))
                },
            )
        }
        BasicText(
            step.text,
            style = GameText.body(28, if (step.status == PlanStepStatus.PENDING) colors.muted else colors.ink, weight = if (step.status == PlanStepStatus.IN_PROGRESS) 600 else 400),
        )
    }
}

/** La dernière réplique de l'agent, telle quelle ; la fin reste visible pendant qu'il écrit. */
@Composable
private fun Reply(live: LiveSession, reply: ThreadLine.AgentText, format: AgentTextFormat, modifier: Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(reply.text, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    DialogBox(
        modifier.fillMaxWidth().testTag("house-reply"),
        speaker = live.session.agent.label,
        speakerColor = Game.colors.agent(live.session.agent),
        showNext = false,
    ) {
        Box(Modifier.verticalScroll(scroll)) { AgentTextBlocks(reply.text, reply.streaming, format, sizePx = 36) }
    }
}

/** La boîte de dialogue : 300 px au plus, 24 px au-dessus du bas ; son haut, en pixels du dessin. */
private const val DIALOG_HEIGHT_PX = 300
private const val DIALOG_TOP_ART = (1080 - 24 - DIALOG_HEIGHT_PX) / 4

/** Hauteur d'un personnage et un pixel d'air : la bulle part de là. */
private const val HEAD_ART = 33
private const val SINGLE_SCENE_PX = 800
private const val MAX_BUBBLE_LEVELS = 8
