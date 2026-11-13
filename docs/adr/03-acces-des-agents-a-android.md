# ADR 03 — Accès des agents à Android (computer use)

Statut : accepté, 13 novembre 2026 (ticket 03). Association du débogage sans fil en attente de l'utilisateur.

## Contexte

Claude Code et Codex tournent dans la Debian (proot) de la Thor. Ils doivent voir l'écran d'Android et agir dessus : capture, arbre d'interface, toucher, glisser, saisie, ouverture d'une appli. L'ADR 01 (point 5) demande de réutiliser un serveur MCP existant, avec adb en local.

Contraintes :
- marcher dans la Debian, en arm64, sous proot ;
- gérer les deux écrans si possible ;
- pas d'abonnement, un outil maintenu ;
- rien ne démarre au boot (décision de l'utilisateur, ADR 01).

Point de départ sur la console : adb n'est installé ni dans Termux ni dans la Debian. adbd n'écoute qu'en USB, vers le Mac.

## Candidats

| Candidat | Verdict |
| --- | --- |
| **mobile-mcp** 1.0.17 (mobile-next, Apache-2.0, Node 20 et plus) | **Retenu.** Publié le 11 novembre 2026. S'appuie sur `mobilecli` (Go, MIT), livré en binaire `linux-arm64`. Trouve `adb` dans le `PATH`. N'installe pas d'APK : il pousse un `.dex` dans `/data/local/tmp` et le lance par `app_process`. Essayé : il marche sous proot. |
| Android-MCP 0.2.0 (PyPI, uiautomator2) | Repli, non essayé : mobile-mcp a suffi. |
| DroidRun | Écarté : il a sa propre boucle d'agent et sa clé de LLM, et il installe une APK. On veut des outils pour nos agents, pas un troisième agent. |

Pour relier la Debian à adbd :

| Voie | Verdict |
| --- | --- |
| **Débogage sans fil**, `adb pair` puis `adb connect 127.0.0.1:<port>` | **Retenue.** Une association, une fois, depuis l'écran de la console. Ensuite, aucun ordinateur n'est nécessaire. |
| `adb tcpip 5555` depuis le Mac, puis `adb connect 127.0.0.1:5555` | Repli seulement. Il faut le Mac à chaque redémarrage. Et `adb tcpip` redémarre adbd, ce qui coupe les autres sessions USB. `start.sh` essaie quand même `127.0.0.1:5555`, au cas où. |
| Client adb : paquet Debian `adb` (34.0.5) ou `android-tools` de Termux | Paquet Debian, pour que mobile-mcp le trouve dans son `PATH`, sans pont entre Termux et la Debian. |

## Décision

1. **Serveur MCP `thor_android`** = mobile-mcp 1.0.17 :
   - épinglé dans `/opt/thor-android` ;
   - lancé par `~dev/.local/bin/thor-android-mcp` ;
   - déclaré aux deux agents comme `thor_browser` : Claude en portée utilisateur, Codex dans `~/.codex/config.toml`.
   
   La télémétrie est coupée (`MOBILEMCP_DISABLE_TELEMETRY=1`). Cela retire aussi l'outil `get_more_tools`, qui envoyait du contexte aux auteurs de mobile-mcp.
2. **adb local** : paquet Debian `adb`, relié à adbd par le débogage sans fil, sur `127.0.0.1`.
3. **`/opt/thor-android/start.sh`**, appelé par le pont à son démarrage. Il démarre le serveur adb s'il n'y en a pas, puis cherche la console dans cet ordre :
   - déjà connectée ;
   - `127.0.0.1:5555` ;
   - port du débogage sans fil, lu par `/system/bin/getprop service.adb.tls.port`, en chemin absolu car le `PATH` de la Debian ne le contient pas (même contexte SELinux que `persist.adb.wifi.guid`, que Termux lit) ;
   - puis par mDNS.
   
   Il écrit une ligne `THOR_ANDROID=ok|erreur …`, documentée dans `thor/computer-use/README.md`. Durée mesurée : 0,15 à 0,4 s.
4. **Écran bas : `thor-screen`**, petit script adb (`ids`, `capture haut|bas`, `tap haut|bas X Y`). Les ids physiques sont relus à chaque appel (`cmd display get-displays`), pas codés en dur.
5. Tout est scripté dans `thor/computer-use/` : `install.sh`, `uninstall.sh`, `pair-from-mac.sh`.

## Essais (13 novembre 2026)

- `install.sh` sur la Thor : adb 34.0.5-debian, mobilecli 1.0.23. Deux passages de suite : le second ne change rien. Après désinstallation puis réinstallation, `config.toml` revient identique à l'octet près.
- L'association attend l'utilisateur. Les essais ont donc pris un **échafaudage** : `adb reverse tcp:5037 tcp:5037`, qui fait voir au client adb de la Debian le serveur adb du Mac.
  - mobilecli joint son serveur sur l'appareil par un `adb forward`. Avec un serveur adb distant, le forward s'ouvre sur le Mac et pas sur la console.
  - Pour l'essai seulement, une cale `adb` (via `ANDROID_HOME`) doublait chaque `forward tcp:N` d'un `reverse tcp:N`.
  - Avec le serveur adb local prévu, ce problème n'existe pas. La cale et les reverse ont été retirés.
- Consigne « ouvre les Paramètres, va dans À propos et dis-moi la version d'Android » :
  - Claude Code 2.1.322, `claude -p … --allowedTools "mcp__thor_android__*"` : « Android 13 », en 25 s, uniquement par les outils `thor_android` ;
  - Codex 0.167.2, `codex exec` : « Android 13 », en 48 s, uniquement par les outils `thor_android`, sans demande de validation.
- Écran bas : Claude a fait `thor-screen capture bas` et décrit l'écran. La consigne lui donnait la commande à lancer : il ne l'a pas trouvée seul.
- Sorties et captures : `artifacts/screenshots/03/`.

## Limites

- **mobile-mcp ne voit que l'écran haut.** mobilecli code `defaultDisplayID = "0"`. Sur l'écran bas, `thor-screen` ne fait que la capture et le toucher : ni arbre d'interface, ni saisie.
- **Les agents ne connaissent pas `thor-screen`** si on ne le leur dit pas. Voir « Questions ouvertes ».
- **Le débogage sans fil exige le Wi-Fi.** Sans réseau, pas d'accès.
- **Après un redémarrage**, le débogage sans fil peut revenir seul si le réseau a été coché « Toujours autoriser sur ce réseau ». Sinon, il faut le rallumer à la main dans les Options pour les développeurs. À confirmer au premier redémarrage. L'association, elle, survit.
- **Le serveur adb vit dans la session proot qui l'a lancé** (`proot --kill-on-exit`). Il faut que le pont appelle `start.sh` depuis sa propre session, qui dure. Un agent lancé hors du pont démarre son propre serveur adb. Celui-ci ne retrouve la console que si le débogage sans fil y est déjà relié. Non vérifié : `adb mdns check` ne répond rien avec le paquet Debian.
- **mobilecli injecte un agent JVMTI dans l'appli au premier plan, si elle est débogable.** C'est le cas des builds de debug de Régie. Il copie `mobilecli.so` et `mobilecli.dex` dans les données de l'appli (`run-as`), puis lance `am attach-agent`, pour détecter Flutter. Les builds de release ne sont pas touchés. Aucun réglage ne le coupe en 1.0.23.
- **Le serveur de mobilecli sur l'appareil** (`app_process … DeviceServer`, utilisateur `shell`) reste actif 30 minutes après le dernier appel. Pendant ce temps, il occupe le service UiAutomation : `uiautomator dump` lancé par un autre outil échoue.
- mobile-mcp expose aussi des outils de « flotte distante » de mobile-next. Ils exigent un compte et restent inactifs ici.

## Sécurité

**Ce que l'accès permet** : tout ce que peut l'utilisateur `shell` d'adb, sans être root :
- voir les deux écrans, toucher, glisser, taper partout, y compris valider une boîte de dialogue ;
- lancer, arrêter, installer et désinstaller des applis, effacer leurs données ;
- lire les journaux (logcat), le presse-papiers et le stockage partagé (`/sdcard`) ;
- changer des réglages (`settings put`) ;
- lire les données privées des applis débogables (`run-as`).

Il ne permet pas de lire les données privées des applis non débogables.

**Qui y a accès** :
- tout processus de Termux et de la Debian (même utilisateur Android) : les deux agents, le pont, code-server, n'importe quel script ou paquet npm qu'on y lance. Ils ont la clé `~dev/.android/adbkey` et le serveur adb ;
- toute appli de la console, tant que le serveur adb tourne : il écoute sur `127.0.0.1:5037` sans authentification ;
- sur le réseau, seuls les appareils dont la clé est associée (TLS mutuel). Aujourd'hui : le Mac, en USB. Après l'association : la Debian. Le port d'association n'est ouvert que pendant que la fenêtre de code est affichée.

**Garde-fous** : les validations propres à chaque agent. Claude n'appelle les outils `thor_android` qu'avec l'autorisation `mcp__thor_android__*`. Codex, en `codex exec`, les a appelés sans aucune validation lors de l'essai.

**Risque connu : un agent peut valider ses propres demandes.** Régie affiche les demandes de validation dans une bannière, sur l'écran haut et sur l'écran bas. Un agent peut toucher cette bannière :
- sur l'écran haut, avec les outils `mobile_*` de `thor_android` ;
- sur l'écran bas, avec `thor-screen tap bas X Y`.

Il peut ainsi valider sa propre demande, ou celle de l'autre agent. Cela contourne la règle du PRD « rien ne part sans un appui sur A ». Rien ne l'en empêche aujourd'hui. Suivis possibles, à trancher par l'utilisateur (voir « Questions ouvertes ») :
- interdire aux agents de toucher Régie : refuser les gestes quand Régie est au premier plan, ou le leur interdire dans leurs consignes ;
- soumettre les outils `thor_android` (et `thor-screen`) à validation, au prix d'une validation par geste.

**Piste non vérifiée : fermer le port 5037.** Avec `ADB_SERVER_SOCKET=localfilesystem:<dossier privé de Termux>`, le serveur adb écoute sur une socket Unix, et non plus sur `127.0.0.1:5037`. Les autres applis ne le joindraient plus. Essai rapide sous proot, le 13 novembre : le serveur démarre sur la socket, le client lui parle, et le port 5037 reste fermé. Non vérifié en revanche : la liaison avec la console par le débogage sans fil, et mobilecli à travers cette socket. La variable devrait aussi être passée aux serveurs MCP : Codex filtre leur environnement. Non retenu pour l'instant.

## Comment couper

- **Tout de suite** : sur la console, désactiver « Utiliser le débogage sans fil » (Paramètres › Options pour les développeurs › Débogage sans fil).
- **Retirer l'association** :
  - Débogage sans fil › Appareils associés › l'appareil › Retirer ;
  - ou « Annuler autorisations pour débog. USB », qui retire aussi le Mac.
- **Côté Debian** : `thor/computer-use/uninstall.sh` depuis le Mac. Il retire `thor_android` des deux agents, arrête le serveur adb et efface les fichiers posés, y compris la clé adb de la Debian.
- `stop.sh` arrête seulement le serveur adb : la clé reste.

## Conséquences

- Le pont (ticket 02) appelle `/opt/thor-android/start.sh` à son démarrage, depuis sa propre session Debian, et lit la ligne `THOR_ANDROID=`.
- Après une réinitialisation de la console : `install.sh`, puis l'association (README).
- Une montée de version de mobile-mcp se fait en changeant `MOBILE_MCP_VERSION` dans `install.sh`, après un nouvel essai sur la Thor.

## Questions ouvertes

1. **Comment les agents apprennent-ils que `thor-screen` existe**, pour l'écran bas ? La preuve « écran bas » a été obtenue avec une consigne qui donnait la commande. Deux pistes :
   - leurs consignes globales (`~/.claude/CLAUDE.md`, `~/.codex/AGENTS.md`), hors du périmètre de ce ticket ;
   - le pont, qui le leur dit à chaque session.
2. **Un agent peut valider ses propres demandes** en touchant la bannière de Régie (voir « Sécurité »). Faut-il interdire aux agents de toucher Régie, ou faire passer les outils `thor_android` en validation ? Question pour l'utilisateur.
