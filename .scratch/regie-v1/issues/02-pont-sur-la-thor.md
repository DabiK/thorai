# 02 — Pont sur la Thor : sessions Claude et Codex exposées en WebSocket

Statut : à fusionner
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

- [x] Depuis le Mac, un petit client de test (via `adb forward`) lance une session Claude et une session Codex sur un projet de démo dans `~/projects`. Il reçoit leurs événements, répond à une validation de chacun, se déconnecte, se reconnecte et rattrape les événements manqués.
- [ ] Rien ne démarre au boot. Un RUN_COMMAND envoyé par adb, comme le fera l'app, démarre le pont ; un autre l'arrête. Après un redémarrage de la console puis un nouveau démarrage, l'historique est toujours listé.
- [x] Les tests unitaires du pont n'appellent pas les vrais agents. Un test de contrat, lancé à la demande sur la Thor, les appelle.

## Rapport

13 novembre 2026.

**Ce qui marche.** Le pont (`bridge/`, port 47600 sur 127.0.0.1, jeton en `Authorization: Bearer`) pilote Claude et Codex par ACP (ADR 02). Il tient plusieurs sessions des deux agents. Il journalise chaque événement avec un numéro global, synchronisé sur disque, et permet le rattrapage après une déconnexion ou un redémarrage du pont. Le contrat est dans `docs/protocole-pont.md`, les enregistrements réels dans `bridge/fixtures/bridge-ws/` (et les bruts ACP dans `bridge/fixtures/acp-raw/`).

**Critères.**
- [x] Client de démo (`thor/bridge/demo.sh`) : une session Claude et une Codex sur `~/projects/regie-demo`, une validation de chacune, déconnexion, rattrapage des numéros 619 à 658 sans trou ni doublon. Preuve : `artifacts/screenshots/02/demo-client.txt`.
- [~] Rien au boot (aucun Termux:Boot). Démarrage et arrêt par RUN_COMMAND vérifiés, mais l'intent part de l'uid de Termux (`thor/bridge/run-command.sh start|stop`) : `adb shell am startservice …` est refusé à l'uid `shell` (« Requires permission com.termux.permission.RUN_COMMAND »). L'app, qui aura la permission, enverra le même intent. Historique listé après un arrêt et un redémarrage du pont, et après un pont tué (SIGKILL, relance par le superviseur en 2 s). **Pas vérifié : le redémarrage de la console** (interdit sans prévenir l'utilisateur). Le verrou d'état périmé après un redémarrage est géré (`boot_id` + ligne de commande) et testé.
- [x] Tests unitaires sans vrai agent (81 tests, `npm run check`). Test de contrat `npm run test:contract`, lancé sur la Thor par `thor/bridge/contract-test.sh` : 4/4.

**Porte de qualité.** `npm run check` : Biome, `tsc` strict, Vitest 9 fichiers, 81 tests, vert.

**Relecture.** Trois tours. Corrigés : session utilisable avant son chargement et son mode (contournait « valider ») ; deux ponts sur le même état ; journal non synchronisé ; sortie Codex relayée en entier à chaque morceau (journal au carré) ; verrou périmé bloquant après redémarrage ; double `hello` ; interruption pendant un chargement ; commande RUN_COMMAND affichée fausse.

**Pour l'utilisateur : vérifier l'historique après un redémarrage.**
1. Redémarrer la console.
2. Vérifier que rien n'a démarré : `adb -s THOR0000000001 shell ps -A | grep -c regie-bridge` doit donner 0.
3. Démarrer le pont : `thor/bridge/run-command.sh start`.
4. Lister l'historique : `thor/bridge/demo.sh --list`. Les sessions de `regie-demo` doivent apparaître.
5. Arrêter : `thor/bridge/run-command.sh stop`.

**Questions.**
- ❓ Codex en mode « valider » demande une validation pour chaque commande, lectures comprises (pas de sandbox sous proot). Le pont doit-il accepter seul les commandes en lecture seule (`cat`, `rg`, `ls`) ?
- ❓ Le journal garde tout, sur disque et en mémoire (une session Claude triviale pèse environ 150 Ko, surtout la liste des skills). Quelle durée de conservation ?
- ❓ Créer une session avec une branche fait `git switch` dans la copie de travail partagée, même si une autre session travaille sur ce projet. Refuser, prévenir, ou passer par un worktree par session ?
- ❓ La liste des skills de l'utilisateur (noms et descriptions) figure dans les fixtures commitées. Acceptable dans le dépôt privé, ou à masquer ?

**Hors périmètre.** Une validation retirée par l'agent (`$/cancel_request`, hors `session/cancel`) reste « en attente » côté pont (constat de relecture non bloquant). Course théorique sur la reprise du verrou périmé par deux ponts sur deux ports différents. Les captures des écrans ne montrent rien du ticket (le pont n'a pas d'interface) : la preuve est `demo-client.txt`.
