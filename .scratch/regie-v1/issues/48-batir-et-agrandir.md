# 48 — Bâtir et agrandir

Statut : à fusionner
Possède : la nouvelle session, le chantier, la carte du hameau
Dépend de : 43, 45

## Objectif

Écrans 10 et 11 du canvas. Le chantier suit la création de la session en 4 étapes : fiche, session créée, agent lancé, première réponse. La fiche est pré-remplie par R2. Un nouveau projet ouvre une nouvelle rue, et la carte du hameau s'affiche en bas.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- [x] Mêmes champs que `NewSessionScreen`, et le nouveau projet.
- [x] Tests.

## Rapport

Branche `ticket/48-batir-et-agrandir`, partie de `ticket/40-vue-jeu` (45 et 47 fusionnés en route). Choix notés dans l'ADR 24 (« Bâtir et agrandir »).

Fait :

- **Moteur (`:game-engine`)** :
  - `VillageLayout.reservation` (`PlotReservation`) : le terrain est réservé dans la disposition enregistrée **avant** `session.create` ; `VillageGenerator.reserve`, `bind` (la session créée), `release` (échec), `forgetUnboundReservation` (au lancement). La maison est bâtie sur ce terrain et nulle part ailleurs ; tant que la session n'est pas liée, une session nouvelle du même projet attend (la vue d'ensemble peut l'apporter avant la réponse du pont). Un nouveau projet ouvre sa rue à la suite ; un échec la retire si elle est restée vide et la dernière.
  - `generate(overview, construction)` : pendant la fiche, le terrain visé est montré sans rien enregistrer ; `Village.site` (`Site`), `Plot.reserved`, `isUnderConstruction`. Le chantier se dessine avec `chantier.0` à `chantier.2` (atlas `decor`), puis la vraie maison de la session (son modèle, son toit), sans jardin ni bulle tant qu'elle est en travaux.
  - `Construction` : les quatre étapes d'après le domaine seul (fiche lancée ; id rendu par le pont ; `Session.active` ; texte ou action dans le fil, validation demandée ou tour fini — sans consigne, l'agent qui attend la sienne). Une étape ne recule pas ; un échec l'arrête avec le message tel quel.
  - Terrains libres piquetés (`PlotFence`, morceaux `cloture.*` raccordés, ouverts face à l'entrée). `Village.map()` (`VillageMap`). `CourierCommand.GoToSite`.
- **Interface (`:game-ui`, paquet `chantier`)** : `NewSessionBottomScreen` (fiche en jeu, mêmes champs et gestes : agent du catalogue, projet ou nouveau projet, branche et base, mode, modèle, réflexion, consigne ; phrase R2 rappelée ; croix, A Bâtir, B Annuler, L2 tenu ; tactile, champs écrits au doigt), `constructionLayer` et `ConstructionPanel` (« Bâtir une maison », quatre étapes, échec lisible), `StreetBanner` (« Nouvelle rue »), `VillageMapScreen` (carte : rues, maisons et états, chantier, terrains libres, rue suivante). `VillageOverlay` ignore les maisons en chantier.
- **App** : `VillageController` (chantier dans la clé de régénération du hameau, marche au terrain, réservation, liaison, libération, observation, entrée dans la maison finie si le coursier est resté devant, carte) ; `AppContainer` (réserve puis `ReviewNewSession.launch()`, lie la session créée, suit son fil, libère sur échec ou annulation ; une fiche ouverte dans une maison ramène au hameau ; sans hameau visible, la session s'ouvre directement, comme avant ; la carte prend la croix, A et B) ; `GamepadActivity`, `TopActivity`, `BottomActivity` (fiche, chantier et carte à la place de `NewSessionScreen`) ; `PreferencesVillageLayoutStore` garde la réservation (champ facultatif, format v1 inchangé).

Tests : moteur `VillageConstructionTest` (9 : terrain visé sans enregistrement puis réservé à l'identique, maison sur le terrain réservé même si un autre se libère, session précoce qui attend, nouvelle rue, échec qui libère et retire la rue, réservation au relancement, chantier dessiné jusqu'à la première réponse, piquets, carte), `ConstructionTest` (4), `CourierInVillageTest` (+1, marche au chantier pendant qu'une rue s'ouvre) ; app `BuildInputTest` (7, de la manette au domaine : X et la croix, réservation avant `session.create` puis maison sur le terrain et entrée à la première réponse, refus et création incertaine qui libèrent, B, nouveau projet, sans hameau visible, carte), `PreferencesVillageLayoutStoreTest` (+1) ; interface `BuildScreensTest` (9, vrais atlas). `VillageDecorTest` et `VillageGeneratorTest` suivent la nouvelle règle (un terrain libre n'a que sa clôture de piquets). `./gradlew check` vert.

Captures Robolectric (`View.draw`, tailles de la Thor) : `docs/images/jeu/48-fiche-bas.png`, `48-un-ecran.png`, `48-terrain-libre.png`, `48-chantier-fondations.png`, `48-maison-finie.png`, `48-echec.png`, `48-nouvelle-rue.png`, `48-carte.png`.

Écarts et décisions :

- **Le chantier se regarde sur le hameau** : la classique ouvrait la session dès sa création. Le hameau à l'écran, on y reste ; à la première réponse, le coursier resté devant le chantier entre dans la maison (iris), sinon on le laisse. Sans hameau visible (mode console, écran du haut pris), la session s'ouvre directement, comme avant.
- **Étapes 2 et 3** : le pont rend l'id après avoir lancé l'agent ; « Agent lancé » suit `Session.active` dans la vue d'ensemble, donc souvent juste après « Session créée ». Sans consigne, la 4e étape s'appelle « Attend ta consigne » au lieu d'inventer une réponse.
- **Échec après la création** (session ou tour en échec) : l'étape s'arrête et le message reste ; la session existe, sa maison garde son terrain (on ne cache pas une vraie session). Seul un échec avant la création libère le terrain.
- **La dernière étape** montre la vraie maison de la session et des étincelles, pas `chantier.3` (qui sert d'icône dans le panneau) : la maison ne change pas d'allure juste après.
- **Carte du hameau** : ouverte en bas pendant un chantier (croix pour la rue, A y aller, B fermer). Pas de « X Bâtir ici » : la fiche pré-remplie exige un agent, qu'on ne choisirait pas à la place de l'utilisateur. Pas d'accès permanent (menu de pause, ticket 49) : à décider avec 51.
- **Une fiche ouverte dans une maison** (ordre R2) ramène au hameau ; B y laisse donc sur le hameau.
- Le nouveau projet est la dernière valeur du champ Projet, comme dans la fiche classique ; la ligne « + Nouveau projet » de la maquette le rappelle. Le panneau des étapes est grand (icônes ×2) et masque le coin haut droit du hameau.
- Un seul chantier à la fois (une réservation dans la disposition).

Reste :

- Ticket 51 : retirer `NewSessionScreen` et son test.
- Essai sur la Thor par l'orchestrateur (aucun banc ni console utilisés ici), notamment l'ordre réel des événements du pont autour de `session.create`.
