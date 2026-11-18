# Plan de développement de la vue jeu

Référence visuelle, comme point de départ et non comme modèle à copier :
- `docs/rebrand/da-jeu.md` ;
- le canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh, page 3, avec les écrans 01 à 13. Chaque écran se lit avec l'outil Artifact, action `read`, chemin `project/E01Titre.dc.html` … `project/E13Notifs.dc.html`.

## Décisions de l'utilisateur (18 novembre)

- **Les maquettes sont un point de départ, pas une spécification à reproduire à l'identique.** La priorité est la cohérence. Il faut un seul système visuel et de jeu sur tous les écrans : mêmes composants, échelle ×4, palette et comportements de la manette. Il faut aussi rester fidèle à ce que l'app expose vraiment, les données et les actions du domaine. Quand une maquette ne colle pas, on adapte et on note l'écart dans le rapport du ticket.
- Le livrable final est **uniquement le jeu**. La vue classique sert de support pendant le travail, puis elle est retirée écran par écran.
- Tout est **interactif** : même un raccourci (liste du bas, L1/R1, R1 vers une validation) fait marcher le coursier jusqu'à la porte.
- Le travail se fait sur **une branche longue**, `ticket/40-vue-jeu`, partie de main le 18 novembre et **jamais fusionnée dans main**. Chaque lot a sa branche, `ticket/4N-…`, partie de `ticket/40-vue-jeu`. L'orchestrateur du jeu la fusionne dans `ticket/40-vue-jeu`, et refond main dans `ticket/40-vue-jeu` après chaque fusion de l'orchestrateur principal, surtout après le ticket 30, qui renomme l'app en `app.thorai`.
- Le code est écrit par des sous-agents Claude (Opus). Codex ne sert qu'à générer les images.
- Le moteur ne bouge pas : pont, agents, cas d'usage du domaine. On change la vue, et seulement les points de branchement nécessaires dans `android/app`.

## Architecture : toute la vue jeu dans `android/game/`

### `android/game/engine`

- Module Gradle `:game-engine`, en **Kotlin pur** (JVM, sans Android), paquet `app.regie.game.engine`. Il dépend de `:domain` seulement.
- Contenu :
  - le modèle du monde : tuiles, carte, collisions ;
  - le **hameau procédural** : rues, terrains, maisons, routes, décor ;
  - la recherche de chemin A* ;
  - les déplacements du coursier sur la grille, dans 4 directions ;
  - les machines d'animation ;
  - les scènes : hameau, intérieur, tour ;
  - la correspondance entre `Overview` et le hameau.
- La disposition est persistée par un port, `VillageLayoutStore` : une maison bâtie ne bouge plus. Le port a une version factice en mémoire.
- Tout ce module est testé en JUnit, sans Android.

### `android/game/ui`

- Module Gradle `:game-ui`, une bibliothèque Android Compose, paquet `app.regie.game.ui`. Il dépend de `:game-engine` et de `:domain`.
- Contenu :
  - le **moteur de rendu pixel** : Canvas Compose, agrandissement entier ×4, `FilterQuality.None`, caméra ;
  - le chargement des atlas ;
  - le **kit pixel** : cadre, cadre épais, touche, étiquette d'état, panneau, bulle, dialogue, menu de choix, bandeau, lignes de diff, niveau du micro ;
  - les polices : Jersey 10, Pixelify Sans et VT323 (OFL), en `res/font` ;
  - les écrans du jeu, haut et bas.
- Assets : `android/game/ui/src/main/assets/game/`.
- Le sol est **dessiné par le code** à partir de la palette Aube : herbe, chemin et eau, avec des bords calculés selon les voisins. On évite ainsi un jeu de tuiles généré par IA, qui ne se raccorde jamais bien. Tous les objets viennent des atlas : maisons, arbres, clôtures, personnages, accessoires.

### `android/app`

