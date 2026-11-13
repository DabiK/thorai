# Journal de reprise

## Où on en est

- **13 novembre, ticket 04 fait** : module `voice/` (ports, local sherpa-onnx, ElevenLabs, factice) et appli d'essai `app.regie.voicelab` (ADR 04). La mesure ElevenLabs n'est plus nécessaire : l'utilisateur a retenu la voix locale par défaut après l'avoir essayée. Recommandation du ticket : termes du dépôt favorisés activés par défaut.

**13 novembre 2026, cadrage terminé.**

- Maquette des 6 écrans validée « dans les grandes lignes » par l'utilisateur.
- PRD, ADR 01 et 12 tickets écrits.
- **Première vague lancée en parallèle**, une session par ticket, chacune dans son worktree :
  - 01 : socle Android ;
  - 02 : pont ;
  - 03 : accès des agents à Android ;
  - 04 : voix.
- **13 novembre, ticket 04 (voix) `bloqué`, branche prête à relire.** Le module `voice/` est livré : ElevenLabs, local sherpa-onnx et appli d'essai `app.regie.voicelab`. Le banc local a tourné sur la Thor. Il manque la clé ElevenLabs et la lecture des 20 consignes par l'utilisateur ; la marche à suivre est dans l'ADR 04.

- **13 novembre, ticket 01 fait** : socle Android livré et vérifié sur la Thor, y compris l'écran du bas éteint (ADR 05). R1 et l'écran du bas en arrière-plan tranchés par l'utilisateur.
- **13 novembre, ticket 02 fait** : pont ACP (ADR 02), contrat `docs/protocole-pont.md`, fixtures réelles dans `bridge/fixtures/bridge-ws/`. Reste à vérifier avec l'utilisateur : l'historique après un redémarrage de la console (marche à suivre dans le rapport du ticket).
- **14 novembre, ticket 05 bloqué sur un essai de l'utilisateur** : vue d'ensemble branchée sur le pont (ADR 06, module `android/gateway-bridge`). Reconnexion et rattrapage vérifiés sur la Thor, « Quitter » aussi. Relecture approuvée en 3 tours. Reste à faire par l'utilisateur pour le critère 3 : accorder `WRITE_SECURE_SETTINGS` (`pm grant`) et RUN_COMMAND, redémarrer la console, ouvrir Régie. Marche à suivre dans le rapport du ticket.

- **Ticket 03 bloqué** (13 novembre) : mobile-mcp déclaré aux deux agents sous `thor_android`, testé avec un échafaudage. Attend l'association du débogage sans fil par l'utilisateur (`thor/computer-use/README.md`) et deux réponses (ADR 03, « Questions ouvertes »).
- **Ticket 03, reprise** (13 novembre) : consignes globales des agents posées (`~dev/.claude/CLAUDE.md`, `~/.codex/AGENTS.md`, bloc entre marqueurs : `thor_android`, `thor-screen`, ne jamais toucher `app.regie`) ; serveur adb de la Debian sur une socket Unix privée (`/home/dev/.thor-adb/server`), plus rien sur 5037. Essayé par la socket avec un échafaudage retiré depuis. Reste bloqué sur la seule association du débogage sans fil.
- **Ticket 03 bloqué sur une question produit** (13 novembre, 23 h 40) : un agent peut-il ouvrir une appli par-dessus Régie ? Les consignes livrées l'interdisent en attendant. Pour le reste : association faite avec la clé de la Debian. Sur le chemin livré, sans le Mac, la consigne « À propos » réussit avec Claude puis avec Codex. mobile-mcp passe en mode adb seul par défaut (`MOBILEMCP_LEGACY_ROBOT=1`) : en mode mobilecli, des ports `127.0.0.1` s'ouvraient sur la console et mobilecli injectait son agent dans le build de debug de Régie. `pair.sh` refuse un serveur adb relayé. **Reste à faire avec l'utilisateur** : redémarrer la console une fois pour voir si le débogage sans fil revient seul, et retirer l'association du Mac dans « Appareils associés » si elle y est encore.
- **13 novembre, ticket 03 fait** : fusionné ; les deux questions sont tranchées par l'utilisateur (ticket 14 : liberté totale des agents).
- **13 novembre, ticket 13 fait** : le pont accepte seul les lectures shell en mode « valider » (règle stricte, testée), purge le détail des sessions après 30 jours (`REGIE_BRIDGE_RETENTION_DAYS`) et laisse les branches à l'agent (consigne en tête, plus de `git switch`). Essayé sur la Thor avec Codex, sur un pont isolé (port 47613). Protocole et ADR 02 à jour. Le pont installé sur la console est à réinstaller (`thor/bridge/install.sh`) après la fusion.
- **Ticket 14 à fusionner** (13 novembre, minuit) : les agents pilotent tout Android sans validation, Régie comprise. mobile-mcp passe en mobilecli par défaut ; pour revenir à adb seul : `touch ~/.config/thor-android/adb-seul`. Les permissions de Claude (`settings.json`) et de Codex (`approve` et `rules/thor-android.rules`) sont posées par `install.sh` et retirées par `uninstall.sh`. Essayé sur la Thor sans aucune validation. **L'association du débogage sans fil est à refaire par l'utilisateur** : l'essai de `uninstall.sh` a effacé la clé adb de la Debian.

