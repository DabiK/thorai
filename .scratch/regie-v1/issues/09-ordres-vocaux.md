# 09 — Ordres vocaux (R2)

Statut : en attente
Bloqué par : 08

## Objectif

R2 maintenu donne un ordre plutôt qu'un message à l'agent.

## À livrer

- **Ordres reconnus** : lancer une session, faire le point (sur une session ou sur toutes), approuver ou refuser la validation affichée, mettre en pause ou tout mettre en pause, passer à une session par son nom.
- **Interprétation** : des règles fixes pour les tournures courantes, puis un petit modèle de langage avec appel de fonctions quand la phrase est ambiguë. Le fournisseur est à choisir et à noter dans un ADR.
- **Confirmation** : tout ordre qui agit se confirme par A. « Approuve » sans A n'approuve rien.
- **Ordre non compris** : il est reformulé à l'écran et ne fait rien.

## Critères d'acceptation

- Sur la Thor, vingt ordres préparés, lus par l'utilisateur, sont bien interprétés. Taux noté dans l'ADR.
