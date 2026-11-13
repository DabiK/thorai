# 01 — Socle Android : deux écrans, manette et vue d'ensemble factice

Statut : bloqué
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

**Statut : bloqué.** Un seul critère n'est pas tenu : l'écran du bas réellement éteint. Il faut l'utilisateur pour l'éteindre.

**Ce qui marche, vérifié sur la Thor :**
- `android/` construit avec Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21 et le BOM Compose 2026.06.01. compileSdk et targetSdk sont à 36, minSdk à 33.
- `:domain` est en Kotlin pur. `:app` est la seule racine de composition. Le build `../voice` est inclus dès que `voice/settings.gradle.kts` existe.
- Deux activités partagent le même état :
  - `TopActivity` sur le display 0. Si elle s'ouvre ailleurs, elle se relance sur le 0.
  - `BottomActivity` sur le display 4.
- Un routeur d'entrées unique sert les deux activités :
  - L2 et R2 sont lus en touche et en axe, avec hystérésis, sans double début ;
  - A est consommé : aucun clic parasite.
- La vue d'ensemble fidèle à la maquette, alimentée par la passerelle factice. La croix déplace le focus depuis les deux écrans.
- Le mode un seul écran (le panneau en bas de l'écran haut) a été vérifié en le forçant (`--ez force_single_screen true`, en debug seulement). La règle de bascule est testée pour un écran retiré et pour les états OFF, DOZE, DOZE_SUSPEND et ON_SUSPEND.
- `./gradlew check` est vert : lint sans avertissement toléré, 58 tests (`domain` et `app`), dont 4 d'architecture.
- Captures : `artifacts/screenshots/01/` (7 états, haut et bas).

**À faire par l'utilisateur** (marche à suivre complète dans l'ADR 02, « Conséquences ») :
1. Lancer Régie (`scripts/thor-install.sh`).
2. Éteindre l'écran du bas par l'assistant d'écran double d'Odin (mode « Only bottom screen » / fermer l'écran secondaire).
3. Relever `adb -s THOR0000000001 shell dumpsys display | grep -E 'Screen-2.*state'` et `adb -s THOR0000000001 logcat -d -s Regie`. Le journal doit contenir `mode d'écran : Single`, et le panneau doit apparaître en bas de l'écran haut.
4. Rallumer l'écran du bas : l'écran du bas de Régie doit revenir.

Si l'écran reste à `state ON` une fois éteint (luminosité nulle), la détection ne bascule pas. Il faudra alors une autre source, à décider.

**Questions :**
- ❓ PRD « Commandes » : R1 veut dire « session suivante », mais la maquette affiche « R1 Voir » sur le bandeau de validation. Aujourd'hui, R1 ouvre la première validation en attente, et passe à la session suivante quand il n'y en a pas. Faut-il garder ce comportement ?
- ❓ Quand l'écran du haut de Régie passe en arrière-plan (Retour, Accueil), l'écran du bas doit-il rester affiché ? Aujourd'hui, il reste.

**Hors périmètre, pour les tickets suivants :**
- Le toucher du micro et la gâchette L2 ne sont pas combinés. Lever le doigt arrête la dictée même si L2 est encore enfoncé (ticket 08).
- Au-delà de 4 sessions, les cartes en trop ne sont pas affichées et le stick droit ne fait rien. Le focus reste sur les 4 cartes visibles (ticket 05).
- `docs/adr/02-socle-android.md` a le même numéro que `02-pilotage-des-agents.md` (ticket 02) : à renuméroter à la fusion.
