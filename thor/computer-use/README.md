# Accès des agents à Android

Claude Code et Codex, dans la Debian de la Thor, voient l'écran d'Android et agissent dessus. Ils passent par le serveur MCP `thor_android` ([mobile-mcp](https://github.com/mobile-next/mobile-mcp) 1.0.17) et par adb en local. Décision et sécurité : [ADR 03](../../docs/adr/03-acces-des-agents-a-android.md).

- **Écran haut** : les outils `mobile_*` de `thor_android` (capture, arbre d'interface, toucher, glisser, saisie, lancer une appli).
- **Écran bas** : mobile-mcp ne le voit pas. Les agents utilisent `thor-screen` :
  - `thor-screen ids` : les deux écrans, avec leur `displayId` et leur id physique ;
  - `thor-screen capture haut|bas FICHIER.png`, qui crée le dossier du fichier. Les consignes demandent `/tmp/thor-screen/`, que Claude lit sans validation ;
  - `thor-screen tap haut|bas X Y` ;
  - `thor-screen swipe haut|bas X1 Y1 X2 Y2 [ms]` ;
  - `thor-screen text haut|bas "texte"`, dans le champ qui a le focus. Caractères ASCII seulement : `input text` d'Android ne sait pas taper les accents ;
  - `thor-screen launch haut|bas PAQUET` : lance l'activité de lancement du paquet sur cet écran, Régie comprise.

  Pas d'arbre d'interface sur l'écran bas : l'agent s'appuie sur la capture.

Les agents l'apprennent par leurs consignes globales ([`consignes.md`](consignes.md)).

**Les agents ont la main sur tout Android, sans validation** (décision de l'utilisateur du 13 novembre, ticket 14) : les deux écrans, toutes les applis, Régie comprise (`app.regie`). Ni Claude ni Codex ne demandent de validation pour les outils `thor_android`, pour `thor-screen` ni pour `adb`. Ce que cela ouvre :
- un agent peut toucher les boutons de Régie, donc valider ses propres demandes ou celles de l'autre agent ;
- une consigne cachée dans une page web, un fichier ou un dépôt peut faire piloter Android par un agent, sans que personne ne valide.

Pour remettre des restrictions plus tard, au choix :
- **adb seul** (plus de ports `127.0.0.1` ni d'injection dans Régie) : `mkdir -p ~/.config/thor-android && touch ~/.config/thor-android/adb-seul`, dans la Debian, en `dev` ;
- **revalider chaque geste** : retirer les entrées de [`permissions.sh`](permissions.sh) de `permissions.allow` (`~dev/.claude/settings.json`), et pour Codex la ligne `default_tools_approval_mode` de `config.toml` et le fichier `~/.codex/rules/thor-android.rules` ;
- **protéger Régie** : ajouter une phrase à `consignes.md` (par exemple « ne touche jamais `app.regie` »), puis relancer `install.sh`. Ce n'est qu'une consigne.

Le serveur adb de la Debian écoute sur une socket Unix privée, `/home/dev/.thor-adb/server` (`ADB_SERVER_SOCKET=localfilesystem:…`), et plus sur `127.0.0.1:5037` : aucune autre appli de la console ne peut le joindre. Tous les scripts et les déclarations MCP passent par elle. Reste un risque, côté serveur de mobilecli sur la console : voir l'ADR 03, « Sécurité ».

`thor-android-mcp` lance mobile-mcp en mode mobilecli (`MOBILEMCP_LEGACY_ROBOT=0`) : arbre d'interface de l'écran haut, toucher par élément, tous les outils. Ce mode ouvre des ports `127.0.0.1` sur la console, lance un démon `mobilecli` dans la Debian et injecte un agent dans l'appli débogable au premier plan, dont le build de debug de Régie. Le mode adb seul (`input`, `uiautomator dump`, `screencap`) reste un réglage : il suffit que le fichier `~dev/.config/thor-android/adb-seul` existe. `install.sh` ne touche pas à ce fichier : le réglage survit à une réinstallation. Pour revenir au mode mobilecli : `rm ~/.config/thor-android/adb-seul`.

## Navigateur visible sur la console

Une demande portant sur la console, son écran, une appli ou « le navigateur »
doit agir sur Android avec `thor_android` ou `thor-screen`. `thor_browser` est
le Chromium **sans fenêtre** de la Debian : il sert aux tests et à l’extraction
web, et l’agent doit annoncer que son action est invisible sur la console.

mobile-mcp **1.0.17** expose directement `mobile_open_url` :

1. Appeler `mobile_list_available_devices` et reprendre l’identifiant renvoyé
   (ce n’est pas la série USB du Mac). Si la liste est vide, lancer
   `/opt/thor-android/start.sh` dans la session Debian de l’agent, puis relire
   la liste. Un échec de connexion se signale, sans passer au navigateur invisible.
2. Appeler `mobile_open_url` avec `device` et `url`, par exemple
   `{"device":"<id renvoyé>","url":"https://www.wikipedia.org"}`.
3. Vérifier la page par capture ou arbre d’interface, puis les applis au
   premier plan des deux écrans. Un succès de l’outil seul ne suffit pas.

L’outil accepte HTTP/HTTPS ; ajouter `https://` à un simple nom de domaine.
Il utilise le navigateur Android par défaut, sans paquet Chrome imposé.
Si Android affiche un choix de navigateur ou une configuration initiale,
la page n’est pas encore prouvée ouverte : suivre ce qui est affiché.

Repli dans la Debian, avec la cale `adb` installée (socket privée conservée) :

```sh
adb shell 'am start --display 0 -a android.intent.action.VIEW -d "https://www.wikipedia.org"'
```

Cet intent `VIEW` sans `-n` ni `-p` utilise l’appli par défaut et vise l’écran
haut (`0`). Les guillemets de l’URL doivent aussi parvenir au shell Android,
notamment pour les `&` d’une requête ; ne pas concaténer une URL brute.
mobile-mcp en mode mobilecli passe par sa commande `url` ; en mode adb seul,
son adapter Android exécute `am start -a android.intent.action.VIEW -d …`.

Sources : [outils documentés de mobile-mcp](https://github.com/mobile-next/mobile-mcp#-available-mcp-tools),
vérifiés avec Context7 le 18 novembre 2026, et code installé de la version
1.0.17 (`lib/server.js`, `lib/mobile-device.js`, `lib/android.js`).
Essais réels et limites : `artifacts/screenshots/24/essai-thor.md`.

Après modification des consignes, relancer `thor/computer-use/install.sh`, puis
créer une **nouvelle session Codex depuis Régie** : les sessions déjà ouvertes
peuvent conserver leurs anciennes consignes. Cela ne réinstalle pas `app.regie`.
Les tests locaux ne lancent ni service, ni console, ni agent :

```sh
bash thor/computer-use/test-consignes.sh
bash thor/computer-use/test-block.sh
bash thor/computer-use/test-permissions.sh
bash thor/computer-use/test-pair.sh
```

## Installer

Depuis le Mac, console branchée en USB :

```sh
thor/computer-use/install.sh
```

Le script peut être relancé sans risque : un second passage ne change aucun fichier. Il pose :
- dans la Debian : les paquets `adb` et `procps` (pour `pgrep`), s'ils manquent, et `/opt/thor-android/` (mobile-mcp, `start.sh`, `stop.sh`, `pair.sh`) ;
- dans `~dev/.local/bin/` : `thor-android-mcp`, `thor-screen` et `adb`, une cale qui impose la socket privée à tout `adb` lancé par les agents. Elle n'écrase pas un `adb` qui ne vient pas d'elle ;
- les consignes de `consignes.md`, entre `<!-- thor-android:début -->` et `<!-- thor-android:fin -->`, dans `~dev/.claude/CLAUDE.md` (Claude) et `~/.codex/AGENTS.md` de Termux (Codex). Le bloc est remplacé s'il existe, sinon ajouté à la fin ; le fichier est créé s'il manque ;
- la déclaration `thor_android` pour Claude (`~dev/.claude.json`, portée utilisateur) et pour Codex (`~/.codex/config.toml` de Termux), avec `ADB_SERVER_SOCKET` dans son `env`. Une ancienne déclaration différente est remplacée ;
- **Claude, sans validation** : les entrées de `permissions.sh` dans `permissions.allow` de `~dev/.claude/settings.json` : `mcp__thor_android__*`, `Bash(thor-screen *)`, `Bash(adb *)` et `Read(//tmp/thor-screen/**)` (lire les captures). Les entrées qui manquent sont ajoutées à la fin ; le fichier est créé s'il manque. Une commande composée (`a && b`) n'est acceptée sans validation que si chaque morceau l'est ;
- **Codex, sans validation** : `default_tools_approval_mode = "approve"` dans `[mcp_servers.thor_android]` (sans elle, Codex demande « Allow the thor_android MCP server to run tool… ? »), et [`thor-android.rules`](thor-android.rules) dans `~/.codex/rules/`, qui autorise `adb` et `thor-screen`. Un appel par chemin complet (`/home/dev/.local/bin/adb`) ne correspond pas à ces règles : Codex applique alors son réglage général, qui, avec `danger-full-access`, ne demande rien pour une commande ordinaire.

Il ne touche à aucune autre entrée de ces fichiers. Si les consignes ne peuvent pas être posées, il s'arrête en erreur avant les déclarations MCP ; une déclaration `thor_android` laissée par une installation précédente reste alors en place (`uninstall.sh` la retire). Pour changer les consignes : modifier `consignes.md`, puis relancer `install.sh`.

## Associer la console (une fois)

L'association passe par une boîte de dialogue sur l'écran de la console. Seul l'utilisateur peut la faire. La console doit être sur un réseau Wi-Fi.

1. Sur la console, ouvrir **Paramètres › Options pour les développeurs › Débogage sans fil**.
2. Activer **Utiliser le débogage sans fil**. À la question « Autoriser le débogage sans fil sur ce réseau ? », cocher **Toujours autoriser sur ce réseau**, puis toucher **Autoriser**.
3. Toucher **Associer l'appareil avec un code d'association**. La fenêtre « Associer à l'appareil » affiche :
   - le **Code d'association via le Wi-Fi**, à 6 chiffres ;
   - l'**Adresse IP et port**, par exemple `203.0.113.1:37145`. Seul le port compte (`37145`).
4. Sans fermer cette fenêtre, lancer l'association avec le port et le code, au choix :
   - depuis le Mac, console en USB :
     ```sh
     thor/computer-use/pair-from-mac.sh 37145 123456
     ```
   - ou sur la console, dans Termux, sur l'autre écran : entrer dans la Debian en `dev` (`proot-distro login debian --user dev`), puis `/opt/thor-android/pair.sh 37145 123456`.
5. Le script répond `THOR_ANDROID=ok serial=… via=sans-fil`. La Debian apparaît dans **Appareils associés**.

L'association doit se faire avec le serveur adb de la Debian, jamais par un serveur relayé (par exemple un échafaudage d'essai qui renvoie la socket vers le serveur adb du Mac) : `adb pair` associerait alors la clé de l'autre machine, et l'accès ne marcherait pas sans elle. `pair.sh` refuse dans ce cas (code 3) : retirer le relais, puis recommencer.

## Au quotidien

Le pont lance `/opt/thor-android/start.sh` à son démarrage. `/opt/thor-android/stop.sh` arrête le démon de mobilecli dans la Debian et son serveur sur la console, puis le serveur adb de la socket. `start.sh` affiche une seule ligne :

| Sortie | Code | Sens |
| --- | --- | --- |
| `THOR_ANDROID=ok serial=<série> via=existant\|tcpip\|sans-fil` | 0 | Les agents peuvent piloter Android. |
| `THOR_ANDROID=erreur raison=adb-absent\|getprop-absent\|serveur-adb` | 1 | Relancer `install.sh`. |
| `THOR_ANDROID=erreur raison=non-connecte` | 2 | Débogage sans fil coupé ou pas associé. |

En cas d'erreur, l'explication en français est sur la sortie d'erreur.

**Après un redémarrage de la console, ou sur un autre réseau Wi-Fi**, le débogage sans fil peut être coupé. Si Régie a reçu `WRITE_SECURE_SETTINGS` (`pm grant`, voir la [notice d'installation](../../docs/installation.md#7-les-autres-autorisations)), elle le rallume à son ouverture puis relance la liaison. Sinon, le rallumer à la main : **Paramètres › Options pour les développeurs › Débogage sans fil › Utiliser le débogage sans fil**. Sur un autre réseau, il faut aussi l'autoriser. L'association, elle, reste valable : pas besoin de refaire le code.

Le serveur adb vit dans la session Debian qui l'a lancé (proot `--kill-on-exit`). Il s'arrête donc avec le pont.

Pour lancer `adb` à la main dans la Debian, passer par `adb` (la cale de `~/.local/bin`), sans changer `ADB_SERVER_SOCKET`. `/usr/bin/adb` en chemin absolu démarrerait un second serveur, sur `127.0.0.1:5037`, joignable par toute appli.

## Couper l'accès

- **Tout de suite, sur la console** : désactiver **Utiliser le débogage sans fil**. Pour retirer aussi l'association : **Débogage sans fil › Appareils associés**, toucher l'appareil, puis **Retirer**. Ou bien **Options pour les développeurs › Annuler autorisations pour débog. USB**, qui révoque aussi le Mac.
- **Côté Debian, depuis le Mac** :
  ```sh
  thor/computer-use/uninstall.sh
  ```
  Ce script :
  - retire `thor_android` des deux agents, leurs autorisations sans validation (les entrées de `permissions.sh` dans `settings.json`, et `~/.codex/rules/thor-android.rules`, puis le dossier `rules` s'il est vide), et le bloc de consignes de `CLAUDE.md` et d'`AGENTS.md`. Seules nos entrées partent de `permissions.allow`. Limite : une entrée identique que l'utilisateur aurait posée lui-même part aussi. Le reste de ces fichiers garde son contenu ; `settings.json` est réécrit au format de jq (indentation de 2, saut de ligne final) et perd un `permissions.allow` resté vide, les autres fichiers reviennent à l'octet près, sauf qu'un fichier sans saut de ligne final en gagne un ; un fichier qui ne contient plus rien après le retrait est effacé, qu'`install.sh` l'ait créé ou qu'il ait déjà été vide avant. Si un bloc ne peut pas être retiré (marqueurs cassés, fichier en lecture seule), le script le signale et finit en erreur, mais coupe quand même tout le reste ;
  - arrête le serveur de mobilecli et le serveur adb ;
  - efface `/opt/thor-android`, les lanceurs, la cale `adb`, le dossier de la socket et `~dev/.mobilecli` (journal du démon de mobilecli). **La clé adb de la Debian (`~dev/.android/adbkey`) est gardée**, pour qu'une réinstallation n'oblige pas à refaire l'association (décision de l'utilisateur, 13 novembre). Pour l'effacer aussi, et perdre l'association : `uninstall.sh --oublier-association` ;
  - efface `/data/data/app.regie/mobilecli`, que mobilecli a pu poser dans les données de Régie (par `run-as`, en USB depuis le Mac). Rien d'autre n'est touché dans ces données. Si Régie est absente ou n'est pas débogable, le script le dit et continue.

  Le paquet `adb` reste installé.