## Ordre des vagues

| Vague | Tickets | Commence quand |
| --- | --- | --- |
| 1 | 01, 02, 03, 04 | tout de suite, en parallèle |
| 2 | 05, 12 | 01, 02 (et 04 pour le 12) fusionnés |
| 3 | 06, puis 07 et 08 | 05 fusionné |
| 4 | 09, 10, puis 11 | 08 fusionné |

## Ordre des tickets d'interface (révisé le 14 novembre)

Les tickets qui touchent `android/` passent **un par un**. Ils installent tous le même APK (`app.regie`) sur la seule console, et se marcheraient dessus. L'ordre suit le chemin critique :
06 → 07 → 08 → 12 → 09 → 10 → 11. Le 08 passe avant le 12, parce qu'il branche la voix dans l'app.

## Règles d'orchestration

- Une session fraîche par ticket, dans son worktree. Elle teste sur la Thor et ne fusionne pas.
- L'orchestrateur relit, fusionne dans `main`, pousse sur le dépôt GitHub privé et lance la vague suivante. L'utilisateur l'a autorisé le 13 novembre.
- Une seule console pour tout le monde : voir `CLAUDE.md`, « La console ».

## Décisions de l'utilisateur

- 13 novembre : **rien ne démarre au boot**. Régie est une app qu'on lance ; c'est elle qui démarre le pont (RUN_COMMAND) et l'arrête quand on quitte.

- 13 novembre, sur les questions du ticket 03 :
  - Régie n'accepte une validation que d'un vrai bouton ou d'un vrai toucher ; les appuis injectés sont ignorés (ticket 07).
  - Les agents apprennent l'existence de `thor_android` et de `thor-screen` par leurs consignes globales sur la console : `CLAUDE.md` de Claude, `AGENTS.md` de Codex.
  - Le serveur adb de la Debian passe sur une socket Unix privée (`ADB_SERVER_SOCKET`) au lieu de `127.0.0.1:5037`.

- 13 novembre, sur les questions du ticket 01 :
  - R1 ouvre la validation en attente s'il y en a une, sinon passe à la session suivante.
  - L'écran du bas de Régie reste affiché quand l'app passe en arrière-plan ; « Quitter » le ferme.

- 13 novembre, après essai de l'appli voicelab : **la voix locale devient le fournisseur par défaut**, pour la transcription comme pour la synthèse. ElevenLabs reste un adapter optionnel. Ticket 12 : la lecture à voix haute utilise la synthèse locale, sauf réglage contraire.

- 13 novembre, sur les questions du ticket 02 (ticket 13) :
  - le pont accepte seul les commandes en lecture seule ;
  - le détail des sessions est gardé 30 jours, puis seulement leur résumé ;
  - Régie ne gère pas les branches : la branche demandée part dans la consigne, et l'agent s'en occupe ;
  - les enregistrements de test du pont restent tels quels, dépôt privé. L'app installée sur la Thor ne montre jamais de session factice : la passerelle factice ne sert qu'aux tests.

- 13 novembre, sur les questions du ticket 03, **et cela remplace les décisions de sécurité prises plus tôt** : les agents peuvent tout piloter sur Android, les deux écrans, Régie comprise, avec la méthode la plus libre (mobilecli). Pas de validation pour ces gestes, pas de règle « vraie main » dans Régie. Les restrictions viendront plus tard, si l'utilisateur en veut (ticket 14).

## Questions en attente

- Nom définitif de l'app (« Régie » est un nom de travail).
- Lecture à voix haute : automatique ou seulement à la demande ? Un réglage est prévu, la valeur par défaut reste à décider.
- Ticket 14 : `uninstall.sh` doit-il continuer à effacer la clé adb de la Debian ? Cela oblige à refaire l'association à la main après toute désinstallation.
