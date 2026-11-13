# 07 — Validations

Statut : en attente
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
- **Seule une vraie main valide** (décision de l'utilisateur, 13 novembre). Une validation, qu'elle vienne de la manette ou du toucher, n'est acceptée que si elle vient d'un vrai périphérique de la console. Les événements injectés sont ignorés : appareil virtuel, `adb shell input`, `mobile-mcp`. Raison : les agents ont accès à l'écran par `mobile-mcp` (ticket 03) et pourraient sinon valider leurs propres demandes. Un test le prouve sur la Thor.
- **Notification Android** quand une validation arrive alors que l'app n'est pas au premier plan.

## Critères d'acceptation

- Sur la Thor, une commande et une modification de fichier sont validées ou refusées pour chacun des deux agents, et l'agent reprend en conséquence.
