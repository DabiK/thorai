package app.regie.game.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.ApprovalDecision
import app.regie.domain.model.FloatingCard
import app.regie.domain.model.FloatingKind
import app.regie.domain.model.Session
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.Bubble
import app.regie.game.ui.kit.BubbleTail
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyHint
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.kit.tagLabel
import app.regie.game.ui.menu.Portrait
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/** Les touches d'une carte volante (ADR 19) : seulement les choix que l'agent offre. */
@Composable
fun cardHints(card: FloatingCard): List<KeyHintSpec> {
    val approval = card.approval
    return buildList {
        if (approval?.optionFor(ApprovalDecision.ONCE) != null) add(KeyHintSpec("A", stringResource(R.string.game_card_allow_once)))
        add(KeyHintSpec("B", stringResource(R.string.game_hint_close)))
        if (approval?.optionFor(ApprovalDecision.REJECT) != null) {
            add(KeyHintSpec("L2", stringResource(R.string.game_card_refuse_dictating)))
        } else if (approval == null) {
            add(KeyHintSpec("L2", stringResource(R.string.game_card_answer)))
        }
    }
}

/**
 * La carte volante en pixel (maquette 12) : l'habitant qui parle, le nom de l'agent à sa couleur,
 * le projet et ce qu'il attend, sa phrase telle quelle, puis les touches. Une question ou une
 * validation passe au cadre d'alerte. Les touches n'apparaissent qu'une fois que la carte les prend.
 */
@Composable
fun PixelFloatingCard(card: FloatingCard, assets: GameAssets, modifier: Modifier = Modifier, keys: Boolean = true) {
    val metrics = Game.metrics
    val colors = Game.colors
    PixelFrame(
        modifier.width(metrics.dp(860)).testTag("floating-card"),
        thick = true,
        colors = if (card.kind == FloatingKind.MESSAGE) FrameDefaults.panel() else FrameDefaults.alert(),
        contentPadding = PaddingValues(horizontal = metrics.dp(32), vertical = metrics.dp(28)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(26))) {
            Portrait(card.agent, assets, pose = "parle")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
                    BasicText(card.agent.label, style = GameText.display(44, colors.agent(card.agent)), maxLines = 1)
                    BasicText(card.project, Modifier.weight(1f, fill = false), style = GameText.body(26, colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                when (card.kind) {
                    FloatingKind.QUESTION -> StateTag(TagState.WAITING, label = stringResource(R.string.game_card_awaiting_answer))
                    FloatingKind.APPROVAL -> StateTag(TagState.WAITING)
                    FloatingKind.MESSAGE -> Unit
                }
                if (card.text.isNotEmpty()) {
                    BasicText(
                        card.text,
                        style = if (card.kind == FloatingKind.APPROVAL) GameText.code(36) else GameText.body(34),
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(Modifier.alpha(if (keys) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(metrics.dp(28))) {
                    cardHints(card).forEach { KeyHint(it, textSizePx = 26) }
                }
            }
        }
    }
}

/**
 * La bulle discrète du mode console : l'état de la session affichée dans Régie et la dernière phrase
 * de l'agent, tant qu'il travaille (ADR 19).
 */
@Composable
fun PixelConsoleBubble(session: Session, lastSentence: String?, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    Bubble(modifier.widthIn(max = metrics.dp(1200)).testTag("console-bubble"), tail = BubbleTail.NONE) {
        val state = TagState.of(session.status)
        StateTag(state, label = tagLabel(state))
        lastSentence?.let { BasicText(it, style = GameText.body(28, Game.colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
