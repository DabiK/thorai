# Accès des agents à Android

Claude Code et Codex, dans la Debian de la Thor, voient l'écran d'Android et agissent dessus. Ils passent par le serveur MCP `thor_android` ([mobile-mcp](https://github.com/mobile-next/mobile-mcp) 1.0.17) et par adb en local. Décision et sécurité : [ADR 03](../../docs/adr/03-acces-des-agents-a-android.md).

- **Écran haut** : les outils `mobile_*` de `thor_android` (capture, arbre d'interface, toucher, glisser, saisie, lancer une appli).
- **Écran bas** : mobile-mcp ne le voit pas. Les agents utilisent `thor-screen` :
  - `thor-screen ids` : les deux écrans, avec leur `displayId` et leur id physique ;
  - `thor-screen capture haut|bas FICHIER.png` ;
  - `thor-screen tap haut|bas X Y` ;
  - `thor-screen swipe haut|bas X1 Y1 X2 Y2 [ms]` ;
  - `thor-screen text haut|bas "texte"`, dans le champ qui a le focus. Caractères ASCII seulement : `input text` d'Android ne sait pas taper les accents.

  Pas d'arbre d'interface sur l'écran bas : l'agent s'appuie sur la capture.

Les agents l'apprennent par leurs consignes globales ([`consignes.md`](consignes.md)), qui leur interdisent aussi de toucher l'app Régie (`app.regie`).

Le serveur adb de la Debian écoute sur une socket Unix privée, `/home/dev/.thor-adb/server` (`ADB_SERVER_SOCKET=localfilesystem:…`), et plus sur `127.0.0.1:5037` : aucune autre appli de la console ne peut le joindre. Tous les scripts et les déclarations MCP passent par elle. Reste un risque, côté serveur de mobilecli sur la console : voir l'ADR 03, « Sécurité ».

## Installer

Depuis le Mac, console branchée en USB :

```sh
thor/computer-use/install.sh
```

Le script peut être relancé sans risque : un second passage ne change aucun fichier. Il pose :
- dans la Debian : les paquets `adb` et `procps` (pour `pgrep`), s'ils manquent, et `/opt/thor-android/` (mobile-mcp, `start.sh`, `stop.sh`, `pair.sh`) ;
- dans `~dev/.local/bin/` : `thor-android-mcp`, `thor-screen` et `adb`, une cale qui impose la socket privée à tout `adb` lancé par les agents. Elle n'écrase pas un `adb` qui ne vient pas d'elle ;
- les consignes de `consignes.md`, entre `<!-- thor-android:début -->` et `<!-- thor-android:fin -->`, dans `~dev/.claude/CLAUDE.md` (Claude) et `~/.codex/AGENTS.md` de Termux (Codex). Le bloc est remplacé s'il existe, sinon ajouté à la fin ; le fichier est créé s'il manque ;
- la déclaration `thor_android` pour Claude (`~dev/.claude.json`, portée utilisateur) et pour Codex (`~/.codex/config.toml` de Termux), avec `ADB_SERVER_SOCKET` dans son `env`. Une ancienne déclaration différente est remplacée.

Il ne touche à aucune autre entrée de ces fichiers. Si les consignes ne peuvent pas être posées, il s'arrête en erreur avant les déclarations MCP ; une déclaration `thor_android` laissée par une installation précédente reste alors en place (`uninstall.sh` la retire). Pour changer les consignes : modifier `consignes.md`, puis relancer `install.sh`.

## Associer la console (une fois)

L'association passe par une boîte de dialogue sur l'écran de la console. Seul l'utilisateur peut la faire. La console doit être sur un réseau Wi-Fi.

1. Sur la console, ouvrir **Paramètres › Options pour les développeurs › Débogage sans fil**.
2. Activer **Utiliser le débogage sans fil**. À la question « Autoriser le débogage sans fil sur ce réseau ? », cocher **Toujours autoriser sur ce réseau**, puis toucher **Autoriser**.
3. Toucher **Associer l'appareil avec un code d'association**. La fenêtre « Associer à l'appareil » affiche :
   - le **Code d'association via le Wi-Fi**, à 6 chiffres ;
   - l'**Adresse IP et port**, par exemple `192.168.1.20:37145`. Seul le port compte (`37145`).
4. Sans fermer cette fenêtre, lancer l'association avec le port et le code, au choix :
   - depuis le Mac, console en USB :
     ```sh
     thor/computer-use/pair-from-mac.sh 37145 123456
     ```
   - ou sur la console, dans Termux, sur l'autre écran : `dev-linux`, puis `/opt/thor-android/pair.sh 37145 123456`.
5. Le script répond `THOR_ANDROID=ok serial=… via=sans-fil`. La Debian apparaît dans **Appareils associés**.

## Au quotidien

Le pont lance `/opt/thor-android/start.sh` à son démarrage. `/opt/thor-android/stop.sh` arrête le serveur de mobilecli sur la console, puis le serveur adb de la socket. `start.sh` affiche une seule ligne :

| Sortie | Code | Sens |
| --- | --- | --- |
| `THOR_ANDROID=ok serial=<série> via=existant\|tcpip\|sans-fil` | 0 | Les agents peuvent piloter Android. |
| `THOR_ANDROID=erreur raison=adb-absent\|getprop-absent\|serveur-adb` | 1 | Relancer `install.sh`. |
| `THOR_ANDROID=erreur raison=non-connecte` | 2 | Débogage sans fil coupé ou pas associé. |

En cas d'erreur, l'explication en français est sur la sortie d'erreur.

**Après un redémarrage de la console, ou sur un autre réseau Wi-Fi**, le débogage sans fil peut être coupé. Il faut alors le rallumer à la main : **Paramètres › Options pour les développeurs › Débogage sans fil › Utiliser le débogage sans fil**. L'association, elle, reste valable : pas besoin de refaire le code.

Le serveur adb vit dans la session Debian qui l'a lancé (proot `--kill-on-exit`). Il s'arrête donc avec le pont.

Pour lancer `adb` à la main dans la Debian, passer par `adb` (la cale de `~/.local/bin`), sans changer `ADB_SERVER_SOCKET`. `/usr/bin/adb` en chemin absolu démarrerait un second serveur, sur `127.0.0.1:5037`, joignable par toute appli.

## Couper l'accès

- **Tout de suite, sur la console** : désactiver **Utiliser le débogage sans fil**. Pour retirer aussi l'association : **Débogage sans fil › Appareils associés**, toucher l'appareil, puis **Retirer**. Ou bien **Options pour les développeurs › Annuler autorisations pour débog. USB**, qui révoque aussi le Mac.
- **Côté Debian, depuis le Mac** :
  ```sh
  thor/computer-use/uninstall.sh
  ```
  Ce script :
  - retire `thor_android` des deux agents et le bloc de consignes de `CLAUDE.md` et d'`AGENTS.md`. Le reste de ces fichiers revient à l'octet près, sauf qu'un fichier sans saut de ligne final en gagne un ; un fichier qu'`install.sh` avait créé et qui reste vide est effacé. Si un bloc ne peut pas être retiré (marqueurs cassés, fichier en lecture seule), le script le signale et finit en erreur, mais coupe quand même tout le reste ;
  - arrête le serveur de mobilecli et le serveur adb ;
  - efface `/opt/thor-android`, les lanceurs, la cale `adb`, le dossier de la socket et la clé adb de la Debian (`~dev/.android/adbkey`).

  Le paquet `adb` reste installé.
