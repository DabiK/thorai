package app.regie.game.ui

import app.regie.game.ui.atlas.GameAssetLoader
import app.regie.game.ui.atlas.GameAssets
import java.io.FileNotFoundException

/** La configuration des tests : l'écran du haut de la Thor, à sa densité réelle (≈ 2,3). */
const val THOR_TOP = "w832dp-h468dp-land-369dpi"

/** L'écran du bas : 1240 × 1080. */
const val THOR_BOTTOM = "w538dp-h468dp-land-369dpi"

/** Les assets de test, lus dans `src/test/resources/game-fixture/` comme le chargeur lit `assets/game/`. */
fun fixtureAssets(names: List<String> = GameAssetLoader.ATLASES): GameAssets = GameAssetLoader.load(
    { path -> GameAssetLoader::class.java.classLoader?.getResourceAsStream(path) ?: throw FileNotFoundException(path) },
    directory = "game-fixture",
    names = names,
)
