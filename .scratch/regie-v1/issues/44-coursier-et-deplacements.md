# 44 — Coursier et déplacements

Statut : bloqué par 41 et 43
Possède : déplacements et animations dans `:game-engine`, contrôleur de jeu, branchement de `InputRouter`
Dépend de : 41, 43

## Objectif

Le coursier marche sur la grille, dans 4 directions, avec collisions et animations. La caméra le suit. A entre dans une maison, avec une transition en iris. Les raccourcis (liste du bas, L1/R1, R1 vers une validation) le font marcher jusqu'à la porte, par A*.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Tests du moteur : pas, collisions, chemins.
- Test du contrôleur de jeu.
- Démonstration sur le banc.

## Rapport

À remplir à la fin du lot.
