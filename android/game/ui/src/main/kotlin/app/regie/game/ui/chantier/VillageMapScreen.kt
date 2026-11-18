package app.regie.game.ui.chantier

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.regie.game.engine.village.ConstructionStage
import app.regie.game.engine.village.DecorCatalog
import app.regie.game.engine.village.MapLot
import app.regie.game.engine.village.MapStreet
import app.regie.game.engine.village.VillageMap
import app.regie.game.ui.R
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.ArrowDirection
import app.regie.game.ui.kit.FrameDefaults
import app.regie.game.ui.kit.KeyChipColors
import app.regie.game.ui.kit.PixelArrow
import app.regie.game.ui.kit.PixelFrame
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.SpriteImage
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameText
import app.regie.game.ui.house.PadAction
import app.regie.game.ui.house.PadActionHint

/** Les gestes de la carte : une rue choisie (croix ou doigt), y aller (A), fermer (B). */
interface MapCallbacks {
    fun onGoTo(street: Int)

    fun onMove(delta: Int)

    fun onClose()
}

/**
 * La carte du hameau (maquette 11), sur l'écran du bas pendant un chantier : une ligne par rue, dans
 * l'ordre d'ouverture, avec ses maisons (couleur du toit, état de la session), son chantier et ses
 * terrains libres ; puis la place de la rue que le prochain projet ouvrira. Tout vient du hameau généré
 * ([VillageMap]) : rien n'est inventé.
 */
@Composable
fun VillageMapScreen(map: VillageMap, selected: Int, assets: GameAssets, callbacks: MapCallbacks, modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    val colors = Game.colors
    Column(modifier.fillMaxSize().background(colors.paper).testTag("carte")) {
        Row(
            Modifier.fillMaxWidth().padding(start = metrics.dp(44), end = metrics.dp(44), top = metrics.dp(32), bottom = metrics.dp(8)),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(24)),
        ) {
            BasicText(stringResource(R.string.carte_title), Modifier.weight(1f), style = GameText.display(64), maxLines = 1)
            BasicText(
                pluralStringResource(R.plurals.carte_streets, map.streets.size, map.streets.size) + " · " +
                    pluralStringResource(R.plurals.carte_houses, map.houseCount, map.houseCount),
                style = GameText.body(26, colors.muted),
                maxLines = 1,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = metrics.dp(36), vertical = metrics.dp(8)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(14)),
        ) {
            map.streets.forEach { street -> StreetRow(street, street.index == selected, assets) { callbacks.onGoTo(street.index) } }
            NextStreet()
            PixelFrame(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = metrics.dp(22), vertical = metrics.dp(16))) {
                BasicText(stringResource(R.string.carte_rule), style = GameText.body(26, lineHeightPercent = 130))
            }
        }
        val chip = KeyChipColors(colors.keyBackground, colors.keyInk)
        Row(
            Modifier.fillMaxWidth().height(metrics.dp(96)).background(colors.bar).padding(horizontal = metrics.dp(40)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(30)),
        ) {
            PadActionHint(PadAction("Croix", stringResource(R.string.carte_streets_key)) { callbacks.onMove(1) }, chip = chip, ink = colors.barInk, textSizePx = 26)
            PadActionHint(PadAction("A", stringResource(R.string.carte_go)) { callbacks.onGoTo(selected) }, chip = chip, ink = colors.barInk, textSizePx = 26)
            PadActionHint(PadAction("B", stringResource(R.string.carte_close), onTap = callbacks::onClose), chip = chip, ink = colors.barInk, textSizePx = 26)
        }
    }
}

