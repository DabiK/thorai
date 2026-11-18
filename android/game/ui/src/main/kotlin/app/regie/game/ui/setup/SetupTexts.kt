package app.regie.game.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.regie.domain.model.BridgeHint
import app.regie.domain.model.SetupAction
import app.regie.domain.model.SetupCommands
import app.regie.domain.model.SetupReview
import app.regie.domain.model.SetupStep
import app.regie.domain.model.VoiceModel
import app.regie.game.ui.R
import java.util.Locale

/** Le nom court d'une étape, écrit sur son panneau et dans le bilan de la fin. */
@Composable
internal fun shortLabel(step: SetupStep): String = stringResource(
    when (step) {
        SetupStep.WELCOME -> R.string.game_setup_short_welcome
        SetupStep.TERMUX -> R.string.game_setup_short_termux
        SetupStep.BRIDGE -> R.string.game_setup_short_bridge
        SetupStep.VOICE -> R.string.game_setup_short_voice
        SetupStep.NOTIFICATIONS -> R.string.game_setup_short_notifications
        SetupStep.CONSOLE_MODE -> R.string.game_setup_short_console
        SetupStep.ANDROID_ACCESS -> R.string.game_setup_short_android
        SetupStep.AGENTS -> R.string.game_setup_short_agents
        SetupStep.FINISH -> R.string.game_setup_short_finish
    },
)

@Composable
internal fun stepTitle(step: SetupStep): String = stringResource(
    when (step) {
        SetupStep.WELCOME -> R.string.game_setup_title_welcome
        SetupStep.TERMUX -> R.string.game_setup_title_termux
        SetupStep.BRIDGE -> R.string.game_setup_title_bridge
        SetupStep.VOICE -> R.string.game_setup_title_voice
        SetupStep.NOTIFICATIONS -> R.string.game_setup_title_notifications
        SetupStep.CONSOLE_MODE -> R.string.game_setup_title_console
        SetupStep.ANDROID_ACCESS -> R.string.game_setup_title_android
        SetupStep.AGENTS -> R.string.game_setup_title_agents
        SetupStep.FINISH -> R.string.game_setup_title_finish
    },
)

/** « Étape 3 sur 9 », suivi de « facultative » pour une étape qui se passe. */
@Composable
internal fun counter(step: SetupStep): String = stringResource(
    if (step.optional) R.string.game_setup_counter_optional else R.string.game_setup_counter,
    step.ordinal + 1,
    SetupStep.entries.size,
)

/** L'explication d'une étape : deux phrases, puis la marche à suivre et une note. */
internal class Explanation(val lead: List<String>, val steps: List<String>, val note: String?)

/** Les ressources d'une explication : tableaux de phrases et note. */
private class ExplanationRes(val lead: Int, val steps: Int? = null, val note: Int? = null)

@Composable
internal fun explanation(step: SetupStep): Explanation {
    val res = when (step) {
        SetupStep.WELCOME -> ExplanationRes(R.array.game_setup_lead_welcome, note = R.string.game_setup_note_welcome)
        SetupStep.TERMUX -> ExplanationRes(R.array.game_setup_lead_termux, R.array.game_setup_steps_termux, R.string.game_setup_note_termux)
        SetupStep.BRIDGE -> ExplanationRes(R.array.game_setup_lead_bridge, R.array.game_setup_steps_bridge, R.string.game_setup_note_bridge)
        SetupStep.VOICE -> ExplanationRes(R.array.game_setup_lead_voice, R.array.game_setup_steps_voice, R.string.game_setup_note_voice)
        SetupStep.NOTIFICATIONS -> ExplanationRes(R.array.game_setup_lead_notifications, note = R.string.game_setup_note_notifications)
        SetupStep.CONSOLE_MODE -> ExplanationRes(R.array.game_setup_lead_console, R.array.game_setup_steps_console, R.string.game_setup_note_console)
        SetupStep.ANDROID_ACCESS -> ExplanationRes(R.array.game_setup_lead_android, R.array.game_setup_steps_android, R.string.game_setup_note_android)
        SetupStep.AGENTS -> ExplanationRes(R.array.game_setup_lead_agents, R.array.game_setup_steps_agents)
        SetupStep.FINISH -> ExplanationRes(R.array.game_setup_lead_finish, note = R.string.game_setup_note_finish)
    }
    return Explanation(
        stringArrayResource(res.lead).toList(),
        res.steps?.let { stringArrayResource(it).toList() }.orEmpty(),
        res.note?.let { stringResource(it) },
    )
}

