# 45 — Hameau, vue d'ensemble

Statut : bloqué par 41, 43 et 44
Possède : les écrans hameau haut et bas dans `:game-ui`, leur branchement à la place d'`OverviewScreens`
Dépend de : 41, 43, 44

## Objectif

Écran 03 du canvas. En haut, le hameau avec les panneaux et les états des maisons. En bas, le carnet : la première validation, les sessions, et les actions (bâtir, console, agents, menu).

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Mêmes informations et mêmes actions que la vue d'ensemble actuelle.
- Tests Compose.
- `./gradlew check` vert.

## Rapport

À remplir à la fin du lot.