@Composable
private fun StreetRow(street: MapStreet, selected: Boolean, assets: GameAssets, onTap: () -> Unit) {
    val metrics = Game.metrics
    val colors = Game.colors
    Row(
        Modifier.fillMaxWidth().semantics { this.selected = selected }.testTag("carte-rue-${street.index}")
            .clickable(interactionSource = null, indication = null, onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(14)),
    ) {
        Box(Modifier.width(metrics.art(3))) {
            if (selected) PixelArrow(ArrowDirection.RIGHT, color = colors.focus, blink = true)
        }
        BasicText(
            street.project.ifEmpty { stringResource(R.string.chantier_new_project) },
            Modifier.width(metrics.dp(STREET_NAME_WIDTH)),
            style = GameText.body(28, if (selected) colors.focus else colors.ink, weight = 600),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PixelFrame(
            Modifier.weight(1f),
            colors = if (selected) FrameDefaults.focus().copy(fill = ROAD) else FrameDefaults.panel().copy(fill = ROAD),
            contentPadding = PaddingValues(horizontal = metrics.dp(14), vertical = metrics.dp(10)),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                street.lots.forEach { lot -> Lot(lot, assets) }
            }
        }
    }
}

/** Un terrain : une maison (toit, fenêtre à la couleur de l'état), un chantier, ou un terrain libre. */
@Composable
private fun Lot(lot: MapLot, assets: GameAssets) {
    val metrics = Game.metrics
    val colors = Game.colors
    val size = metrics.dp(LOT)
    when (lot) {
        is MapLot.Built -> {
            val house = lot.house
            // Un contour vert profond : un toit clair (chaume) se détache du mur.
            PixelFrame(Modifier.size(size).alpha(if (lot.underConstruction) BUILDING_ALPHA else 1f).testTag("carte-maison-${house.session.id.value}")) {
                Column {
                    Spacer(Modifier.fillMaxWidth().height(metrics.dp(ROOF)).background(Color(house.roof.base)))
                    Box(Modifier.fillMaxWidth().weight(1f).background(colors.panel), contentAlignment = Alignment.Center) {
                        Spacer(Modifier.size(metrics.dp(WINDOW)).background(stateColor(TagState.of(house.session.status))))
                    }
                }
            }
        }
        is MapLot.Building -> Box(Modifier.size(size).testTag("carte-chantier"), contentAlignment = Alignment.Center) {
            val stage = lot.site.stage ?: ConstructionStage.PLOT
            SpriteImage(DecorCatalog.DEFAULT.sites[stage.ordinal], assets, Modifier.alpha(if (lot.site.stage == null) BUILDING_ALPHA else 1f), scale = 1, placeholderPixels = 0)
        }
        is MapLot.Free -> PixelFrame(
            Modifier.size(size).testTag("carte-libre"),
            colors = FrameDefaults.paper().copy(line = PLOT_LINE, fill = PLOT_GRASS),
        ) {}
    }
}

/** La place de la rue suivante : elle s'ouvrira avec le prochain projet. */
@Composable
private fun NextStreet() {
    val metrics = Game.metrics
    val colors = Game.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        Box(Modifier.width(metrics.art(3)))
        BasicText(stringResource(R.string.carte_next), Modifier.width(metrics.dp(STREET_NAME_WIDTH)), style = GameText.body(28, colors.muted, weight = 600), maxLines = 1)
        PixelFrame(Modifier.weight(1f), colors = FrameDefaults.paper().copy(line = NEXT_LINE), contentPadding = PaddingValues(horizontal = metrics.dp(14), vertical = metrics.dp(18))) {
            BasicText(stringResource(R.string.carte_next_hint), style = GameText.body(24, colors.muted), maxLines = 1)
        }
    }
}

@Composable
private fun stateColor(state: TagState): Color = Game.colors.let {
    when (state) {
        TagState.WORKING -> it.work
        TagState.WAITING -> it.wait
        TagState.FAILED -> it.error
        else -> it.idle
    }
}

private const val STREET_NAME_WIDTH = 230
private const val LOT = 76
private const val ROOF = 30
private const val WINDOW = 18
private const val BUILDING_ALPHA = 0.6f

/** La route, le bord d'un terrain libre et son herbe, la rue à venir (maquette 11). */
private val ROAD = Color(0xFFE5A669)
private val PLOT_LINE = Color(0xFFA16948)
private val PLOT_GRASS = Color(0xFFA0D944)
private val NEXT_LINE = Color(0xFFA19E5C)
