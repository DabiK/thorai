# 43 — Hameau procédural

Statut : prêt
Possède : `android/game/engine/` (monde, génération, collisions, chemins)
Dépend de : le contrat des assets (empreintes et portes)

## Objectif

Construire le hameau à partir de `Overview` : une rue par projet, une maison par session (modèle et couleur de toit stables), des terrains libres, des routes reliées à la place, du décor et des collisions. Une rue pleine s'allonge. Une maison bâtie ne bouge plus.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## À livrer

- Le générateur, déterministe à partir d'une graine.
- Le port `VillageLayoutStore` et sa version factice.
- La grille de collisions et la recherche de chemin A*.
- L'agrandissement des rues.

## Critères d'acceptation

- Tests : déterminisme, aucune superposition, chaque porte accessible depuis la place, stabilité quand on ajoute ou retire une session, agrandissement.
- `./gradlew check` vert.

## Rapport

À remplir à la fin du lot.
