package app.regie.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.regie.R
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.Delivery
import app.regie.domain.model.DictationReview
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Neighbours
import app.regie.domain.model.Session
import app.regie.domain.model.SessionSettingsReview
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.house.AgentBlock
import app.regie.game.ui.house.AgentSpan
import app.regie.game.ui.house.AgentTextFormat
import app.regie.game.ui.house.DictationCallbacks
import app.regie.game.ui.house.HouseBottomScreen
import app.regie.game.ui.house.HouseCallbacks
import app.regie.game.ui.house.HouseDictationScreen
import app.regie.game.ui.house.HouseNotice
import app.regie.game.ui.house.HouseTopScreen
import app.regie.game.ui.house.NoticeTone
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.house.rememberInteriorScenes
import app.regie.game.ui.theme.GameTheme
import app.regie.input.Button
import app.regie.input.Trigger
import app.regie.session.SessionControlState
import app.regie.session.SessionNotice
import app.regie.ui.formatAge
import app.regie.ui.markdown.Markdown
import app.regie.ui.markdown.MdBlock
import app.regie.ui.screens.InlineApproval
import app.regie.ui.screens.SessionCallbacks
import app.regie.ui.screens.ThreadScroll
import app.regie.ui.screens.recognizedTerms
import app.regie.ui.screens.rememberNow
import app.regie.ui.screens.settingsHint

/*
 * Branchement de la maison (ticket 46) : les écrans du jeu reçoivent l'état existant et les rappels
 * des contrôleurs, sans règle nouvelle. Ils remplacent SessionScreens, ThreadLayout,
 * SessionSettingsScreen et DictationScreens aux points d'appel ; ces fichiers partent au ticket 51.
 */

/** Le markdown des agents, analysé comme dans l'app (ADR 07), pour la vue jeu. */
val MarkdownFormat = AgentTextFormat { text ->
    Markdown.parse(text).map { block ->
        when (block) {
            is MdBlock.CodeBlock -> AgentBlock.Code(block.code)
            is MdBlock.Text -> AgentBlock.Text(
                block.spans.map { AgentSpan(it.text, it.bold, it.italic, it.code) },
                block.heading, block.marker, block.depth, block.quote,
            )
        }
    }
}

/** Les rappels d'une session, vus par la maison. */
fun SessionCallbacks.house(select: (Int) -> Unit): HouseCallbacks = object : HouseCallbacks {
    override fun onKey(key: PadKey) = onButton(Button.valueOf(key.name))
    override fun onSpeak(held: Boolean) = onTrigger(Trigger.L2, held)
    override fun onOpenSettings(row: String?) = if (row == null) onOptions() else onSettingRow(row)
    override fun onSelectSetting(index: Int) = select(index)
    override fun onDraftChange(text: String) = this@house.onDraftChange(text)
    override fun onSend() = this@house.onSend()
}

private fun ThreadScroll.input() = ScrollInput(steps, velocity)

/**
 * L'écran du haut d'une session : la maison. Un seul écran : le carnet (ou la dictée, [dictationPanel])
 * prend la droite, la validation finit le fil comme avant.
 */
@Composable
fun GameSessionTop(
    live: LiveSession?,
    gone: Boolean,
    neighbours: Neighbours?,
    control: SessionControlState,
    draft: String,
    settings: SessionSettingsReview,
    singleScreen: Boolean,
    scroll: ThreadScroll,
    callbacks: SessionCallbacks,
    approval: ApprovalReview,
    onDecision: (ApprovalId, ApprovalDecision) -> Unit,
    onNextApproval: () -> Unit,
    onDismissApproval: () -> Unit,
    onSelectSetting: (Int) -> Unit,
    dictation: DictationReview,
    dictationPanel: (@Composable () -> Unit)?,
) {
    val now by rememberNow()
    // Une dictée ou la table en modification capte A, B et X : la carte garde ses touchers, sans puces.
    val shortcuts = !settings.visible && !dictation.visible
    val card: (@Composable () -> Unit)? = if (approval.request != null) ({
        InlineApproval(approval, onDecision, onNextApproval, shortcutsEnabled = shortcuts, onDismiss = onDismissApproval)
    }) else null
    GameTheme {
        HouseTopScreen(
            live, gone, rememberGameAssets(), rememberInteriorScenes(),
            ageLabel = live?.session?.let { formatAge(now - it.updatedAt) },
            dictation = dictation,
            format = MarkdownFormat,
            approval = card,
            single = if (!singleScreen) null else ({
                dictationPanel?.invoke() ?: SessionCarnet(
                    live, gone, neighbours, control, draft, settings, scroll, callbacks, approval, onDecision, onNextApproval,
                    onDismissApproval, onSelectSetting, compact = true, threadApproval = card,
                )
            }),
        )
    }
}

