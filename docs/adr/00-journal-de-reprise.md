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

- **Ticket 03 bloqué** (13 novembre) : mobile-mcp déclaré aux deux agents sous `thor_android`, testé avec un échafaudage. Attend l'association du débogage sans fil par l'utilisateur (`thor/computer-use/README.md`) et deux réponses (ADR 03, « Questions ouvertes »).
- **Ticket 03, reprise** (13 novembre) : consignes globales des agents posées (`~dev/.claude/CLAUDE.md`, `~/.codex/AGENTS.md`, bloc entre marqueurs : `thor_android`, `thor-screen`, ne jamais toucher `app.regie`) ; serveur adb de la Debian sur une socket Unix privée (`/home/dev/.thor-adb/server`), plus rien sur 5037. Essayé par la socket avec un échafaudage retiré depuis. Reste bloqué sur la seule association du débogage sans fil.
- **Ticket 03 bloqué sur une question produit** (13 novembre, 23 h 40) : un agent peut-il ouvrir une appli par-dessus Régie ? Les consignes livrées l'interdisent en attendant. Pour le reste : association faite avec la clé de la Debian. Sur le chemin livré, sans le Mac, la consigne « À propos » réussit avec Claude puis avec Codex. mobile-mcp passe en mode adb seul par défaut (`MOBILEMCP_LEGACY_ROBOT=1`) : en mode mobilecli, des ports `127.0.0.1` s'ouvraient sur la console et mobilecli injectait son agent dans le build de debug de Régie. `pair.sh` refuse un serveur adb relayé. **Reste à faire avec l'utilisateur** : redémarrer la console une fois pour voir si le débogage sans fil revient seul, et retirer l'association du Mac dans « Appareils associés » si elle y est encore.

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

## Questions en attente

- Nom définitif de l'app (« Régie » est un nom de travail).
- Lecture à voix haute : automatique ou seulement à la demande ? Un réglage est prévu, la valeur par défaut reste à décider.
- Ticket 03 : un agent peut-il ouvrir une autre appli par-dessus Régie (qui passe alors en arrière-plan sur cet écran) ? Les consignes l'interdisent pour l'instant (variante prudente) : quand Régie occupe les deux écrans, aucun agent ne peut agir sur Android.
- Ticket 03 : garder mobile-mcp en mode adb seul (`MOBILEMCP_LEGACY_ROBOT=1`, réglage livré) ? Il supprime les ports `127.0.0.1` et l'injection de mobilecli dans Régie, mais l'arbre d'interface de l'écran haut n'est plus fiable et quelques outils disparaissent. Voir ADR 03, « Questions ouvertes ».
