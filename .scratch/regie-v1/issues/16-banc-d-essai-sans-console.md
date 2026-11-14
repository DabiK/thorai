# 16 — Banc d'essai sans la console : émulateur à deux écrans et pont sur le Mac

Statut : à fusionner
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

- [x] Depuis un Mac sans la console, en trois commandes : émulateur démarré, pont lancé, Régie installée. Régie affiche alors la vraie session Codex lancée sur le projet de démo, sur les deux écrans de l'émulateur. Captures dans `artifacts/screenshots/16/`.

## Rapport

STATUT: done
RÉSUMÉ: Banc en trois commandes confirmé avec Codex réel sur deux écrans.
Sur Mac, les écritures demandent maintenant validation ; les PNG sont entièrement décodés.
CRITÈRES:
- [x] Trois commandes et vraie démo Codex → revision-{emu-start,bridge-start,install-demo}.txt, captures revision-17.
- [x] Écrans 1920×1080 et 1240×1080 → 34 PNG décodés, emu-shot.sh.
- [x] Modes Mac conformes → refus B / autorisation A de shell et apply_patch ; auto-edit conserve la validation shell (revision-validation.txt).
- [x] PNG incomplets rejetés → 13 tests scripts, cas tronqués et mutation du décodage détectée.
- [x] Absence de Termux → ExternalBridgeTest, BridgeStatusTest, QuitTest et démo réelle.
- [x] A/B/L2/R2 et voix synthétique → captures initiales 02–08 ; A/B/R1 rejoués pendant la correction.
- [x] Guide dix lignes et repli solo → docs/emulateur.md, scripts/ticket-solo.md.
PORTE DE QUALITÉ: Android ./gradlew check : 310 tests + lint ; pont npm run check : 286 tests + typage/lint ; scripts : 13 tests.
THOR: absente ; Regie_Thor API 36 et pont Mac testés ; quatre nouveaux Codex arrêtés, aucun Claude lancé.
REVIEW: deux constats corrigés tests d’abord ; modes contrôlés après création/reprise, PNG valides sans pixels également rejetés. Relecture après commit à consigner.
DÉCISIONS PRISES: ADR 14 complété ; modes Mac read-only décidés dans le domaine, PRoot conservé ; Pillow requis pour les captures.
POUR L'UTILISATEUR: 1. Rebrancher Thor ; 2. thor-install.sh et permission Termux ; 3. refuser/autoriser shell et édition avec les boutons ; 4. micro réel et écran bas ; 5. Quitter. Pas à pas : revision-validation.txt.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/16/revision-{13-shell-pending,14-edit-pending,16-auto-shell,17-demo}-{haut,bas}.png.
HORS PÉRIMÈTRE: phrase vocale « Fais le point sur la session » non reconnue par les règles existantes (constat initial conservé).
