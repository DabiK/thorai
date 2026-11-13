# 05 — Vue d'ensemble branchée sur le pont

Statut : en attente
Bloqué par : 01, 02
Possède : adapter `AgentGateway` vers le pont (module `android/gateway-bridge` ou équivalent)

## Objectif

Remplacer la passerelle factice par le vrai pont. La vue d'ensemble (écran 1 de la maquette) montre alors les vraies sessions de la Thor, mises à jour en direct.

## À livrer

- **Adapter WebSocket du port `AgentGateway`**, selon `docs/protocole-pont.md` :
  - jeton ;
  - reconnexion automatique avec rattrapage par numéro de séquence ;
  - état de connexion visible et sobre (« pont injoignable » avec la marche à suivre).
- **Passerelle factice** : elle rejoue maintenant les enregistrements de `bridge/fixtures/`.
- **Vue d'ensemble réelle** : agent, projet, branche, statut, étape, dernière action, ancienneté. Une information non exposée par l'agent est montrée comme absente, jamais inventée.
- **Première validation en attente** en haut de l'écran du bas. R1 l'ouvre (le contenu viendra au ticket 07).
- **Versions** de Claude et Codex affichées, avec un avertissement hors de la plage testée.

## Critères d'acceptation

- Sur la Thor, une session lancée depuis le client de test du pont apparaît dans la vue d'ensemble et change d'état en direct.
- Si on tue le pont puis qu'on le relance, l'app se reconnecte seule, sans doublon ni perte d'événement.