/** Les commandes que montre l'étape (et que Y copie) : des données, montrées telles quelles. */
internal fun commands(step: SetupStep, appId: String): String? = when (step) {
    SetupStep.TERMUX -> SetupCommands.INSTALL
    SetupStep.ANDROID_ACCESS -> SetupCommands.pairing(appId)
    else -> null
}

/** Le libellé d'une action sur l'étape affichée. */
@Composable
internal fun actionLabel(action: SetupAction, review: SetupReview): String = stringResource(
    when (action) {
        SetupAction.NEXT -> if (review.step == SetupStep.WELCOME) R.string.game_setup_start else R.string.game_setup_continue
        SetupAction.BACK -> R.string.game_setup_back
        SetupAction.SKIP -> R.string.game_setup_skip
        // Sur la fin, refermer conclut le parcours : on arrive au titre ou au hameau.
        SetupAction.CLOSE -> if (review.step == SetupStep.FINISH) R.string.game_setup_done else R.string.game_setup_later
        SetupAction.OPEN_TERMUX_RELEASES -> R.string.game_setup_open_termux_releases
        SetupAction.COPY_INSTALL_COMMANDS, SetupAction.COPY_PAIRING_COMMANDS ->
            if (review.checks.termux) R.string.game_setup_copy_open_termux else R.string.game_setup_copy
        SetupAction.GRANT_RUN_COMMAND -> R.string.game_setup_grant_run_command
        SetupAction.RETRY_BRIDGE -> R.string.game_setup_retry_bridge
        SetupAction.GRANT_MICROPHONE -> R.string.game_setup_grant_microphone
        SetupAction.DOWNLOAD_VOICE -> if (review.checks.voiceModel is VoiceModel.Failed) R.string.game_setup_retry_download else R.string.game_setup_download
        SetupAction.GRANT_NOTIFICATIONS -> R.string.game_setup_grant_notifications
        SetupAction.OPEN_ACCESSIBILITY -> R.string.game_setup_open_accessibility
        SetupAction.OPEN_APP_INFO -> R.string.game_setup_open_app_info
        SetupAction.OPEN_DEVELOPER_OPTIONS -> R.string.game_setup_open_developer
        SetupAction.OPEN_AGENTS -> if (review.done) R.string.game_setup_manage_accounts else R.string.game_setup_connect_agents
        SetupAction.CREATE_SESSION -> R.string.game_setup_create_session
    },
)

/** Ce que A affiche quand l'étape attend sans rien demander (le pont démarre). */
@Composable
internal fun waitingLabel(step: SetupStep): String = stringResource(
    when (step) {
        SetupStep.BRIDGE -> R.string.game_bridge_connecting
        SetupStep.AGENTS -> R.string.game_setup_waiting_bridge
        else -> R.string.game_setup_waiting
    },
)

@Composable
internal fun bridgeHint(hint: BridgeHint): String = stringResource(
    when (hint) {
        BridgeHint.START_EXTERNAL -> R.string.game_setup_hint_external
        BridgeHint.GRANT_RUN_COMMAND -> R.string.game_setup_hint_grant
        BridgeHint.TERMUX_MISSING -> R.string.game_setup_hint_termux
        BridgeHint.TOKEN_MISSING, BridgeHint.TOKEN_REFUSED -> R.string.game_setup_hint_token
        BridgeHint.RESTARTING -> R.string.game_setup_hint_restarting
    },
)

internal fun megabytes(bytes: Long) = "${(bytes + 500_000) / 1_000_000} Mo"

internal fun gigabytes(bytes: Long) = String.format(Locale.FRANCE, "%.1f Go", bytes / 1e9)
