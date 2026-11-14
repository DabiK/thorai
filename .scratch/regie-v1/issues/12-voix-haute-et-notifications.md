# 12 — Points écrits et notifications

Statut : à fusionner
Bloqué par : 04, 05

## Objectif

Savoir où en sont ses sessions sans tout relire, et être prévenu des décisions à prendre.

**Pas de lecture à voix haute** : l'utilisateur n'en veut pas (14 novembre). La synthèse vocale du module `voice/` n'est pas branchée dans l'app.

## À livrer

- **Résumés courts écrits**, affichés dans l'app :
  - à la fin d'une session : ce qui a été fait, les tests, ce qui reste ;
  - sur demande, avec Y depuis la vue d'ensemble ou une session : un point d'avancement.
  
  Ils sont rédigés à partir de ce que l'agent a exposé. Les générer avec un petit modèle ou les demander à l'agent : à trancher dans un ADR, en épargnant le quota Claude (Codex ou modèle local de préférence).
- **Notifications Android** pour les décisions seulement : validation demandée, question de l'agent, fin de session, échec. Un toucher sur la notification ouvre la bonne session.

## Critères d'acceptation

- [ ] Sur la Thor, une validation demandée alors que Régie est en arrière-plan produit une notification qui ouvre la session.
- [x] La fin d'une session affiche son résumé en moins de 10 lignes.
- [x] Y affiche un point d'avancement de la session sélectionnée.


## Rapport

- Résumés écrits demandés à l'agent dans son tour normal (ADR 10), sans tour supplémentaire ni synthèse vocale. Repli explicite en l'absence de bilan. Y depuis la carte ouvre la bonne session ; fin de tour affiche le point automatiquement en moins de 10 lignes.
- Notifications : validations conservées, questions/fins/échecs ajoutés derrière un port ; aucun bruit de progression, pas de rafale sur l'historique, retentative si canal/permission bloqué, intents distincts par session.
- Porte Android : 230 tests et lint verts. Pont : 270 tests, typage/lint verts. Tests sans agent/service/console réels.
- Thor : APK installé, un Codex réel, quatre tours ; manette sendevent, un/deux écrans, bilan automatique, Y, intents session et validation, arrière-plan avec bas visible. Aucun Claude ni permission accordée, agent d'essai arrêté par Quitter.
- Critère notification réelle non démontré : POST_NOTIFICATIONS refusée avec USER_FIXED. Statut à fusionner selon la consigne nocturne, tout ce qui ne dépend pas de l'utilisateur est vert. Pas à pas précis dans `artifacts/screenshots/12/essai-thor.txt`.
- Captures à regarder : 07–11, puis 16–18 (version finale, question et un/deux écrans). Journal agent réel : `codex-events.json`. Bilan de l'agent attribué, non considéré comme preuve indépendante de ses tests.
- Relecture de `git diff main...HEAD` après 2d2e5b1 : quatre régressions rouges puis vertes corrigées (titre tardif rouvrant le point, question absente du panneau, ancien marqueur fermant un bilan incomplet, notification d’échec attendant le journal). Test Compose supplémentaire : moins de 10 lignes, champs longs et question compris. APK corrigé réinstallé, ouverture à froid et Y vérifiés ; même session Codex reprise puis arrêtée par Quitter. Aucun test désactivé ; diff et maquette relus, aucune règle métier ajoutée aux adapters.
