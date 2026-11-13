# Journal de reprise

## Où on en est

**13 novembre 2026, cadrage terminé.**

- Maquette des 6 écrans validée « dans les grandes lignes » par l'utilisateur.
- PRD, ADR 01 et 12 tickets écrits.
- **Première vague lancée en parallèle**, une session par ticket, chacune dans son worktree :
  - 01 : socle Android ;
  - 02 : pont ;
  - 03 : accès des agents à Android ;
  - 04 : voix.

- **13 novembre, ticket 01 fait** : socle Android livré et vérifié sur la Thor, y compris l'écran du bas éteint (ADR 05). R1 et l'écran du bas en arrière-plan tranchés par l'utilisateur.
- **13 novembre, ticket 02 fait** : pont ACP (ADR 02), contrat `docs/protocole-pont.md`, fixtures réelles dans `bridge/fixtures/bridge-ws/`. Reste à vérifier avec l'utilisateur : l'historique après un redémarrage de la console (marche à suivre dans le rapport du ticket).

## Ordre des vagues

| Vague | Tickets | Commence quand |
| --- | --- | --- |
| 1 | 01, 02, 03, 04 | tout de suite, en parallèle |
| 2 | 05, 12 | 01, 02 (et 04 pour le 12) fusionnés |
| 3 | 06, puis 07 et 08 | 05 fusionné |
| 4 | 09, 10, puis 11 | 08 fusionné |

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

## Questions en attente

- Nom définitif de l'app (« Régie » est un nom de travail).
- Lecture à voix haute : automatique ou seulement à la demande ? Un réglage est prévu, la valeur par défaut reste à décider.
- Ticket 02 : en mode « valider », Codex demande une validation même pour lire (pas de sandbox sous proot). Le pont doit-il accepter seul les lectures ?
- Ticket 02 : durée de conservation du journal d'événements du pont.
- Ticket 02 : une session créée avec une branche fait `git switch` dans la copie de travail partagée. Refuser si une session travaille sur le projet, prévenir, ou un worktree par session ?
- Ticket 02 : la liste des skills de l'utilisateur apparaît dans les fixtures commitées. À masquer ?