`android/app` reste la seule racine de composition. Il fait trois choses :
- brancher les écrans du jeu à la place des écrans classiques, dans `TopActivity` et `BottomActivity`, à travers `GamepadActivity.setScreen()` ;
- envoyer les entrées de la manette au contrôleur du jeu quand le hameau est affiché ;
- porter l'adapter `SharedPreferences` de `VillageLayoutStore`.

### Règles

- `:game-ui` n'importe ni `gateway.`, ni `console.`, ni `voice.`, ni `commandmodel.`, ni `domain.port.`, ni `AppContainer`. `:game-engine` n'importe rien d'Android.
- Le lot 1 étend `ArchitectureTest` à ces deux modules.
- Les écrans du jeu restent sans état. Ils reçoivent l'état (les `StateFlow` existants de `AppContainer`) et les rappels (`ControlCallbacks`, `SessionCallbacks`…), comme les écrans classiques.
- Porte de qualité : `./gradlew check` dans `android/`. Lint compte les avertissements comme des erreurs, et `allWarningsAsErrors` est actif.
- Textes : `docs/textes.md`. Les données des agents ne sont jamais réécrites.
- **Une seule console.** Les lots testent sur Robolectric et sur le banc (`docs/emulateur.md`). Seul l'orchestrateur installe sur la Thor, au moment de l'intégration.

## Contrat des assets (`assets/game/`)

Chaque atlas est un fichier `<atlas>.png` accompagné d'un `<atlas>.json` :

```json
{
  "pixel": 1,
  "frames": { "coursier.marche.bas.0": {"x":0,"y":0,"w":24,"h":32,"ax":12,"ay":31} },
  "anims":  { "coursier.marche.bas": {"frames":["coursier.marche.bas.0","coursier.marche.bas.1","coursier.marche.bas.2","coursier.marche.bas.1"], "ms":140} },
  "objects":{ "maison.chaumiere": {"frame":"maison.chaumiere","footprint":{"w":6,"h":4},"door":{"tx":3,"ty":3},"roofKeys":["#FF00A0","#C8007E","#900060"]} }
}
```

- Les coordonnées sont en pixels natifs. Le rendu agrandit ×4.
- `ax` et `ay` donnent l'ancrage, au niveau des pieds.
- `footprint` est donné en tuiles de 16 px.
- `door` est la tuile de la porte. La tuile située juste en dessous doit rester accessible.
- Le toit des maisons est peint avec trois **couleurs clés**, du clair au sombre, que le rendu remplace par les couleurs de la session.

Les atlas attendus :

