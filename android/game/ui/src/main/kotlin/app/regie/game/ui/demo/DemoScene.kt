package app.regie.game.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.regie.domain.model.Agent
import app.regie.domain.model.DiffKind
import app.regie.domain.model.DiffLine
import app.regie.game.engine.world.Ground
import app.regie.game.engine.world.Placed
import app.regie.game.engine.world.RoofColors
import app.regie.game.engine.world.TileMap
import app.regie.game.engine.world.TilePos
import app.regie.game.engine.world.World
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.kit.Bubble
import app.regie.game.ui.kit.BubbleTail
import app.regie.game.ui.kit.BubbleText
import app.regie.game.ui.kit.Choice
import app.regie.game.ui.kit.ChoiceMenu
import app.regie.game.ui.kit.DialogBox
import app.regie.game.ui.kit.DialogText
import app.regie.game.ui.kit.KeyHintBar
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.MicMeter
import app.regie.game.ui.kit.PixelDiffLine
import app.regie.game.ui.kit.SignPanel
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.kit.Toast
import app.regie.game.ui.render.Camera
import app.regie.game.ui.render.PixelWorld
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameTheme

/**
 * Une petite scène de démonstration, pour le banc et les aperçus : une place pavée et sa
 * fontaine, deux chemins, une rivière et son pont, trois maisons aux toits de session.
 * Les textes sont des exemples, pas des données d'agent.
 */
object DemoScene {
    const val WIDTH = 36
    const val HEIGHT = 20

    val plaza = TilePos(17, 10)

    fun world(): World {
        val map = TileMap(WIDTH, HEIGHT)
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, ground: Ground) {
            for (y in y0..y1) for (x in x0..x1) map.setGround(TilePos(x, y), ground)
        }
        // Rivière au nord-ouest, traversée par un pont.
        fill(0, 2, 13, 4, Ground.WATER)
        fill(6, 2, 7, 4, Ground.BRIDGE)
        // Chemins : du pont vers la place, et d'est en ouest.
        fill(6, 5, 7, 9, Ground.PATH)
        fill(2, 9, 33, 10, Ground.PATH)
        fill(17, 11, 18, 17, Ground.PATH)
        // La place.
        fill(14, 7, 21, 13, Ground.PLAZA)
        val objects = listOf(
            Placed("maison.chaumiere", TilePos(3, 8), RoofColors(0xFFE5A669.toInt(), 0xFFDB6633.toInt(), 0xFF9E5A37.toInt())),
            Placed("maison.chaumiere", TilePos(24, 8), RoofColors(0xFF718EA0.toInt(), 0xFF5A718C.toInt(), 0xFF203648.toInt())),
            Placed("maison.chaumiere", TilePos(24, 17), RoofColors(0xFFA2DA3B.toInt(), 0xFF63A53A.toInt(), 0xFF1F583A.toInt())),
            Placed("decor.fontaine", TilePos(17, 11)),
            Placed("decor.arbre", TilePos(12, 15)),
            Placed("decor.arbre", TilePos(30, 4)),
        )
        objects.filter { it.objectId.startsWith("maison") }.forEach { house ->
            for (dy in 0 until 4) for (dx in 0 until 6) map.setBlocked(house.anchor.offset(dx, -dy))
        }
        return World(map, objects)
    }
}

/** La scène de démonstration à l'écran du haut : le monde, un panneau, une bulle et un bandeau. */
@Composable
fun DemoTopScreen(assets: GameAssets, modifier: Modifier = Modifier) {
    val world = DemoScene.world()
    val metrics = Game.metrics
    Box(modifier.fillMaxSize()) {
        PixelWorld(world, assets, Camera.center(DemoScene.plaza), Modifier.fillMaxSize()) { projection ->
            SignPanel(
                "regie",
                Modifier.anchorBottomCenter { projection.tileTop(TilePos(25, 8)) },
                subtitle = "tri des sessions",
                state = TagState.WORKING,
            )
            SignPanel(
                "api-meteo",
                Modifier.anchorBottomCenter { projection.tileTop(TilePos(25, 17)) },
                subtitle = "migration",
                state = TagState.WAITING,
            )
        }
        Toast(
            "La cartographe attend ta validation",
            Modifier.align(Alignment.TopCenter).padding(top = metrics.dp(24)),
            subtitle = "api-meteo · migration",
            alert = true,
            action = KeyHintSpec("R1", "Voir"),
        )
        Bubble(
            Modifier.align(Alignment.BottomStart).padding(start = metrics.dp(120), bottom = metrics.dp(120)),
            tail = BubbleTail.BOTTOM_START,
        ) {
            MicMeter(listOf(0.3f, 0.8f, 0.55f, 1f, 0.45f, 0.7f), heightPx = 48)
            BubbleText("…")
        }
    }
}

/** La scène de démonstration à l'écran du bas : le kit pixel. */
@Composable
fun DemoBottomScreen(modifier: Modifier = Modifier) {
    val metrics = Game.metrics
    Column(modifier.fillMaxSize().background(Game.colors.paper)) {
        Column(Modifier.weight(1f).padding(metrics.dp(44)), verticalArrangement = Arrangement.spacedBy(metrics.dp(22))) {
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                TagState.entries.forEach { StateTag(it) }
            }
            DialogBox(speaker = Agent.CLAUDE.label, speakerColor = Game.colors.agent(Agent.CLAUDE)) {
                DialogText("Je voudrais lancer cette commande. D'accord ?")
            }
            ChoiceMenu(
                listOf(
                    Choice("Autoriser une fois", "A"),
                    Choice("Toujours dans cette session", "X"),
                    Choice("Refuser", "B"),
                ),
                selected = 0,
            )
            Column {
                PixelDiffLine(DiffLine(DiffKind.REMOVED, "    fun sessions() = store.all()", 42, null))
                PixelDiffLine(DiffLine(DiffKind.ADDED, "        .sortedByDescending { it.updatedAt }", null, 43), selected = true)
                PixelDiffLine(DiffLine(DiffKind.CONTEXT, "}", 47, 47))
            }
        }
        KeyHintBar(
            listOf(
                KeyHintSpec("Start", "Menu"),
                KeyHintSpec("B", "Agents"),
                KeyHintSpec("A", "Y aller"),
                KeyHintSpec("X", "Bâtir"),
                KeyHintSpec("Y", "Console"),
            ),
        )
    }
}

@Preview(widthDp = 832, heightDp = 468, name = "Écran du haut")
@Composable
private fun DemoTopPreview() {
    GameTheme { DemoTopScreen(GameAssets.Placeholder) }
}

@Preview(widthDp = 538, heightDp = 468, name = "Écran du bas")
@Composable
private fun DemoBottomPreview() {
    GameTheme { DemoBottomScreen() }
}
