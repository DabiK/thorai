# Fiche de la console : AYN Thor

Relevé matériel du 13 novembre 2026, conservé comme référence de la Thor.
Les versions et l’état des permissions ci-dessous sont un instantané, pas un contrôle actuel.
L’installation reproductible sur une console neuve est dans la [notice d’installation](installation.md).
Les lanceurs `claude`, `codex`, `dev-start`, `dev-stop`, `dev-linux` et le navigateur sont des outils
préexistants de cette console, pas des prérequis ni des livrables de ThorAI.

## Matériel et système

- QCS8550 (Snapdragon 8 Gen 2), 8 cœurs, 15,6 Go de RAM.
- Android 13 (SDK 33), build Thor_V1.0.0.377, correctif de sécurité de janvier 2024, langue fr-FR.
- adb : appareil USB autorisé, sélectionné automatiquement s’il est unique ; sinon exporter `THOR_SERIAL` avec la série donnée par `adb devices`.

## Écrans

| | Écran haut | Écran bas |
| --- | --- | --- |
| `displayId` | 0 | 4 (« Screen-2 ») |
| Pixels | 1920×1080 | 1240×1080 |
| dp | 832×468 | 538×468 |
| Densité | 369 dpi | 369 dpi |
| Fréquence | 60 ou 120 Hz | 60 ou 120 Hz |
| Tactile | non | oui (fts_ts_3) |

- Android voit l'écran bas comme un écran séparé, avec les drapeaux PRESENTATION et TRUSTED et sa propre barre de navigation. Une appli ordinaire peut y lancer une activité (`ActivityOptions.setLaunchDisplayId(4)` + `FLAG_ACTIVITY_NEW_TASK`). Le launcher Cocoon Shell le fait déjà.
- `screencap -d` attend l'identifiant **physique** de l'écran, pas le `displayId`. On le trouve avec `adb shell dumpsys SurfaceFlinger --display-id`.

## Manette

- Le périphérique s'appelle « Odin Controller » (2020:0111). Il a A, B, X, Y, C, Z, L1, R1, les sticks cliquables, la croix, Start, Select et Mode.
- L2 et R2 arrivent comme des touches **et** comme des axes analogiques (`AXIS_LTRIGGER`/`AXIS_BRAKE`). Il faut écouter les deux.
- **Les touches vont à l'écran touché en dernier** : un seul écran a le focus à la fois. L'app doit lire la manette dans les deux activités.
- Raccourcis système appliqués quand l'appli ne consomme pas la touche : A→DPAD_CENTER, B→BACK, X→DEL, Y→SPACE, Select→MENU, Mode→HOME. **Ne pas utiliser A pour un appui maintenu**, il devient un clic.

## Voix

- Clavier : Gboard.
- **Aucun service de reconnaissance vocale ni de synthèse** : `com.google.android.as`, `.tts` et `googlequicksearchbox` sont absents. `SpeechRecognizer` et `TextToSpeech` d'Android n'ont donc pas de moteur.

## Termux et les agents

- **Termux** 0.118.3, version GitHub *debug*, ce qui permet `adb shell run-as com.termux`. Termux:Widget 0.15.0 est installé. Termux:API et Termux:Boot ne le sont pas ; ThorAI n’en a pas besoin. Une extension doit venir des releases GitHub, signée comme Termux.
- **Debian 13** via proot-distro, utilisateur `dev`. Rootfs : `$PREFIX/var/lib/proot-distro/containers/debian/rootfs`.
- **Node** 24.22.3 dans la Debian (`/opt`, liens dans `/usr/local/bin`). Termux a aussi `nodejs-lts`.
- **Claude Code** 2.1.322 : `/home/dev/.local/bin/claude`, connecté à claude.ai, identifiants dans `/home/dev/.claude/`.
- **Codex** 0.167.2 : paquet npm complet dans `/opt/codex-cli`, lien `/usr/local/bin/codex`. Connecté par `codex login --device-auth`. Réglages dans le `~/.codex` de Termux, monté en `/home/dev/.codex` :
  - `sandbox_mode="danger-full-access"`, car bubblewrap ne marche pas sous proot ;
  - `approval_policy="on-request"`.
- **Lanceurs** `claude` et `codex` dans le `~/bin` de Termux. Ils passent par `proot-distro login` et montent `~/projects` sur `/home/dev/projects`.
- **Services** :
  - tmux `thor-services`, journaux et PID dans `~/.local/state/thor/` ;
  - `termux-wake-lock` ;
  - `dev-start`, `dev-stop`, `dev-status` dans `~/bin`.
- **Pour arrêter un processus de la Debian** : `proot-distro kill`. Un simple `kill` ne suffit pas.
- **Autres outils** :
  - code-server sur `127.0.0.1:8080` ;
  - Playwright MCP déclaré sous le nom `thor_browser` dans les deux agents (Chromium sans fenêtre, avec `--no-sandbox`), invisible sur la console : les consignes du ticket 24 réservent les demandes visibles à `thor_android` ;
  - `openssh` installé, mais aucun sshd configuré.
- **Réglages Android** : Termux est exempté de l'économie de batterie, `settings_enable_monitor_phantom_procs=false`.
- **RUN_COMMAND** : le service est exposé, mais `allow-external-apps=false` au relevé initial. `thor/bridge/install.sh` le passe à `true` ; ThorAI demande la permission RUN_COMMAND à son ouverture.
