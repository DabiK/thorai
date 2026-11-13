# 05 — Vue d'ensemble branchée sur le pont

Statut : bloqué
Bloqué par : essai du critère 3 par l'utilisateur (deux permissions à accorder, redémarrage de la console)
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

- [x] Sur la Thor, une session lancée depuis le client de test du pont apparaît dans la vue d'ensemble et change d'état en direct.
- [x] Si on tue le pont puis qu'on le relance, l'app se reconnecte seule, sans doublon ni perte d'événement.
- [ ] Après un redémarrage de la console, ouvrir Régie suffit pour que les agents retrouvent l'accès à Android, sans passer par les réglages (association déjà faite, Wi-Fi déjà autorisé).

## Rapport

14 novembre 2026. Le code est complet, relu (3 tours, approuvé) et essayé sur la Thor. Il ne reste que le critère 3, qui demande l'utilisateur.

**Livré** (ADR 06) :
- module `android/gateway-bridge` : client WebSocket OkHttp 5.4.0, avec le jeton (`files/bridge-token`), la reconnexion (0,5 s → 5 s) et le rattrapage par `since`/`journal` (doublons ignorés, `resync`) ; trace de connexion dans logcat (`Regie`), sans jeton ;
- passerelle factice qui rejoue `bridge/fixtures/bridge-ws/` par le même décodage (`--ez fake_gateway true`, en debug) ; les enregistrements sont dans `android/bridge-fixtures`, hors de l'APK de release ;
- port `AgentGateway` en état (sessions, versions, connexion) ; statuts alignés sur le pont ; cas d'usage `StartUp`, `KeepBridgeUp`, `Quit` ;
- au lancement : `adb_wifi_enabled` remis à 1 (avec `WRITE_SECURE_SETTINGS`), puis `regie-bridge-start` par RUN_COMMAND si le pont ne répond pas ou si le débogage sans fil vient d'être rallumé ; permission RUN_COMMAND demandée au premier lancement ; relance tant que le pont reste injoignable, seulement si Régie est visible ;
- `thor/bridge/android-access.sh` (`~/bin/regie-android-access`) : `regie-bridge-start` le lance à chaque appel ; il exécute `/opt/thor-android/start.sh` (ticket 03) s'il existe, avec 6 essais à 5 s d'intervalle ;
- cartes : agent, projet, branche, statut, titre, étape (plan ACP), dernière action, ancienneté qui avance ; « Plan non communiqué par l'agent » quand il n'y en a pas ; versions avec « version non testée » en ambre ; bannière de la première validation, ouverte par R1 ;
- « Quitter » (tactile, écran du bas) : confirmation si une session travaille, puis `bridge.shutdown` (`regie-bridge-stop` en secours), puis fermeture.

**Critères** :
- [x] Session du client de test en direct : `04-demo-*`, `05-demo-fin-*` (« En cours », dernière action qui change, « Attend ta validation » et bannière).
- [x] Pont tué (`kill -9`) puis relancé par son superviseur, et arrêt complet suivi d'un redémarrage : `since=1246` → `rattrapage terminé (1 événement, seq 1247 → 1247, 0 ignoré car déjà vu, aucun trou)` ; 15 sessions au pont comme dans l'app, pas de doublon (`logcat-02-07-pont.txt`, `logcat-11-final.txt`).
- [ ] Après un redémarrage, ouvrir Régie suffit : non prouvé, il faut les permissions et un redémarrage, voir plus bas. Ce qui a été vérifié : `regie-android-access` a relié les agents (`THOR_ANDROID=ok … via=sans-fil`, 13 novembre, 22:41 UTC). Au dernier essai, il a échoué 6 fois en `non-connecte` alors que `adb_wifi_enabled` valait 1 : l'association sans fil du ticket 03 n'était pas valable à ce moment-là.

**Porte de qualité** : `./gradlew check` vert, 115 tests (domaine 27, passerelle 31, app 57), lint propre. `bash -n` et shellcheck passent sur les scripts.

**Pour l'utilisateur** (pour le critère 3 et le démarrage du pont par l'app) :
1. Association sans fil (ticket 03) : `thor/computer-use/README.md`, « Associer la console », si ce n'est pas déjà fait.
2. Depuis le Mac, Régie installée : `adb -s THOR0000000001 shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS`.
3. Sur la console : Régie › Infos de l'appli › Autorisations › Autorisations supplémentaires › « Run commands in Termux environment » › Autoriser. Régie la demande aussi à son lancement : répondre « Autoriser ».
4. Redémarrer la console, ne rien ouvrir d'autre, puis ouvrir Régie.
5. Vérifier : `adb -s THOR0000000001 logcat -d -s Regie | grep lancement` donne `débogage sans fil TURNED_ON` (ou `ALREADY_ON`) et `pont démarrage SENT` ; `~/.local/state/thor/regie-bridge.log` dans Termux contient `android-access essai … THOR_ANDROID=ok` ; demander à Claude « Quelle version d'Android ? » (réponse attendue par `thor_android`).

**Constats non bloquants laissés** (relecture 3) :
- le test « pas de second chargement du journal » vérifie sans attendre ;
- l'état « jeton absent » reste affiché environ 7 s après la copie du jeton ;
- sans `updatedAt` ni `createdAt`, la date de la session retombe sur 1970 (le contrat les fournit toujours) ;
- « Quitter » ferme l'app sans rien dire si le pont n'a pas pu être arrêté ;
- `TopActivity` importe la constante de permission de l'adapter Termux ;
- si « Quitter » arrive pendant un démarrage du pont, le pont peut rester démarré.

**Hors périmètre** :
- `thor/bridge/demo.sh` réécrit les enregistrements versionnés `bridge/fixtures/bridge-ws/*.jsonl` (tronqués si la démo est coupée) : ticket 02 ;
- un Retour envoyé à l'écran du bas ferme l'écran du bas de Régie, qui ne revient qu'au prochain retour au premier plan : ticket 01 ;
- un « « » orphelin en fin de ligne dans « R2 pour un ordre… » : il faut une espace insécable.