| Atlas | Contenu |
| --- | --- |
| `personnages` | Coursier : 4 directions × 3 images de marche, plus parler. Scribe, bricoleur, cartographe : repos (2), travail (4), attente avec parchemin ou « ! » (2), sommeil (2) |
| `maisons` | Au moins 6 modèles de maison, aux toits en couleurs clés. Chaque modèle a sa porte, une cheminée (point d'émission de fumée) et ses fenêtres (rectangles à éclairer) |
| `decor` | Arbres (3 ou plus), buissons, fleurs, rochers, clôtures (morceaux droits et coins), fontaine, puits, bancs, lanternes, panneaux, pont, chantier en 4 étapes |
| `icones` | Lettre, parchemin, rouleau, cloche, oiseau (2 images), touches |
| `fonds` | Titre ; intérieurs vides, sans personnage, pour le scribe, le bricoleur et la cartographe ; tour de la console vide (480 × 270) |

Tant qu'un atlas manque, les lots utilisent des formes de remplacement. Le contrat ne change pas.

## Lots

Tickets dans `.scratch/regie-v1/issues/`, numéros 40 à 69. Le 40 est l'épopée.

| Lot | Contenu | Dépend de |
| --- | --- | --- |
| 41 · Socle | Modules `:game-engine` et `:game-ui`, branchement Gradle, moteur de rendu (carte et sprites, caméra, ×4), chargement des atlas et couleurs clés, kit pixel, polices, `ArchitectureTest`, ADR 21 « Vue jeu » | — |
| 42 · Assets | Génération Codex, outils de pixellisation et d'empaquetage des atlas, `assets/game/` selon le contrat, sources et licences dans `da-jeu.md` | contrat seul |
| 43 · Hameau procédural | Moteur : `Overview` donne les rues (une par projet), les terrains, les maisons (une par session : modèle et toit stables), les routes reliées à la place, le décor, les collisions. Port `VillageLayoutStore`, agrandissement des rues. Tests : déterminisme, aucune superposition, chaque porte accessible, stabilité quand on ajoute ou retire une session | contrat seul |
| 44 · Coursier et déplacements | Marche sur la grille avec collisions et animations, caméra qui suit, A pour entrer, marche automatique par A* pour les raccourcis, transition en iris, contrôleur de jeu branché sur `InputRouter` | 1, 3 |
| 45 · Hameau, vue d'ensemble | Écran 03 : le monde, les panneaux et les états en haut ; en bas, le carnet (validation, sessions, actions). Remplace `OverviewScreens` | 1, 3, 4 |
| 46 · Maison = session | Écrans 04 à 07 : intérieur selon l'agent, animations selon l'état, bulle d'activité, plan en journal de quête, boîte de dialogue, fil et réglages en bas, dictée. Remplace `SessionScreens`, `ThreadLayout`, `SessionSettingsScreen` et `DictationScreens` | 1 |
| 47 · Validation et modifications | Écrans 08 et 09 : le parchemin et le menu de choix, le tableau de liège. Remplace `ApprovalScreens`, `InlineApproval` et `DiffScreens` | 6 |
| 48 · Bâtir et agrandir | Écrans 10 et 11 : chantier en 4 étapes, fiche de nouvelle session, nouvelle rue, carte du hameau. Remplace `NewSessionScreen` | 3, 5 |
| 49 · Titre, menu, courrier, console | Écrans 01, 02, 12 et 13 : titre, menu Start (agents, voix, réglages, Quitter), oiseau et courrier, carte volante pixel et tour de la console, ordre vocal. Remplace `AgentAuthenticationScreen`, `VoiceSettingsScreen`, `VoiceCommandScreen` et `ConsoleScreens` | 1 |
| 50 · Cohabitation sur la Thor | applicationId propre (suffixe `.jeu`) pour que la version jeu cohabite avec l'app classique de l'utilisateur. Vérifier si le pont accepte plusieurs jetons (ticket 27 : l'app génère le jeton et l'envoie par RUN_COMMAND). Sinon, le rendre multi-jetons, avec ses tests | — |
| 51 · Retrait du classique | Supprime les écrans et composants classiques devenus inutiles et met à jour `InterfaceCopyTest`. Captures des deux écrans sur la Thor, rangées dans `docs/images/jeu/` | tous |

Phases :
1. 41, 42, 43 et 50 en parallèle ;
2. 44, 46 et 49 ;
3. 45 et 47 ;
4. 48 ;
5. 51.

## Pour chaque lot

1. Lire `CLAUDE.md`, ce plan, `docs/rebrand/da-jeu.md`, les écrans du canvas concernés, et le code classique à remplacer.
2. Travailler dans son propre worktree, sur la branche du lot partie de `ticket/40-vue-jeu`. Ne jamais pousser, ni fusionner, ni rebaser sur main.
   - Le code du jeu va dans `android/game/`. Les écrans classiques ne sont pas réécrits : seuls les points de branchement de `android/app` changent, pour limiter les conflits avec main.
   - Aucune capture dans `artifacts/screenshots/`, ce dossier sera purgé de l'historique. Les visuels vérifiés vont dans `docs/images/jeu/`.
   - Aucune donnée personnelle dans les commits : pas de chemin `/home/…`, ni le numéro de série de la console, ni le nom du Wi-Fi.
3. Avancer par petites étapes, avec les tests à côté. `./gradlew check` doit être vert.
4. Rapport : ce qui est fait, ce qui reste, les écarts au plan. Le mettre dans la section « Rapport » du ticket, plus un commit Conventional Commits.
