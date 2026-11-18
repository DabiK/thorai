package app.regie.ui.game

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.regie.game.ui.approval.ApprovalMenu
import app.regie.game.ui.approval.ApprovalParchment
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
import app.regie.ui.SessionCallbacks
import app.regie.ui.ThreadScroll
import app.regie.ui.recognizedTerms
import app.regie.ui.rememberNow
import app.regie.ui.settingsHint

/*
 * Branchement de la maison (ticket 46) : les écrans du jeu reçoivent l'état existant et les rappels
 * des contrôleurs, sans règle nouvelle. Les écrans classiques de la session, des réglages et de la
 * dictée sont retirés depuis le ticket 51.
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
    override fun onNextApproval() = this@house.onNextApproval()
}

private fun ThreadScroll.input() = ScrollInput(steps, velocity)

/**
 * L'écran du haut d'une session : la maison. Un seul écran : le carnet (et sa dictée, [dictationPanel])
 * prend la droite. Une validation (ticket 47) : l'habitant tend son parchemin, borné, dans la pièce ;
 * le parchemin entier finit le fil du carnet et le menu de choix prend la place des touches.
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
    onDismissApproval: () -> Unit,
    onSelectSetting: (Int) -> Unit,
    dictation: DictationReview,
    dictationPanel: (@Composable () -> Unit)?,
) {
    val now by rememberNow()
    val assets = rememberGameAssets()
    // Le parchemin tendu dans la pièce, tant que la validation attend ; borné, il renvoie au fil pour la suite.
    val request = approval.request?.takeIf { approval.pending || approval.sending }
    val card: (@Composable () -> Unit)? = if (request != null) ({
        val scroll = rememberScrollState()
        LaunchedEffect(request.id) { scroll.scrollTo(0) }
        ApprovalParchment(request, live?.session?.agent, assets, scroll = scroll, overflowNote = true)
    }) else null
    GameTheme {
        HouseTopScreen(
            live, gone, assets, rememberInteriorScenes(),
            ageLabel = live?.session?.let { formatAge(now - it.updatedAt) },
            dictation = dictation,
            format = MarkdownFormat,
            approval = card,
            // Un seul écran : le fil reste, la dictée prend le panneau du bas (ADR 17).
            single = if (!singleScreen) null else ({
                SessionCarnet(
                    live, gone, neighbours, control, draft, settings, scroll, callbacks, approval, onDecision,
                    onDismissApproval, onSelectSetting, compact = true, dictation = dictationPanel,
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
    onDismissApproval: () -> Unit,
    onSelectSetting: (Int) -> Unit,
    /** Mode console : une autre appli occupe le haut. */
    console: Boolean = false,
) {
    GameTheme {
        SessionCarnet(live, gone, neighbours, control, draft, settings, scroll, callbacks, approval, onDecision, onDismissApproval, onSelectSetting,
            console = console)
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
    onDismissApproval: () -> Unit,
    onSelectSetting: (Int) -> Unit,
    compact: Boolean = false,
    console: Boolean = false,
    dictation: (@Composable () -> Unit)? = null,
) {
    val notice = control.notice
    val assets = rememberGameAssets()
    val request = approval.request
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
        // Le menu de choix prend la place des touches ; R1 vers la suivante passe par la barre des voisines.
        approval = if (request != null) ({
            ApprovalMenu(approval, standalone = false, onChoose = { onDecision(request.id, it.decision) }, onDismiss = onDismissApproval,
                shortcuts = !settings.visible)
        }) else null,
        approvalQueued = approval.queue.any { it.id != request?.id },
        // Le parchemin entier finit le fil : la croix et le stick le lisent jusqu'au bout, l'écran du haut n'étant pas tactile.
        threadApproval = if (request != null && (approval.pending || approval.sending)) ({
            ApprovalParchment(request, live?.session?.agent, assets)
        }) else null,
        compact = compact,
        console = console,
        dictation = dictation,
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
