# 53 — Parcours d'accueil dans le style du jeu

Statut : à fusionner
Possède : la présentation du parcours d'accueil du ticket 28 (`SetupScreens`) dans `:game-ui`, et son branchement
Dépend de : 49

## Objectif

Le parcours d'accueil (ticket 28, ADR 23) garde aujourd'hui le style classique, alors que tout le reste devient le jeu. Il s'agit de le présenter dans le style pixel, en gardant exactement les mêmes étapes et les mêmes gestes :
- permissions ;
- accessibilité de ThorAI ;
- association au pont ;
- connexion des agents, dont l'étape opencode facultative ;
- voix.

Par exemple : l'arrivée au hameau, avec une étape par panneau ou par personnage. Le raccourci « Préparer la console » trouve aussi sa place dans le menu de pause.

Référence : `docs/rebrand/plan-dev-jeu.md`, `docs/rebrand/da-jeu.md`, ADR 23, ADR 24.

## Critères d'acceptation

- Mêmes étapes, mêmes permissions demandées à l'utilisateur. Aucune permission n'est accordée par l'app ni par un agent.
- Tests Compose et captures.
- `./gradlew check` vert.

## Rapport

Branche `ticket/53-parcours-d-accueil-en-jeu`, partie de `ticket/40-vue-jeu` (socle 41, assets 42, hameau 43, coursier 44, maison 46, rendu 52, titre et menu 49), puis refondue sur `ticket/40-vue-jeu` après la fusion du ticket 45.

### Critères

- [x] Mêmes étapes, mêmes permissions demandées à l'utilisateur, rien n'est accordé par l'app ni par un agent. L'écran du jeu ne fait que transmettre l'action de la touche à `ReviewSetup.press`, comme le classique : `SetupReview.keys`, `SetupChecks`, `AppContainer.performSetup` et le routage de la manette ne changent pas. Tests : `SetupScreensTest` (chaque étape atteinte, chaque touche du domaine montrée et jouée au doigt, refus définitif vers les infos de l'appli, téléchargement et progression, agents dont opencode, fin), `SetupWiringTest` (le vrai cas d'usage au doigt ; une permission ne part qu'en demande, l'état vérifié ne bouge pas), `SetupInputTest` inchangé et vert (manette), `GameMenuInputTest` (« Préparer la console » dans Start, puis la manette au parcours).
- [x] Tests Compose et captures : `docs/images/jeu/53-*.png` (Robolectric, graphismes natifs, vrais atlas, tailles de la Thor).
- [x] `./gradlew check` vert dans `android/` (app 271, game-ui 153, game-engine 103, domaine 191 tests, lint sans avertissement).

### Livré

- Moteur (`:game-engine`, `setup/ArrivalRoad`) : la route d'arrivée au hameau, un panneau par étape, la tuile où le coursier lit chacun, la place et deux maisons au bout, les habitants près du panneau des comptes. Fixe, déterministe, testée (`ArrivalRoadTest` : ordre, accès par la route, aucun chevauchement, portes praticables).
- Vue (`:game-ui`, `setup/`) : `GameSetupScreen`.
  - En haut : le coursier entre par la gauche et marche au pas du hameau jusqu'au panneau de l'étape, qu'il lit ; une case au-dessus de chaque autre panneau (verte prête, vide à faire, ambre clignotante pour l'étape), comme le journal de quête. Le panneau de l'étape porte son nom, « Étape n sur 9 » et « ! À faire » (ambre qui sautille) ou « Prête ». L'explication tient dans un grand cadre posé sur la scène ; les commandes (installation, association avec le nom de l'app) passent à droite. Le stick droit la fait défiler.
  - En bas : le carnet, avec l'avancement (compteur et une case par étape), l'état vérifié, l'action de A dans un grand cadre, B, X et Y dans la barre des touches, « Reprendre plus tard » au doigt dans l'en-tête.
  - Un seul écran : la route en bandeau, l'explication à gauche, l'état et A à droite, B, X et Y en bas.
- Menu de pause : entrée « Préparer la console » (`MenuEntry.SETUP`), entre « Réglages » et « Tout mettre en pause ». Elle rouvre le parcours à la première étape non faite et ferme le menu ; son détail « Étape facultative restante » reprend le point ambre de « Configurer ».
- App : seuls les points de branchement changent (`GamepadActivity`, `AppContainer.pressMenu`). `SetupScreens.kt` reste pour le ticket 51.

### Écarts et décisions

- Pas de personnage qui parle : l'explication est le texte du panneau, pour ne pas personnifier l'app (`docs/textes.md`). Les seuls personnages sont le coursier et les habitants des agents annoncés par le pont (contents s'ils sont connectés, en attente sinon).
- A n'est pas dans la barre des touches mais dans le grand cadre de l'action, au-dessus : c'est l'action de l'étape, et elle dit ce qu'elle attend quand il n'y en a pas (« Connexion au pont… », sans effet).
- Textes du classique, deux renvois adaptés : Y sur la fin dit « Terminer » (on arrive au titre ou au hameau, plus à l'accueil) ; la note de fin renvoie à « Start › Préparer la console ». L'état du pont qui se connecte dit « Connexion… », comme le classique.
- Le panneau de l'étape ne s'affiche que s'il tient dans ce que les cadres laissent voir ; sur un seul écran, seule la case ambre le remplace.

### Reste

- Pas d'essai sur la Thor ni au banc (consigne du lot) : marche du coursier, défilement au stick et demandes de permission à vérifier à l'intégration.
- Le carnet du hameau (ticket 45) garde son lien tactile « Configurer » ; le menu dit « Préparer la console ». Un seul nom serait plus cohérent : à trancher à l'intégration ou au ticket 51.
- Le PRD (parcours d'accueil) parle encore de « Configurer, sur l'accueil » ; les captures `49-pause-*` montrent le menu de pause sans la nouvelle entrée.

### Captures

`docs/images/jeu/53-*.png` : `bienvenue-haut`, `termux-haut`, `acces-android-haut`, `agents-haut` (route, panneau et explication) ; `bienvenue-bas`, `pont-bas`, `voix-bas`, `mode-console-bas`, `agents-bas`, `fin-bas` (carnet) ; `un-ecran` ; `menu-pause-haut`, `menu-pause-bas`. Pour les refaire, dans `android/` : `./gradlew :game-ui:testDebugUnitTest --tests 'app.regie.game.ui.setup.*' --rerun -PgameCaptures=$PWD/../docs/images/jeu`.
