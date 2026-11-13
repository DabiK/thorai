# 06 — Session en direct

Statut : en attente
Bloqué par : 05

## Objectif

Écran 3 de la maquette : suivre une session en direct et agir dessus sans clavier.

## À livrer

- **En haut, le fil** : messages de l'utilisateur (avec la mention « dicté »), réponses en flux, actions résumées en une ligne (lu, modifié +/−, lancé, résultat).
- **En haut, à droite** : le plan de l'agent s'il l'expose, et les fichiers modifiés.
- **En bas** :
  - L1/R1 pour passer à la session voisine ;
  - X pour le diff (ouvre l'écran 5 quand il existe) ;
  - Y pour faire le point à voix haute (ticket 12 ; en attendant, un résumé écrit) ;
  - Start pour interrompre ;
  - B pour revenir.
- Envoyer un message écrit de test : le texte libre est un secours, la dictée arrive au ticket 08.

## Critères d'acceptation

- Sur la Thor, on suit une vraie session Claude et une vraie session Codex jusqu'à la fin, on en interrompt une, et on passe de l'une à l'autre avec L1/R1.
