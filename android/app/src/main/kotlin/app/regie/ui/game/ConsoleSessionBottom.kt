package app.regie.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.ApprovalId
import app.regie.domain.model.LiveSession
import app.regie.domain.model.Neighbours
import app.regie.domain.model.Session
import app.regie.domain.model.SessionSettingsReview
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.console.ConsoleTowerHeader
import app.regie.game.ui.theme.GameTheme
import app.regie.session.SessionControlState
import app.regie.ui.SessionCallbacks
import app.regie.ui.ThreadScroll

/**
 * La session console quand une autre appli occupe le haut (ticket 49, maquette 12) : la tour en
 * en-tête, puis le carnet de la session du ticket 46 en mode console. Le carnet garde tout : fil,
 * avis (« Rien entendu. Aucun message envoyé. », envoi non parti, interruption refusée), brouillon
 * au clavier, validation et touches. La tour ne fait que situer la scène.
 */
@Composable
fun ConsoleSessionBottom(
    session: Session,
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
) {
    val assets = rememberGameAssets()
    Column(Modifier.fillMaxSize()) {
        GameTheme { ConsoleTowerHeader(live?.session ?: session, assets, Modifier.fillMaxWidth()) }
        Box(Modifier.weight(1f)) {
            GameSessionBottom(live, gone, neighbours, control, draft, settings, scroll, callbacks, approval, onDecision,
                onDismissApproval, onSelectSetting, console = true)
        }
    }
}
