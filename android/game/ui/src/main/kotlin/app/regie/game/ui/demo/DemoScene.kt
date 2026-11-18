package app.regie.game.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.regie.domain.model.Agent
import app.regie.domain.model.BridgeStatus
import app.regie.domain.model.DiffKind
import app.regie.domain.model.DiffLine
import app.regie.domain.model.Overview
import app.regie.domain.model.Progress
import app.regie.domain.model.Session
import app.regie.domain.model.SessionId
import app.regie.domain.model.SessionStatus
import app.regie.game.engine.village.HouseCatalog
import app.regie.game.engine.village.InMemoryVillageLayoutStore
import app.regie.game.engine.village.Village
import app.regie.game.engine.village.VillageGenerator
import app.regie.game.engine.world.TilePos
import app.regie.game.ui.atlas.GameAssets
import app.regie.game.ui.atlas.houseCatalog
import app.regie.game.ui.kit.Choice
import app.regie.game.ui.kit.ChoiceMenu
import app.regie.game.ui.kit.DialogBox
import app.regie.game.ui.kit.DialogText
import app.regie.game.ui.kit.KeyHintBar
import app.regie.game.ui.kit.KeyHintSpec
import app.regie.game.ui.kit.PixelDiffLine
import app.regie.game.ui.kit.SignPanel
import app.regie.game.ui.kit.StateTag
import app.regie.game.ui.kit.TagState
import app.regie.game.ui.render.Camera
import app.regie.game.ui.render.PixelWorld
import app.regie.game.ui.render.anchorBottomCenter
import app.regie.game.ui.render.fallbacks
import app.regie.game.ui.theme.Game
import app.regie.game.ui.theme.GameTheme
import kotlin.time.Instant

/**
 * La démonstration du socle, pour le banc, les aperçus et les tests : le hameau tiré par le vrai
 * générateur ([VillageGenerator], disposition en mémoire) sur 3 ou 8 sessions d'exemple, avec les
 * modèles de maison de l'atlas s'il est là. Les textes sont des exemples, pas des données d'agent.
 */
object DemoScene {
    private val start = Instant.fromEpochSeconds(1_800_000_000)

    private val SAMPLES = listOf(
        Triple("regie", "tri des sessions", SessionStatus.WORKING),
        Triple("api-meteo", "migration", SessionStatus.AWAITING_APPROVAL),
        Triple("site-perso", "refonte", SessionStatus.IDLE),
        Triple("regie", "vue jeu", SessionStatus.IDLE),
        Triple("carnet", "export pdf", SessionStatus.WORKING),
        Triple("api-meteo", "cache", SessionStatus.FAILED),
        Triple("dotfiles", "zsh", SessionStatus.STOPPED),
        Triple("carnet", "recherche", SessionStatus.WORKING),
    )

    fun sessions(count: Int): List<Session> = SAMPLES.take(count).mapIndexed { i, (project, title, status) ->
        Session(
            id = SessionId("demo-$i"),
            agent = listOf(Agent.CLAUDE, Agent.CODEX, Agent("opencode", "opencode"))[i % 3],
            project = project,
            branch = null,
            title = title,
            status = status,
            active = status != SessionStatus.STOPPED,
            updatedAt = start,
            lastActivityAt = start,
            progress = if (status == SessionStatus.WORKING) Progress(2, 5, "Étape") else null,
        )
    }

    /** Le hameau de [count] sessions, avec les maisons de [assets] s'il en a. */
    fun village(count: Int, assets: GameAssets): Village {
        val generator = VillageGenerator(InMemoryVillageLayoutStore(), catalog = assets.houseCatalog() ?: HouseCatalog.DEFAULT)
        return generator.generate(Overview.of(sessions(count), emptyList(), BridgeStatus.Connected))
    }
}

/** Le hameau généré, ses panneaux de maison, la caméra sur [focus] (par défaut l'arrivée du coursier). */
@Composable
fun DemoVillage(village: Village, assets: GameAssets, modifier: Modifier = Modifier, focus: TilePos = village.spawn) {
    val fallbacks = remember(village) { village.fallbacks() }
    val center = Camera.center(focus)
    PixelWorld(village.world, assets, { center }, modifier.fillMaxSize(), fallbacks = fallbacks) { projection ->
        village.houses.forEach { house ->
            SignPanel(
                house.session.project,
                Modifier.anchorBottomCenter { projection.tileTop(house.labelAnchor) },
                subtitle = house.session.title,
                state = TagState.of(house.session.status),
            )
        }
    }
}

/** L'écran du haut de la démonstration : le hameau autour de la place. */
@Composable
fun DemoTopScreen(assets: GameAssets, modifier: Modifier = Modifier, sessions: Int = 3) {
    val village = remember(assets, sessions) { DemoScene.village(sessions, assets) }
    DemoVillage(village, assets, modifier)
}

/** L'écran du bas de la démonstration : la suite du hameau, sous la place, et la barre des touches. */
@Composable
fun DemoBottomScreen(assets: GameAssets, modifier: Modifier = Modifier, sessions: Int = 8) {
    val village = remember(assets, sessions) { DemoScene.village(sessions, assets) }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // La deuxième rangée de maisons, sous la place, et l'avenue qui la dessert.
            val house = village.houses.firstOrNull { it.street >= 2 } ?: village.houses.firstOrNull()
            val focus = house?.let { TilePos((it.door.x + village.origin.x) / 2, it.door.y - 1) } ?: village.spawn
            DemoVillage(village, assets, focus = focus)
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

/** Le kit pixel sur le papier de l'écran du bas. */
@Composable
fun DemoKitScreen(modifier: Modifier = Modifier) {
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
        KeyHintBar(listOf(KeyHintSpec("A", "Choisir"), KeyHintSpec("B", "Revenir")))
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
    GameTheme { Box(contentAlignment = Alignment.Center) { DemoKitScreen() } }
}
