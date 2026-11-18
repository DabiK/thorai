# 40 — Vue jeu : épopée

Statut : en cours
Possède : la branche `ticket/40-vue-jeu`, `docs/rebrand/`

## Objectif

Demande de l'utilisateur, 18 novembre : remplacer toute l'interface de ThorAI par un jeu d'exploration en pixel art, vu de dessus (direction Aube). Chaque maison est une session, et chaque rue un projet. Le moteur (pont, agents, domaine) ne change pas : seule la vue change. La branche `ticket/40-vue-jeu` part de main et va jusqu'au bout sans jamais être fusionnée dans main.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Lots

41 socle · 42 assets · 43 hameau procédural · 44 coursier et déplacements · 45 hameau, vue d'ensemble · 46 maison = session · 47 validation et modifications · 48 bâtir et agrandir · 49 titre, menu, courrier, console · 50 cohabitation sur la Thor · 51 retrait du classique.

## Critères d'acceptation

- Tous les écrans de la page 3 du canvas existent dans l'app, à la manette, sur les deux écrans.
- La vue classique est retirée.
- `./gradlew check` et `npm run check` sont verts.
- Captures de la Thor dans `docs/images/jeu/`.

## Rapport

À remplir à la fin du lot.
