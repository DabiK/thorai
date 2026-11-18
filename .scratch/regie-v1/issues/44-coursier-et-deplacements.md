# 44 — Coursier et déplacements

Statut : fusionné dans ticket/40-vue-jeu
Possède : déplacements et animations dans `:game-engine`, contrôleur de jeu, branchement de `InputRouter`
Dépend de : 41, 43

## Objectif

Le coursier marche sur la grille, dans 4 directions, avec collisions et animations. La caméra le suit. A entre dans une maison, avec une transition en iris. Les raccourcis (liste du bas, L1/R1, R1 vers une validation) le font marcher jusqu'à la porte, par A*.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- [x] Tests du moteur : pas, collisions, chemins.
- [x] Test du contrôleur de jeu.
- [ ] Démonstration sur le banc (le bac à sable a l'écran `coursier` ; la démonstration a été faite en Robolectric, voir le rapport).

## Rapport

Branche `ticket/44-coursier-et-deplacements`, partie de `ticket/40-vue-jeu` (socle 41, assets 42 et moteur 43 compris).

Fait, dans `:game-engine` (Kotlin pur, paquet `courier`) :

- `Direction` (4 directions), `CourierCommand` (`Move(direction, pressed)`, `Interact`, `GoTo(sessionId, enter = true)`, `Next`, `Previous`, `Talk(active)`), `CourierEvent` (`EnterHouse`, `ArrivedAtDoor`), `CourierAction` (`IDLE`, `WALK`, `TALK`).
- `CourierController(village, start, facing, onEvent)` : piloté par le temps (`advance(ms)`), sans allocation d'une image à l'autre.
  - Un pas d'une tuile en 160 ms (`STEP_MILLIS`), vitesse constante, jamais d'arrêt entre deux tuiles ; collisions de `TileMap` (bord de carte compris) : une tuile bloquée tourne le coursier sans le déplacer.
  - Direction tenue : il marche ; la dernière enfoncée l'emporte, l'autre reprend quand on la relâche. Un appui pendant un pas est mis en réserve et joué après, même relâché ; A pendant un pas est joué à l'arrivée.
  - A devant une porte (sur `entry`, regard vers `door`) : `EnterHouse`.
  - `GoTo` : A* (`findPath`) depuis la fin du pas en cours jusqu'à `entry`, puis regard vers la porte, puis `EnterHouse` (ou `ArrivedAtDoor` si `enter = false`). Une direction ou A l'interrompt après le pas commencé ; un nouveau `GoTo` la remplace.
  - R1 (`Next`) : d'abord la validation en attente (même règle que `Overview.firstPendingApproval`), avec entrée ; si le coursier y est déjà, la maison suivante. L1/R1 : maison précédente ou suivante dans l'ordre stable du hameau (rue après rue, terrain après terrain), en bouclant, arrêt devant la porte ; loin de toute maison, la plus proche.
  - `setVillage` : position reportée par `Village.relocate` ; un pas en cours continue s'il mène encore à une tuile libre voisine, sinon le coursier s'y pose ; la marche automatique repart vers la maison déplacée, ou s'arrête si elle a disparu.
  - `rest(facing)` : oublie les directions tenues, la réserve et la marche (retour d'une maison).
  - Animation : `action` et `actionMillis` (l'horloge de la marche continue d'un pas à l'autre et repart à chaque changement).

Fait, dans `:game-ui` (paquet `scene`) :

- `VillageSceneState(village, start, facing)` : `send(command)`, `updateVillage(village)` (à chaque régénération), `leaveHouse()`, et pour l'affichage `foot`, `cameraFocus`, `action`, `facing`, `facingDoor` (la maison dont il regarde la porte, pour « A Entrer »), `destination`, `entering`, `leaving`. `rememberVillageSceneState(village)`. Son horloge (`runFrames`) ne tourne que pendant un pas ; un écart d'image est borné à 100 ms.
- `VillageScene(state, assets, onEnterHouse, overlay)` : `PixelWorld` (focus en lambda, `village.fallbacks()`), le coursier interpolé au pixel, la caméra qui le suit, l'emplacement `overlay` pour les panneaux (sous l'iris), puis l'iris qui se ferme sur la porte et appelle `onEnterHouse(sessionId)` ; au retour, il se rouvre. Rien ne recompose quand le coursier marche.
- `IrisTransition(center, opening, onFinished)` : bord en vrais pixels (rangées de 4 pixels physiques), couleur `#12201F` de la maquette 04.
- Images du coursier lues dans `personnages` : `coursier.debout.<dir>`, `coursier.marche.<dir>`, `coursier.parle` ; silhouette de repli sans atlas.
- Bac à sable : `--es screen coursier` (croix ou flèches, A ou Entrée, L1, R1, X), branchement de démonstration seulement.

Tests : `CourierControllerTest` (26 : pas, vitesse, collisions, mur tenu, réserve, deux directions, A, A pendant le pas, `GoTo`, annulation, L1/R1 et ordre, validation d'abord, régénération pendant un pas et pendant une marche, maison disparue, animation), `CourierInVillageTest` (toutes les portes d'un vrai hameau atteintes ; rue qui s'allonge en pleine marche), `VillageSceneStateTest` (glissement, caméra, pause longue, porte, entrée, retour, régénération), `IrisTransitionTest` (fermeture au pixel, rappel unique, ouverture), `VillageSceneTest` (vrais atlas : le coursier derrière un buisson est identique, au pixel près, à un sprite fixe de `PixelWorld` ; la caméra suit ; l'iris précède `onEnterHouse` ; le retour). `./gradlew check` vert.

Captures Robolectric (écran du haut, 1920 × 1080) : `docs/images/jeu/44-marche.png`, `44-iris.png`, `44-derriere-buisson.png`.

Décisions et écarts :

- **Pas de branchement dans `android/app`** (consigne de l'orchestrateur) : `InputRouter` → commandes est laissé au ticket 45. À faire là : croix et stick → `Move(direction, pressed)` en ignorant les répétitions de touche ; A → `Interact` ; L1/R1 → `Previous`/`Next` ; « Y aller » de la liste du bas → `GoTo(id)` ; L2 → `Talk` ; `onEnterHouse` ouvre la maison, `leaveHouse()` au retour.
- **Le coursier est dessiné dans une couche au-dessus de `PixelWorld`**, pas dans `sprites` (triée à la composition : la changer à chaque image recomposerait et allouerait). La couche redessine, découpés au rectangle du coursier, les objets qui sont devant lui ; noté dans l'ADR 24. Si le ticket 52 ajoute à `PixelWorld` des sprites lus au dessin, la couche pourra s'y fondre. Seules allocations de la scène pendant la marche : deux `ArtPoint` par image où le coursier a bougé (ses pieds et la caméra, que l'API `focus: () -> ArtPoint` impose) ; les objets animés du décor sont résolus à chaque image, comme dans `PixelWorld`.
- L1/R1 bouclent et s'arrêtent devant la porte (la maquette 04 y propose « A Entrer, L1 R1 Autre maison ») ; `GoTo` et R1 vers une validation entrent. `GoTo(enter = false)` reste possible.
- Une seule image de parole dans l'atlas (de face) : `TALK` la montre quelle que soit la direction.
- Pas de démonstration sur le banc ni sur la Thor : les autres lots utilisent le même identifiant d'app en parallèle. Pas à pas : `adb shell am start -n app.regie/app.regie.game.ui.demo.GameSandboxActivity --es screen coursier --ei sessions 8`, puis la croix, A, L1, R1, X.
- Journal de reprise non modifié (lot parallèle de l'épopée 40 ; l'orchestrateur le tient).
