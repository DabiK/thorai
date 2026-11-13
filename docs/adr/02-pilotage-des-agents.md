# ADR 02 — Pilotage des agents par ACP

Statut : accepté, 13 novembre 2026 (ticket 02).

## Contexte

L'ADR 01 laissait deux candidats pour piloter Claude Code et Codex depuis le pont :
- **ACP** (Agent Client Protocol), avec les adaptateurs maintenus ;
- **en repli**, les adapters d'`agent-chat` (Agent SDK pour Claude, `codex app-server` pour Codex).

Un essai sur la Thor devait les départager. Il s'est fait le 13 novembre 2026, dans la Debian (proot, utilisateur `dev`), sur un projet de démo `~/projects/regie-demo`. Le client d'essai enregistre chaque message JSON-RPC, dans les deux sens. Les enregistrements caviardés sont dans `bridge/fixtures/acp-raw/`.

## Décision

**Le pont pilote les deux agents par ACP v1**, avec :
- `@agentclientprotocol/claude-agent-acp` pour Claude ;
- `@agentclientprotocol/codex-acp` pour Codex.

On n'utilise pas le repli `agent-chat` :
- ACP tient pour les deux agents sur la Thor, sans clé d'API : les connexions claude.ai et ChatGPT existantes sont réutilisées.
- Un seul schéma couvre les deux agents : messages en flux, appels d'outils, diff, validations, modes, reprise, interruption, message pendant un tour.
- `codex-acp` 2.x est déjà un adaptateur au-dessus de `codex app-server`. Le repli referait ce travail.
- Le pont se réduit à multiplexer des sessions ACP sur le WebSocket, et à compenser les écarts listés plus bas.

### Versions testées

| Élément | Version |
| --- | --- |
| Node (Debian) | 24.22.3, npm 11.19.1 |
| `@agentclientprotocol/claude-agent-acp` | 0.107.0 (embarque `@anthropic-ai/claude-agent-sdk` 0.3.323) |
| `@agentclientprotocol/codex-acp` | 2.6.0 (embarque `@openai/codex` 0.166.0) |
| `@agentclientprotocol/sdk` (client) | 2.6.0 |
| `zod` | 4.6.7 |
| Claude Code (binaire utilisé) | 2.1.322, `/home/dev/.local/bin/claude` |
| Codex (binaire utilisé) | 0.167.2, `/usr/local/bin/codex` |
| Protocole | ACP v1 (`protocolVersion: 1`) |

Paquets écartés : `@zed-industries/claude-agent-acp` (0.23.1) et `@zed-industries/codex-acp` (0.16.0) sont dépréciés au profit des paquets `@agentclientprotocol/*`.

Le SDK 2.6.0 n'a plus `ClientSideConnection`. Le client s'écrit avec `acp.client({ name }).onRequest(...).onNotification(...).connect(acp.ndJsonStream(out, in))`, puis `conn.agent.request(...)` et `conn.agent.notify(...)`. On importe `@agentclientprotocol/sdk/unstable`, car les adaptateurs utilisent des parties instables (`session/list`, `usage_update`, `session_info_update`…).

### Installation

Dans la Debian, dans un dossier dédié, versions exactes :

```bash
mkdir -p ~/.local/share/regie/bridge && cd ~/.local/share/regie/bridge
npm install --save-exact --no-fund --no-audit \
  @agentclientprotocol/claude-agent-acp@0.107.0 \
  @agentclientprotocol/codex-acp@2.6.0 \
  @agentclientprotocol/sdk@2.6.0 zod@4.6.7
```

L'essai a installé dans `~/.local/share/regie/bridge-spike`. Ce dossier reste en place.

### Lancement

Le pont tourne dans la Debian, comme les lanceurs `~/bin/claude` et `~/bin/codex` de Termux :

```bash
proot-distro login debian --user dev \
  --bind "$HOME/projects:/home/dev/projects" \
  --bind "$HOME/.codex:/home/dev/.codex" -- …
```

- `HOME=/home/dev` : les identifiants de Claude sont dans `/home/dev/.claude`.
- Le montage de `~/.codex` apporte `auth.json` et `config.toml` à Codex.

Chaque adaptateur est un sous-processus en stdio :

```bash
# Claude
CLAUDE_CODE_EXECUTABLE=/home/dev/.local/bin/claude NO_BROWSER=1 \
  node node_modules/@agentclientprotocol/claude-agent-acp/dist/index.js

# Codex
CODEX_PATH=/usr/local/bin/codex NO_BROWSER=1 \
INITIAL_AGENT_MODE=workspace-write \
CODEX_CONFIG='{"developer_instructions":"Sur cette machine, le sandbox Linux (bubblewrap) est indisponible : toute commande lancée dans le sandbox échoue. Lance donc chaque commande shell directement avec sandbox_permissions=\"require_escalated\" et une justification courte, sans essai préalable dans le sandbox."}' \
  node node_modules/@agentclientprotocol/codex-acp/dist/index.js
```

