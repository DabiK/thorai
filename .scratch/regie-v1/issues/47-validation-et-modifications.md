# 47 — Validation et modifications

Statut : à fusionner
Possède : les écrans de validation et de diff dans `:game-ui`, et leur branchement
Dépend de : 46

## Objectif

Écrans 08 et 09 du canvas. La validation devient un parchemin tendu par l'habitant, avec un menu de choix. Le diff devient une feuille épinglée sur le tableau de liège, ligne par ligne à la croix.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- [x] Mêmes choix qu'aujourd'hui (une fois, toujours dans la session, refuser, refuser en dictant), et le diff commentable à la voix.
- [x] Tests Compose.

## Rapport

Branche `ticket/47-validation-et-modifications`, partie de `ticket/40-vue-jeu`, à jour du ticket 46 corrigé et de main (ThorAI).

Livré dans `:game-ui`, écrans sans état nourris par l'état existant (`approvalReview`, `inlineApproval`, `diffReview`) :
- `approval/ApprovalViews.kt` : `ApprovalParchment`, le parchemin tendu (cartouche de l'agent, icône `ui.parchemin`) avec la demande telle que l'agent l'expose : ce qu'il fait, la commande ou le fichier, le dossier, pourquoi, les fichiers concernés, le plan, l'avant / après ; une donnée absente est annoncée (« Justification non communiquée. »…). `ApprovalMenu`, le menu de choix : constat de l'écran classique (« Attend ta validation », « Réponse en cours… », « Réponse non confirmée… »), puis A Autoriser une fois, B Refuser, X Autoriser pour la session (seulement pour l'option `allow_for_session`, ADR 08), L2 Refuser et dicter une consigne (maintenu au doigt) ; « ← Fermer la validation » une fois traitée. `WaitingMarker`, le « ! » ambre qui sautille au-dessus de l'habitant.
- `approval/ApprovalScreens.kt` : `ApprovalScene` (haut de l'écran des validations, R1 depuis le hameau ou une notification) : la pièce de la session demandeuse, l'habitant en pose d'attente (`*.attente`, parchemin en main), le coursier, le « ! », et le parchemin entier à gauche que la croix et le stick font défiler (`ApprovalController.scrollSteps` / `scrollVelocity`). `ApprovalNotebook` (bas) : la session, le nombre en attente, le menu en colonne, les autres validations à toucher, ↑↓ Défiler, L1 Revenir, R1 Voir la suivante.
- `approval/ApprovalModel.kt` : la logique sans Android (choix, ordre, curseur, constat, touche d'une décision), testée en JUnit.
- `diff/DiffScreens.kt` : `CorkBoardScreen` (haut) : la maison derrière un voile, le tableau de liège, l'étiquette épinglée (chemin, +/−, rang du fichier), la feuille et sa punaise, les éditions (« Édition 1 », ou « … extrait · position dans le fichier non communiquée »), les lignes `PixelDiffLine` colorées, la ligne choisie encadrée et ramenée en vue, le stick qui défile ; pendant une explication, la demande puis la réponse de l'agent (markdown de l'ADR 07, curseur pendant l'écriture), la croix et le stick la font défiler. `DiffNotebook` (bas) : fichiers à toucher avec leurs comptes, la ligne choisie (« Ligne 43 », « Ligne 7 · supprimée · extrait »), L2 Commenter à la voix (maintenu au doigt), Y Expliquer le bloc, ↑↓ Ligne, ←→ Fichier, B Revenir, L1 R1 Naviguer ; en explication, A Demander (seulement si possible) et B Revenir aux modifications, avec le constat de l'écran classique.
- Kit : `ChoiceMenu` reçoit un `itemModifier` (toucher d'une option) ; `PixelDiffLine` passe à la ligne au lieu de couper (ADR 12 : le code se lit en entier).

Branchement dans `android/app`, aux seuls points d'appel : `ui/game/ValidationScreens.kt` (`GameApprovalTop`, `GameApprovalBottom`, `GameDiff`) relaie les touchers comme la manette (`Button`, `touchTrigger(L2)`, `selectApproval`, `selectDiffFile`). `TopActivity` et `BottomActivity` (branche validation) et `GamepadActivity` (branche modifications) les appellent. Dans la maison, `HouseScreens.kt` remplace `InlineApproval` par le parchemin et le menu. `ApprovalScreens`, `InlineApproval` et `DiffScreens` restent dans le code pour le ticket 51 ; leurs tests aussi (`DiffScreenTest`, `InterfaceCopyTest`), verts.

Bloquant de la relecture du 46 (le haut n'est pas tactile) : le détail complet d'une validation se lit à la manette partout.
- Écran des validations : le parchemin du haut défile à la croix et au stick (`ApprovalController`).
- Maison, deux écrans comme un seul : le parchemin entier finit le fil du carnet, que la croix et le stick font défiler (`SessionController`). Le fil s'arrête au début du parchemin, pas à sa fin. Dans la pièce du haut, le parchemin est borné et dit « La suite se lit dans le fil. » tant que sa fin est cachée. Le menu du carnet tient en une rangée pour laisser la hauteur au fil.
- Tests : `ValidationWiringTest.on two screens the whole request is read with the cross in the bottom thread`, `…on a single screen the whole request ends the thread of the notebook`, `…the approval screen top scrolls its parchment with the approval controller steps` ; `ApprovalScreensTest.the cross scrolls the whole parchment…` et `…in the house the whole parchment ends the thread and the cross reads it to the end`.

Preuves :
- `ApprovalMenuModelTest` (choix, ordre A B X L2, X seulement pour la session, curseur, constats, touches), `ApprovalScreensTest` et `DiffScreensTest` (Robolectric, graphismes natifs, vrais atlas : pose d'attente et « ! », demande verbatim, avant / après, données absentes, touchers et L2 maintenu, choix désactivés après réponse, un seul écran, lignes longues, extrait, sélection ramenée en vue, états vides, explication rendue et défilée), `ValidationWiringTest` dans `app` (relais vers les mêmes touches que la manette, défilement du détail à la manette). `HouseWiringTest` et `ConsoleModeTest` suivent la signature sans `onNextApproval` (R1 passe par la barre des voisines, comme avant).
- Captures Robolectric : `docs/images/jeu/47-validation-haut.png`, `47-validation-bas.png`, `47-validation-un-ecran.png`, `47-maison-validation-haut.png`, `47-maison-validation-bas.png`, `47-modifications-haut.png`, `47-modifications-bas.png`, `47-modifications-explication.png`, `47-modifications-un-ecran.png`. Elles sont dessinées par `View.draw` comme au ticket 46 : `captureToImage()` attend un nouveau dessin de la fenêtre que Robolectric ne produit pas ici (délai dépassé).
- `./gradlew check` vert dans `android/`.

Écarts aux maquettes 08 et 09, pour rester fidèle au domaine et cohérent :
- Pas de phrase prêtée à l'agent (« Je voudrais lancer cette commande. D'accord ? ») : le titre dit ce qu'il fait, avec les mots de l'écran classique. « Fichiers concernés » plutôt que « Ce que ça touche » : ce sont des chemins.
- La croix ne déplace pas le curseur du menu : elle fait défiler la demande (comportement d'aujourd'hui, `ApprovalController`), et chaque option garde sa touche. Le curseur reste sur A, l'action par défaut, et disparaît une fois la décision prise. « X Autoriser pour la session » garde le libellé de l'app (`docs/textes.md` : un verbe).
- Pas de « Mode de la session » sous le menu : l'écran classique ne le montre pas et la maison l'a déjà dans ses réglages.
- Deux écrans : le parchemin à gauche, la pièce à droite (720 px) avec l'habitant qui le tend, au lieu d'un cadre posé sur la pièce : la demande garde toute la hauteur. Un seul écran : la pièce passe sous un voile derrière le parchemin, le carnet prend la droite.
- Dans la maison, le menu tient en une rangée (même curseur, mêmes touches) et L2 n'y refuse pas : dans une session, L2 parle à l'agent (PRD, Commandes). Le refus dicté reste sur l'écran des validations, comme aujourd'hui.
- Tableau de liège : un numéro par ligne (le nouveau, l'ancien pour une suppression), pas les deux colonnes « ancien / nouveau » de l'écran classique ; « L1 R1 Naviguer » plutôt que « L1 R1 Fichier » (←→ change de fichier, L1 / R1 de session). L1 R1 sans aide sur un seul écran, faute de place.
- « Régler la voix » n'est pas sur ces écrans : main l'a gardé sur l'accueil seulement, la dictée l'offre.

Reste :
- Ticket 51 : retirer `ApprovalScreens`, `InlineApproval`, `DiffScreens`, leurs tests classiques, et mettre à jour `InterfaceCopyTest`.
- Vérifier la lisibilité sur la Thor à l'intégration (taille du parchemin dans la pièce, menu en rangée du carnet).
- Question produit ouverte : faut-il qu'L2, dans une maison avec une validation en attente, refuse en dictant une consigne plutôt que parler à l'agent ? Aujourd'hui non ; le menu de la maison ne le propose donc pas.
