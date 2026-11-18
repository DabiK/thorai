package app.regie.game.ui.approval

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.domain.model.Agent
import app.regie.domain.model.ApprovalKind
import app.regie.domain.model.Session
import app.regie.domain.usecase.ApprovalReview
import app.regie.game.engine.village.InhabitantKind
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.house.HouseLook
import app.regie.game.ui.house.HouseScene
import app.regie.game.ui.house.InteriorScene
import app.regie.game.ui.house.InteriorSize
import app.regie.game.ui.house.PadAction
import app.regie.game.ui.house.PadActionBar
import app.regie.game.ui.house.PadKey
import app.regie.game.ui.house.Pose
import app.regie.game.ui.house.ScrollInput
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.Sprite
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText

/**
 * L'écran du haut d'une validation ouverte depuis le hameau (R1) ou une notification : la pièce de la
 * session qui la demande, l'habitant en attente qui tend son parchemin, le « ! » au-dessus de lui ;
 * à gauche, le parchemin entier, que la croix et le stick font défiler ([scroll]).
 *
 * [single] : un seul écran. La pièce passe derrière le parchemin, le carnet ([single]) prend la droite.
 */
@Composable
fun ApprovalScene(
    review: ApprovalReview,
    session: Session?,
    assets: GameAssets,
    scenes: Map<String, InteriorScene>,
    scroll: ScrollInput,
    modifier: Modifier = Modifier,
    single: (@Composable () -> Unit)? = null,
) {
    val metrics = Game.metrics
    val request = review.request
    val pose = when {
        review.pending || review.sending -> Pose.WAITING
        session != null -> HouseLook.pose(session)
        else -> Pose.IDLE
    }
    val waiting = request != null && (review.pending || review.sending)
    Row(modifier.fillMaxSize().background(InteriorSize.NIGHT)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (single != null) {
                Room(session?.agent, pose, waiting, assets, scenes, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Game.colors.scrim))
            }
            val parchment = rememberScrollState()
            LaunchedEffect(request?.id) { parchment.scrollTo(0) }
            parchment.followPad(scroll)
            if (request == null) {
                BasicText(stringResource(R.string.game_approval_done), style = GameText.body(32, Game.colors.paper), modifier = Modifier.align(Alignment.Center))
            } else {
                Row(
                    Modifier.fillMaxSize().padding(start = metrics.dp(48), end = metrics.dp(if (single != null) 32 else 0), top = metrics.dp(40), bottom = metrics.dp(40)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ApprovalParchment(request, session?.agent, assets, Modifier.weight(1f), scroll = parchment)
                    // Le parchemin part des mains de l'habitant.
                    if (single == null) PixelArrow(ArrowDirection.RIGHT, rows = 3)
                }
            }
        }
        if (single != null) {
            Box(Modifier.width(metrics.dp(SINGLE_NOTEBOOK_PX)).fillMaxHeight()) { single() }
        } else {
            Room(session?.agent, pose, waiting, assets, scenes, Modifier.width(metrics.dp(ROOM_PX)).fillMaxHeight())
        }
    }
}

/** La pièce de la session, vue autour de l'habitant ; le coursier à l'entrée, le « ! » s'il attend. */
@Composable
internal fun Room(
    agent: Agent?,
    pose: Pose,
    waiting: Boolean,
    assets: GameAssets,
    scenes: Map<String, InteriorScene>,
    modifier: Modifier,
    extra: @Composable BoxScope.() -> Unit = {},
) {
    val kind = agent?.let { InhabitantKind.of(it) } ?: InhabitantKind.FALLBACK
    val background = HouseLook.interior(kind)
    val scene = scenes[background] ?: InteriorScene.DEFAULT
    val inhabitant = HouseLook.inhabitant(kind, pose)
    Box(modifier) {
        HouseScene(
            assets,
            background,
            sprites = listOf(Sprite(inhabitant, scene.inhabitant), Sprite(HouseLook.courier(false), scene.entrance)),
            focus = scene.inhabitant,
            modifier = Modifier.fillMaxSize().testTag("approval-room").semantics { contentDescription = inhabitant },
        ) { projection ->
            if (waiting) {
                WaitingMarker(Modifier.anchorBottomCenter { projection.toScreen(ArtPoint(scene.inhabitant.x, scene.inhabitant.y - HEAD_ART)) })
            }
        }
        extra()
    }
}

