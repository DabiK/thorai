# 01 — Socle Android : deux écrans, manette et vue d'ensemble factice

Statut : prêt
Bloqué par : rien
Possède : `android/`, `scripts/thor-*.sh`

## Objectif

Poser l'app Android sur la Thor, avec ses deux écrans, sa manette et le système visuel de la maquette. La démonstration se fait sur l'écran 1 de la maquette (vue d'ensemble), avec des données factices.

## À livrer

- **Projet Gradle `android/`** : Kotlin, Compose, minSdk 33, applicationId `app.regie`. Wrapper versionné, catalogue de versions, porte de qualité `./gradlew check` (lint, tests, et un test d'architecture si c'est simple).
- **Modules** :
  - `:domain` : Kotlin pur. Modèle (`Session`, `Agent`, `SessionStatus`, `Approval`, `SessionEvent`), port `AgentGateway` et une passerelle factice qui expose les 4 sessions de la maquette.
  - `:app` : interface et composition.
  - Laisser la place à `includeBuild("../voice")`. Le ticket 04 fournit ce build ; ne pas créer `voice/`.
- **Deux activités** dans un seul processus, qui partagent le même état :
  - écran haut sur le display 0 ;
  - écran bas lancé sur le display 4 avec `ActivityOptions.setLaunchDisplayId`.
  - Si l'écran bas est absent ou éteint (suivi avec `DisplayManager`), l'app bascule en mode un seul écran : la zone du bas devient un panneau en bas de l'écran haut.
- **Routeur d'entrées** unique, alimenté par les deux activités : quel que soit l'écran qui a le focus, la manette fait la même chose.
  - L2 et R2 en maintien, lus en touche **et** en axe, avec début et fin d'appui.
  - A, B, X, Y, L1, R1, Start, la croix, le stick droit.
  - Le A système ne doit pas déclencher de clic parasite.
  - Les actions sont celles du PRD (« Commandes »). Tant que les autres tickets n'existent pas, elles appellent des cas d'usage vides.
- **Système visuel de la maquette** :
  - couleurs : fond `#0C0D0F`, surfaces `#16181B`/`#1F2226`, texte `#EDEBE7`, gris `#A1A5AC`/`#868B93` ;
  - couleurs d'état : en cours `#6FB2FF`, attente `#F4B740`, terminé `#5ED19A`, micro `#FF6A4D` ;
  - polices Geist et Geist Mono (licence OFL, embarquées) ;
  - composants : carte de session, pastille d'état, puce de bouton, bouton micro.
- **Écran 1 de la maquette** sur les deux écrans, alimenté par la passerelle factice. La carte qui a le focus est entourée ; la croix déplace le focus.
- **Scripts** :
  - `scripts/thor-install.sh` : construit, installe et lance sur la Thor ;
  - `scripts/thor-shot.sh <dossier>` : capture les deux écrans, en trouvant les identifiants physiques.

## Critères d'acceptation

- [x] L'app s'ouvre sur la Thor avec la vue d'ensemble en haut et le micro en bas, fidèle à la maquette.
- [x] La croix déplace le focus, sur l'écran actif comme après avoir touché l'autre écran.
- [x] Le maintien de L2 et de R2 est détecté (un état visible sur l'écran du bas suffit pour l'instant).
- [ ] L'écran bas éteint ou absent → mode un seul écran, sans plantage.
- [x] Les captures des deux écrans sont dans `artifacts/screenshots/01/`.

## Rapport

(à remplir)
