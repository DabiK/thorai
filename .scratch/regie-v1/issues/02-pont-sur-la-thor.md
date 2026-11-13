# 02 — Pont sur la Thor : sessions Claude et Codex exposées en WebSocket

Statut : prêt
Bloqué par : rien
Possède : `bridge/`, `thor/bridge/`, `docs/protocole-pont.md`, `docs/adr/02-*`

## Objectif

Faire tourner dans la Debian de la Thor un pont qui démarre, garde et expose les sessions de Claude Code et de Codex. L'app pourra ainsi tout piloter par une connexion locale. **On réutilise, on n'écrit pas de harness** (ADR 01).

## À faire

1. **Essai sur la Thor, à faire en premier.**
   - Installer dans la Debian (utilisateur `dev`) les adaptateurs ACP `@zed-industries/claude-agent-acp` et `codex-acp`. Vérifier qu'ils réutilisent les connexions déjà faites (claude.ai et ChatGPT), sans clé d'API.
   - Dérouler une session par agent : `initialize`, `session/new`, une consigne qui lit un fichier et en modifie un autre (pour obtenir une demande de validation), la réponse à la validation, `session/load` après redémarrage du processus.
   - Relever ce qui est exposé : messages en flux, appels d'outils, diff, plan, modes, validations, reprise.
   - Si ACP ne tient pas, partir des adapters d'`~/Documents/agent-chat/src/adapters/harness/` (Agent SDK + `codex app-server`).
   - Consigner le choix et les écarts constatés dans `docs/adr/02-pilotage-des-agents.md`, avec les versions testées.
2. **Le pont** (`bridge/`, TypeScript strict, Node 24, porte de qualité `npm run check`) :
   - WebSocket sur `127.0.0.1` uniquement, protégé par un jeton.
   - Plusieurs sessions en parallèle, des deux agents.
   - Les sessions continuent quand l'app se déconnecte.
   - Chaque événement porte un numéro de séquence, pour que l'app rattrape ce qu'elle a manqué.
   - Liste des sessions actives et de l'historique, avec projet, branche et statut.
   - Création d'une session : agent, dossier de `~/projects`, branche éventuelle, mode de validation, consigne.
   - Envoi d'un message pendant que l'agent travaille, si l'agent le permet ; sinon le message attend son tour.
   - Interruption, réponse aux validations, versions des agents.
3. **Le contrat** : `docs/protocole-pont.md`, avec des enregistrements réels des deux agents dans `bridge/fixtures/`. Ce sont eux que la passerelle factice de l'app rejouera.
4. **Le jeton** : aucun secret en stockage partagé. Piste : `thor/bridge/install.sh` génère le jeton et l'écrit côté Termux et côté app par `run-as`, ce qui marche parce que les deux sont en build debug.
5. **Le démarrage, à la demande seulement.** L'utilisateur ne veut **rien au boot**, pas de Termux:Boot : c'est une app qu'il lance.
   - `thor/bridge/start.sh` et `stop.sh`, avec `termux-wake-lock` tant que le pont tourne.
   - Activation de RUN_COMMAND dans Termux (`allow-external-apps=true`), pour que l'app démarre et arrête le pont. Documenter la permission `com.termux.permission.RUN_COMMAND` à accorder à l'app.
   - Relance du pont s'il plante pendant que l'app tourne.
   - Arrêt propre sur demande : le bouton « Quitter » de l'app arrête les sessions puis le pont.
   - Journaux dans `~/.local/state/thor/`.
   
   Tout doit être scripté dans `thor/bridge/`.

## Critères d'acceptation

- Depuis le Mac, un petit client de test (via `adb forward`) lance une session Claude et une session Codex sur un projet de démo dans `~/projects`. Il reçoit leurs événements, répond à une validation de chacun, se déconnecte, se reconnecte et rattrape les événements manqués.
- Rien ne démarre au boot. Un RUN_COMMAND envoyé par adb, comme le fera l'app, démarre le pont ; un autre l'arrête. Après un redémarrage de la console puis un nouveau démarrage, l'historique est toujours listé.
- Les tests unitaires du pont n'appellent pas les vrais agents. Un test de contrat, lancé à la demande sur la Thor, les appelle.

## Rapport

(à remplir)
