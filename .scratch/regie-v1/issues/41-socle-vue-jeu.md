# 41 — Socle de la vue jeu

Statut : prêt
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

- Tests du moteur et du kit (Robolectric et Compose).
- `./gradlew check` vert.
- Une scène de démonstration rendue sur le banc.

## Rapport

À remplir à la fin du lot.
