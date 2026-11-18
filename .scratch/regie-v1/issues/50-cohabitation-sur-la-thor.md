# 50 — Cohabitation sur la Thor

Statut : prêt
Possède : `android/app/build.gradle.kts` (identifiant), la gestion du jeton dans `bridge/` si nécessaire
Dépend de : —

## Objectif

L'utilisateur garde l'app classique sur la Thor. La version jeu s'installe à côté, sous son propre applicationId (suffixe `.jeu`). Depuis le ticket 27, l'app génère le jeton du pont et le lui envoie par RUN_COMMAND. Il faut vérifier si le pont ne garde qu'un seul jeton. Dans ce cas, deux apps se déconnecteraient l'une l'autre, et le pont doit accepter plusieurs jetons.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Les deux apps sont installées et connectées au même pont, sans se gêner.
- Tests du pont.
- `npm run check` et `./gradlew check` verts.

## Rapport

À remplir à la fin du lot.
