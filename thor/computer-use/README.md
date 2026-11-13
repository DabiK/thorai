# Accès des agents à Android

Claude Code et Codex, dans la Debian de la Thor, voient l'écran d'Android et agissent dessus. Ils passent par le serveur MCP `thor_android` ([mobile-mcp](https://github.com/mobile-next/mobile-mcp) 1.0.17) et par adb en local. Décision et sécurité : [ADR 03](../../docs/adr/03-acces-des-agents-a-android.md).

- **Écran haut** : les outils `mobile_*` de `thor_android` (capture, arbre d'interface, toucher, glisser, saisie, lancer une appli).
- **Écran bas** : mobile-mcp ne le voit pas. Les agents utilisent `thor-screen` :
  - `thor-screen ids` : les deux écrans, avec leur `displayId` et leur id physique ;
  - `thor-screen capture haut|bas FICHIER.png` ;
  - `thor-screen tap haut|bas X Y`.

## Installer

Depuis le Mac, console branchée en USB :

```sh
thor/computer-use/install.sh
```

Le script peut être relancé sans risque. Il pose :
- dans la Debian : le paquet `adb` et `/opt/thor-android/` (mobile-mcp, `start.sh`, `stop.sh`, `pair.sh`) ;
- dans `~dev/.local/bin/` : `thor-android-mcp` et `thor-screen` ;
- la déclaration `thor_android` pour Claude (`~dev/.claude.json`, portée utilisateur) et pour Codex (`~/.codex/config.toml` de Termux).

Il ne touche à aucune autre entrée de ces fichiers.

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

Le pont lance `/opt/thor-android/start.sh` à son démarrage. `/opt/thor-android/stop.sh` arrête le serveur adb de la Debian. `start.sh` affiche une seule ligne :

| Sortie | Code | Sens |
| --- | --- | --- |
| `THOR_ANDROID=ok serial=<série> via=existant\|tcpip\|sans-fil` | 0 | Les agents peuvent piloter Android. |
| `THOR_ANDROID=erreur raison=adb-absent\|serveur-adb` | 1 | Relancer `install.sh`. |
| `THOR_ANDROID=erreur raison=non-connecte` | 2 | Débogage sans fil coupé ou pas associé. |

En cas d'erreur, l'explication en français est sur la sortie d'erreur.

**Après un redémarrage de la console, ou sur un autre réseau Wi-Fi**, le débogage sans fil peut être coupé. Il faut alors le rallumer à la main : **Paramètres › Options pour les développeurs › Débogage sans fil › Utiliser le débogage sans fil**. L'association, elle, reste valable : pas besoin de refaire le code.

Le serveur adb vit dans la session Debian qui l'a lancé (proot `--kill-on-exit`). Il s'arrête donc avec le pont.

## Couper l'accès

- **Tout de suite, sur la console** : désactiver **Utiliser le débogage sans fil**. Pour retirer aussi l'association : **Débogage sans fil › Appareils associés**, toucher l'appareil, puis **Retirer**. Ou bien **Options pour les développeurs › Annuler autorisations pour débog. USB**, qui révoque aussi le Mac.
- **Côté Debian, depuis le Mac** :
  ```sh
  thor/computer-use/uninstall.sh
  ```
  Ce script retire `thor_android` des deux agents, arrête le serveur adb et efface `/opt/thor-android`, les lanceurs et la clé adb de la Debian (`~dev/.android/adbkey`). Le paquet `adb` reste installé.
