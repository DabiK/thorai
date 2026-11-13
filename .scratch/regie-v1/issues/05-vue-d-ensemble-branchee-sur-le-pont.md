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
- **Démarrage du pont au lancement de l'app.** Si le pont ne répond pas, l'app le démarre par RUN_COMMAND (`thor/bridge/start.sh`, ticket 02). La permission est demandée à l'utilisateur au premier lancement. Rien ne démarre au boot.
- **Débogage sans fil rétabli au lancement** (décision de l'utilisateur, 13 novembre : l'accès des agents à Android doit survivre aux redémarrages, sans rien au boot). L'app déclare `WRITE_SECURE_SETTINGS`, que l'utilisateur accorde lui-même une fois avec `adb shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS` (marche à suivre dans le rapport). À chaque lancement, avant de démarrer le pont, l'app remet `adb_wifi_enabled` à 1 si c'est coupé. Le pont retrouve ensuite le nouveau port, qui change à chaque fois (`thor/computer-use/start.sh`, ticket 03). Sans la permission, l'app le dit sobrement et le reste fonctionne.
- **« Quitter »** arrête les sessions puis le pont, avec une confirmation si une session travaille encore.
- **Passerelle factice** : elle rejoue maintenant les enregistrements de `bridge/fixtures/`.
- **Vue d'ensemble réelle** : agent, projet, branche, statut, étape, dernière action, ancienneté. Une information non exposée par l'agent est montrée comme absente, jamais inventée.
- **Première validation en attente** en haut de l'écran du bas. R1 l'ouvre (le contenu viendra au ticket 07).
- **Versions** de Claude et Codex affichées, avec un avertissement hors de la plage testée.

## Critères d'acceptation

- Sur la Thor, une session lancée depuis le client de test du pont apparaît dans la vue d'ensemble et change d'état en direct.
- Si on tue le pont puis qu'on le relance, l'app se reconnecte seule, sans doublon ni perte d'événement.
- Après un redémarrage de la console, ouvrir Régie suffit pour que les agents retrouvent l'accès à Android, sans passer par les réglages (association déjà faite, Wi-Fi déjà autorisé).
