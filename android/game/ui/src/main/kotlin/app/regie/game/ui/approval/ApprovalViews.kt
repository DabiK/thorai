package app.regie.game.ui.approval

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import app.regie.domain.model.Agent
import app.regie.domain.model.Approval
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalKind
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.house.PadAction
import app.regie.game.ui.house.PadActionHint
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.Blink
import app.regie.game.ui.kit.Choice
import app.regie.game.ui.kit.ChoiceMenu
import app.regie.game.ui.kit.DialogBox
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChip
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale
import app.regie.game.ui.theme.GameText
import kotlinx.coroutines.flow.collectLatest

/**
 * Le parchemin que l'habitant tend : la demande telle que l'agent l'expose, sans rien réécrire.
 * Ce qu'il fait (commande, fichier ou action), la commande ou le fichier, le dossier, pourquoi, les
 * fichiers concernés, le plan et l'avant / après ; une information absente est annoncée, jamais déduite.
 *
 * [scroll] : le parchemin a une hauteur bornée et défile ; [overflowNote] dit alors, tant que la fin
 * est cachée, que la suite se lit dans le fil (l'écran du haut n'est pas tactile).
 */
@Composable
fun ApprovalParchment(
    request: Approval,
    agent: Agent?,
    assets: GameAssets,
    modifier: Modifier = Modifier,
    scroll: ScrollState? = null,
    overflowNote: Boolean = false,
) {
    val colors = Game.colors
    DialogBox(
        modifier.testTag("approval-parchment"),
        speaker = agent?.label,
        speakerColor = agent?.let { colors.agent(it) } ?: colors.line,
        portrait = { SpriteImage("ui.parchemin", assets, scale = GameScale.ICON, placeholderPixels = 32) },
        showNext = false,
    ) {
        if (scroll == null) {
            ApprovalDetails(request)
        } else {
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) { ApprovalDetails(request) }
            if (overflowNote && scroll.canScrollForward) {
                BasicText(stringResource(R.string.game_approval_more), style = GameText.body(26, colors.alertLine, weight = 600))
            }
        }
    }
}

@Composable
private fun ApprovalDetails(request: Approval) {
    val metrics = Game.metrics
    val colors = Game.colors
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        BasicText(
            stringResource(
                when (request.kind) {
                    ApprovalKind.COMMAND -> R.string.game_approval_command
                    ApprovalKind.FILE_CHANGE -> R.string.game_approval_file
                    ApprovalKind.OTHER -> R.string.game_approval_other
                },
            ),
            style = GameText.body(40, weight = 600, lineHeightPercent = 110),
        )
        CodeSheet(request.subject ?: stringResource(R.string.game_approval_no_subject), colors.paperDeep, sizePx = 40)
        request.workingDirectory?.let { BasicText(stringResource(R.string.game_approval_in, it), style = GameText.code(28, colors.muted)) }
        Field(stringResource(R.string.game_approval_why)) {
            BasicText(request.reason ?: stringResource(R.string.game_approval_no_reason), style = GameText.body(32))
        }
        // Les fichiers déjà montrés par leur avant / après ne sont pas répétés.
        val otherPaths = request.paths.filterNot { path -> request.changes.any { it.path == path } }
        if (otherPaths.isNotEmpty() || request.changes.isEmpty()) {
            Field(stringResource(R.string.game_approval_paths)) {
                if (otherPaths.isEmpty()) BasicText(stringResource(R.string.game_approval_no_paths), style = GameText.body(32))
                else otherPaths.forEach { BasicText(it, style = GameText.code(32)) }
            }
        }
        request.plan?.let { plan -> Field(stringResource(R.string.game_approval_plan)) { BasicText(plan, style = GameText.body(32)) } }
        request.changes.forEach { change ->
            Field(change.path, code = true) {
                change.before?.let {
                    BasicText(stringResource(R.string.game_approval_before), style = GameText.body(24, colors.muted))
                    CodeSheet(it, colors.diffRemoved, sizePx = 32)
                }
                BasicText(stringResource(R.string.game_approval_after), style = GameText.body(24, colors.muted))
                CodeSheet(change.after, colors.diffAdded, sizePx = 32)
            }
        }
    }
}

@Composable
private fun Field(title: String, code: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val metrics = Game.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
        BasicText(title, style = if (code) GameText.code(30, Game.colors.muted) else GameText.body(24, Game.colors.muted))
        content()
    }
}

/** Une commande, un chemin ou un extrait, en VT323 sur une bande de papier : passe à la ligne, jamais coupé. */
@Composable
private fun CodeSheet(text: String, background: Color, sizePx: Int) {
    val metrics = Game.metrics
    BasicText(
        text,
        style = GameText.code(sizePx),
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = metrics.dp(14), vertical = metrics.dp(6)),
    )
}

/**
 * Le menu de choix d'une validation : où elle en est, puis chaque décision possible avec sa touche.
 * Un toucher sur une option la choisit ; « Refuser et dicter une consigne » se maintient au doigt
 * comme L2. [standalone] : l'écran des validations, menu en colonne, où L2 refuse en dictant une
 * consigne et où l'absence d'autorisation pour la session est dite ; sinon, le carnet d'une maison,
 * menu en une rangée. [onDismiss] ferme une validation traitée.
 */
