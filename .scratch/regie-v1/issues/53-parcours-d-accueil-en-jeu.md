# 53 — Parcours d'accueil dans le style du jeu

Statut : prêt
Possède : la présentation du parcours d'accueil du ticket 28 (`SetupScreens`) dans `:game-ui`, et son branchement
Dépend de : 49

## Objectif

Le parcours d'accueil (ticket 28, ADR 23) garde aujourd'hui le style classique, alors que tout le reste devient le jeu. Il s'agit de le présenter dans le style pixel, en gardant exactement les mêmes étapes et les mêmes gestes :
- permissions ;
- accessibilité de ThorAI ;
- association au pont ;
- connexion des agents, dont l'étape opencode facultative ;
- voix.

Par exemple : l'arrivée au hameau, avec une étape par panneau ou par personnage. Le raccourci « Préparer la console » trouve aussi sa place dans le menu de pause.

Référence : `docs/rebrand/plan-dev-jeu.md`, `docs/rebrand/da-jeu.md`, ADR 23, ADR 24.

## Critères d'acceptation

- Mêmes étapes, mêmes permissions demandées à l'utilisateur. Aucune permission n'est accordée par l'app ni par un agent.
- Tests Compose et captures.
- `./gradlew check` vert.

## Rapport

À remplir à la fin du lot.
