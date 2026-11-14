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
RÉSUMÉ: Banc autonome livré en trois commandes, avec un vrai Codex sur un projet isolé.
Régie fonctionne sur les deux écrans sans Termux ; les sessions d’essai sont arrêtées.
CRITÈRES:
- [x] Trois commandes et vraie session Codex → artifacts/screenshots/16/three-commands.txt, captures 09.
- [x] Écrans 1920×1080 et 1240×1080, 369 dpi → captures vérifiées par emu-shot.sh.
- [x] Pont local, jeton propre et run-as, reverse → scripts/bridge-mac.sh, emu-install.sh ; essai-emulateur.txt.
- [x] Absence de Termux → ExternalBridgeTest, BridgeStatusTest, QuitTest ; essai réel sans permission RUN_COMMAND.
- [x] A/B/L2/R2 et voix synthétique → captures 02–08, dix tests de scripts hors console.
- [x] Mode d’emploi de dix lignes et repli solo → docs/emulateur.md, scripts/ticket-solo.md.
PORTE DE QUALITÉ: Android ./gradlew check : 310 tests + lint ; pont npm run check : 285 tests + typage/lint ; scripts : 10 tests.
THOR: absente ; essais sur Regie_Thor Android 36, displays 0/2, pont Mac, deux Codex arrêtés.
REVIEW: première livraison relue en cours de travail ; relecture main...HEAD après commit à consigner ci-dessous.
DÉCISIONS PRISES: ADR 14 ; AVD dédié, état séparé, pont externe sans commande locale, consigne PRoot retirée sur Mac.
POUR L’UTILISATEUR: 1. Rebrancher Thor ; 2. thor-install.sh ; 3. vérifier Termux et vrais boutons ; 4. micro réel et écran bas éteint/rallumé ; 5. Quitter (détail dans essai-emulateur.txt).
QUESTIONS: aucune pour le ticket.
CAPTURES: artifacts/screenshots/16/09-cold-demo-{haut,bas}.png ; 06-r2-wav et 08-l2-review.
HORS PÉRIMÈTRE: « Fais le point sur la session » est bien transcrit mais non reconnu comme ordre par les règles existantes ; aucune modification de cet interpréteur.
