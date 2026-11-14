# Protocole du pont

Le contrat entre l'app et le pont (ticket 02). Le pont tourne dans la Debian de la Thor. Il pilote Claude Code et Codex par ACP (ADR 02) et expose leurs sessions à l'app par un WebSocket local.

Le pont **relaie** ce que les agents envoient. Il ne réinvente pas leur schéma : chaque événement ACP est enveloppé (numéro, session, date, type) et corrigé quand l'ADR 02 le demande.

## Connexion

- Adresse : `ws://127.0.0.1:47600`. Le pont n'écoute que sur `127.0.0.1`, jamais sur `0.0.0.0`.
- Jeton obligatoire, dans l'en-tête de la requête d'ouverture :

  ```
  Authorization: Bearer <jeton>
  ```

- Sans jeton, ou avec un mauvais jeton : réponse HTTP `401`, la connexion est fermée avant toute donnée. La comparaison se fait à temps constant.
- `GET http://127.0.0.1:47600/health` répond `{"ok":true}`, sans jeton. Il dit seulement si le pont est démarré.
- Pendant les premiers instants du démarrage, une connexion reçoit `503` : réessayer.
- Le pont envoie un ping WebSocket toutes les 20 s. Un client qui ne répond pas est déconnecté.
- Messages en texte JSON, 1 Mo au plus. Un message binaire est refusé.

## Le jeton

- 32 octets aléatoires, écrits en hexadécimal (64 caractères).
- Créé une seule fois par `thor/bridge/install.sh`, puis gardé.
- Côté pont : `/home/dev/.config/regie/bridge-token` dans la Debian, en `600`. Le pont refuse de démarrer si le fichier manque ou si d'autres peuvent le lire.
- Côté app : `files/bridge-token` dans le stockage privé de `app.regie` (`context.filesDir`), en `600`. L'installation l'y copie par `adb shell run-as app.regie`.
- Le jeton ne passe jamais dans le dépôt, les journaux ou un stockage partagé (`/sdcard`, `/data/local/tmp`).
- Si l'app n'était pas installée lors de l'installation : `thor/bridge/install.sh token-app` copie seulement le jeton.

## Trames

Trois sortes de messages.

Requête du client, avec un `id` libre (nombre ou texte) :

```json
{"id": 4, "method": "session.create", "params": {"agent": "claude", "project": "regie-demo", "approvalMode": "ask", "prompt": "Lis notes.md"}}
```

Réponse du pont, avec le même `id`, soit `result`, soit `error` :

```json
{"id": 4, "result": {"session": {"sessionId": "bc4efdf5-…", "status": "working", "…": "…"}}}
{"id": 9, "error": {"code": "bad_project", "message": "Projet introuvable dans ~/projects : ../etc."}}
```

Événement du pont, sans `id` :

```json
{"event": {"seq": 98, "at": "2026-11-13T21:02:19.823Z", "sessionId": "01a1d6b7-…", "type": "approval_resolved", "data": {"approvalId": "ae0f5ff8-…", "outcome": {"outcome": "selected", "optionId": "allow_once"}, "by": "client"}}}
```

Les requêtes sont traitées en parallèle : les réponses peuvent arriver dans un autre ordre. Le client s'appuie sur `id`.

## Numéros de séquence et rattrapage

