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

## Questions en attente

- Nom définitif de l'app (« Régie » est un nom de travail).
- Lecture à voix haute : automatique ou seulement à la demande ? Un réglage est prévu, la valeur par défaut reste à décider.
