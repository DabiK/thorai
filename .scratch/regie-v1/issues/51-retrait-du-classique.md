# 51 — Retrait de la vue classique

Statut : à fusionner
Possède : la suppression des écrans classiques, `InterfaceCopyTest`, les captures
Dépend de : 44, 45, 46, 47, 48, 49

## Objectif

Supprimer les écrans et composants classiques devenus inutiles, mettre à jour les tests de textes, puis prendre les captures des deux écrans sur la Thor dans `docs/images/jeu/`.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Plus aucun écran classique.
- `./gradlew check` vert.
- Captures complètes.

## Rapport

Branche `ticket/51-retrait-du-classique`, partie de `ticket/40-vue-jeu` (tous les lots du jeu fusionnés : 41 à 50, 52, 53). Choix notés dans l'ADR 24 (« Le retrait du classique et le plein écran »).

### Critères

- [x] Plus aucun écran classique. 35 fichiers retirés : les 13 écrans de `ui/screens` (vue d'ensemble, session, fil, réglages, dictée, validations, validation dans le fil, modifications, fiche, connexion des agents, ordre vocal, console, parcours d'accueil), les 5 composants de `ui/components`, `ui/theme` (Palette, Geist), `ui/markdown/CodeHighlight.kt`, les polices Geist et leur licence, 53 textes devenus inutiles (lint), et 11 tests de composables classiques. Chaque branche de `GamepadActivity.setScreen` rend un écran du jeu ; `GameTheme` est à la racine.
- [x] `./gradlew check` vert dans `android/`.
- [x] Captures complètes, Robolectric (`View.draw`, graphismes natifs, vrais atlas, tailles des écrans de la Thor), `docs/images/jeu/51-*.png` : titre (haut, bas), hameau (haut, bas), maison (haut, bas), validation (haut, bas), modifications (haut, bas), bâtir (haut, bas), menu de pause (haut, bas), courrier, console (tour et carnet en bas, carte volante par-dessus une autre appli). Pas d'essai sur la Thor ni au banc (consigne du lot) : l'orchestrateur teste sur la console.

### Gardé, et pourquoi

- `ControlCallbacks`, `SessionCallbacks`, `ThreadScroll` → `ui/Callbacks.kt` ; `rememberNow` → `ui/Formats.kt` ; `recognizedTerms` → `ui/DictationTerms.kt` ; `settingsHint` → `ui/SettingsHint.kt`. Les écrans du jeu branchés dans `ui/game/` s'en servent. `ControlCallbacks.onOpenApproval` et `SessionCallbacks.onOverview` sont retirés (plus aucun appel).
- `ui/markdown/Markdown.kt` (ADR 07) : `MarkdownFormat` du jeu l'utilise.
- `OverviewController`, `SessionController`, `ApprovalController`, l'état et le domaine : le moteur.
- `res/values/colors.xml` et `themes.xml` : le thème de fenêtre des activités.

### Repli du ticket 45

`VillageController` passe encore la manette à `OverviewController` avant le premier hameau, pendant la confirmation de « Quitter » et en mode console. C'est un repli de la manette seulement : l'écran reste celui du jeu (herbe nue en attendant le hameau, carnet du bas, `CarnetQuitPrompt` par-dessus). La seule branche classique qui restait à l'écran (`SessionSettingsScreen` hors d'une session, inatteignable puisque `navigate` ferme la table) est retirée.

### Plein écran

`GamepadActivity` (donc `TopActivity` et `BottomActivity`) cache la barre d'état et la barre de navigation (`WindowInsetsControllerCompat.hide(systemBars())`, comportement `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` : un glissement les montre un instant par-dessus le jeu, sans le déplacer). Elles sont recachées à chaque retour du focus. `safeDrawingPadding` reste pour le clavier. Le mode console n'est pas touché : carte volante et bulle vivent dans la fenêtre du service d'accessibilité (ADR 19), et l'appli au premier plan garde ses barres. Test `SystemInsetsTest` (rouge sans le correctif) : comportement par glissement, barres cachées à l'ouverture, recachées au retour du focus (demande lue sur l'`InsetsController` d'Android 13, Robolectric ne simulant pas les barres).

