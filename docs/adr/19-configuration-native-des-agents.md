# ADR 19 — Garder la configuration native des agents dans les sessions ACP

Statut : accepté, 14 novembre 2026 (ticket 19).

## Constat et sources

Le PRD dit que les outils, consignes et réglages restent ceux des agents.
L'hypothèse « `mcpServers: []` retire les MCP de l'utilisateur » n'est pas
confirmée : dans les versions épinglées, ce champ porte les **ajouts du client**.
Le défaut de navigation rapporté sur la Thor n'est pas reproduit sur le Mac.

Vérification le 14 novembre, documentation actuelle et code effectivement installé :

- [`claude-agent-acp`](https://github.com/agentclientprotocol/claude-agent-acp/blob/main/src/acp-agent.ts),
  via Context7, puis `dist/v1/acp-agent.js` du paquet **0.107.0** :
  le SDK reçoit déjà `settingSources: ["user", "project", "local"]`, le preset
  `claude_code` et les MCP du client comme ajouts. Aucun `strictMcpConfig: true`.
  Les consignes globales `CLAUDE.md`, les réglages et permissions de ces sources
  restent donc à la charge de Claude Code. `_meta.claudeCode.options` est accepté
  à la création et au chargement. La découverte du MCP utilisateur au démarrage
  est prouvée sans prompt ; l'effet sémantique des consignes Claude n'est pas
  testé, pour ne consommer aucun quota.
- [`codex-acp`](https://github.com/agentclientprotocol/codex-acp/blob/main/README.md),
  paquet **2.6.0**, `createSessionConfig` dans `dist/index.js` : il appelle
  `config/read` avec le cwd et conserve la configuration native quand il n'y a
  pas de MCP client. Il ne vide pas `mcp_servers`. `CODEX_CONFIG` est une
  surcharge, pas un remplacement du fichier. Context7 renvoie l'ancien adapter
  Rust Zed pour ce nom : nous n'en extrapolons pas le comportement au paquet TS.
- Documentation officielle OpenAI :
  [MCP](https://developers.openai.com/codex/mcp/) et
  [AGENTS.md](https://developers.openai.com/codex/guides/agents-md/) :
  configuration dans `~/.codex/config.toml` (ou `CODEX_HOME`), consignes globales
  dans ce même dossier, permissions MCP natives, notamment
  `default_tools_approval_mode = "approve"`. Le contrat observe ces trois
  propriétés ensemble, avant et après reprise.

## Décision

**Pas de lecteur TOML/JSON ni de copie de la configuration dans le pont.**
Les agents continuent à résoudre eux-mêmes leurs MCP (stdio, HTTP, plugins…),
leurs secrets, leurs règles et leurs consignes. Le processus hérite de
l'environnement du pont et du cwd de la session. Les montages de la Debian
restent ceux de l'ADR 02 ; aucun changement dans `thor/`.

Le pont rend explicites les options Claude `settingSources` et
`strictMcpConfig: false`, pour `session/new` **et** `session/load`. Cela fixe le
contrat attendu sans dépendre d'un changement futur des valeurs par défaut.
C'est un durcissement de l'intégration, pas une preuve de réparation de la
navigation physique. Codex garde `mcpServers: []`, sans surcharge des MCP.
Ce sont des paramètres de transport/SDK ; aucune nouvelle règle métier.

Les niveaux de validation choisis dans Régie et le contournement PRoot restent
ceux des ADR 02/14. Ils remplacent déjà certains réglages de mode du terminal :
ce ticket ne revendique pas une identité de toutes les fonctions CLI/ACP.
Les permissions **par outil MCP** restent celles de la configuration native.
Les commandes shell `adb`/`thor-screen` sous PRoot restent à vérifier sur Thor.

## Contrat reproductible sur Mac

Depuis `bridge/` :

```sh
REGIE_MCP_CONTRACT_EVIDENCE="$PWD/../artifacts/screenshots/19" npm run test:contract:mcp
```

Node 24+, `npm ci`, Codex déjà connecté dans `CODEX_HOME` (ou `~/.codex`),
Claude Code installé. `CODEX_PATH` et `CLAUDE_CODE_EXECUTABLE` permettent de
choisir leurs binaires ; défaut `~/.local/bin/{codex,claude}` sur le Mac.
**Ne pas lancer tout `test:contract` pour cet essai** : l'ancien contrat ACP
contient des prompts Claude. Le contrat MCP dédié ne lui en envoie aucun.

Le test lance le vrai `src/main.ts` sur **47619**, avec ses propres projet,
jeton et état. `REGIE_MCP_CONTRACT_PORT` permet un autre port ; 47600 est refusé.
Il n'utilise ni adb ni l'émulateur, réservés au ticket 17. Il déclare le serveur
factice dans des **profils utilisateur temporaires** : `.codex/config.toml`
via `CODEX_HOME`, et configuration Claude via `CLAUDE_CONFIG_DIR` et
`claude mcp add-json --scope user`. Les configurations habituelles du Mac sont
intactes ; seul un lien vers `auth.json` réutilise la connexion Codex.

Le protocole MCP factice journalise les inventaires et les appels. Codex doit
transmettre un marqueur aléatoire présent seulement dans `AGENTS.md`, appeler
une fois l'outil sans validation, et restituer un reçu aléatoire renvoyé par
l'outil. Après arrêt complet et relance du pont, le test reprend la même session
et exige un nouvel appel et un nouveau reçu. Claude est créé sans prompt :
seuls `initialize` et `tools/list` sont observés ; l'URL du modèle est en plus
neutralisée sur le loopback. Les deux sessions sont arrêtées, les processus
MCP doivent avoir disparu, puis les profils temporaires sont retirés.

La porte `npm run check` ne lance aucun agent. Le test unitaire échange avec
un faux serveur ACP et vérifie les paramètres de création/reprise des deux
agents. Preuves : `artifacts/screenshots/19/`.

## Vérification physique au retour de l'utilisateur

1. Rebrancher la Thor, vérifier `adb devices` : série `THOR0000000001`.
2. Après fusion, quitter Régie, installer le pont par `thor/bridge/install.sh`
   et l'app par `scripts/thor-install.sh`, puis ouvrir Régie.
3. Si l'accès Android indique une absence de connexion, rallumer le débogage
   sans fil et refaire l'association Debian avec
   `thor/computer-use/pair-from-mac.sh` (guide computer-use). Ne pas réinstaller
   ou désinstaller l'accès Android pour une simple association perdue.
4. Depuis Régie, créer une session **Codex**, niveau « valider », puis envoyer :
   « ouvre les Paramètres et dis-moi la version d'Android ».
5. Constater l'ouverture réelle des Paramètres, les appels MCP ou
   `thor-screen`/`adb` dans le fil, la bonne version et l'absence de validation
   pour les gestes Android. Faire une capture des deux écrans :
   `scripts/thor-shot.sh artifacts/screenshots/19`.
6. Quitter Régie, la rouvrir, reprendre cette session et refaire la demande ;
   vérifier les mêmes effets puis Quitter pour arrêter les agents.

Sans la console, l'association adb, mobilecli, les deux écrans et les règles
shell PRoot ne sont pas prouvés. Le journal signale déjà une association perdue
au ticket 14 et un accès indisponible au 11 : ce sont des pistes, pas un diagnostic.
