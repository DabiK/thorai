# 16 — Banc d'essai sans la console : émulateur à deux écrans et pont sur le Mac

Statut : prêt
Bloqué par : rien
Possède : `scripts/emu-*.sh`, `scripts/bridge-mac.sh`, `docs/emulateur.md`

## Objectif

L'utilisateur est parti avec la Thor. Les tickets suivants doivent pouvoir être développés et testés de bout en bout sans elle, avec de vrais agents.

## À livrer

- **`scripts/emu-start.sh`** : démarre un AVD Android 13 ou plus récent qui ressemble à la Thor.
  - Écran principal paysage 1920×1080.
  - Second écran 1240×1080, ajouté par la console de l'émulateur (`multidisplay`) ou l'équivalent documenté.
  - Le script attend la fin du démarrage. L'image système `android-36` arm64 et des AVD existent déjà sur le Mac (`~/.android/avd`) ; en créer un dédié, `Regie_Thor`, s'il le faut.
- **`scripts/bridge-mac.sh`** : lance le pont (`bridge/`) sur le Mac.
  - Les adaptateurs ACP sont installés en local.
  - Dossier de projets de test séparé, par exemple `~/Documents/regie-sandbox/projects`, avec un projet de démo.
  - Jeton propre au banc ; `adb reverse tcp:47600 tcp:47600` vers l'émulateur ; jeton copié dans l'app par `run-as`.
  - Les essais utilisent **Codex** comme agent : le quota Claude de l'utilisateur est presque épuisé.
- **Installation de Régie sur l'émulateur.** L'absence de Termux doit être gérée proprement : le pont est alors joint par le `reverse`, sans RUN_COMMAND.
- **Manette.** Simuler les touches (`input keyevent` ou `sendevent`, y compris L2 et R2) et capturer les deux écrans : `scripts/emu-shot.sh`.
- **`docs/emulateur.md`** : mode d'emploi en dix lignes. Mettre à jour `scripts/ticket-solo.md` : si `adb devices` ne montre pas la Thor (`THOR0000000001`), on teste sur l'émulateur.

## Critères d'acceptation

- Depuis un Mac sans la console, en trois commandes : émulateur démarré, pont lancé, Régie installée. Régie affiche alors la vraie session Codex lancée sur le projet de démo, sur les deux écrans de l'émulateur. Captures dans `artifacts/screenshots/16/`.
