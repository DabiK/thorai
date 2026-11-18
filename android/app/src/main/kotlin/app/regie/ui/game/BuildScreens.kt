package app.regie.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.regie.domain.model.NewSessionField
import app.regie.domain.model.NewSessionForm
import app.regie.domain.model.SessionId
import app.regie.game.engine.village.Construction
import app.regie.game.engine.village.map
import app.regie.game.ui.atlas.rememberGameAssets
import app.regie.game.ui.chantier.BuildCallbacks
import app.regie.game.ui.chantier.BuildKey
import app.regie.game.ui.chantier.ConstructionPanel
import app.regie.game.ui.chantier.MapCallbacks
import app.regie.game.ui.chantier.NewSessionBottomScreen
import app.regie.game.ui.chantier.VillageMapScreen
import app.regie.game.ui.chantier.constructionLayer
import app.regie.game.ui.scene.VillageSceneState
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameTheme
import app.regie.game.ui.village.VillageTopScreen
import app.regie.input.Button

/*
 * Branchement de « Bâtir et agrandir » (ticket 48) : la fiche de nouvelle session en jeu à la place de
 * NewSessionScreen, le chantier sur le hameau, et la carte du hameau en bas pendant le chantier. Les
 * écrans du jeu reçoivent l'état du domaine (NewSessionForm) et le chantier du hameau, et renvoient les
 * mêmes gestes que la fiche classique. NewSessionScreen part au ticket 51.
 */

/** Les gestes de la fiche, vus par l'app : les mêmes rappels que `NewSessionScreen`. */
fun buildCallbacks(
    onButton: (Button) -> Unit,
    onHeld: (Boolean) -> Unit,
    onField: (NewSessionField) -> Unit,
    onInstruction: (String) -> Unit,
    onBranch: (String) -> Unit,
    onProjectName: (String) -> Unit,
): BuildCallbacks = object : BuildCallbacks {
    override fun onKey(key: BuildKey) = onButton(Button.valueOf(key.name))
    override fun onHold(held: Boolean) = onHeld(held)
    override fun onField(field: NewSessionField) = onField(field)
    override fun onInstruction(text: String) = onInstruction(text)
    override fun onBranch(text: String) = onBranch(text)
    override fun onProjectName(text: String) = onProjectName(text)
}

/** La fiche, sur l'écran du bas ; seule sur l'écran en mode un seul écran ([compact]). */
@Composable
fun GameNewSessionBottom(form: NewSessionForm, callbacks: BuildCallbacks, compact: Boolean = false) {
    GameTheme { NewSessionBottomScreen(form, callbacks, compact = compact) }
}

/**
 * L'écran du haut pendant la fiche (maquette 10) : le hameau, où le coursier marche jusqu'au terrain
 * visé, et le panneau « Bâtir une maison » avec ses étapes. Sans hameau encore généré, le panneau seul.
 */
@Composable
fun GameNewSessionTop(scene: VillageSceneState?, construction: Construction?, onEnterHouse: (SessionId) -> Unit) {
    GameTheme {
        val assets = rememberGameAssets()
        if (scene == null) {
            Box(Modifier.fillMaxSize().background(Color(assets.palette.grass.base))) {
                ConstructionPanel(construction, assets, Modifier.align(Alignment.TopEnd).padding(Game.metrics.dp(40)))
            }
        } else {
            VillageTopScreen(scene, assets, onEnterHouse, layer = remember(construction) { constructionLayer(construction, panel = true) })
        }
    }
}

/** La carte du hameau, sur l'écran du bas pendant un chantier (maquette 11). */
@Composable
fun GameVillageMap(scene: VillageSceneState, selected: Int, callbacks: MapCallbacks) {
    GameTheme {
        val assets = rememberGameAssets()
        val map = remember(scene.village) { scene.village.map() }
        VillageMapScreen(map, selected, assets, callbacks)
    }
}