@Composable
fun ApprovalMenu(
    review: ApprovalReview,
    standalone: Boolean,
    onChoose: (ApprovalChoice) -> Unit,
    modifier: Modifier = Modifier,
    onSpeak: ((Boolean) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val metrics = Game.metrics
    val colors = Game.colors
    val choices = ApprovalMenuModel.choices(review, withInstruction = standalone && onSpeak != null)
    val speak by rememberUpdatedState(onSpeak)
    val choose by rememberUpdatedState(onChoose)
    val holdHint = stringResource(R.string.game_approval_hold)
    val labels = choices.map { choice ->
        Choice(
            stringResource(
                when {
                    choice.dictate -> R.string.game_approval_reject_instruction
                    choice.decision == ApprovalDecision.ONCE -> R.string.game_approval_once
                    choice.decision == ApprovalDecision.SESSION -> R.string.game_approval_session
                    else -> R.string.game_approval_reject
                },
            ),
            choice.key,
            detail = holdHint.takeIf { choice.dictate },
            enabled = choice.enabled,
        )
    }
    val touch: (Int) -> Modifier = { index ->
        val choice = choices[index]
        when {
            !choice.enabled -> Modifier
            choice.dictate -> Modifier.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    speak?.invoke(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        speak?.invoke(false)
                    }
                })
            }
            else -> Modifier.clickable { choose(choice) }
        }
    }
    val cursor = ApprovalMenuModel.cursor(choices)
    Column(modifier.testTag("approval-menu"), verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
        StatusLine(ApprovalMenuModel.status(review))
        if (standalone) ChoiceMenu(labels, cursor, Modifier.fillMaxWidth(), touch)
        else ChoiceRow(labels, cursor, touch)
        if (standalone && review.request != null && review.request?.optionFor(ApprovalDecision.SESSION) == null) {
            BasicText(stringResource(R.string.game_approval_no_session), style = GameText.body(24, colors.muted))
        }
        if (onDismiss != null && !review.pending && !review.sending && review.queue.isEmpty()) {
            PadActionHint(PadAction("←", stringResource(R.string.game_approval_close), onTap = onDismiss))
        }
    }
}

/**
 * Le menu de choix en une rangée, pour le carnet d'une maison : le fil garde la hauteur où lire le
 * parchemin. Même curseur, mêmes touches et mêmes touchers que [ChoiceMenu].
 */
@Composable
private fun ChoiceRow(choices: List<Choice>, selected: Int, touch: (Int) -> Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(Modifier.fillMaxWidth(), thick = true, contentPadding = PaddingValues(horizontal = metrics.dp(18), vertical = metrics.dp(14))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)), verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
            choices.forEachIndexed { index, choice ->
                val on = index == selected
                Row(
                    touch(index)
                        .semantics { this.selected = on }
                        .then(if (on) Modifier.background(colors.paperDeep) else Modifier)
                        .alpha(if (choice.enabled) 1f else 0.4f)
                        .padding(horizontal = metrics.dp(12), vertical = metrics.dp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
                ) {
                    if (on) PixelArrow(ArrowDirection.RIGHT, color = colors.focus, blink = true)
                    choice.key?.let { KeyChip(it) }
                    BasicText(choice.label, style = GameText.body(30), maxLines = 1)
                }
            }
        }
    }
}

/** « Attend ta validation » en étiquette ambre ; les autres constats, avec les mots de l'écran classique. */
@Composable
private fun StatusLine(status: ApprovalStatus) {
    val colors = Game.colors
    if (status == ApprovalStatus.WAITING) {
        StateTag(TagState.WAITING)
        return
    }
    val (text, color) = when (status) {
        ApprovalStatus.SENDING -> R.string.game_approval_sending to colors.muted
        ApprovalStatus.ACCEPTED -> R.string.game_approval_accepted to colors.focus
        ApprovalStatus.ACCEPTED_NEXT -> R.string.game_approval_accepted_next to colors.focus
        ApprovalStatus.UNKNOWN -> R.string.game_approval_unknown to colors.alertLine
        ApprovalStatus.NOT_SENT -> R.string.game_approval_not_sent to colors.error
        else -> R.string.game_approval_unavailable to colors.error
    }
    BasicText(stringResource(text), style = GameText.body(26, color, weight = 600))
}

/** Le « ! » ambre qui sautille au-dessus de l'habitant tant qu'il attend ta validation. */
@Composable
fun WaitingMarker(modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val clock = Game.clock
    Column(
        modifier.offset { IntOffset(0, if (Blink.bobUp(clock.value)) -2 * metrics.pixelPx else 0) }.testTag("approval-marker"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelFrame(colors = FrameDefaults.filled(colors.wait), contentPadding = PaddingValues(horizontal = metrics.dp(16), vertical = metrics.dp(2))) {
            BasicText("!", style = GameText.display(52, colors.waitInk))
        }
        PixelArrow(ArrowDirection.DOWN, rows = 2)
    }
}

/** Le défilement d'un parchemin à la manette : la croix par pas, le stick droit en continu. */
@Composable
internal fun PadScroll(state: ScrollState, input: ScrollInput, stepPx: Int = STEP_PX) {
    LaunchedEffect(state, input) { input.steps.collect { state.animateScrollBy(it * stepPx.toFloat()) } }
    LaunchedEffect(state, input) {
        input.velocity.collectLatest { velocity ->
            if (velocity == 0f) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                state.scrollBy(velocity * STICK_PX * (frame - last) / 1e9f)
                last = frame
            }
        }
    }
}

/** Un pas de la croix et la vitesse du stick, en pixels de l'écran. */
private const val STEP_PX = 160
private const val STICK_PX = 1600f
