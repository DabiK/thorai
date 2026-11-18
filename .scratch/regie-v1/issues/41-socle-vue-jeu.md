# 41 — Socle de la vue jeu

Statut : à fusionner
Possède : `android/game/` (création des modules), `android/settings.gradle.kts`, `ArchitectureTest`, ADR 21
Dépend de : —

## Objectif

Poser les modules `:game-engine` (Kotlin pur) et `:game-ui` (Compose), le moteur de rendu pixel ×4 et le kit d'interface pixel, sur lesquels tous les autres lots s'appuient.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## À livrer

- Les modules, branchés dans Gradle et dans `android/app` (dépendance seulement).
- Le rendu : carte de tuiles et sprites sur un Canvas Compose, agrandissement entier, `FilterQuality.None`, caméra.
- Le sol dessiné par le code (herbe, chemin, eau, avec leurs bords) à partir de `palette.json`.
- Le chargement des atlas selon le contrat, avec le remplacement des couleurs clés des toits, et des formes de remplacement tant qu'un atlas manque.
- Le kit pixel : cadre, cadre épais, touche, étiquette d'état, panneau, bulle, boîte de dialogue, menu de choix, bandeau, lignes de diff, niveau du micro.
- Les polices Jersey 10, Pixelify Sans et VT323 (OFL).
- `ArchitectureTest` étendu à `android/game/`.
- L'ADR 21 « Vue jeu ».

## Critères d'acceptation

- [x] Tests du moteur et du kit (Robolectric et Compose).
- [x] `./gradlew check` vert.
- [x] Une scène de démonstration rendue sur le banc.

## Rapport

Branche `ticket/41-socle-vue-jeu`, partie de `ticket/40-vue-jeu` et à jour de sa fusion de main (ticket 31, agents dynamiques).

Livré :
- `:game-ui` (`android/game/ui`, `app.regie.game.ui`), bibliothèque Compose qui dépend de `:game-engine` et `:domain` ; `android/app` en dépend sans l'afficher. `:game-engine` n'a pas été touché (lot 43 en parallèle).
- Rendu : `PixelWorld(world, assets, focus, sprites, scale, overlay)` ; sol peint par `GroundPainter` (herbe, chemin, place, eau, pont, bords et coins selon `GroundEdges`, texture déterministe) ; objets et `Sprite` triés par le bas ; toits repeints et mis en cache ; ×4 en pixels physiques, `FilterQuality.None` ; `Camera` centrée et bornée, `WorldProjection` et `Modifier.anchorBottomCenter` pour accrocher panneaux et bulles ; `SpriteImage` pour une image d'atlas hors du monde (icônes ×2, portraits).
- Atlas : `GameAssetLoader` lit `assets/game/<atlas>.png|json` et `palette.json` ; `GameAssets.resolve` (objet, animation, image) ; atlas absent ou invalide signalé dans `problems` et remplacé par des formes. `rememberGameAssets()` charge hors du fil principal.
- Kit (Aube) : `GameTheme`, `Game.colors|fonts|metrics|clock`, `GameText`, `PixelFrame`/`pixelFrame`, `PixelShape`, `PixelArrow`, `KeyChip`, `KeyHint`, `KeyHintBar`, `KeyOrder`, `StateTag`/`TagState`, `SignPanel`, `Bubble`, `DialogBox`, `ChoiceMenu`, `Toast`/`ToastHost`, `PixelDiffLine`, `MicMeter`, `Blink`.
- Polices Jersey 10, Pixelify Sans (variable) et VT323 dans `res/font`, licences OFL dans `assets/licences/`, sources dans `docs/rebrand/da-jeu.md`.
- `ArchitectureTest` : deux règles, vue jeu et moteur du jeu. ADR 21.

Preuves :
- 36 tests dans `:game-ui` (sol, bords, caméra, contrat JSON, recoloration, chargement, rendu ×4 net pixel par pixel, kit) ; `./gradlew check` vert dans `android/`.
- Banc : `GameSandboxActivity` (debug), captures `docs/images/jeu/41-banc-haut.png` et `41-banc-bas.png` ; sur l'écran du haut, 6 250 pixels du dessin vérifiés, tous des carrés 4 × 4 uniformes.

Écarts et ajouts au plan :
- `palette.json` n'avait pas de format : `{"colors": [...], "roles": {"grass.light": ...}}`, 16 rôles (`<sol>.light|base|dark` et `outline`), décrit dans l'ADR 21. Le lot 42 doit le suivre ou le faire évoluer.
- La cheminée et les fenêtres des maisons n'ont pas de champ dans le contrat : non lues, à fixer avec le lot 42.
- `Placed.anchor` est lu comme la tuile en bas à gauche de l'empreinte ; `door` comme relatif au coin haut gauche de l'empreinte.
- Maquettes adaptées pour la cohérence : touche vert profond sur le papier (papier sur papier dans la maquette), touches triées dans l'ordre de `docs/textes.md`, session arrêtée « au repos », couleur neutre pour un agent inconnu du thème.
- Le bac à sable debug est déclaré dans la source `debug` de `:game-ui` : il entre dans l'APK debug de l'app, jamais en release.

Reste : pas de branchement d'écran (lots 44 à 49). Aucun essai sur la Thor (consigne du lot).
