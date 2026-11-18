# 49 — Titre, menu, courrier, console

Statut : à fusionner
Possède : titre, menu Start (agents, voix, réglages, Quitter), notifications, mode console et ordre vocal dans `:game-ui`
Dépend de : 41

## Objectif

Écrans 01, 02, 12 et 13 du canvas : l'écran titre, le menu de pause avec la connexion des agents et les réglages, l'oiseau messager et le courrier, la carte volante pixel et la tour de la console.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Mêmes fonctions que `AgentAuthenticationScreen`, `VoiceSettingsScreen`, `VoiceCommandScreen` et `ConsoleScreens`.
- Tests.

## Rapport

Branche `ticket/49-titre-menu-courrier-console`, partie de `ticket/40-vue-jeu` (avec le socle 41, les assets 42 et le moteur 43).

### Critères

- [x] Mêmes fonctions que `AgentAuthenticationScreen` : agents annoncés par le pont (liste dynamique), état par agent, code d'appareil de Codex (adresse, code, QR de l'adresse seule), marche de Claude et clé d'opencode (champ masqué, vidé à l'envoi) comme aujourd'hui, A selon l'état (connecter, déconnecter, confirmer), B (fermer, annuler, garder le compte), Y actualiser, croix et onglets tactiles pour choisir l'agent. Tests `MenuScreensTest` (QR décodé), `GameMenuInputTest`, `AgentAuthenticationInputTest` inchangé et vert.
- [x] Mêmes fonctions que `VoiceSettingsScreen` : fournisseur, clé ElevenLabs, clé Mistral, microphone, B revenir, au doigt comme aujourd'hui (`MenuScreensTest`).
- [x] Mêmes fonctions que `VoiceCommandScreen` : phrase entendue, action proposée, niveau et durée, A confirmer, B annuler ou revenir, R2 maintenu pour redire (aussi au doigt), lien « Régler la voix », défilement à la croix et au stick (`ConsoleScreensTest`).
- [x] Mêmes fonctions que `ConsoleScreens` : carte volante (L2 Répondre, B Fermer ; pour une validation, seulement les choix offerts : A Autoriser une fois, L2 Refuser en dictant ; touches cachées tant que la carte ne les prend pas), bulle de la session qui travaille, ordre vocal par-dessus une autre appli. Placement et priorités inchangés (`OverlayContent`, `OverlayWindow`), tests de l'ADR 19 inchangés et verts.
- [x] Tests : `GameMenuTest`, `CourierTest` (moteur, JUnit) ; `MenuScreensTest`, `ConsoleScreensTest`, `CourierToastTest` (Robolectric, graphismes natifs, vrais atlas) ; `GameMenuInputTest` (de la manette au domaine).

### Livré