- `CLAUDE_CODE_EXECUTABLE` et `CODEX_PATH` font utiliser les binaires de la console, pas ceux embarqués.
- `NO_BROWSER=1` masque la connexion par navigateur. Aucun `authenticate` n'est nécessaire.
- `codex-acp` lance son propre `codex app-server` en stdio. Il n'utilise pas le démon app-server du Codex interactif.
- Un même processus d'adaptateur tient **plusieurs sessions en parallèle**. Testé pour les deux agents : deux sessions, deux consignes simultanées, des réponses correctement séparées par `sessionId`.
- Temps mesurés :
  - `initialize` : 0,85 s pour Claude, 0,3 s pour Codex ;
  - `session/new` : 0,8 à 1,1 s pour les deux.

### Modes de validation de Codex

Bubblewrap ne marche pas sous proot :

```
bwrap: Can't bind mount /dev/null on null: readlink(/proc/self/fd/N) for "/proc/self/fd/4": Invalid argument
```

Toute commande lancée dans le sandbox échoue donc. Le Landlock historique est refusé lui aussi (« filesystem-restricted execution requires bubblewrap »). L'adaptateur impose ses propres modes ; il ne lit pas `sandbox_mode` ni `approval_policy` de `~/.codex/config.toml`.

| Mode | Résultat sur la Thor |
| --- | --- |
| `agent` (défaut de l'adaptateur) | Chaque commande échoue d'abord dans bwrap. Puis un « Guardian Review » (un `tool_call` de kind `think`) l'approuve seul. L'utilisateur n'est presque jamais sollicité. **Écarté.** |
| `read-only`, `workspace-write` seuls | Les premières commandes échouent dans bwrap, puis Codex demande l'escalade : une validation par commande. |
| **`workspace-write` + consigne `developer_instructions`** | **Retenu pour « valider »**. Aucun échec bwrap, pas de Guardian. Une `session/request_permission` pour chaque commande shell et chaque écriture. Le mode tient après `session/load` grâce à `INITIAL_AGENT_MODE`. Enregistrements : `codex-session-untrusted.jsonl` et `codex-load-untrusted.jsonl`. |
| **`agent-full-access`** | **Retenu pour « tout autoriser »** : approval `never` et `danger-full-access`. Aucun échec, aucune validation. Enregistrement : `codex-session-full-access.jsonl`. |
| Profil `permission-profile:regie` (`":root"="write"`, réseau ouvert, déclaré par un wrapper `codex -c`) | Pas de bwrap, mais aucune validation : il revient au même que `agent-full-access`. Inutile. |
| `approval_policy="untrusted"` | **Retiré par Codex** : « approval_policy = "untrusted" is no longer supported ». |

Conséquences du mode « valider » :
- Les lectures (`cat`, `rg`) demandent aussi une validation : pas de sandbox, donc tout passe par l'escalade.
- Le pont peut accepter seul les commandes en lecture seule. C'est une règle produit à trancher avec l'utilisateur.
- Si le modèle oublie la consigne, il retombe sur un échec bwrap puis une escalade. Rien ne casse, mais l'appel `failed` doit être masqué.

### Ce que chaque agent expose

| Élément | Claude | Codex |
| --- | --- | --- |
| `loadSession`, `session/list`, `resume` | oui | oui |
| Message en flux (`agent_message_chunk`) | oui | oui, en morceaux très fins |
| Réflexion (`agent_thought_chunk`) | jamais vue | oui |
| Appels d'outils | `kind` read, edit, execute, switch_mode, other ; `title` lisible ; `rawInput` complété en plusieurs updates ; sortie dans `rawOutput` et `content` | `kind` execute, read, edit, think, switch_mode ; `title` = la commande ; sortie dans `_meta.terminal_output_delta` et `_meta.terminal_exit` |
| Diff | `content: [{type:"diff", path, oldText, newText}]`, dans le `tool_call_update` et dans la validation | même forme, mais seulement dans le `tool_call` « Editing files » |
| `plan` (session update) | jamais vu, TodoWrite absent | jamais vu, `update_plan` absent |
| Plan | mode `plan` : validation « Approve Plan » (`switch_mode`, `rawInput.plan` en markdown), puis `current_mode_update` | `collaboration_mode=plan` : validation « Implement this plan? » (`switch_mode`, `rawInput.plan`) |
| Modes | `default`, `acceptEdits`, `plan`, `auto`, `bypassPermissions` ; **mode initial `auto`** (réglage de l'utilisateur) | `read-only`, `workspace-write`, `agent`, `agent-full-access` ; initial `agent` |
| `configOptions` | mode, model, effort, fast, ultracode, agent | mode, collaboration_mode, model, reasoning_effort, fast-mode |
| Options de validation | `allow-once`, `allow-with-updates` (allow_always), `reject` ; plan : `exit-plan-default`, `exit-plan-auto`, `exit-plan-bypass`… | exec : `allow_once`, `accept_execpolicy_amendment` (allow_always), `cancel` ; edit : `allow_once`, `allow_for_session`, `cancel` ; plan : `implement_plan`, `revise_plan` |
| Commandes (`available_commands_update`) | oui, avec les skills de l'utilisateur | oui |
| Usage | `usage_update` (fenêtre de 1 M, coût en USD, limite sur 7 jours) + usage en fin de tour | `usage_update` (fenêtre de 475 k) + usage en fin de tour |
| État du tour | `session_info_update` (titre) | `session_info_update` : titre et `threadStatus` (active, idle, waitingOnApproval) |
| Message pendant un tour | `session/prompt` mis en file ; `_session/steering` → `injected` | idem |
| `session/cancel` | `stopReason: cancelled` en 75 ms, la session reste utilisable | idem en 110 ms |
| Reprise après redémarrage | `session/load` rejoue l'historique et garde le mode | rejoue l'historique ; le mode repart à `agent` sans `INITIAL_AGENT_MODE` |

### Ce que le pont doit compenser

- **Mode Claude** : forcer `session/set_mode` après `session/new`. Sinon Claude démarre en `auto`, réglage de l'utilisateur, et ne demande rien.
- **Mode Codex** :
  - lancer avec `INITIAL_AGENT_MODE` et `CODEX_CONFIG` (voir plus haut) ;
  - réappliquer le mode de la session par `session/set_mode` après chaque `session/load`, car `INITIAL_AGENT_MODE` est commun à tout le processus.
- **Diff Codex** : la validation « Edit files » ne porte que `locations`. Le pont retrouve le diff dans le `tool_call` qui a le même `toolCallId`.
- **Sortie des commandes Codex** : elle arrive dans `_meta.terminal_output_delta` et `_meta.terminal_exit`, même si le client n'annonce pas de terminal. Le pont la remet dans le contenu de l'appel.
- **Appels `failed` dont la sortie commence par `bwrap:`** : les masquer.
- **`auth/status_update` et `_auth/status_update`** :
  - ils contiennent l'e-mail du compte ;
  - les filtrer, sans les journaliser ni les relayer à l'app ;
  - en garder seulement l'état connecté ou non.
- **Message pendant un tour** :
  - `_session/steering` (`{sessionId, prompt}`) pour l'injecter dans le tour en cours ;
  - sinon `session/prompt`, que les deux agents mettent en file.
- **Pas de `plan`** : le plan de l'agent passe par une validation `switch_mode` dont le texte est `rawInput.plan`. L'interface l'affiche comme un plan à approuver.
- **Silences de Claude** : pendant une longue réponse, Claude peut rester 30 s sans aucun événement. L'interface montre « en cours » sans attendre d'événement. Pour Codex, `threadStatus` suffit.
- **« Toujours autoriser » de Codex** (`accept_execpolicy_amendment`) : il écrit probablement une règle dans la config de Codex. Je ne l'ai pas vérifié. Ne pas le proposer tant que ce n'est pas tranché.
- **Arrêt** :
  - fermer stdin suffit pour Claude ;
  - Codex met environ 2 s, et il a fallu un SIGTERM au bout de 4 s une fois ;
  - après un SIGKILL, aucun processus orphelin.

## Conséquences

- Le contrat du pont (`docs/protocole-pont.md`) s'appuie sur ce schéma ACP, avec les compensations ci-dessus. Les fixtures de `bridge/fixtures/acp-raw/` servent de référence à la passerelle factice.
- L'app propose deux modes Codex : « valider » (`workspace-write` + consigne) et « tout autoriser » (`agent-full-access`). Le mode `agent` n'est pas proposé.
- **Risques** :
  - **Taille** : 826 Mo installés, dont environ 740 Mo de binaires embarqués inutilisés (`claude-agent-sdk-linux-arm64` 346 Mo, `codex-linux-arm64` 391 Mo). `npm install --omit=optional` est à tester.
  - **Mises à jour** : une nouvelle version de Claude Code, Codex ou d'un adaptateur peut changer le schéma ou les modes. Les adaptateurs évoluent vite (0.107 et 2.6 en novembre 2026). Les versions restent épinglées et l'app avertit quand on en sort.
  - **Consigne de Codex** : le contournement de bwrap repose sur le modèle. Un modèle qui l'ignore produit à nouveau des appels `failed`, sans casser la session.
  - **Lectures en mode « valider »** : elles demandent une validation, ce qui peut faire beaucoup de validations. À arbitrer avec l'utilisateur, par exemple en acceptant seul les commandes en lecture seule.
  - **Effets de bord** : le mode plan de Claude écrit un fichier dans `~/.claude/plans/`. Les sessions créées restent listées par `session/list`.
- Non testé : `session/resume`, `close` et `delete`, les serveurs MCP fournis par le client, `authenticate`, ACP v2.