/**
 * Le carnet du bas d'une validation : la session qui la demande, le menu de choix (A, B, X et L2 tenu
 * pour refuser en dictant une consigne), les autres validations de la file à toucher, puis L1 Revenir
 * et R1 Voir la suivante.
 */
@Composable
fun ApprovalNotebook(
    review: ApprovalReview,
    sessions: List<Session>,
    callbacks: ApprovalCallbacks,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val metrics = Game.metrics
    val colors = Game.colors
    val request = review.request
    val session = sessions.firstOrNull { it.id == request?.sessionId }
    Column(modifier.fillMaxSize().background(colors.paper)) {
        Row(
            Modifier.fillMaxWidth().height(metrics.dp(88)).background(colors.paperDeep).padding(horizontal = metrics.dp(28)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(18)),
        ) {
            BasicText(stringResource(R.string.game_approval_title), style = GameText.display(52))
            session?.let {
                StateTag(TagState.WAITING, Modifier.weight(1f, fill = false), label = "${it.agent.label} · ${it.project}")
            }
            Box(Modifier.weight(1f))
            BasicText(stringResource(R.string.game_approval_pending, review.queue.size), style = GameText.body(24, colors.muted), maxLines = 1)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = metrics.dp(24), vertical = metrics.dp(16)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(18)),
        ) {
            ApprovalMenu(
                review, standalone = true,
                onChoose = { callbacks.onKey(ApprovalMenuModel.key(it.decision)) },
                onSpeak = callbacks::onSpeak,
            )
            OtherApprovals(review, sessions, callbacks)
        }
        PadActionBar(
            listOf(
                PadAction(listOf("↑↓"), stringResource(R.string.game_approval_scroll)),
                PadAction("L1", stringResource(R.string.game_approval_back)) { callbacks.onKey(PadKey.L1) },
                PadAction("R1", stringResource(R.string.game_approval_next)) { callbacks.onKey(PadKey.R1) },
            ),
            compact = compact,
        )
    }
}

/** Les autres validations de la file : projet et commande, un toucher l'ouvre. */
@Composable
private fun OtherApprovals(review: ApprovalReview, sessions: List<Session>, callbacks: ApprovalCallbacks) {
    val metrics = Game.metrics
    val colors = Game.colors
    val others = review.queue.filter { it.id != review.request?.id }
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
        BasicText(stringResource(R.string.game_approval_others), style = GameText.body(24, colors.muted))
        if (others.isEmpty()) BasicText(stringResource(R.string.game_approval_no_others), style = GameText.body(26, colors.muted))
        others.forEach { other ->
            val project = sessions.firstOrNull { it.id == other.sessionId }?.project
            val subject = other.subject ?: stringResource(
                when (other.kind) {
                    ApprovalKind.COMMAND -> R.string.game_approval_command
                    ApprovalKind.FILE_CHANGE -> R.string.game_approval_file
                    ApprovalKind.OTHER -> R.string.game_approval_other
                },
            )
            BasicText(
                listOfNotNull(project, subject).joinToString(" · "),
                style = GameText.code(30),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { callbacks.onSelect(other.id) }.padding(vertical = metrics.dp(6)),
            )
        }
    }
}

/** Hauteur d'un personnage et un pixel d'air : le « ! » part de là. */
internal const val HEAD_ART = 33
private const val ROOM_PX = 720
private const val SINGLE_NOTEBOOK_PX = 760
