# Installer Régie sur une AYN Thor

Cette notice part d’une Thor neuve et va jusqu’à la première session pilotée à la voix. Chaque étape se termine par une vérification : ne passer à la suivante que si elle réussit.

Les commandes reprennent celles des scripts du dépôt, relues une à une. La séquence complète n’a pas encore été rejouée sur une console sortie de boîte : si une étape diverge, le [dépannage](#dépannage) liste les cas connus.

## Ce qu’il faut

- **Une AYN Thor** sous Android 13, reliée au Wi-Fi, avec plusieurs gigaoctets libres : Debian, Node et les agents, plus 1,2 Go pour le modèle de voix.
- **Pour le parcours USB seulement, un Mac** avec un câble USB-C. Il construit l’app et pousse les scripts sur la console. Les scripts sont écrits pour macOS ; un Linux avec bash devrait convenir, mais n’a pas été essayé.
- **Un compte pour chaque agent utilisé** : un abonnement Claude qui inclut Claude Code, un compte ChatGPT pour Codex. Un seul des deux suffit pour commencer.
- **Un téléphone**, pour valider la connexion des agents.

Les commandes se tapent dans trois terminaux. Chaque bloc indique le sien :

| Terminal | Où |
| --- | --- |
| **Mac** | Le terminal du Mac, à la racine du dépôt cloné. |
| **Termux** | L’appli Termux, sur la console. |
| **Debian** | La Debian, ouverte depuis Termux, en `root` ou en `dev` selon l’étape. |

## Sans Mac : installation sur la console

Ce parcours remplace les étapes 1 à 5 ci-dessous. Il ne demande aucun ordinateur.

1. Installer Termux depuis ses [releases officielles](https://github.com/termux/termux-app/releases), puis l'ouvrir. Le parcours local fonctionne sans `run-as` ; la variante GitHub debug reste nécessaire pour utiliser ensuite les scripts USB du Mac. Garder la même provenance de Termux lors des mises à jour.
2. Dans Termux, préparer le dépôt puis lancer l'installeur :

   ```sh
   pkg update && pkg install git
   git clone https://github.com/DabiK/regie.git
   cd regie
   bash thor/install-on-device.sh
   ```

   Le dépôt doit être accessible au téléchargement (public, ou avec votre propre accès). L'installeur prépare Debian 13, Node et les deux agents aux versions éprouvées, le pont et l'accès Android. Il peut être relancé après une interruption ou une mise à jour ; projets, connexions, clé adb et consignes hors des blocs Régie sont gardés. Il ne lance aucun agent et ne démarre rien au boot.
3. Télécharger l'APK et son fichier `.sha256` dans les [releases de Régie](https://github.com/DabiK/regie/releases). Pour vérifier le téléchargement dans Termux, autoriser une fois `termux-setup-storage`, puis lancer `cd ~/storage/downloads && sha256sum -c regie-<version>.apk.sha256`. Ouvrir l'APK depuis le gestionnaire de fichiers Android et autoriser cette source si Android le demande. **Un ancien debug doit être désinstallé si sa signature diffère** : les préférences et modèles privés de Régie seront perdus, les projets et comptes dans Termux sont conservés.
4. Ouvrir Régie et accepter **exécuter des commandes Termux** et les notifications. L'app crée son jeton et le transmet à la commande de configuration Termux par RUN_COMMAND ; aucune copie `run-as app.regie` n'est nécessaire. Attendre l'accueil et les versions des agents.
5. Activer **Options pour les développeurs › Débogage sans fil › Associer avec un code**. Garder la boîte de dialogue ouverte. Dans Termux, entrer dans la Debian :

   ```sh
   proot-distro login debian --user dev \
     --bind "$HOME/projects:/home/dev/projects" --bind "$HOME/.codex:/home/dev/.codex" \
     --work-dir /home/dev -- bash -l
   /opt/thor-android/pair.sh <port> <code>
   adb shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS
   adb shell settings put global settings_enable_monitor_phantom_procs false
   exit
   ```

   `port` vient de la boîte de dialogue d'association, pas de la page principale. Le script doit annoncer `THOR_ANDROID=ok`. Ni le code ni une clé ne se saisissent dans Régie.
6. Retirer Termux de l'optimisation de batterie. Suivre les étapes 7 (microphone et Accessibilité), 8 (comptes) et 9 (première session) ci-dessous. Si le pont a démarré avant l'association, quitter puis rouvrir Régie.

**Mise à jour locale** : quitter Régie, faire `git pull` dans le dépôt Termux, puis `bash thor/install-on-device.sh`, installer l'APK signé par la même clé et rouvrir Régie. Une nouvelle association n'est pas nécessaire.

## 1. Préparer le Mac

Installer git, un JDK 21 ou 25, et le SDK Android en ligne de commande avec `platform-tools` (pour `adb`), la plateforme `android-36` et des build-tools 36. `adb` doit être dans le `PATH`.

Dans le terminal **Mac** :

```sh
git clone https://github.com/DabiK/regie.git
cd regie
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools  # adapter à l’emplacement du SDK
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > android/local.properties
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > voice/local.properties
```

Sur la console, ouvrir **Paramètres › À propos**, toucher sept fois le numéro de build, puis activer **Options pour les développeurs › Débogage USB**. Brancher le Mac et accepter sa clé sur la console. Puis, dans le terminal **Mac** :

```sh
adb devices
export THOR_SERIAL=XXXXXXXX  # remplacer par la série affichée par adb devices
```

**Les scripts sélectionnent l’unique appareil adb autorisé.** Avec plusieurs appareils, exporter `THOR_SERIAL` dans chaque terminal. Les commandes adb écrites ci-dessous utilisent cette variable.

**Vérification** : `adb -s "$THOR_SERIAL" get-state` répond `device`.

## 2. Termux et Debian

### Termux, version GitHub

Installer **Termux depuis ses [releases GitHub](https://github.com/termux/termux-app/releases)**, fichier `…github-debug_arm64-v8a.apk`. Les scripts passent par `run-as com.termux`, qui exige cette variante *debug*. Ne pas mélanger avec la version F-Droid ou Google Play : les signatures diffèrent. Depuis le terminal **Mac** :

```sh
adb -s "$THOR_SERIAL" install termux-app_<version>+github-debug_arm64-v8a.apk
```

Ouvrir Termux une fois sur la console et attendre la fin de son installation initiale. Puis, dans le terminal **Mac** :

```sh
adb -s "$THOR_SERIAL" shell run-as com.termux id
adb -s "$THOR_SERIAL" shell settings put global settings_enable_monitor_phantom_procs false
```

La première commande doit afficher un `uid=`. « package not debuggable » signifie que ce Termux n’est pas la variante GitHub debug. La seconde empêche Android de tuer les processus de fond de Termux.

Sur la console, retirer aussi Termux de l’optimisation de la batterie (Infos sur l’appli › Batterie).

### Debian

Dans **Termux** :

```sh
pkg update && pkg upgrade
pkg install proot-distro curl util-linux
mkdir -p ~/projects ~/.codex
proot-distro install --name debian debian:13
```

`curl` et `util-linux` sont souvent déjà présents ; les scripts de démarrage du pont s’en servent (`curl`, `flock`, `setsid`). Le conteneur doit s’appeler `debian`. `~/projects` recevra les projets des agents, `~/.codex` la configuration de Codex : la Debian les voit en `/home/dev/projects` et `/home/dev/.codex`.

**Vérification**, dans **Termux** :

```sh
cat "$PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc/debian_version"
```

La commande affiche un numéro en 13. Les scripts attendent ce chemin exact ; s’il diffère, la version de PRoot Distro range ses conteneurs ailleurs ([documentation de PRoot Distro](https://github.com/termux/proot-distro#readme)).

## 3. Claude Code et Codex dans la Debian

Les versions ci-dessous sont celles éprouvées avec Régie. D’autres peuvent fonctionner ; l’accueil de Régie les signale comme « version non testée ».

### En root, Node et Codex

Dans **Termux**, ouvrir la Debian en root :

```sh
proot-distro login debian
```

Puis, dans **Debian, en root** :

```sh
apt-get update
apt-get install -y ca-certificates curl xz-utils git jq procps python3 ripgrep
useradd --create-home --shell /bin/bash dev
mkdir -p /home/dev/projects /home/dev/.codex
chown -R dev:dev /home/dev
cd /tmp
curl -fLO https://nodejs.org/dist/v24.22.3/node-v24.22.3-linux-arm64.tar.xz
curl -fLO https://nodejs.org/dist/v24.22.3/SHASUMS256.txt
grep ' node-v24.22.3-linux-arm64.tar.xz$' SHASUMS256.txt | sha256sum -c -
```

Ne continuer que si la dernière ligne répond `OK`. Puis, toujours en root :

```sh
tar -xJf node-v24.22.3-linux-arm64.tar.xz -C /opt
ln -s /opt/node-v24.22.3-linux-arm64/bin/node /usr/local/bin/node
ln -s /opt/node-v24.22.3-linux-arm64/bin/npm /usr/local/bin/npm
ln -s /opt/node-v24.22.3-linux-arm64/bin/npx /usr/local/bin/npx
npm install --global --prefix /opt/codex-cli @openai/codex@0.167.2
ln -s /opt/codex-cli/bin/codex /usr/local/bin/codex
exit
```

Le pont cherche Codex en `/usr/local/bin/codex` et lance Node depuis la Debian. `jq` sert à l’installation de l’accès Android, `git` à la création de projets.

### En dev, Claude Code et réglages de Codex

De retour dans **Termux**, ouvrir la Debian en `dev`, avec les mêmes montages que le pont. **C’est la commande d’entrée dans la Debian** ; elle resservira :

```sh
proot-distro login debian --user dev \
  --bind "$HOME/projects:/home/dev/projects" \
  --bind "$HOME/.codex:/home/dev/.codex" \
  --work-dir /home/dev -- bash -l
```

Dans **Debian, en dev** :

```sh
curl -fsSL https://claude.ai/install.sh | bash -s 2.1.322
printf '\nexport PATH="$HOME/.local/bin:/usr/local/bin:$PATH"\n' >> ~/.profile
export PATH="$HOME/.local/bin:/usr/local/bin:$PATH"
```

L’installeur pose Claude en `~/.local/bin/claude`, là où le pont le cherche. La ligne ajoutée à `~/.profile` le rend visible aux scripts d’installation. ([Installation de Claude Code](https://code.claude.com/docs/en/setup).)

Le sandbox Linux de Codex ne fonctionne pas sous PRoot. Sur une installation neuve, `~/.codex/config.toml` n’existe pas encore ; le créer avec ces deux réglages, toujours en **dev** :

```sh
cat >> ~/.codex/config.toml << 'EOF'
sandbox_mode = "danger-full-access"
approval_policy = "on-request"
EOF
```

Si le fichier existait déjà avec des sections `[…]`, placer plutôt ces deux lignes avant la première section. Régie applique ensuite le mode de validation choisi pour chaque session ([ADR 02](adr/02-pilotage-des-agents.md)).

**Vérification**, en **dev** :

```sh
node --version     # v24.22.3
codex --version    # codex-cli 0.167.2
claude --version   # 2.1.322 (Claude Code)
exit
```

La connexion aux comptes vient plus tard, à l’[étape 8](#8-connecter-les-agents).

## 4. Le pont et l’accès des agents à Android

Les deux scripts se lancent depuis le **Mac**, console branchée. Ils sont idempotents : on peut les relancer après une mise à jour.

```sh
thor/bridge/install.sh
thor/computer-use/install.sh
```

`thor/bridge/install.sh` fait quatre choses :

1. il copie le pont dans la Debian (`/home/dev/.local/share/regie/bridge`) et installe ses dépendances ;
2. il crée un jeton aléatoire, qui protège la connexion entre l’app et le pont ;
3. il passe `allow-external-apps=true` dans Termux, pour que Régie puisse démarrer le pont ;
4. il pose `regie-bridge-configure`, `regie-bridge-start`, `regie-bridge-stop`, `regie-bridge-supervise` et `regie-android-access` dans le `~/bin` de Termux.

À ce stade, il signale que l’app `app.regie` n’est pas installée : c’est normal, son jeton sera copié à l’étape suivante. Rien ne démarre au boot.

`thor/computer-use/install.sh` donne aux agents la main sur Android :

1. dans la Debian, `adb`, [mobile-mcp](https://github.com/mobile-next/mobile-mcp) 1.0.17 et les scripts de `/opt/thor-android/` ;
2. pour `dev`, les commandes `thor-screen` (écran du bas) et une cale `adb` qui garde le serveur adb sur une socket privée ;
3. des consignes globales pour les deux agents, dans `~/.claude/CLAUDE.md` et `~/.codex/AGENTS.md`, entre deux marqueurs ;
4. le serveur MCP `thor_android` déclaré à Claude et à Codex, et ses outils autorisés sans validation.

Le reste des fichiers des agents est conservé. Le détail et la façon de retirer cet accès sont dans le [guide de l’accès Android](../thor/computer-use/README.md).

**Vérification** : le premier script finit par « Installé. » après les étapes 1/4 à 4/4, le second après les étapes 1/5 à 5/5. Pour essayer le pont à la main, dans **Termux** : `~/bin/regie-bridge-start` répond « Pont démarré sur 127.0.0.1:47600. », puis `~/bin/regie-bridge-stop` répond « Pont arrêté. ».

## 5. L’application

Dans le terminal **Mac** :

```sh
scripts/thor-install.sh
thor/bridge/install.sh token-app
```

`thor-install.sh` construit l’APK de debug, installe `app.regie` et l’ouvre. Il n’installe ni ne désinstalle rien d’autre. `token-app` copie ensuite le jeton du pont dans le stockage privé de l’app, sans l’afficher ni l’écrire sur le Mac.

À l’ouverture, Régie demande deux autorisations sur la console. Les accepter :

- **exécuter des commandes dans Termux** (« Run commands in Termux environment ») : Régie démarre et arrête le pont ainsi ;
- **les notifications** : validations demandées, questions, fins et échecs de session.

Régie a démarré avant d’avoir son jeton : la relancer depuis le **Mac** :

```sh
adb -s "$THOR_SERIAL" shell am force-stop app.regie
adb -s "$THOR_SERIAL" shell am start -n app.regie/.TopActivity
```

**Vérification** : l’écran du haut affiche « Régie », « 0 session active » et les versions de Claude et de Codex. L’écran du bas montre le micro et « Maintiens L2 et parle ».

## 6. Associer le débogage sans fil

Les agents agissent sur Android par le débogage sans fil de la console, depuis la Debian. L’association se fait une fois, avec la clé de la Debian : ensuite, le Mac n’est plus nécessaire.

1. Sur la console, ouvrir **Paramètres › Options pour les développeurs › Débogage sans fil** et l’activer. À la question sur ce réseau, cocher **Toujours autoriser sur ce réseau**, puis **Autoriser**.
2. Toucher **Associer l’appareil avec un code d’association**, et laisser la fenêtre ouverte. Elle affiche un code à 6 chiffres et une adresse `IP:port` ; seul le port compte.
3. Dans le terminal **Mac**, avec ce port et ce code :

```sh
thor/computer-use/pair-from-mac.sh <port> <code>
```

Le port d’association n’est pas le port de connexion affiché sur l’écran principal du débogage sans fil. Ne pas utiliser `adb pair` depuis le Mac à la place : c’est la clé du Mac qui serait associée.

**Vérification** : le script répond `THOR_ANDROID=ok serial=… via=sans-fil`, et la Debian apparaît dans **Appareils associés**.

## 7. Les autres autorisations

| Autorisation | À quoi elle sert | Comment l’accorder |
| --- | --- | --- |
| Commandes Termux | Démarrer et arrêter le pont | Demandée à l’ouverture ([étape 5](#5-lapplication)). |
| Notifications | Prévenir d’une décision à prendre | Demandée à l’ouverture. |
| `WRITE_SECURE_SETTINGS` | Rallumer le débogage sans fil à l’ouverture de Régie, coupé à chaque redémarrage | Une fois, depuis le Mac (ci-dessous). |
| Microphone | La dictée | Dans Régie, toucher **Régler la voix**, puis **Autoriser le microphone**. |
| Accessibilité | Le mode console : L2 et R2 depuis toute appli | Dans les réglages d’accessibilité (ci-dessous). |

Dans le terminal **Mac** :

```sh
adb -s "$THOR_SERIAL" shell pm grant app.regie android.permission.WRITE_SECURE_SETTINGS
adb -s "$THOR_SERIAL" shell am force-stop app.regie
adb -s "$THOR_SERIAL" shell am start -n app.regie/.TopActivity
```

Pour le **mode console**, toucher l’avis « Mode console » en bas de l’accueil : les réglages d’accessibilité s’ouvrent sur l’écran du bas. Dans **Applications téléchargées**, choisir **Régie, mode console**, l’activer et accepter. Si Android parle de paramètre restreint : **Infos sur l’appli Régie › ⋮ › Autoriser les paramètres restreints**, puis recommencer.

Le service ne lit que la manette, jamais le contenu de l’écran. Il ne prend que L2 et R2, plus les boutons qu’un panneau de Régie attend ; le reste va à l’appli au premier plan ([ADR 19](adr/19-mode-console.md)).

![Accueil, écran du bas, avant l’activation du mode console](../artifacts/screenshots/vitrine/accueil-bas.png)

**Vérification** : de retour dans Régie, l’avis « Mode console » a disparu. Le microphone est accordé dans **Régler la voix**.

## 8. Connecter les agents

Les connexions restent dans les fichiers des agents, dans la Debian. Aucun jeton ne passe par Régie ni par ses journaux ([ADR 18](adr/18-connexion-des-agents.md)). Au premier contact avec le pont, Régie propose une fois de connecter un agent qui ne l’est pas. Sinon, depuis l’accueil, **B** ouvre **Connexion des agents** ; Gauche et Droite choisissent l’agent.

### Codex, par code d’appareil

1. Choisir Codex, puis **A**. L’écran du haut affiche une adresse, un code et un QR code.
2. Scanner le QR code avec le téléphone, ou ouvrir l’adresse, et saisir le code.
3. Revenir à la console et attendre « Connecté ».

| Écran du haut | Écran du bas |
| --- | --- |
| ![Adresse, code et QR code de Codex](../artifacts/screenshots/23/09-final-simulation-code-haut.png) | ![Connexion en cours](../artifacts/screenshots/23/09-final-simulation-code-bas.png) |

*Capture du banc d’essai, avec un code fictif.*

Si le site refuse, activer la connexion par code d’appareil dans les réglages de sécurité du compte ChatGPT ; sur un compte d’organisation, c’est l’administrateur qui l’autorise.

### Claude, dans Termux

Claude Code ne propose pas de code d’appareil : sa connexion se fait dans Termux. Dans **Termux**, ouvrir la Debian avec la commande d’entrée de l’[étape 3](#en-dev-claude-code-et-réglages-de-codex), puis, en **dev** :

```sh
claude auth login
```

Ouvrir l’adresse affichée sur le téléphone, se connecter, puis recopier dans Termux le code que le navigateur affiche. Revenir dans Régie, choisir Claude, puis **Y** pour actualiser. L’app rappelle ces étapes avec **A**, « Marche à suivre ».

Un changement de compte est refusé tant que l’agent tourne : choisir **Quitter**, rouvrir Régie, puis recommencer.

**Vérification** : dans **Connexion des agents**, chaque agent utilisé affiche « Connecté ».

## 9. Premier lancement

1. **Créer une session.** Sur l’accueil, **X**. La fiche propose l’agent, le projet, le mode de validation, le modèle, la réflexion et la consigne. Pour un premier essai, choisir **Nouveau projet…** et lui donner un nom : Régie crée un dépôt Git vide dans `~/projects` de Termux. **A** lance la session.
2. **Parler.** Maintenir **L2**, dire une consigne, relâcher. La transcription s’écrit sur l’écran du bas. **A** l’envoie, **B** l’efface. La première dictée télécharge le modèle de transcription, environ 490 Mo : il faut du réseau et 1,2 Go libres. Ensuite, la transcription fonctionne hors ligne.
3. **Suivre et régler.** L’écran du haut montre le fil de l’agent. En bas, la table des réglages donne le mode, le modèle et la réflexion : **Droite** la passe en modification, la croix choisit et change, **A** applique. **B** revient à l’accueil.
4. **Piloter la console.** Sur l’accueil, **Y** prépare la session « console », en mode « Tout autoriser ». **A** la crée. Demander par exemple « ouvre le navigateur sur wikipedia.org » : Chrome s’ouvre en haut, Régie reste en bas.
5. **Quitter.** Toucher **Quitter** en bas de l’accueil. Le pont et les sessions s’arrêtent. Passer à une autre appli, au contraire, laisse tourner les sessions.

**Vérification** : la session apparaît sur l’accueil avec son agent, son projet et son état, et l’agent a répondu dans le fil.

Le [README](../README.md#les-commandes-de-la-manette) liste toutes les commandes de la manette.

## Mettre à jour et désinstaller

**Mettre à jour** : choisir **Quitter** dans Régie, faire `git pull`, puis relancer depuis le **Mac** les scripts des parties qui ont changé : `scripts/thor-install.sh` pour l’app, `thor/bridge/install.sh` pour le pont, `thor/computer-use/install.sh` pour l’accès Android. Après un changement de consignes, créer une nouvelle session : les sessions ouvertes gardent les anciennes.

**Désinstaller**, depuis le **Mac** :

```sh
thor/computer-use/uninstall.sh
adb -s "$THOR_SERIAL" uninstall app.regie
```

Le premier retire l’accès Android, ses déclarations et ses consignes ; il garde la clé adb de la Debian, sauf avec `--oublier-association`. Le pont n’a pas de script de désinstallation : ses fichiers sont `~/bin/regie-*` dans Termux, `/home/dev/.local/share/regie/` et `/home/dev/.config/regie/` dans la Debian. Sur la console, couper aussi le débogage sans fil et retirer la Debian des **Appareils associés**.

## Dépannage

### Messages de Régie

| Message | Que faire |
| --- | --- |
| « Clé du pont absente » ou « refusée » | Vérifier les commandes Termux et relancer Régie : elle reconfigure le pont. En debug USB, `thor/bridge/install.sh token-app` reste disponible. |
| « Termux ne répond pas » | Dans Termux : relancer `bash thor/install-on-device.sh` depuis le dépôt ; ou **Mac** : `thor/bridge/install.sh`, puis rouvrir Régie. Vérifier que Termux a été ouvert au moins une fois. |
| « Autorise les commandes Termux » | Sur la console : **Infos sur l’appli Régie › Autorisations › Autorisations supplémentaires**, activer « Run commands in Termux environment ». |
| « Autorise la reconnexion Android depuis le Mac » | **Mac** : la commande `pm grant` de l’[étape 7](#7-les-autres-autorisations). |
| « version non testée » sur l’accueil | Claude Code s’est mis à jour seul. Cela peut marcher ; pour revenir à la version éprouvée, relancer son installeur avec `2.1.322` ([mises à jour de Claude Code](https://code.claude.com/docs/en/setup#disable-auto-updates)). |
| « Rien entendu. Aucun message envoyé. » | La dictée était vide : maintenir L2 pendant toute la phrase. |
| « Action non confirmée… » dans Connexion des agents | Une session de cet agent tourne : **Quitter**, rouvrir Régie, recommencer. |

### Autres problèmes

- **`run-as: package not debuggable`** : Termux n’est pas la variante GitHub debug. La remplacer efface les données de Termux, donc la Debian : reprendre à l’étape 2.
- **Les agents ne voient pas Android.** Le journal du pont le dit. Dans **Termux** :

  ```sh
  grep android-access ~/.local/state/thor/regie-bridge.log | tail -n 5
  ```

  `raison=non-connecte` : le débogage sans fil est coupé, ou la console est sur un autre réseau Wi-Fi. Avec `WRITE_SECURE_SETTINGS`, Régie le rallume à son ouverture ; sinon, le rallumer à la main. L’association reste valable. Si elle a été perdue, refaire l’[étape 6](#6-associer-le-débogage-sans-fil). `raison=adb-absent` ou `serveur-adb` : relancer `thor/computer-use/install.sh` depuis le Mac.
- **Le pont ne démarre pas, ou s’arrête.** Lire la fin de `~/.local/state/thor/regie-bridge.log` dans Termux. Après cinq plantages en deux minutes, le superviseur abandonne : corriger la cause, puis rouvrir Régie.
- **L2 ne fait rien dans une autre appli.** Vérifier le service d’accessibilité (étape 7). Le service ne prend rien tant que Régie n’est pas reliée au pont, ni pendant **Quitter**.
- **La dictée reste muette.** Vérifier le microphone dans **Régler la voix**, puis le réseau et l’espace libre pour le premier téléchargement du modèle.
- **L’écran du bas de Régie a disparu.** Rouvrir Régie depuis le lanceur : l’écran du bas revient.
- **Android ferme Termux en arrière-plan.** Revoir l’exemption de batterie et le réglage des processus fantômes de l’étape 2. Une mise à jour système peut les annuler.
- **Codex refuse la connexion par code.** Activer l’option dans les réglages de sécurité du compte ChatGPT, puis relancer avec **A**.

Pour aller plus loin : la [fiche de la console](thor.md), le [guide de l’accès Android](../thor/computer-use/README.md) et le [protocole du pont](protocole-pont.md).

## Ajouter opencode et OpenCode Go

Les installeurs Termux et Mac du pont installent aussi **opencode 2.1.2**, build
Debian glibc arm64 officielle de `@opencode/cli-linux-arm64`, SHA-512 vérifié.
Pour installer uniquement ce binaire depuis le Mac : `thor/opencode/install.sh`.
L'accès Android ajoute `~/.config/opencode/AGENTS.md` et le MCP global `thor_android`
par le CLI natif. Une nouvelle session charge ces consignes.

Aucun compte n'est connecté par les scripts. Dans Régie : accueil, **B**, choisir
**opencode**, saisir sa clé **OpenCode Go** dans le champ masqué, puis **A** ou
« Enregistrer la clé ». Régie transmet cette saisie au pont ; opencode seul la
stocke. Ne jamais coller une clé dans un script, un terminal partagé ou un rapport.
Si une découverte d'options a déjà démarré opencode, choisir Quitter puis rouvrir
Régie avant la connexion. Go est facultatif ; les autres fournisseurs se configurent
par `opencode auth login` dans la Debian, puis sont proposés dans les modèles ACP.

Mise à jour de l'app déjà signée en release depuis le Mac :
`scripts/thor-install.sh --release`, avec la configuration de signature existante
hors du dépôt. Ne pas installer le debug par-dessus une release de signature différente.
Lors d'une désinstallation de l'accès Android, retirer aussi `thor_android` de
`mcp.servers` dans la configuration globale opencode ; le désinstalleur historique
Claude/Codex ne modifie pas cette configuration JSONC.
