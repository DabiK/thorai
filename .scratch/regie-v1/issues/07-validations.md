# 07 — Validations

Statut : bloqué
Bloqué par : 06

## Objectif

Écran 4 de la maquette : répondre aux demandes de validation des agents.

## À livrer

- **En haut** : ce que l'agent veut faire (commande ou fichier), pourquoi s'il le dit, ce que ça touche, et la file des autres demandes.
- **En bas** :
  - A : autoriser une fois ;
  - X : toujours dans cette session, si l'agent le permet ;
  - B : refuser.
- **Refus avec consigne dictée** : branché au ticket 08. Ici, préparer le cas d'usage.
- **Niveau de validation au lancement d'une session** (plan d'abord, modifications automatiques, tout valider), traduit vers les modes de chaque agent.
- **Notification Android** quand une validation arrive alors que l'app n'est pas au premier plan.

## Critères d'acceptation

- [ ] Sur la Thor, une commande et une modification de fichier sont validées ou refusées pour chacun des deux agents, et l'agent reprend en conséquence. Non essayé : préparation Android partielle, arrêt sur erreur du lanceur de tests du pont.


## Rapport

STATUT: failed

Reprise solo Codex du 14 novembre 2026. Le blocage SDK est levé : la porte Android initiale et la porte après les changements passent. Le ticket reste `bloqué` : l’interface et les essais de bout en bout ne sont pas livrés.

### Travail conservé, partiel

- `Approval` conserve la commande exacte (retours à la ligne compris), la justification, le dossier, toutes les cibles, le plan, les diffs et les choix exposés.
- `AgentGateway` expose la liste complète des validations et `answerApproval`. La passerelle réelle envoie `approval.answer` ; une coupure sans accusé donne `UNKNOWN`. La passerelle factice permet une réponse locale sans appeler un agent.
- `AnswerApproval` prépare le refus avec consigne du ticket 08 : refuse d’abord, transmet ensuite la consigne uniquement après un accusé positif. Il empêche le second envoi d’une décision déjà envoyée, y compris sans accusé. **Ce cas d’usage n’est pas encore branché dans l’app.**
- La sélection X ne retient que `allow_for_session`, lorsque proposé. `accept_execpolicy_amendment` et `allow-with-updates` ne sont pas présentés comme des autorisations de session. Le code installé de `claude-agent-acp` 0.107.0 (`permissions/effects.js`, `permissions/options/shared.js`) confirme que `allow-with-updates` applique un changement durable ; son seul `kind: allow_always` ne suffit pas.
- Tests écrits et exécutés rouges avant les changements, puis verts. Trois nouveaux tests : conservation des détails, identifiants envoyés et accusé perdu, sélection non persistante et absence de double envoi. Le refus avec consigne, les réponses positives et la passerelle factice restent à compléter en couverture avant branchement UI.

### Échec d’environnement et arrêt

Le dossier `bridge/node_modules` était absent. J’ai exécuté `npm ci --omit=optional --no-fund --no-audit` : installation réussie, mais ce choix a également omis les dépendances natives du lanceur de tests. C’est une erreur de préparation de ma part : l’option issue de l’installation de production sur la Thor ne convient pas aux tests du Mac.

`cd bridge && npm test -- src/domain/bridge.test.ts` échoue avant les tests. Extraits exacts :

```text
Error: Cannot find native binding. npm has a bug related to optional dependencies (https://github.com/npm/cli/issues/4828). Please try `npm i` again after removing both package-lock.json and node_modules directory.
  cause: Error: Cannot find module '@rolldown/binding-wasm32-wasi'
    cause: Error: Cannot find module './rolldown-binding.wasi.cjs'
```

Sortie complète : `artifacts/screenshots/07/red-modes.txt`. Installation : `artifacts/screenshots/07/npm-ci.txt`. Aucun nouvel essai du pont, conformément à « Une commande qui échoue pour une raison d’environnement […] : n’insiste pas ». Le test des modes encore non implémentés a été retiré ; aucun fichier de `bridge/` n’est modifié. Le verrou npm est intact.

### Vérifications et limites

- `cd android && ./gradlew check` : **184 tests et lint verts**, sortie `artifacts/screenshots/07/android-check-reprise.txt`.
- `npm run check` : non lancé après l’échec préalable du lanceur de tests ; aucune porte verte revendiquée pour le pont.
- Aucune installation sur la Thor, aucun agent lancé, aucun geste sur la console.
- Maquettes `Validation.dc.html` et `Lancer.dc.html` absentes localement, outil Artifact indisponible et lien claude.ai inaccessible par l’outil web. Aucune conformité visuelle revendiquée.
- Documentation actuelle consultée par Context7 : notifications Android et ACP `session/set_config_option` (sorties dans ce dossier de preuves). Aucun code de notification ou de mode n’est livré.
- Relecture solo : aucun critère coché, aucune règle métier ajoutée dans un adapter, aucune permission durable exposée comme permission de session. Nettoyage des commentaires et du format. Aucun ADR créé : la restriction aux seules permissions de session découle du ticket.

### Reprise technique

1. Lors d’une nouvelle reprise, installer les dépendances de développement du pont **avec les dépendances facultatives de la plateforme Mac**, sans supprimer ni régénérer `package-lock.json` ; puis vérifier `npm run check`.
2. Compléter les tests de `AnswerApproval` (refus et consigne, autorisation de session, réponse positive, requête disparue, concurrence) et les réponses de transport. Traiter distinctement `unknown_approval` / `unknown_option` avant de brancher l’UI.
3. Brancher l’écran 4, la file complète, A/X/B, R1 et les modes un/deux écrans. Le cas vide `OpenApproval` reste inchangé ; aucune action de validation n’est encore accessible dans l’app.
4. Implémenter les trois niveaux de lancement et leurs traductions, en conservant les anciens modes de sessions enregistrées. Codex expose `collaboration_mode=plan` par `session/set_config_option` ; les modes actuels du pont restent `ask` et `allow-all`.
5. Implémenter les notifications via un port ; le bas reste visible en arrière-plan. Ajouter la permission Android et le chemin d’ouverture depuis la notification.
6. Exécuter les portes, installer avec `scripts/thor-install.sh`, vérifier sur la Thor et capturer avec `scripts/thor-shot.sh artifacts/screenshots/07`. Toute nouvelle session réelle doit être Codex ; arrêter celles lancées par l’essai.

### Vérification avec l’utilisateur, après implémentation

1. Accorder les permissions demandées par Régie, notamment les notifications.
2. Pour chaque agent, demander une commande puis une édition ; ouvrir la validation avec R1, accepter par A, vérifier l’action et la reprise du fil.
3. Répéter avec B et vérifier que l’action refusée n’a pas eu lieu. Essayer X uniquement lorsqu’une option limitée à la session est proposée ; vérifier qu’une nouvelle session demande à nouveau.
4. Passer Régie en arrière-plan, provoquer une validation, vérifier la notification, son ouverture et le maintien du bas.

Aucun critère d’acceptation tenu ; ne pas fusionner cette préparation comme livraison du ticket 07.
