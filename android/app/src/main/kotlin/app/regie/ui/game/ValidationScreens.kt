package app.regie.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.Overview
import app.regie.domain.usecase.ApprovalReview
import app.regie.domain.usecase.DiffReview
import app.regie.game.ui.approval.ApprovalCallbacks
import app.regie.game.ui.approval.ApprovalNotebook
import app.regie.game.ui.approval.ApprovalScene
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.diff.CorkBoardScreen
import app.regie.game.ui.diff.DiffCallbacks
import app.regie.game.ui.diff.DiffNotebook
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.house.rememberInteriorScenes
import app.regie.game.ui.theme.GameTheme
import app.regie.input.Button
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/*
 * Branchement de la validation et des modifications (ticket 47) : le parchemin, le menu de choix et le
 * tableau de liège reçoivent l'état existant (`approvalReview`, `diffReview`) et rendent la main aux
 * contrôleurs, sans règle nouvelle. Ils remplacent ApprovalScreens et DiffScreens aux points d'appel ;
 * ces fichiers partent au ticket 51.
 */

/** Les rappels de l'écran des validations : les touches vont au routeur, comme la manette. */
fun approvalCallbacks(onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onSelect: (ApprovalId) -> Unit): ApprovalCallbacks =
    object : ApprovalCallbacks {
        override fun onKey(key: PadKey) = onButton(Button.valueOf(key.name))
        override fun onSpeak(held: Boolean) = onHeld(held)
        override fun onSelect(id: ApprovalId) = onSelect(id)
    }

/** L'écran du haut d'une validation (R1 depuis le hameau, notification) ; un seul écran : le carnet à droite. */
@Composable
fun GameApprovalTop(
    review: ApprovalReview,
    overview: Overview,
    singleScreen: Boolean,
    steps: SharedFlow<Int>,
    velocity: StateFlow<Float>,
    onButton: (Button) -> Unit,
    onHeld: (Boolean) -> Unit,
    onSelect: (ApprovalId) -> Unit,
) {
    val input = remember(steps, velocity) { ScrollInput(steps, velocity) }
    val session = overview.sessions.firstOrNull { it.id == review.request?.sessionId }
    GameTheme {
        ApprovalScene(
            review, session, rememberGameAssets(), rememberInteriorScenes(), input,
            single = if (!singleScreen) null else ({
                ApprovalNotebook(review, overview.sessions, approvalCallbacks(onButton, onHeld, onSelect), compact = true)
            }),
        )
    }
}

/** L'écran du bas d'une validation : le menu de choix et la file. */
@Composable
fun GameApprovalBottom(review: ApprovalReview, overview: Overview, onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onSelect: (ApprovalId) -> Unit) {
    GameTheme { ApprovalNotebook(review, overview.sessions, approvalCallbacks(onButton, onHeld, onSelect)) }
}

/** Les rappels des modifications : les touches vont au routeur, un toucher sur un fichier à `ReviewDiff`. */
fun diffCallbacks(onButton: (Button) -> Unit, onHeld: (Boolean) -> Unit, onFile: (Int) -> Unit): DiffCallbacks = object : DiffCallbacks {
    override fun onKey(key: PadKey) = onButton(Button.valueOf(key.name))
    override fun onSpeak(held: Boolean) = onHeld(held)
    override fun onFile(index: Int) = onFile(index)
}

/**
 * Les modifications : le tableau de liège en haut, le carnet en bas ; un seul écran, le carnet à
 * droite du tableau. Même contrat que `DiffScreen` : [bottom] dit l'écran qui affiche.
 */
@Composable
fun GameDiff(
    review: DiffReview,
    overview: Overview,
    bottom: Boolean,
    single: Boolean,
    velocity: StateFlow<Float>,
    onButton: (Button) -> Unit,
    onFile: (Int) -> Unit,
    onHeld: (Boolean) -> Unit,
    steps: Flow<Int> = emptyFlow(),
) {
    val callbacks = remember(onButton, onHeld, onFile) { diffCallbacks(onButton, onHeld, onFile) }
    GameTheme {
        if (bottom) {
            DiffNotebook(review, callbacks)
        } else {
            val input = remember(steps, velocity) { ScrollInput(steps, velocity) }
            CorkBoardScreen(
                review, overview.sessions.firstOrNull { it.id == review.sessionId }, rememberGameAssets(), rememberInteriorScenes(), input,
                format = MarkdownFormat,
                single = if (!single) null else ({ DiffNotebook(review, callbacks, compact = true) }),
            )
        }
    }
}