### Petits restes

- Le lien du carnet s'appelle « Préparer la console », comme l'entrée du menu de pause (même action, `openSetup`) ; les deux entrées restent (la barre du bas est tactile, le menu est à la manette).
- Captures `49-pause-haut`, `49-pause-bas` et `49-agents-haut` (menu à gauche) refaites avec « Préparer la console » ; `45-carnet`, `45-un-ecran`, `45-quitter` refaites avec le nouveau libellé.
- Une seule aide touchable : `CarnetHint` (45) est fondu dans `PadActionHint` (46), qui reçoit `modifier` et `active`.

### Tests

Les tests des composables classiques sont retirés ou migrés vers les écrans du jeu, sans perdre de couverture :
- `InterfaceCopyTest` vise les écrans du jeu (avis d'envoi, portée « Autoriser pour la session », touches B X L2 Start, hameau vide, dictée A B X Y) ;
- `SessionActionsIntegrationTest` (A, réglages, décisions au doigt, R1, fermeture) et `LongInstructionReviewTest` (consigne longue au stick) passent par la maison et la fiche du jeu ;
- `ConsoleModeTest` : le carnet propose la session console et « Préparer la console » ;
- repris dans `HouseWiringTest` : table des réglages complète et toucher d'une ligne, choix préparés et Interrompre pendant l'application, validation résolue fermée au doigt, L2 tenu au doigt après Compléter ou Corriger ; dans `VillageWiringTest` : la ligne choisie reste visible ; dans `MenuScreensTest` : clé d'un agent futur, champ masqué, A de la manette ; dans `BuildScreensTest` : catalogue, projet en chargement ou pont injoignable, création incertaine ; dans `ConsoleScreensTest` : « Redire » au toucher ; dans `HouseThreadTest` : une réponse qui s'allonge ne déplace pas la lecture ; dans `HouseLookTest` : clavier jamais en plein écran (`withoutFullscreen`) ;
- `FinalScreensTest` (game-ui) : les 21 écrans finals, avec leurs captures.

Ces migrations ont révélé trois écarts du jeu, corrigés :
- L2 tenu au doigt pendant la relecture d'une dictée était relâché aussitôt (la barre change, l'aide perdait son identité) : `key(action.keys)` dans `PadActionBar`.
- Pendant les réglages d'une session, le menu de la validation disparaissait : il reste au doigt, sans touche ni curseur, au-dessus des touches des réglages (ADR 15).
- La fiche du jeu ne défilait pas au stick et coupait la consigne à trois lignes : elle suit `newSessionVelocity` et montre toute la consigne choisie (ADR 13).

### Écarts et décisions

- « Voir la suivante » touché pendant les réglages d'une session montre la validation suivante, comme l'écran classique (rétabli après la première fusion, à la demande de l'orchestrateur : le tactile est un secours). La touche R1 reste prise par les réglages. `HouseCallbacks.onNextApproval` et `SessionCallbacks.onNextApproval` mènent à `nextInlineApproval` ; `SessionActionsIntegrationTest` le vérifie, sur un et deux écrans.
- Les captures `51-*` sont dessinées au niveau des écrans de `:game-ui`, avec des données d'exemple rédigées comme l'app (carnet), et sans barre système, comme la Thor en plein écran.
- `README.md`, `docs/developpement.md`, `docs/textes.md` et l'index des ADR décrivent la version jeu ; le PRD n'est pas modifié (orchestrateur).

### Reste

- Essai sur la Thor par l'orchestrateur : plein écran sur les deux écrans (glissement depuis le bord, clavier de l'écran du bas, retour d'une autre appli), mode console (carte volante en haut à droite) et lisibilité des écrans finals.

