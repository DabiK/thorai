package app.regie.game.ui.chantier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.regie.game.engine.village.Construction
import app.regie.game.engine.village.ConstructionStage
import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.village.RoofPalette
import app.regie.game.engine.village.Site
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.SignPanel
import app.regie.game.ui.kit.Toast
import app.regie.game.ui.render.ArtPoint
import app.regie.game.ui.render.GroundPainter
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.render.WorldProjection
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameScale
import app.regie.game.ui.theme.GameText
import app.regie.game.ui.village.DefaultVillageLayer
import app.regie.game.ui.village.VillageLayer

/**
 * Le calque du hameau pendant un chantier (maquettes 10 et 11) : celui de la vue d'ensemble
 * ([DefaultVillageLayer]), le panneau planté devant le chantier, les étincelles de la maison finie, le
 * bandeau d'une nouvelle rue et, en haut à droite, le panneau « Bâtir une maison » et ses quatre étapes.
 *
 * - [construction] : le chantier suivi par l'app, `null` pendant une fiche sans projet choisi.
 * - [panel] : montrer le panneau des étapes (fiche ouverte, ou chantier en cours).
 */
fun constructionLayer(construction: Construction?, panel: Boolean): VillageLayer = { scope ->
    DefaultVillageLayer(this, scope)
    val site by remember(scope.scene) { derivedStateOf { scope.scene.village.site } }
    site?.let { SiteSign(it, scope.projection) }
    site?.takeIf { it.stage == ConstructionStage.FINISHED }?.let { Sparkles(it, scope.assets, scope.projection) }
    if (panel) {
        ConstructionPanel(construction, scope.assets, Modifier.align(Alignment.TopEnd).padding(Game.metrics.dp(40)))
    }
    if (construction?.newProject == true && construction.stage != null && !construction.failed && construction.stage != ConstructionStage.FINISHED) {
        StreetBanner(construction.project, scope.assets, Modifier.align(Alignment.TopStart).padding(Game.metrics.dp(40)))
    }
}

/** Le panneau du chantier : le projet, et « Chantier » ou « Terrain choisi » pendant la fiche. */
@Composable
private fun SiteSign(site: Site, projection: WorldProjection) {
    // Une maison finie a son propre panneau ; un échec avant la création a libéré le terrain.
    if (site.stage == ConstructionStage.FINISHED) return
    val title = site.project.ifEmpty { stringResource(R.string.chantier_new_project) }
    val subtitle = stringResource(if (site.stage == null) R.string.chantier_target else R.string.chantier_sign)
    val tile = GroundPainter.TILE
    val top = ArtPoint((site.footprint.left * tile + site.footprint.width * tile / 2), site.footprint.top * tile - SIGN_CLEARANCE)
    SignPanel(
        title,
        Modifier.anchorBottomCenter { projection.toScreen(top) }.testTag("chantier-panneau"),
        subtitle = subtitle,
        bob = false,
    )
}

/** Les étincelles au-dessus de la maison finie, à l'échelle du monde. */
@Composable
private fun Sparkles(site: Site, assets: GameAssets, projection: WorldProjection) {
    val tile = GroundPainter.TILE
    val y = site.footprint.top * tile - SPARKLE_RISE
    listOf(1, 3, 5).forEachIndexed { index, column ->
        val point = ArtPoint(site.footprint.left * tile + column * tile, y - (index % 2) * tile / 2)
        SpriteImage(SPARKLE, assets, Modifier.anchorBottomCenter { projection.toScreen(point) }.testTag("chantier-etincelle"), placeholderPixels = 0)
    }
}

/**
 * « Bâtir une maison » et ses quatre étapes (fiche, session créée, agent lancé, première réponse), en
 * images du chantier. L'étape atteinte est en gras, les suivantes pâlies. Un échec arrête l'étape et
 * donne le message du pont ou de l'agent, tel quel.
 */
@Composable
fun ConstructionPanel(construction: Construction?, assets: GameAssets, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    val reached = construction?.stage?.ordinal ?: -1
    PixelFrame(
        modifier.widthIn(max = metrics.dp(PANEL_WIDTH)).testTag("chantier-etapes"),
        thick = true,
        contentPadding = PaddingValues(horizontal = metrics.dp(30), vertical = metrics.dp(24)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
            BasicText(stringResource(R.string.chantier_title), style = GameText.display(48))
            BasicText(
                when {
                    construction == null -> stringResource(R.string.chantier_choose_project)
                    construction.newProject -> stringResource(R.string.chantier_new_street, construction.project.ifEmpty { stringResource(R.string.chantier_new_project) })
                    else -> stringResource(R.string.chantier_street, construction.project)
                },
                style = GameText.body(26, colors.muted),
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(22)), verticalAlignment = Alignment.Bottom) {
                ConstructionStage.entries.forEach { stage ->
                    val done = stage.ordinal <= reached
                    val current = stage.ordinal == reached
                    val stopped = current && construction?.failed == true
                    Column(
                        Modifier.alpha(if (done) 1f else DIM).semantics { selected = current }.testTag("chantier-etape-${stage.name}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(metrics.dp(8)),
                    ) {
                        SpriteImage(
                            DecorCatalog.DEFAULT.sites[stage.ordinal],
                            assets,
                            scale = GameScale.ICON,
                            roof = RoofPalette.AUBE.first(),
                        )
                        BasicText(
                            stageLabel(stage, construction?.prompted != false),
                            style = GameText.body(22, if (stopped) colors.error else colors.ink, weight = if (current) 700 else 400).copy(textAlign = TextAlign.Center),
                            maxLines = 1,
                        )
                    }
                }
            }
            construction?.error?.let { error ->
                Column(Modifier.testTag("chantier-echec"), verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                    BasicText(stringResource(R.string.chantier_failed), style = GameText.body(26, colors.error, weight = 700))
                    // Le message du pont ou de l'agent est une donnée : il est montré tel quel.
                    BasicText(error.ifBlank { stringResource(R.string.chantier_failed_silent) }, style = GameText.body(26, colors.ink), maxLines = 4)
                }
            }
        }
    }
}

/** Le nom d'une étape ; sans consigne, la dernière est « Attend ta consigne », pas une réponse inventée. */
@Composable
fun stageLabel(stage: ConstructionStage, prompted: Boolean): String = stringResource(
    when (stage) {
        ConstructionStage.PLOT -> R.string.chantier_stage_plot
        ConstructionStage.FOUNDATIONS -> R.string.chantier_stage_foundations
        ConstructionStage.FRAME -> R.string.chantier_stage_frame
        ConstructionStage.FINISHED -> if (prompted) R.string.chantier_stage_finished else R.string.chantier_stage_ready
    },
)

/** Le bandeau d'une nouvelle rue (maquette 11), avec le panneau planté au début de la rue. */
@Composable
fun StreetBanner(project: String, assets: GameAssets, modifier: Modifier = Modifier) {
    Box(modifier.testTag("chantier-nouvelle-rue")) {
        Toast(
            stringResource(R.string.chantier_new_street, project.ifEmpty { stringResource(R.string.chantier_new_project) }),
            icon = { SpriteImage(STREET_SIGN, assets, scale = GameScale.ICON, placeholderPixels = 0) },
        )
    }
}

private const val PANEL_WIDTH = 900
private const val DIM = 0.45f
private const val SIGN_CLEARANCE = 2
private const val SPARKLE_RISE = 6
private const val SPARKLE = "etincelle"
private const val STREET_SIGN = "panneau.simple"
