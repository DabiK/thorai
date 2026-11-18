package app.regie.game.ui.house

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.DictationPhase
import app.regie.domain.model.DictationReview
import app.regie.domain.model.DictationTarget
import app.regie.domain.model.Session
import app.regie.game.ui.R
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.MicMeter
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.delay

/**
 * La dictée (L2), dans le carnet du bas : le destinataire, la durée, la transcription en direct
 * (57 px, soit 25 sp sur la Thor) avec les termes du dépôt reconnus ([terms]) en vert, le niveau du
 * micro, puis A envoie, B efface, X corrige un mot, Y complète. L'écran du haut garde la maison ;
 * le coursier y parle.
 */
@Composable
fun HouseDictationScreen(
    state: DictationReview,
    session: Session?,
    terms: List<IntRange>,
    scroll: ScrollInput,
    callbacks: DictationCallbacks,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val metrics = Game.metrics
    val colors = Game.colors
    val clock = Game.clock
    val recording = state.phase == DictationPhase.RECORDING
    Column(modifier.fillMaxSize().background(colors.paper)) {
        Row(
            Modifier.fillMaxWidth().padding(start = metrics.dp(44), end = metrics.dp(32), top = metrics.dp(if (compact) 20 else 32), bottom = metrics.dp(12)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(16)),
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
                BasicText(
                    stringResource(
                        when (state.target) {
                            is DictationTarget.Rejection -> R.string.game_dictation_rejection
                            is DictationTarget.DiffComment -> R.string.game_dictation_comment
                            else -> R.string.game_dictation_message
                        },
                    ),
                    style = GameText.body(28, colors.muted),
                    maxLines = 1,
                )
                session?.let {
                    BasicText(it.agent.label, style = GameText.display(48, colors.agent(it.agent)), maxLines = 1)
                    BasicText(it.project, style = GameText.body(28, weight = 700), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    it.branch?.let { branch -> BasicText(branch, style = GameText.code(30, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (recording) Box(Modifier.size(metrics.art(7)).graphicsLayer { alpha = Blink.pulse(clock.value) }.background(colors.error))
            BasicText(duration(state.durationMs), style = GameText.code(36, if (recording) colors.error else colors.muted))
            BasicText(
                stringResource(R.string.game_dictation_voice),
                style = GameText.body(24, colors.muted),
                modifier = Modifier.clickable(onClick = callbacks::onVoiceSettings).padding(metrics.dp(8)),
            )
        }
        PixelFrame(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = metrics.dp(24)),
            thick = true,
            contentPadding = PaddingValues(horizontal = metrics.dp(36), vertical = metrics.dp(28)),
        ) { Transcript(state, terms, scroll) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = metrics.dp(36), vertical = metrics.dp(16)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(24)),
        ) {
            val levels = rememberLevels(state, MAX_LEVELS)
            val levelLabel = stringResource(R.string.game_dictation_level)
            MicMeter(
                levels.ifEmpty { listOf(0f) },
                Modifier.semantics {
                    contentDescription = levelLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(state.level, 0f..1f)
                },
                color = if (recording) colors.work else colors.idle,
            )
            BasicText(phaseText(state), style = GameText.body(28, weight = 600), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("dictation-phase"))
        }
        if (state.phase == DictationPhase.CORRECT) {
            Row(Modifier.padding(start = metrics.dp(36), bottom = metrics.dp(12)), horizontalArrangement = Arrangement.spacedBy(metrics.dp(36))) {
                PadActionHint(PadAction("←", stringResource(R.string.game_dictation_before)) { callbacks.onKey(PadKey.LEFT) })
                PadActionHint(PadAction("→", stringResource(R.string.game_dictation_after)) { callbacks.onKey(PadKey.RIGHT) })
            }
        }
        PadActionBar(dictationActions(state, callbacks), compact = compact)
    }
}

/** A, B, X, Y comme l'écran classique ; L2 se tient aussi au doigt. */
@Composable
private fun dictationActions(state: DictationReview, callbacks: DictationCallbacks): List<PadAction> = listOfNotNull(
    PadAction("A", stringResource(R.string.game_dictation_send)) { callbacks.onKey(PadKey.A) }.takeIf { state.editable },
    PadAction("B", stringResource(if (state.phase == DictationPhase.SENT) R.string.game_dictation_back else R.string.game_dictation_erase)) { callbacks.onKey(PadKey.B) },
    PadAction("X", stringResource(R.string.game_dictation_correct)) { callbacks.onKey(PadKey.X) }.takeIf { state.editable },
    PadAction("Y", stringResource(R.string.game_dictation_complete)) { callbacks.onKey(PadKey.Y) }.takeIf { state.editable },
    PadAction(listOf("L2"), stringResource(R.string.game_action_speak), onHold = callbacks::onSpeak),
)

@Composable
private fun phaseText(state: DictationReview): String = state.notice ?: stringResource(
    when (state.phase) {
        DictationPhase.RECORDING -> R.string.game_dictation_recording
        DictationPhase.FINALIZING -> R.string.game_dictation_finalizing
        DictationPhase.SENDING -> R.string.game_dictation_sending
        else -> R.string.game_dictation_review
    },
)

/**
 * La transcription en direct : les termes du dépôt reconnus en vert, le mot à corriger en ambre.
 * La croix et le stick la font défiler ; en correction, elle suit le mot choisi.
 */
@Composable
private fun Transcript(state: DictationReview, terms: List<IntRange>, scrolling: ScrollInput) {
    val metrics = Game.metrics
    val colors = Game.colors
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(scrolling) { scrolling.steps.collect { scroll.scrollTo(scroll.value + it * STEP_PX) } }
    LaunchedEffect(scrolling.velocity) {
        while (true) {
            delay(16)
            if (scrolling.velocity.value != 0f) scroll.scrollTo(scroll.value + (scrolling.velocity.value * STICK_PX).toInt())
        }
    }
    LaunchedEffect(state.selectedWord, state.phase, layout) {
        if (state.phase == DictationPhase.CORRECT) state.words.getOrNull(state.selectedWord)?.let { word ->
            layout?.let { result -> scroll.scrollTo(result.getLineTop(result.getLineForOffset(word.range.first)).toInt()) }
        }
    }
    // Pendant l'enregistrement, la fin du texte reste visible.
    LaunchedEffect(state.text, state.phase) { if (state.phase == DictationPhase.RECORDING) scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("dictation-transcript"), verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        val text = buildAnnotatedString {
            append(state.text.ifEmpty { "…" })
            terms.filter { it.last < state.text.length }.forEach {
                addStyle(SpanStyle(background = colors.mine, color = colors.focus, fontFamily = Game.fonts.code), it.first, it.last + 1)
            }
            if (state.phase == DictationPhase.CORRECT) state.words.getOrNull(state.selectedWord)?.let {
                addStyle(SpanStyle(color = colors.waitInk, background = colors.wait), it.range.first, it.range.last + 1)
            }
        }
        BasicText(text, style = GameText.body(57), onTextLayout = { layout = it })
        (state.target as? DictationTarget.DiffComment)?.let { BasicText(it.location, style = GameText.code(30, colors.muted)) }
    }
}

private fun duration(ms: Long) = "${ms / 60_000}:${(ms / 1000 % 60).toString().padStart(2, '0')}"

private const val MAX_LEVELS = 32
private const val STEP_PX = 340
private const val STICK_PX = 46
