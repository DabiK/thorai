# 46 — Maison = session

Statut : fusionné dans ticket/40-vue-jeu
Possède : les écrans de session, de fil, de réglages et de dictée dans `:game-ui`, et leur branchement
Dépend de : 41

## Objectif

Écrans 04 à 07 du canvas : l'intérieur selon l'agent, ses animations selon l'état, la bulle d'activité, le plan en journal de quête, la boîte de dialogue, le fil et les réglages en bas, la dictée L2.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Mêmes informations et mêmes actions que `SessionScreens` et `DictationScreens`.
- Fil brut, sans rien ajouter (ADR 17).
- Tests Compose.

## Rapport

Branche `ticket/46-maison-session`, partie de `ticket/40-vue-jeu`, à jour du socle définitif (41, 42, 43 fusionnés dans `ticket/40-vue-jeu`).

Livré, dans `:game-ui` (`app.regie.game.ui.house`), écrans sans état nourris par l'état existant :
- `HouseTopScreen` : l'intérieur de la maison selon l'habitant (`InhabitantKind.of(agent)`, fond `fond.interieur.<habitant>`), l'habitant animé selon la session (travail, attente, repos, sommeil pour une session arrêtée ou hors processus), le coursier à l'entrée (clé `scenes` de `fonds.json`, lue par `InteriorScenes`), la bulle d'activité (dernière action, avec les mots du fil, seulement quand l'agent travaille), le panneau de la maison (projet, agent, branche, état et ancienneté, contexte), le plan en journal de quête avec les fichiers modifiés (seulement s'ils sont exposés), la dernière réplique de l'agent, telle quelle, dans `DialogBox`. Pendant une dictée vers cette session, le coursier parle (bulle avec micro et niveau).
- `HouseBottomScreen` : voisines L1 / R1, fil brut (`HouseThread` : messages, actions sur une ligne dépliables au toucher, texte en cours avec curseur, heure une fois par minute, suivi du bas, croix et stick), avis, table des réglages en cases éditable comme avant (Droite, « → Modifier », toucher d'une case ; valeur en attente en ambre), message écrit de secours, touches B, X, L2, Start ; en modification ↑↓, ←→, A, B, Start ; une validation prend la place des touches.
- `HouseDictationScreen` : destinataire, durée, transcription à 57 px (25 sp) avec les termes du dépôt en vert et le mot à corriger en ambre, niveau du micro, A Envoyer, B Effacer, X Corriger un mot, Y Compléter, L2 tenu au doigt, « Régler la voix ».
- `PadActionBar` : la barre des touches du kit, touchable (L2 maintenu au doigt).

Branchement dans `android/app`, aux seuls points d'appel : `ui/game/HouseScreens.kt` adapte l'état et les rappels existants (`SessionCallbacks`, `SessionNotice`, `settingsHint`, `recognizedTerms`, markdown de l'ADR 07) ; `TopActivity` et `BottomActivity` (branche session) et `GamepadActivity` (branches dictée) appellent les écrans du jeu. `SessionScreens`, `ThreadLayout`, `SessionSettingsScreen` et `DictationScreens` restent dans le code pour le ticket 51 ; `InlineApproval` (ticket 47) est hébergé tel quel : avec son détail au-dessus de la pièce, ses décisions à la place des touches du bas, en fin de fil sur un seul écran.

Un seul écran : l'intérieur se resserre à gauche autour de l'habitant (800 px, toujours ×4), le carnet du bas occupe la droite (fil, validation, réglages, champ, touches) ; pendant une dictée de la session, le fil reste et la dictée prend la place de la table, du champ et des touches (ADR 17). Mode console : le fil est toujours en bas, et le carnet nomme la session suivie (« Codex · projet »).

Preuves :
- `HouseLookTest` (habitant, animations par état, coursier, bulle, réplique verbatim, lecture des scènes, écarts du fil), `HouseScreensTest` (Robolectric, graphismes natifs, vrais atlas : haut au travail et au repos, coursier qui parle seulement pour sa session, validation hébergée, session indisponible, fil brut, ordre et rappels des touches, table en modification, validation à la place des touches, envoi écrit et avis qui s'efface, dictée et ses touches, un seul écran), `HouseWiringTest` dans `app` (markdown, rappels vers les contrôleurs, avis de l'app, validation, un seul écran avec dictée). Les tests des écrans classiques restent verts : ils visent leurs composables, qui existent encore.
- Captures Robolectric : `docs/images/jeu/46-maison-travail.png`, `46-maison-repos.png`, `46-dictee-haut.png`, `46-fil-bas.png`, `46-reglages-bas.png`, `46-dictee-bas.png`, `46-un-ecran.png`, `46-un-ecran-dictee.png` (hauteur réduite par les barres système de Robolectric).
- `./gradlew check` vert dans `android/`.

Écarts aux maquettes 04 à 07, pour rester fidèle au domaine et cohérent :
- Personnages de 30 px dans la pièce du ticket 42, pas de grand portrait ; la pièce remonte de quelques pixels pour que le coursier reste au-dessus de la boîte de dialogue.
- Pas de flèche « suite » dans la boîte de dialogue ni de « A Déplier » : aucune action ne les sert ; le détail d'une action se déplie au toucher, comme avant.
- Touches et libellés de `docs/textes.md` et de l'ADR 17 (B Revenir, X Voir les modifications, L2 Parler, Start Interrompre ; Droite pour modifier les réglages), pas ceux de la maquette (« Sortir », « Lui parler », « Y Réglages ») : Y n'a pas d'effet en session (ADR 17).
- Pas d'étiquette « Claude · 10:47 » sur les messages : fil brut, sans étiquette ajoutée (ADR 17) ; l'heure reste une fois par minute.
- Pas de « … » au-dessus de l'habitant pendant la dictée ni d'aide « Termes du dépôt reconnus en vert » : rien d'inventé, aide sans geste caché.
- La fiche de la maison à l'entrée (écran 04) est laissée au ticket 44 (marche, porte, iris).

Reste :
- Ticket 47 : remplacer `InlineApproval` par le parchemin et le menu de choix.
- Ticket 51 : retirer les écrans classiques et mettre à jour `InterfaceCopyTest`.
- Vérifier la lisibilité sur la Thor (taille du texte du fil, cases des réglages) à l'intégration, par l'orchestrateur.
- `scenes` est lu par `InteriorScenes` dans la maison : à rapprocher du chargeur si le ticket 52 le lit aussi.

### Relecture (18 novembre)

- Bloquant corrigé : sur un seul écran, la dictée d'une session ne remplace plus tout le carnet. Le fil (avec la carte de validation en fin de fil) reste, la dictée prend un panneau de 520 px à la place de la table, du champ et des touches. Tests `on a single screen a dictation keeps the thread…` (game-ui) et `HouseWiringTest`, capture `46-un-ecran-dictee.png`. La réplique de l'agent reste lisible dans le fil, à droite ; la pièce compacte n'a pas de boîte de dialogue.
- Bloquant confié au ticket 47 : le détail d'une validation sur deux écrans (carte du haut qui ne défile qu'au doigt).
- Mode console : le carnet nomme la session suivie (`console`, depuis `BottomActivity` quand le haut est pris par une autre appli). Les deux tests de `ConsoleModeTest` qui visaient `SessionBottomScreen` visent maintenant `GameSessionBottom`.
- `HouseThreadTest` : la croix remonte puis 0 ramène en bas, « Revenir en bas » au toucher, le stick tenu, une nouvelle ligne suivie en bas mais pas pendant la lecture plus haut.
- Un seul écran : le journal de quête passe en bas de la colonne et la pièce remonte d'autant (`compactLift`), la bulle d'activité part de l'épaule de l'habitant. Le test vérifie que le journal ne recouvre ni l'habitant ni le coursier, et que la bulle ne recouvre pas le panneau.
- Branche à jour de `ticket/40-vue-jeu` (coursier du ticket 44, puis main avec les tickets 28 et 30). L'ADR de la vue jeu est désormais l'ADR 24.
