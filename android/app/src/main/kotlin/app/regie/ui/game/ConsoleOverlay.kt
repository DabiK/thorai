package app.regie.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.regie.R
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.console.CommandScroll
import app.regie.game.ui.console.GameCommandOverlay
import app.regie.game.ui.console.PixelConsoleBubble
import app.regie.game.ui.console.PixelFloatingCard
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameTheme
import app.regie.game.ui.theme.GameText
import app.regie.input.Button
import app.regie.overlay.OverlayContent
import app.regie.ui.ThreadScroll

/**
 * Ce que ThorAI pose par-dessus l'écran du haut quand une autre appli l'occupe (ADR 19), dans le
 * style du jeu : la dictée du carnet (ticket 46) avec la phrase à laquelle on répond, l'ordre vocal,
 * la carte volante et la bulle (ticket 49). Rien ne s'y touche : la manette passe par le service.
 */
@Composable
fun GameConsoleOverlay(content: OverlayContent, onButton: (Button) -> Unit, transcript: ThreadScroll, command: ThreadScroll) {
    GameTheme {
        val assets = rememberGameAssets()
        val metrics = Game.metrics
        when (content) {
            is OverlayContent.Dictation -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                content.replyingTo?.let {
                    PixelFrame(Modifier.widthIn(max = metrics.dp(PANEL_WIDTH)), contentPadding = PaddingValues(horizontal = metrics.dp(24), vertical = metrics.dp(12))) {
                        BasicText("${stringResource(R.string.card_replying_to)} : $it", style = GameText.body(28, Game.colors.muted),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                PixelFrame(Modifier.size(metrics.dp(PANEL_WIDTH), metrics.dp(PANEL_HEIGHT)).testTag("console-dictation"), thick = true) {
                    Box {
                        // Hors de ThorAI, rien ne se touche : la voix ne s'ouvre pas, L2 se maintient à la manette.
                        GameDictation(content.review, content.session, onButton, {}, {}, transcript, compact = true)
                    }
                }
            }
            is OverlayContent.Command -> GameCommandOverlay(content.review, scroll = CommandScroll(command.steps, command.velocity))
            is OverlayContent.Card -> PixelFloatingCard(content.card, assets, keys = content.keys)
            is OverlayContent.Bubble -> PixelConsoleBubble(content.session, content.lastSentence)
        }
    }
}

private const val PANEL_WIDTH = 1400
private const val PANEL_HEIGHT = 620
