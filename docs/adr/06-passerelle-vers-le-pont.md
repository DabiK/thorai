# ADR 06 — Passerelle vers le pont, lancement et « Quitter »

Statut : accepté, 13 novembre 2026 (ticket 05).

## Contexte

Le ticket 05 branche la vue d'ensemble sur le vrai pont (contrat : `docs/protocole-pont.md`). Il faut :
- un client WebSocket avec jeton, reconnexion et rattrapage par numéro de séquence ;
- démarrer le pont au lancement de l'app (RUN_COMMAND), rallumer le débogage sans fil, et tout arrêter par « Quitter » ;
- une passerelle factice qui rejoue les enregistrements du pont ;
- ne rien montrer que le pont n'expose pas.

## Décisions

### Bibliothèques

| Brique | Version | Pourquoi |
| --- | --- | --- |
| OkHttp (`com.squareup.okhttp3:okhttp`) | 5.4.0 | client WebSocket éprouvé, ping, codes HTTP de l'ouverture (401, 503). La 5.5.1 exige compileSdk 37 (`minCompileSdk=37` dans l'AAR `okhttp-android`), la console reste en 36 (ADR 05) |
| MockWebServer (`mockwebserver3`) | 5.4.0 | faux pont local pour les tests : montée en WebSocket, 401, 503 |
| kotlinx-serialization-json | 1.12.0 | lecture de l'arbre JSON (`JsonElement`) sans plugin de compilation : le pont relaie le schéma ACP, l'app n'en prend que ce qu'elle montre |

Passer à OkHttp 5.5 : en même temps que compileSdk 37 (voir ADR 05).

### Module `:gateway-bridge`

- Kotlin JVM pur, avec le lint d'Android, comme `:domain`. Il dépend du domaine seulement.
- `Frames` lit les trames ; `BridgeState` reconstruit l'état (sessions, validations, versions, dernier `seq`) ; `BridgeAgentGateway` tient la connexion (OkHttp) ; `ReplayAgentGateway` est la passerelle factice.
- La passerelle factice rejoue `demo-run.jsonl` au rythme de l'enregistrement, et **passe par le même décodage** (`BridgeState.onResponse`, `apply`) : les réponses sont aiguillées par les requêtes enregistrées, comme la vraie le fait avec les siennes. Les tests du décodage tournent ainsi sur des données réelles.
- Les enregistrements sont lus là où le ticket 02 les a rangés (`bridge/fixtures/bridge-ws/`), sans copie, par un petit module sans code, `:bridge-fixtures`, qui en fait des ressources rangées sous `bridge-ws/`. Seuls le debug de `:app` (`debugImplementation`) et les tests de `:gateway-bridge` en dépendent : l'APK debug les embarque (environ 680 Ko), l'APK release non (vérifié avec `assembleRelease`). La passerelle factice n'est de toute façon choisissable qu'en debug.
- L'ancienne maquette codée en dur (`domain/fake/FakeAgentGateway`) disparaît : plus rien n'en avait besoin.
- `ArchitectureTest` : l'interface n'importe aucun adapter (`app.regie.gateway.*`, `app.regie.console.*`) ; le domaine n'importe ni OkHttp ni kotlinx-serialization ; la passerelle ne dépend ni d'Android ni de l'app ; un adapter n'importe pas l'autre ; seul `RegieApplication.kt` choisit la passerelle.

### Port `AgentGateway`

Il publie un **état** et non plus un flux d'événements (`SessionEvent` disparaît) :
- `sessions`, `agents` (versions), `connection` : des `StateFlow` ;
- `maintainConnection()` : se connecte et se reconnecte jusqu'à annulation ;
- `isBridgeUp()` : `GET /health` ;
- `shutdownBridge()` : `bridge.shutdown`, vrai si le pont a répondu.

