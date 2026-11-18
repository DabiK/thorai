# 45 — Hameau, vue d'ensemble

Statut : fusionné dans ticket/40-vue-jeu
Possède : les écrans hameau haut et bas dans `:game-ui`, leur branchement à la place d'`OverviewScreens`
Dépend de : 41, 43, 44

## Objectif

Écran 03 du canvas. En haut, le hameau avec les panneaux et les états des maisons. En bas, le carnet : la première validation, les sessions, et les actions (bâtir, console, agents, menu).

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- [x] Mêmes informations et mêmes actions que la vue d'ensemble actuelle (`VillageWiringTest`, `VillageScreensTest`) ; « Tout mettre en pause » est dans le menu de pause du ticket 49 (Start).
- [x] Tests Compose.
- [x] `./gradlew check` vert.

## Rapport

Branche `ticket/45-hameau-vue-ensemble`, partie de `ticket/40-vue-jeu` (41 à 44, puis 46, 52, 49 et main fusionnés en route). ADR 24 complété (« La vue d'ensemble en hameau »).

Fait :

- **`:game-ui`, paquet `village`** :
  - `VillageTopScreen(scene, assets, onEnterHouse, layer, carnet, prompt)` : la scène du coursier (ticket 44), le calque `DefaultVillageLayer` = `VillageOverlay` du ticket 52 (bulle d'état sur chaque toit, panneau complet de la seule maison choisie) plus « A Entrer » au-dessus du coursier devant une porte. Le calque est un paramètre (`VillageLayer`), remplaçable.
  - `CarnetBottomScreen` (écran du bas) : en-tête (Hameau, nombre de sessions, état du pont, versions des agents, Configurer avec son point, Régler la voix), avis du pont injoignable, **première validation en tête** (habitant, « Codex attend ta validation », projet et session, commande ; R1 Voir), une ligne par session (habitant selon l'état, projet, branche, session et agent, demande, erreur ou dernière action, état, étape, ancienneté ; ligne choisie encadrée avec curseur ; stick droit), puis le micro (phrase de L2, L2 Dicter et R2 Donner un ordre tenus au doigt, L1 R1 Changer de maison, Quitter) et la barre A Y aller ou Entrer, B Agents, X Bâtir, Y Console, Start Menu (le menu de pause du ticket 49).
  - `CompactCarnet` (un seul écran, sous le hameau) : validation en tête, liste des sessions en lignes courtes (maison choisie suivie, stick droit), micro, compteur, raccourcis, Quitter, barre. `CarnetQuitPrompt` par-dessus.
  - `focusedHouse()` : la maison choisie = celle où va le coursier, sinon dont il regarde la porte, sinon la plus proche (`Village.nearestHouse`).
  - `GameAssetLoader.houseCatalog(assets)` : les modèles de maison lus dans `maisons.json` seul.
- **`android/app`** :
  - `VillageController` (paquet `overview`) : régénération seulement quand une maison peut changer (sessions, projets, agents, titres, états, validations ; l'ordre de la vue d'ensemble n'y compte pas), hors du fil principal (un fil de `Dispatchers.Default`, un seul à la fois), un seul `VillageSceneState` pour le processus, routage de la manette vers le coursier, repli sur `OverviewController` sans hameau ou sans écran du haut visible.
  - `PreferencesVillageLayoutStore` (paquet `storage`, ajouté aux adapters d'`ArchitectureTest`) : la disposition en JSON versionné.
  - `InputRouter` : directions tenues à part (croix sans répétitions, stick gauche avec hystérésis, relâchées par `reset`) ; `TriggerAxes` lit `AXIS_X`/`AXIS_Y` et la croix en axe (`AXIS_HAT_X`/`Y`). Les mouvements restent non consommés (ADR 05) : Android en tire des touches de croix (`ViewRootImpl.SyntheticJoystickHandler`, `FLAG_FALLBACK`, source AOSP relue), depuis le stick comme depuis une croix en axe. Celles du stick sont avalées (sinon chaque direction arrivait deux fois, et une diagonale en tenait deux) ; celles d'une croix en axe sont gardées, répétitions comprises.
  - `AppContainer` (branches de la vue d'ensemble seulement) : `village.handle` à la place de `controller.handle`, focus synchronisé avant R2 et le mode console, `enterHouse` (l'iris fermé ouvre la session), `goToHouse`, `leaveHouse` au retour sur la vue d'ensemble, le coursier parle pendant une dictée lancée du hameau.
  - `TopActivity` et `BottomActivity` : `GameOverviewTop` et `GameOverviewBottom` (`ui/game/VillageScreens.kt`, textes de l'app) à la place d'`OverviewTopScreen` et `OverviewBottomScreen` ; la rangée de raccourcis classique n'est plus posée au-dessus du hameau (le carnet les porte).

Manette sur le hameau : croix et stick gauche tenus = marcher ; A = entrer devant une porte, sinon marcher jusqu'à la maison choisie et y entrer ; L1/R1 = maison précédente/suivante, arrêt devant la porte ; R1 (et le bandeau « R1 Voir ») avec une validation en attente = la première de la vue d'ensemble (`firstPendingApproval`, celle du bandeau) : devant sa porte, y rentrer (au retour de la maison sans avoir répondu, par exemple), sinon y marcher et entrer ; sa maison pas encore bâtie, la validation s'ouvre directement ; ligne touchée = y marcher et entrer ; X, Y, L2, R2, stick droit = comme avant, sur la maison choisie ; B = agents ; Start = menu de pause (ticket 49) ; « R1 Voir » sous l'oiseau messager (ticket 49) = le coursier marche jusqu'à la maison de la lettre et y entre. Le titre, le parcours d'accueil, le menu de pause et les panneaux ouverts prennent la croix avant le coursier ; L2 et R2 marchent partout, menu compris. Entrée : l'iris se ferme, puis la session s'ouvre ; au retour, le coursier ressort devant la porte.

Tests : `VillageControllerTest` (+2 : retour de maison puis R1 ; deux validations, hameau plus ancien que la vue d'ensemble), `InputRouterTest` (+2 : touches tirées du stick avalées, diagonale comprise ; touches d'une croix en axe gardées), `GameMenuInputTest` (+3 : « R1 Voir » de l'oiseau fait marcher le coursier puis ouvre la session ; menu de pause et parcours d'accueil prennent la croix avant le coursier ; L2 vise la maison du coursier, qui parle, R2 reste valable), `VillageControllerTest` (17 : `walkTo`, génération et disposition gardée, régénération sans bouger le coursier ni les maisons, rechargement, directions tenues et répétitions, panneau ouvert par-dessus, R1 vers la validation, L1 puis A, A vers la maison choisie, focus et L2, ligne touchée, X/Y/Start, repli sans écran du haut et sans hameau, Quitter, retour de maison, parole), `PreferencesVillageLayoutStoreTest` (3), `InputRouterTest` (+5), `VillageWiringTest` (5 : mêmes informations et actions que la vue classique), `VillageScreensTest` (6, vrais atlas, graphismes natifs). `./gradlew check` vert.

Captures Robolectric (taille des écrans de la Thor, barres système déduites) : `docs/images/jeu/45-hameau.png`, `45-porte.png`, `45-carnet.png`, `45-un-ecran.png`, `45-quitter.png`.

Écarts et décisions :

- **Start** : après la fusion du ticket 49, Start ouvre le menu de pause (« Tout mettre en pause » s'y valide par A, PRD à jour) ; la barre reprend son libellé « Menu » (`game_menu_title`).
- **Repli sur la vue d'ensemble classique** : il ne reste que dans la manette (`VillageController` passe la main à `OverviewController`), dans trois cas : avant le premier hameau généré, pendant la confirmation de « Quitter », et quand l'écran du haut n'est pas visible (mode console : une autre appli occupe le haut, le carnet du bas reste affiché, A ouvre directement la ligne choisie, une ligne touchée ou « R1 Voir » ouvre directement, L1/R1 déplacent la ligne). Aucun écran classique n'est plus posé sur la vue d'ensemble ; `ConsoleSessionBottom` (49) sert la session console, pas la vue d'ensemble.
- **R2 « Donner un ordre »** près du micro, demandé pour le jeu, alors que le ticket 30 avait retiré la phrase de R2 de l'accueil classique.
- **Captures** : `captureToImage()` attend un dessin que Robolectric ne fait pas ici (délai dépassé) ; les captures dessinent la vue dans un `Canvas`, comme celles du ticket 44.
- **Libellés de la barre** courts, ceux de la maquette (Agents, Bâtir, Console) : les verbes complets ne tiennent pas sur 1240 px.
- Le carnet compact (un seul écran) ne reprend pas les versions des agents.
- Régénération mémorisée : une vue d'ensemble qui ne change aucune maison (ancienneté, dernière action) ne refait pas le monde ; le carnet, lui, suit chaque vue d'ensemble.
- `CarnetHint` (aide touchable) double un peu `PadAction` du ticket 46, arrivé après : à fondre au ticket 51.

Reste :

- Ticket 48 : « Bâtir » fait encore apparaître la fiche classique, sans chantier.
- Essai sur la Thor par l'orchestrateur (aucun banc ni console utilisés ici).