- Moteur (`:game-engine`, Kotlin pur) : `menu/GameMenu` (titre et pause, entrées, courrier, réducteur sans état caché) et `courier/Courier` (lettres récentes, la plus récente d'abord, sans doublon, 30 au plus, lu, réglé, ancienneté).
- Vue (`:game-ui`) : `menu/` (titre, menu principal, menu de pause, agents, réglages de la voix, confirmation de « Quitter », pièces communes `PaperScreen`, `TapHintBar`, `MenuList`, `MenuBackdrop`), `courier/` (liste du courrier, oiseau messager), `console/` (carte volante, bulle, ordre vocal, tour de la console et fil compact). Textes dans `res/values/strings_menu.xml`. ZXing ajouté à `:game-ui` pour le QR (même version, ADR 18).
- App (points de branchement seulement, écrans classiques gardés pour le ticket 51) :
  - `AppContainer` : `gameMenu`, `courier`, `showTitle()`, `pressMenu()`, Start sur le hameau, fermeture du menu à chaque navigation ;
  - `courier/CourierNotifications` : adapter des ports `SessionNotifications` et `ApprovalNotifications`, branché sur une seconde instance de `NotifySessions` et `NotifyApprovals`. Le courrier ne contient donc que ce que le domaine décide de signaler (validation, question, fin, échec), avec ses titres et textes. Les notifications Android ne changent pas ;
  - `GamepadActivity.setScreen` : agents, réglages de la voix et ordre vocal en jeu ; nouvelle branche du menu ; l'oiseau messager par-dessus l'écran du haut ;
  - `TopActivity` : écran titre au premier lancement du processus ;
  - `BottomActivity` : la tour pour la session console quand une autre appli occupe le haut ;
  - `ConsoleService` : carte, bulle et ordre vocal en pixel.

### Correspondance des touches retenue

| Où | Touche | Effet |
| --- | --- | --- |
| Titre (au lancement) | Croix, A | choisir : Continuer, Nouvelle session (fiche existante), Agents, Réglages, Quitter (confirmation existante) |
| Titre | Start | Continuer |
| Titre | B | rien : premier écran |
| Hameau | Start | menu de pause (au lieu de « tout mettre en pause » direct) |
| Hameau | B | connexion des agents, directement (ticket 23 inchangé) |
| Hameau | X, Y | inchangés : nouvelle session, session console |
| Menu de pause | Croix, A | Courrier, Agents, Réglages, Tout mettre en pause, Quitter |
| Menu de pause | B, Start | fermer |
| Agents ou Réglages ouverts depuis le menu | B | revenir au menu |
| Courrier | Croix, A, X, B | choisir, ouvrir (validation encore en attente, sinon la session), tout marquer lu, revenir |
| Maison (session) | Start | interrompre (inchangé) |
| Partout | L2, R2 | dicter, ordre vocal ; ils marchent aussi sous le menu |

« Tout mettre en pause » reste une fonction du PRD : elle est dans le menu de pause et demande A, comme le reste. Le menu ne s'ouvre que sur le hameau. Ouvrir une session, une validation ou une notification le ferme.

### Écarts aux maquettes

- Menu de pause : « Carnet », « Manette » et « Affichage » ne sont pas dans le domaine, ils sont retirés. « Courrier » et « Tout mettre en pause » sont ajoutés. Les réglages par défaut de Codex (modèle, réflexion, mode) ne sont pas exposés hors d'une session, ils ne sont pas montrés.
- Décor des menus : le fond du titre assombri (`fond.titre`), le même pour le titre, la pause, les agents et l'ordre vocal. Il ne dépend pas du hameau du lot 45, et tous les menus gardent ainsi le même décor.
- Agents : le QR et le code sont en bas, comme dans la maquette, et non plus en haut. Le haut liste les agents avec leur habitant (scribe, bricoleur, cartographe ; un agent inconnu prend l'habitant par défaut du moteur).
- Logo du titre : le nom de l'app (`app_name`), pour suivre le renommage du ticket 30 sans toucher au jeu.
- Courrier : titres et détails du domaine (« Session terminée », « L'agent te pose une question », « Session en échec », « Codex attend ta validation ») au lieu des formules de la maquette. L'oiseau propose « R1 Voir » seulement pour une validation, sur le hameau.
- Carte volante : en haut à droite (ADR 19), pas en bas.
- Tour : l'étiquette donne l'état de la session et non le mode. Le fil compact ne montre que les lignes du fil. Les touches sont celles de la session (→ Modifier, B Revenir, L2 Parler, Start Interrompre) ; une validation en attente met ses choix dans les touches.

### Reste

- Pas d'essai sur la Thor ni au banc (consigne du lot) : écran titre au lancement réel, service d'accessibilité et carte volante à vérifier à l'intégration.
- La dictée par-dessus une autre appli garde son panneau classique (`ConsoleOverlayScreen`), en attendant la dictée du jeu (ticket 46).
- Le lien « B · Connexion des agents » et le « Quitter » tactile de l'accueil classique restent jusqu'au ticket 45.
- Le courrier vit en mémoire : il repart vide à chaque lancement.

### Captures

Robolectric, graphismes natifs, vrais atlas, à la taille des écrans de la Thor : `docs/images/jeu/49-*.png`. Pour les refaire : `./gradlew :game-ui:testDebugUnitTest --rerun -PgameCaptures=$PWD/../docs/images/jeu` dans `android/`.