/** L'écran du bas d'une session : le fil brut, la table des réglages et les touches. */
@Composable
fun GameSessionBottom(
    live: LiveSession?,
    gone: Boolean,
    neighbours: Neighbours?,
    control: SessionControlState,
    draft: String,
    settings: SessionSettingsReview,
    scroll: ThreadScroll,
    callbacks: SessionCallbacks,
    approval: ApprovalReview,
    onDecision: (ApprovalId, ApprovalDecision) -> Unit,
    onNextApproval: () -> Unit,
    onDismissApproval: () -> Unit,
    onSelectSetting: (Int) -> Unit,
) {
    GameTheme {
        SessionCarnet(live, gone, neighbours, control, draft, settings, scroll, callbacks, approval, onDecision, onNextApproval, onDismissApproval, onSelectSetting)
    }
}

@Composable
private fun SessionCarnet(
    live: LiveSession?,
    gone: Boolean,
    neighbours: Neighbours?,
    control: SessionControlState,
    draft: String,
    settings: SessionSettingsReview,
    scroll: ThreadScroll,
    callbacks: SessionCallbacks,
    approval: ApprovalReview,
    onDecision: (ApprovalId, ApprovalDecision) -> Unit,
    onNextApproval: () -> Unit,
    onDismissApproval: () -> Unit,
    onSelectSetting: (Int) -> Unit,
    compact: Boolean = false,
    threadApproval: (@Composable () -> Unit)? = null,
) {
    val notice = control.notice
    val house = remember(callbacks, onSelectSetting) { callbacks.house(onSelectSetting) }
    val input = remember(scroll) { scroll.input() }
    HouseBottomScreen(
        live, gone, neighbours, settings,
        settingsHint = if (settings.visible) settingsHint(settings) else null,
        draft = draft,
        notice = notice?.let { houseNotice(it) },
        onNoticeSeen = { notice?.let(callbacks::onNoticeSeen) },
        scroll = input,
        callbacks = house,
        format = MarkdownFormat,
        approval = if (approval.request != null) ({
            InlineApproval(approval, onDecision, onNextApproval, details = false, onDismiss = onDismissApproval)
        }) else null,
        approvalQueued = approval.queue.any { it.id != approval.request?.id },
        threadApproval = threadApproval,
        compact = compact,
    )
}

/** La dictée L2 dans le carnet : transcription en direct, termes du dépôt, niveau du micro, A B X Y. */
@Composable
fun GameDictation(
    state: DictationReview,
    session: Session?,
    onButton: (Button) -> Unit,
    onHeld: (Boolean) -> Unit,
    onSettings: () -> Unit,
    scroll: ThreadScroll,
    compact: Boolean = false,
) {
    val input = remember(scroll) { scroll.input() }
    val terms = remember(state.text, state.vocabulary) { recognizedTerms(state.text, state.vocabulary).map { it.range } }
    val callbacks = remember(onButton, onHeld, onSettings) {
        object : DictationCallbacks {
            override fun onKey(key: PadKey) = onButton(Button.valueOf(key.name))
            override fun onSpeak(held: Boolean) = onHeld(held)
            override fun onVoiceSettings() = onSettings()
        }
    }
    GameTheme { HouseDictationScreen(state, session, terms, input, callbacks, compact = compact) }
}

/** Les avis de la session, avec les mots de l'app : un avis final s'efface seul, « Envoi… » attend. */
@Composable
private fun houseNotice(notice: SessionNotice): HouseNotice = when (notice) {
    SessionNotice.Sending -> HouseNotice(stringResource(R.string.notice_sending), NoticeTone.MUTED, lasting = true)
    SessionNotice.Interrupting -> HouseNotice(stringResource(R.string.notice_interrupting), NoticeTone.MUTED, lasting = true)
    SessionNotice.NothingHeard -> HouseNotice(stringResource(R.string.notice_nothing_heard), NoticeTone.WARN)
    is SessionNotice.Interrupted ->
        if (notice.heard) HouseNotice(stringResource(R.string.notice_interrupted), NoticeTone.NORMAL)
        else HouseNotice(stringResource(R.string.notice_interrupt_failed), NoticeTone.ERROR)
    is SessionNotice.Delivered -> when (notice.delivery) {
        Delivery.PROMPT -> HouseNotice(stringResource(R.string.delivery_prompt), NoticeTone.NORMAL)
        Delivery.STEERING -> HouseNotice(stringResource(R.string.delivery_steering), NoticeTone.NORMAL)
        Delivery.QUEUED -> HouseNotice(stringResource(R.string.delivery_queued), NoticeTone.WARN)
        Delivery.CANCELLED -> HouseNotice(stringResource(R.string.delivery_cancelled), NoticeTone.WARN)
        Delivery.UNKNOWN -> HouseNotice(stringResource(R.string.delivery_unknown), NoticeTone.WARN)
        Delivery.NOT_SENT -> HouseNotice(stringResource(R.string.delivery_not_sent), NoticeTone.ERROR)
    }
}