Pourquoi : un `resync` remplace tout l'état, et une ligne de la carte (dernière action, étape) dépend d'événements précédents (`tool_call` puis ses `tool_call_update`). Traduire le protocole ACP en concepts du domaine reste dans l'adapter ; les règles de la vue d'ensemble (ordre, compteur, première validation, confirmation de « Quitter ») sont dans le domaine. Le ticket 06 (fil d'une session) ajoutera ce qu'il lui faut au port.

`connection` : `Connecting`, `Connected`, ou `Unreachable(raison)` avec `TOKEN_MISSING`, `TOKEN_REFUSED` (401) ou `NOT_RESPONDING`.

### Connexion et rattrapage

- Adresse `http://127.0.0.1:47600` (OkHttp monte en WebSocket). `res/xml/network_security_config.xml` n'autorise le clair que pour `127.0.0.1`.
- Jeton relu dans `files/bridge-token` à chaque tentative (il peut être copié pendant que Régie tourne), envoyé en `Authorization: Bearer`. Il n'est jamais journalisé.
- Nouvelles tentatives après 0,5 s, 1 s, 2 s, 4 s, puis toutes les 5 s. Le 503 du démarrage est une tentative ratée comme une autre.
- Le pont est dit injoignable après 5 échecs de suite (environ 12 s, le temps d'un démarrage), ou tout de suite pour un jeton absent ou refusé.
- `hello` porte `since` (dernier `seq` appliqué) et `journal` dès qu'une première connexion a abouti. Tout événement de `seq` inférieur ou égal au dernier vu est ignoré.
- Sans `since`, ou si la réponse porte `resync: true` : l'état est oublié et repart de la réponse à `hello`, puis le journal (`session.events`) des 4 sessions affichées est rechargé pour retrouver leur plan et leur dernière action. Avec rattrapage, les événements manqués suffisent.
- Pendant un rattrapage, un événement `session` plus ancien que l'état de `hello` (`updatedAt` antérieur) ne fait pas reculer la session.
- L'état vit en mémoire : au relancement de l'app, un `hello` sans `since` suffit.
- Ping OkHttp toutes les 15 s, pour repérer une connexion à moitié morte.

### Ce que la carte montre

| Carte | Source |
| --- | --- |
| agent, projet, branche, titre | session du pont (`agent`, `project`, `branch`, `title`) |
| statut | `status`, avec les libellés du tableau « Statuts » du protocole |
| étape x sur y, étape en cours | dernier `session_update` `plan` (ACP) : entrées `completed`, total, entrée `in_progress` |
| dernière action | dernier `tool_call` commencé, mis à jour par ses `tool_call_update` : `rawInput.command`, sinon `title`, et son `status` |
| ancienneté | dernier événement de la session (`at`), sinon `updatedAt` ; les mises à jour de réglages (`available_commands_update`, `usage_update`…) ne comptent pas. Elle avance chaque seconde à l'écran |
| validation | `approval_request` / `approval_resolved` et `approvals` de `hello` : la commande, sinon le fichier (relatif au projet), sinon le titre |

- Ce que le pont n'expose pas est absent, jamais inventé : sans plan, « Plan non communiqué par l'agent » et pas de barre d'étapes ; plus de résumé ni de lignes ajoutées et retirées.
- Un appel retiré par le pont (`_meta.regie.hidden`, échec du sandbox de Codex) disparaît : la dernière action redevient la précédente.
- Ordre : sessions actives d'abord, puis la plus récemment changée (`updatedAt`) d'abord. `updatedAt` plutôt que la dernière activité : les cartes ne changent pas de place à chaque morceau de réponse.
- Compteur de l'en-tête : les sessions actives (chargées dans un agent).
- Versions : `agents.versions`, numéro tiré de `binary` ; `isTested: false` en ambre, « version non testée » ; une version que le pont n'a pas lue n'est pas affichée.
- Statut « Échec » : le rouge du micro, seul rouge de la maquette.

### Lancement (`StartUp`, domaine)

Ports : `AndroidAccess` (débogage sans fil) et `BridgeLauncher` (RUN_COMMAND), adapters Android dans `app/console/`.

À la création de l'écran du haut, puis à chaque retour au premier plan, et tant que le pont se tait :
1. **Débogage sans fil** : si Régie a `WRITE_SECURE_SETTINGS`, `Settings.Global` `adb_wifi_enabled` est remis à 1 s'il était à 0. Sans la permission, l'écran du bas le dit sobrement (« Accès des agents à Android ») et le reste fonctionne.
2. **Pont** : `regie-bridge-start` part par RUN_COMMAND si `/health` ne répond pas, **ou** si le débogage sans fil vient d'être rallumé : le script relie alors les agents au nouveau port, même si le pont tournait déjà.
3. Un démarrage envoyé il y a moins de 30 s n'est pas renvoyé.

Tant que la connexion reste `Unreachable(NOT_RESPONDING)`, `KeepBridgeUp` (domaine) relance `StartUp` : tout de suite, puis à chaque échéance (le reste des 30 s si un démarrage vient de partir, sinon 30 s). Ainsi un pont qui tombe juste après un démarrage est relancé sans attendre un retour au premier plan. Il ne relance que si un écran de Régie est visible et qu'elle ne quitte pas : Android refuse de démarrer un service depuis l'arrière-plan. Sinon il revérifie toutes les 5 s.

Le rapport d'un lancement dit ce qui s'est passé cette fois-ci : pont déjà là, démarrage envoyé (et son résultat), ou démarrage déjà demandé il y a moins de 30 s.

- `com.termux.permission.RUN_COMMAND` est demandée à l'utilisateur au premier lancement de chaque processus (`RequestPermission`). Refusée : « Pont injoignable » et la marche à suivre (Infos de l'appli › Autorisations › Autorisations supplémentaires).
- `<queries>` déclare `com.termux` (visibilité des paquets, Android 11 et plus).
- Rien au boot.

Marche à suivre affichée selon la cause : permission RUN_COMMAND, Termux absent,
jeton absent ou refusé (autoriser les commandes Termux puis rouvrir Régie), sinon
« Régie relance le pont et se reconnecte seule. ». Le chemin debug
`thor/bridge/install.sh token-app` reste disponible.

### Accès des agents à Android après un redémarrage

- `thor/bridge/android-access.sh`, installé en `~/bin/regie-android-access` (Termux), lance `/opt/thor-android/start.sh` (ticket 03) dans la Debian, comme `dev`. Il réessaie toutes les 5 s, 6 fois au plus, tant que la console n'est pas joignable (code 2 : le port sans fil met un moment à apparaître). Journal : `~/.local/state/thor/regie-bridge.log`.
- Sans le ticket 03 installé : une ligne de journal, « accès Android non installé (ticket 03) », et rien d'autre.
- `regie-bridge-start` le lance, détaché, à chaque appel, même quand le pont tourne déjà.
- À accorder une fois, depuis le Mac, l'app installée :

  ```
  adb -s $THOR_SERIAL shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS
  ```

### « Quitter »

- Un libellé tactile, « Quitter », à droite de la barre de l'écran du bas (et du panneau du mode un seul écran). Le PRD ne lui donne pas de bouton de manette : aucun n'est inventé.
- Si une session est `working` ou `awaiting_approval` : « N sessions travaillent encore. Quitter les arrête. » A confirme, B annule, au doigt aussi.
- Puis `bridge.shutdown` (3 s au plus pour la réponse) ; sans réponse, `regie-bridge-stop` par RUN_COMMAND. Puis les deux écrans se ferment et le processus s'arrête.

### Passerelle factice — tests et émulateur seulement

Mécanisme historique de debug, réservé aux tests : ne pas l’activer sur la Thor de l’utilisateur. Sur un émulateur contrôlé, au premier lancement du processus :

```
adb -s emulator-5580 shell am force-stop app.regie
adb -s emulator-5580 shell am start -n app.regie/.TopActivity --ez fake_gateway true
```

Rien ne part vers Termux : ni permission demandée, ni démarrage, ni débogage sans fil.

## Conséquences

- `CLAUDE.md` ne cite plus `SessionEvent` dans le modèle du domaine.
- Tant que les agents n'envoient pas de plan ACP (aucun dans les enregistrements), les cartes disent « Plan non communiqué par l'agent ».
- Les sessions de l'historique (`stopped`) restent dans la vue d'ensemble, après les actives : avec plus de 4 sessions, la grille n'en montre que 4.

## Révision du 18 novembre — APK de release (ticket 27)

L'app crée 32 octets aléatoires et conserve le secret privé ; le debug peut encore
recevoir le secret par `install.sh token-app`. `StartUp` utilise le port
`BridgePairing` tant que le WebSocket n'est pas authentifié, même si `/health`
répond. `KeepBridgeUp` reprend aussi les erreurs de jeton, au premier plan et à
l'échéance de 30 s. Un lancement refusé pour permission manquante reste réessayable.

Le nouvel adapter configure Termux par RUN_COMMAND, avec seulement le nom d'une
socket Unix abstraite éphémère. Le serveur Android ne livre le secret qu'à l'UID
Termux, contrôlé par les credentials du noyau ; son attente expire après 30 s.
Le script lit la socket par Python dans la Debian (même UID Android), valide le
format, arrête le pont si le secret change, écrit atomiquement le fichier 0600
dans la Debian puis redémarre. Un secret identique ne coupe aucune session.
Le protocole décrit les extras exacts.

Le premier essai par URI privée a été retiré : même avec une permission de lecture
accordée à Termux, Android refuse `content read` avec
`requires android.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY`. Aucun provider
n'est livré dans l'app. Les APIs Android LocalServerSocket/peerCredentials et
Os.setsockoptTimeval ont été vérifiées par Context7 avant le correctif.

La transmission directe en arguments **ou en stdin** a été écartée : Termux peut
les recopier dans ses traces détaillées. Aucun secret n'est dans l'intent, ni
journalisé par Régie ou la commande de configuration. Sources vérifiées par
Context7 puis lecture du [RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
et du [service Termux](https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/app/RunCommandService.java).

La commande nécessite Termux et RUN_COMMAND comme avant, mais aucun accès debug
au stockage de Régie. Après désinstallation/réinstallation, le nouveau secret de
l'app remplace celui du pont ; les projets et connexions Termux restent présents.