- Chaque événement a un `seq` : global à toutes les sessions, croissant de 1 en 1.
- Les événements sont écrits sur disque, et synchronisés (`fdatasync`), avant d'être envoyés. Après un redémarrage du pont ou de la console, la numérotation reprend où elle s'était arrêtée : ni trou, ni doublon.
- La purge des vieux événements (voir « Conservation de l'historique ») laisse des trous dans les numéros du journal, jamais de retour en arrière : un numéro n'est jamais réutilisé. Un client qui rattrape depuis un numéro purgé reçoit ce qui reste, dans l'ordre.
- Un seul pont par journal : un second pont lancé sur le même dossier d'état, ou sur le même port, s'arrête avant de lire ou d'écrire le journal. Le verrou (`bridge.lock`) note le PID, le `boot_id` et le script du pont : un verrou d'un démarrage précédent de la console, ou dont le PID sert maintenant à un autre programme, est repris.
- Le journal a un identifiant, `journal` dans la réponse à `hello`. Un nouveau journal (l'ancien effacé) a un nouvel identifiant.
- Le client garde le dernier `seq` reçu et l'identifiant du journal.
- À chaque connexion, il envoie `hello` avec ce numéro et cet identifiant. Le pont répond, puis envoie tous les événements qui suivent, dans l'ordre, puis les nouveaux en direct.
- Un seul `hello` par connexion : un second est refusé (`bad_request`).
- Sans `since`, le client ne reçoit que les nouveaux événements. Il lit l'état dans la réponse à `hello`.
- Si `journal` n'est pas celui du pont, ou si `since` dépasse son dernier numéro, la réponse porte `"resync": true`. Le pont n'envoie alors que les nouveaux événements : le client oublie ce qu'il sait et repart de la réponse à `hello`.
- Les sessions continuent sans client. Une validation attend sa réponse, même client déconnecté.

```json
{"id": 1, "method": "hello", "params": {"since": 100, "journal": "5b0f2c1e-…"}}
{"id": 1, "result": {"bridge": {"version": "0.1.0", "pid": 15645, "startedAt": "2026-11-13T21:01:40.496Z"}, "journal": "5b0f2c1e-…", "lastSeq": 106, "resync": false, "sessions": ["…"], "approvals": ["…"], "agents": [{"agent": "claude", "running": true, "authenticated": true}, {"agent": "codex", "running": true, "authenticated": true}]}}
{"event": {"seq": 101, "…": "…"}}
```

## Méthodes

| Méthode | Paramètres | Résultat |
| --- | --- | --- |
| `hello` | `since?`, `journal?` | état du pont, puis le flux d'événements (voir plus haut) |
| `sessions.list` | — | `{sessions}` : en cours et historique, la plus récente d'abord |
| `session.create` | `agent`, `project`, `branch?`, `approvalMode`, `prompt?` | `{session}` ; le pont ne change pas de branche (voir plus bas) |
| `session.prompt` | `sessionId`, `text` | `{delivery}` |
| `session.cancel` | `sessionId` | `{}` |
| `session.resume` | `sessionId` | `{session}`, rechargée dans son agent |
| `session.events` | `sessionId`, `since?` | `{events}` : le journal de cette session |
| `approval.answer` | `approvalId`, `optionId` | `{}` |
| `approvals.list` | — | `{approvals}` : validations en attente |
| `agents.versions` | — | `{agents}` : versions installées et testées |
| `projects.list` | — | `{projects}` : dossiers de `~/projects`, avec leur branche |
| `bridge.shutdown` | — | `{}`, puis le pont s'arrête (voir « Arrêt ») |

### Créer une session

```json
{"id": 4, "method": "session.create", "params": {"agent": "codex", "project": "regie-demo", "branch": "essai", "approvalMode": "ask", "prompt": "Lis notes.md et ajoute son résumé en une seule ligne à la fin de todo.md."}}
```

- `agent` : `claude` ou `codex`.
- `project` : un dossier de `~/projects`. Tout chemin qui en sort est refusé (`..`, chemin absolu, lien vers l'extérieur).
- `branch` (facultatif) : **le pont ne touche pas aux branches**, c'est l'agent qui les gère (ticket 13). Le pont met en tête du premier message envoyé à l'agent :

  ```
  Travaille sur la branche <branch>, crée-la depuis main si elle n’existe pas.
  ```

  suivi d'une ligne vide, puis du message. L'agent choisit comment faire (`git switch`, worktree…). Le premier message est la consigne de `prompt`, sinon le premier `session.prompt`, même s'il est injecté dans un tour (`steering`). Les messages suivants partent tels quels. Le nom doit être un nom de branche valide (`git check-ref-format --branch`), sinon : erreur `bad_request`, aucune session n'est créée.
- `approvalMode` : `plan`, `auto-edit`, `ask` (ou `allow-all` pour compatibilité) (voir « Modes de validation »).
- `prompt` (facultatif) : la consigne, envoyée dès la création.

### Parler à une session

```json
{"id": 12, "method": "session.prompt", "params": {"sessionId": "01a1d6b7-…", "text": "Arrête-toi après le premier fichier."}}
{"id": 12, "result": {"delivery": "steering"}}
```

`delivery` dit comment le message est parti :
- `prompt` : aucun tour en cours, un nouveau tour commence ;
- `steering` : un tour est en cours et l'agent a accepté le message dedans (`_session/steering`) ;
- `queued` : un tour est en cours et l'agent a refusé : le message attend la fin du tour ;
- `cancelled` : le message n'est pas parti, la session a été interrompue (`session.cancel`) pendant qu'il attendait, par exemple pendant son chargement.

Une session de l'historique (`active: false`) est rechargée d'abord (`session/load`, puis son mode), sans action du client. Les messages envoyés à une même session partent dans l'ordre où le pont les a reçus, et aucun ne part avant que la session soit chargée et son mode appliqué.

### Interrompre

`session.cancel` envoie `session/cancel`. Les messages qui attendaient encore (session en cours de chargement) ne partent pas : leur `delivery` vaut `cancelled`. Le tour se termine avec `stopReason: "cancelled"`. Les validations en attente de cette session sont réglées en `cancelled`. La session reste utilisable.

### Reprendre une session de l'historique

`session.resume` recharge la session dans son agent (`session/load`), puis réapplique son mode de validation. L'agent rejoue alors tout l'historique : le pont ne le relaie pas, il est déjà dans le journal (`session.events`).

## Événements

Enveloppe : `seq`, `at` (date ISO), `sessionId` (`null` pour le pont ou un agent), `type`, `data`.

| `type` | `data` |
| --- | --- |
| `session` | la session entière (voir « Sessions »), à chaque changement de statut, de titre ou de validations en attente |
| `session_update` | la mise à jour ACP (`update` de `session/update`), telle quelle sauf corrections (voir plus bas) |
| `user_message` | `{text, delivery, prefix?}` : un message envoyé à l'agent. `prefix` : la consigne de branche ajoutée en tête du premier message (voir « Créer une session ») ; l'agent a reçu `prefix`, une ligne vide, puis `text` |
| `approval_request` | une validation à donner (voir « Validations ») |
| `approval_resolved` | `{approvalId, outcome, by}` ; `by` vaut `client`, `cancel`, `agent_exit` ou `shutdown` |
| `turn_end` | `{stopReason, usage}` : fin d'un tour (`end_turn`, `cancelled`, `max_tokens`, `refusal`…) |
| `session_error` | `{message}` : le tour a échoué |
| `agent` | `{agent, running, authenticated, exit?}` : processus d'un agent démarré, connecté, arrêté |
| `bridge` | `{state}` : `started`, `stopping`, `stopped` |

Les `session_update` les plus courants (schéma ACP v1) :
- `agent_message_chunk` : un morceau de réponse (`content.text`). Codex envoie des morceaux très fins.
- `agent_thought_chunk` : la réflexion (Codex seulement).
- `tool_call` puis `tool_call_update` : une action de l'agent (`toolCallId`, `kind`, `title`, `status`, `content`, `locations`, `rawInput`).
- `session_info_update` : le titre ; pour Codex, `_meta.codex.threadStatus`.
- `usage_update`, `available_commands_update`, `config_option_update`, `current_mode_update`.

Silences : Claude peut rester 30 s sans rien envoyer pendant une longue réponse. Le statut `working` suffit pour afficher « en cours ».

### Corrections faites par le pont (ADR 02)

- **Sortie des commandes de Codex** : elle arrive par morceaux dans `_meta.terminal_output_delta`. Le pont la met dans `content` (`[{"type": "content", "content": {"type": "text", "text": "…"}}]`), avec `_meta.regie` :
  - `{"output": "delta"}` : le morceau reçu, seul. L'app **l'ajoute** à la sortie de l'appel ;
  - `{"output": "final"}`, à la fin de la commande (`terminal_exit`) : toute la sortie, une seule fois. L'app **remplace** ce qu'elle a reconstitué ;
  - la sortie finale, et chaque morceau, sont limités aux 64 Ko de la fin ; `"truncated": true` dit qu'il manque le début ;
  - le morceau brut est retiré de `_meta` : il n'est pas envoyé deux fois.

  Le contenu `terminal` (un terminal que le client n'a jamais créé) est retiré.
- **Échecs dans le sandbox de Codex** (sortie commençant par `bwrap:`) : le `tool_call` a déjà été envoyé. Le pont envoie alors à la place de l'échec :

  ```json
  {"sessionUpdate": "tool_call_update", "toolCallId": "exec-…", "status": "failed", "content": [], "_meta": {"regie": {"hidden": "sandbox_unavailable"}}}
  ```

  L'app **retire** cet appel du fil.
- **Diff de Codex** : la validation d'une écriture ne porte que `locations`. Le pont y remet le diff (`content: [{type: "diff", path, oldText, newText}]`) du `tool_call` qui a le même `toolCallId`.
- **Compte de l'agent** : `auth/status_update` et `_auth/status_update` contiennent l'e-mail du compte. Ils ne sont ni journalisés ni relayés. Il n'en reste que `authenticated` dans l'événement `agent`.

## Sessions

```json
{"sessionId": "00000000-0000-4000-8000-000000000013", "agent": "codex", "project": "regie-demo", "cwd": "/home/dev/projects/regie-demo", "branch": "essai", "requestedBranch": "essai", "approvalMode": "ask", "status": "idle", "title": "Résumer notes.md dans todo.md", "createdAt": "2026-11-13T21:02:09.969Z", "updatedAt": "2026-11-13T21:02:42.537Z", "lastError": null, "modifiedFiles": ["todo.md"], "purgedAt": null, "active": true, "pendingApprovals": []}
```

- `sessionId` : l'identifiant ACP, donné par l'agent.
- `branch` : la branche du dossier de la session (`cwd`), relue par le pont (`git symbolic-ref --short HEAD`) à la création, à la fin de chaque commande de l'agent, à la fin de chaque tour et au rechargement de la session. Un changement envoie l'événement `session`. `null` hors d'un dépôt git, ou sur un `HEAD` détaché. Si l'agent travaille dans un autre dossier (un worktree qu'il a créé ailleurs), `branch` reste celle de `cwd`.
- `requestedBranch` : la branche demandée à la création (`branch` de `session.create`), `null` sinon. Elle peut différer de `branch` : c'est l'agent qui s'y place.
- `title` : le titre donné par l'agent, sinon le début de la première consigne.
- `updatedAt` : la dernière activité de la session. Un arrêt du pont, qui la passe en `stopped`, n'en est pas une.
- `modifiedFiles` : les fichiers que l'agent a modifiés, relatifs au projet quand ils y sont : ceux de ses éditions terminées (diff ou `locations` d'un appel `edit`, `delete` ou `move`). Une écriture faite par une commande shell (`printf … >> todo.md`) n'y est pas.
- `purgedAt` : quand les événements de la session ont été purgés (voir « Conservation de l'historique »), `null` sinon.
- `active` : chargée dans un processus d'agent. `false` pour l'historique.
- `pendingApprovals` : les `approvalId` en attente.
- L'historique est sur disque : il survit à un redémarrage du pont et de la console.

### Conservation de l'historique

- Le détail d'une session (ses événements) est gardé **30 jours** après sa dernière activité (`updatedAt`).
- Au-delà, ses événements sont effacés du disque et de la mémoire du pont. La session reste dans `sessions.list` avec son résumé : titre, agent, projet, statut, dates, branche, fichiers modifiés. `purgedAt` dit quand. `session.events` ne rend plus que ce qui a suivi la purge.
- Une session purgée peut être reprise (`session.resume`, `session.prompt`) : l'agent garde son propre historique.
- Une session en cours de chargement, en cours (`working`) ou qui attend une validation n'est jamais purgée.
- Les événements sans session (`bridge`, `agent`) plus vieux que la durée sont purgés aussi.
- Le nettoyage tourne au démarrage du pont, puis toutes les 24 h.
- La durée se règle par `REGIE_BRIDGE_RETENTION_DAYS` (en jours, décimales permises, 30 par défaut). Une valeur qui n'est pas un nombre positif empêche le pont de démarrer. Le superviseur lance le pont par `bash -lc` : la variable peut se mettre dans `/home/dev/.profile` de la Debian.

### Statuts

| `status` | Dans l'app |
| --- | --- |
| `working` | en cours |
| `awaiting_approval` | attend ta validation |
| `idle` | en attente de consigne |
| `stopped` | arrêtée (pont arrêté ou redémarré) ; on la reprend par `session.resume` ou en lui parlant |
| `failed` | échec : tour en erreur, ou agent arrêté tout seul ; `lastError` dit pourquoi |

## Modes de validation

Trois niveaux produit, plus `allow-all` conservé pour les sessions existantes. Le pont les traduit en modes ACP, et les réapplique après chaque `session/load` :

| `approvalMode` | Claude | Codex |
| --- | --- | --- |
| `plan` (« plan d’abord ») | `plan` | `workspace-write` puis `collaboration_mode=plan` par `session/set_config_option` |
| `auto-edit` (« modifications automatiques ») | `acceptEdits` | `workspace-write` ; le domaine du pont accepte les demandes `kind: edit` une fois |
| `ask` (« tout valider ») | `default` | `workspace-write`, avec la consigne qui évite le sandbox (ADR 02) |
| `allow-all` (« tout autoriser ») | `bypassPermissions` | `agent-full-access` |

- Claude démarre en `auto`, le réglage de l'utilisateur : le pont force le mode juste après la création.
- Le mode `agent` de Codex n'est jamais utilisé.
- En `auto-edit`, seules les éditions structurées sont acceptées automatiquement, jamais une écriture shell. Les choix durables ne sont pas utilisés. La fiche de lancement du ticket 11 proposera les trois niveaux produit ; `allow-all` reste compatible avec les sessions existantes (ADR 08).
- En `ask`, Codex demande aussi une validation pour chaque lecture (`cat`, `rg`). Le pont accepte seul les commandes en lecture seule (voir « Lectures acceptées par le pont »).

### Lectures acceptées par le pont

En `ask`, `plan` et `auto-edit`, le pont répond seul à une demande de validation (ticket 13) quand toutes ces conditions tiennent :
- l'appel est une commande shell : `kind: "execute"`, la commande dans `rawInput.command`. C'est la forme des deux agents : `Bash` de Claude, exec de Codex. Une édition, un plan ou un outil MCP ne relève pas de cette règle de lecture ; les éditions structurées en `auto-edit` suivent la règle distincte décrite plus haut ;
- `rawInput` ne porte que la commande et sa description (`command`, `cwd`, `description`, `command_description`, `timeout`, `run_in_background`). Un accès réseau (`url`) ou des permissions en plus (`additionalPermissions`) demandés par Codex remontent ;
- l'agent propose une option `allow_once` : le pont choisit celle-là, jamais « toujours » ;
- la commande ne fait que lire (règle ci-dessous).

La demande n'est alors pas relayée : ni `approval_request`, ni `approval_resolved`. L'appel reste dans le fil comme une action ordinaire (`session_update` `tool_call`, puis ses `tool_call_update`). Le journal du pont le note : `read-only command approved by the bridge: <commande>`.

La règle est stricte : dans le doute, la commande remonte.
- Programmes acceptés : `cat`, `ls`, `head`, `tail`, `wc`, `nl`, `grep`, `rg`, `find`, `pwd`, `cd`, `echo`, `which`, `stat`, `sed -n` avec un script d'affichage seul (`12p`, `1,40p`), `git status`, `git log`, `git diff`, `git show`, `git rev-parse`, `git branch` sans nom (liste seulement). Tout autre programme remonte, comme un programme appelé par son chemin (`/bin/cat`).
- Options refusées : `find` avec `-exec`, `-execdir`, `-ok`, `-okdir`, `-delete`, `-fprint`, `-fprint0`, `-fprintf`, `-fls` ; `rg` avec `--pre`, `--hostname-bin`, `-z`, `--search-zip` ; `git` avec une option avant la sous-commande (`-c`, `-C`, `--git-dir`…), `--output`, `--ext-diff`, `--textconv`, abréviations comprises (`--out=…`). Les autres programmes de la liste n'ont pas d'option qui écrit ou lance un programme ; `file` et `tree` n'y sont pas, car leurs options d'écriture s'abrègent (`file --comp`, `tree --output`).
- Motifs : pour `find`, `rg`, `git` et `sed`, un motif non protégé remonte, car il pourrait devenir une option (`-[e]xec` vaut `-exec` si un fichier de ce nom existe). Sont des motifs : `*`, `?`, un `[` n'importe où dans la ligne, un `^` en début de mot (zsh).
- Enchaînements : `&&`, `||`, `;`, `|` et les retours à la ligne entre commandes qui lisent toutes. Une seule qui écrit, et tout remonte.
- Remontent toujours : une redirection (`>`, `>>`, `<`, `2>&1`, here-doc), une substitution (`$(…)`, `` `…` ``, `<(…)`, `>(…)`), une variable (`$X`, `${X}`, même entre guillemets doubles), une tâche de fond (`&`), un sous-shell ou un groupe (`(…)`, `{ …; }`), une affectation (`X=1 cmd`), un commentaire, un guillemet non fermé, et pour zsh (le shell de l'outil `Bash` de Claude peut l'être) un mot qui commence par `=` (`=rm`) ou un qualificateur de motif (`*(…)`, refusé avec les parenthèses).
- `bash -c '…'`, `bash -lc '…'`, `sh -c '…'` : le script est jugé avec la même règle.

Le découpage de la commande passe par `shell-quote` 1.12 (adapter `adapters/shell/`). Les tests de la règle, commande par commande, sont dans `bridge/src/adapters/shell/shell-quote-parser.test.ts`.

## Validations

```json
{"approvalId": "6011c239-…", "sessionId": "bc4efdf5-…", "toolCall": {"toolCallId": "toolu_01Qr…", "kind": "edit", "status": "pending", "title": "Edit todo.md", "content": [{"type": "diff", "path": "/home/dev/projects/regie-demo/todo.md", "oldText": "…", "newText": "…"}], "locations": [{"path": "/home/dev/projects/regie-demo/todo.md"}]}, "options": [{"optionId": "allow-once", "name": "Yes", "kind": "allow_once"}, {"optionId": "allow-with-updates", "name": "Yes, allow all edits during this session", "kind": "allow_always"}, {"optionId": "reject", "name": "No", "kind": "reject_once"}], "plan": null}
```

- `toolCall` : l'appel d'outil ACP (commande dans `rawInput.command` ou `title`, diff dans `content`).
- `options` : celles de l'agent. L'app les présente par leur `kind` : `allow_once` (autoriser une fois), `allow_always` (toujours dans cette session), `reject_once` (refuser). Elle répond avec l'`optionId`.
- L'option Codex `accept_execpolicy_amendment` (« ne plus demander pour les commandes qui commencent par… ») est retirée : elle écrirait une règle dans la config de Codex.
- `plan` : quand l'agent demande à sortir de son mode plan (`kind: "switch_mode"`), le texte du plan en markdown. L'app l'affiche comme un plan à approuver.
- Réponse : `{"method": "approval.answer", "params": {"approvalId": "6011c239-…", "optionId": "allow-once"}}`. Une option que l'agent n'a pas proposée est refusée (`unknown_option`).

## Versions

```json
{"agent": "codex", "adapter": {"name": "@agentclientprotocol/codex-acp", "version": "2.6.0"}, "binary": "codex-cli 0.167.2", "tested": {"adapter": "2.6.0", "binary": "0.167.2"}, "isTested": true, "running": true, "authenticated": true}
```

- `binary` : la sortie de `claude --version` ou `codex --version`.
- `isTested: false` : l'app avertit que la version installée n'est pas celle testée (ADR 02).
- `authenticated` : `null` tant que l'agent n'a pas démarré.

## Erreurs

| `code` | Quand |
| --- | --- |
| `bad_request` | JSON invalide, méthode inconnue, paramètre manquant ou invalide (nom de branche invalide compris) |
| `unknown_session` | `sessionId` inconnu |
| `unknown_approval` | la validation n'attend plus (déjà réglée, ou tour interrompu) |
| `unknown_option` | `optionId` non proposé par l'agent |
| `bad_project` | projet hors de `~/projects` ou introuvable |
| `agent_failed` | l'agent n'a pas démarré, ou a refusé la demande (le message de l'agent suit) |
| `shutting_down` | le pont s'arrête |
| `internal` | erreur inattendue, détaillée dans le journal du pont |

`message` est en français, lisible par l'utilisateur.

## Démarrage et arrêt

Rien ne démarre au boot. L'app démarre le pont à son lancement et l'arrête quand on quitte.

### Les commandes

L'app envoie l'intent RUN_COMMAND de Termux :

```
am startservice --user 0 -n com.termux/com.termux.app.RunCommandService \
  -a com.termux.RUN_COMMAND \
  --es com.termux.RUN_COMMAND_PATH /data/data/com.termux/files/home/bin/regie-bridge-start \
  --ez com.termux.RUN_COMMAND_BACKGROUND true
```

- Démarrer : `/data/data/com.termux/files/home/bin/regie-bridge-start`.
- Arrêter : `/data/data/com.termux/files/home/bin/regie-bridge-stop`.
- Depuis l'app : un `Intent` avec l'action `com.termux.RUN_COMMAND`, le composant `com.termux/com.termux.app.RunCommandService`, les extras `com.termux.RUN_COMMAND_PATH` (texte) et `com.termux.RUN_COMMAND_BACKGROUND` (`true`), envoyé par `startService` (ou `startForegroundService`).
- RUN_COMMAND coupe la sortie vers 100 Ko et ne sert qu'à démarrer et arrêter. Le suivi passe par le WebSocket.

### Ce qu'il faut accorder

- Dans Termux : `allow-external-apps=true` dans `~/.termux/termux.properties`. L'installation le règle.
- Dans l'app : déclarer `<uses-permission android:name="com.termux.permission.RUN_COMMAND" />`. L'utilisateur l'accorde une fois : Infos de l'appli > Autorisations > Autorisations supplémentaires > « Run commands in Termux environment ».
- Sans cette permission, Android refuse l'intent : `Error: Requires permission com.termux.permission.RUN_COMMAND`. C'est ce que répond `adb shell am startservice …` : l'utilisateur `shell` n'a pas la permission. Depuis le Mac, `thor/bridge/run-command.sh start|stop` envoie le même intent avec l'uid de Termux (son `am`, par `run-as`).

### Démarrage

`regie-bridge-start` :
- ne fait rien si le pont tourne déjà ;
- attend la fin d'un arrêt en cours, puis démarre (même verrou que `regie-bridge-stop`) ;
- lance le superviseur `regie-bridge-supervise`, détaché ;
- attend que `/health` réponde (30 s au plus) et le dit.

Le superviseur :
- prend le verrou de veille (`termux-wake-lock`) tant que le pont tourne ;
- lance le pont dans la Debian, comme `dev`, avec les montages de l'ADR 02 ;
- le relance s'il plante, après 1, 2, 4… jusqu'à 30 s ; il abandonne après 5 plantages en 2 minutes ;
- s'arrête quand le pont s'arrête proprement.

Le pont lance un processus d'adaptateur par agent, au premier usage. Chaque processus tient toutes les sessions de son agent.

### Arrêt

`regie-bridge-stop`, ou `bridge.shutdown` par le WebSocket :
- le pont interrompt les tours en cours (`session/cancel`) et règle les validations en attente ;
- il ferme les adaptateurs : stdin fermé, puis SIGTERM au bout de 4 s, puis SIGKILL ;
- il s'arrête, et le superviseur avec lui ;
- le verrou de veille est rendu, sauf si les autres services de la Thor (`thor-services`) tournent encore : ils le partagent.

`regie-bridge-stop` ne touche qu'aux processus du pont, par leurs PID enregistrés. Jamais de `proot-distro kill` global : d'autres services tournent dans la Debian.

Les sessions arrêtées restent dans l'historique, en `stopped`.

### Fichiers

| Quoi | Où |
| --- | --- |
| Pont installé | Debian : `/home/dev/.local/share/regie/bridge/` |
| Sessions et journal d'événements | Debian : `/home/dev/.local/state/regie/bridge/` (`sessions.json`, `events.jsonl` dont la première ligne nomme le journal et, après une purge, garde le dernier numéro `lastSeq`, `bridge.pid`, `bridge.lock`) |
| Jeton du pont | Debian : `/home/dev/.config/regie/bridge-token` |
| Scripts | Termux : `~/bin/regie-bridge-start`, `regie-bridge-stop`, `regie-bridge-supervise` |
| Journal du pont et du superviseur | Termux : `~/.local/state/thor/regie-bridge.log` (le précédent en `.1` au-delà de 5 Mo) |
| PID du superviseur | Termux : `~/.local/state/thor/regie-bridge-supervisor.pid` |

Réglages du pont, par variables d'environnement : `REGIE_BRIDGE_PORT`, `REGIE_BRIDGE_TOKEN_FILE`, `REGIE_BRIDGE_STATE_DIR`, `REGIE_PROJECTS_DIR`, `REGIE_BRIDGE_RETENTION_DAYS` (voir « Conservation de l'historique »), `CLAUDE_CODE_EXECUTABLE`, `CODEX_PATH` (voir `bridge/src/main.ts`).

## Depuis le Mac

- Installer ou mettre à jour : `thor/bridge/install.sh`. Idempotent.
- Client de démo : `thor/bridge/demo.sh` (pont démarré). Il passe par `adb forward tcp:47600 tcp:47600`, lance une session Claude et une Codex sur `~/projects/regie-demo`, répond aux validations, se déconnecte, se reconnecte et vérifie le rattrapage. Il remet le projet de démo à son premier commit.
- Test de contrat, avec les vrais agents : `thor/bridge/contract-test.sh`.

## Enregistrements

- `bridge/fixtures/bridge-ws/` : le flux réel du pont, enregistré par le client de démo sur la Thor. E-mails caviardés.
  - `claude-session.jsonl`, `codex-session.jsonl` : les événements d'une session, un par ligne, avec une validation.
  - `demo-run.jsonl` : toute la démo, dans les deux sens (`{t, dir, frame}`), déconnexion et rattrapage compris.
- `bridge/fixtures/acp-raw/` : les messages ACP bruts des deux adaptateurs (essai de l'ADR 02).

La passerelle factice de l'app rejoue `bridge/fixtures/bridge-ws/`.
