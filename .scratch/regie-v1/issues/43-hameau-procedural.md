# 43 — Hameau procédural

Statut : à fusionner
Possède : `android/game/engine/` (monde, génération, collisions, chemins)
Dépend de : le contrat des assets (empreintes et portes)

## Objectif

Construire le hameau à partir de `Overview` : une rue par projet, une maison par session (modèle et couleur de toit stables), des terrains libres, des routes reliées à la place, du décor et des collisions. Une rue pleine s'allonge. Une maison bâtie ne bouge plus.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## À livrer

- Le générateur, déterministe à partir d'une graine.
- Le port `VillageLayoutStore` et sa version factice.
- La grille de collisions et la recherche de chemin A*.
- L'agrandissement des rues.

## Critères d'acceptation

- Tests : déterminisme, aucune superposition, chaque porte accessible depuis la place, stabilité quand on ajoute ou retire une session, agrandissement.
- `./gradlew check` vert.

## Rapport

Fait, dans `android/game/engine` (Kotlin pur, sans toucher à `World.kt`) :

- `world/TileRect.kt` : rectangle de tuiles, partagé.
- `pathfinding/FindPath.kt` : `findPath(map, from, to): List<TilePos>?`, A* en 4 directions, départ et arrivée inclus, déterministe ; `null` si l'arrivée est bloquée ou inaccessible ; le départ peut être bloqué.
- `village/` :
  - `VillageGenerator(store, catalog, decor, roofs, config, inhabitants).generate(overview): Village` : complète la disposition, l'enregistre si elle a changé, puis dessine.
  - `Village` : `world`, `origin` (centre de la place), `spawn` (devant la fontaine), `plaza`, `streets` (`Street` : projet, côté, route, panneau, terrains), `houses` (`House` : session, habitant, modèle, toit, empreinte, porte, entrée, `labelAnchor`, cheminée et fenêtres), `decorations`, `freePlots`, `houseOf`, `houseAtEntry`, `relocate`.
  - `HouseCatalog` / `HouseModel` : données injectables (empreinte, porte, cheminée, fenêtres) ; six modèles par défaut, le premier est l'exemple du contrat (`maison.chaumiere`, 6 × 4, porte 3, 3).
  - `DecorCatalog` : les clés de l'atlas `decor` (arbres, buissons, fleurs, rochers, clôture, fontaine, banc, lanterne, panneau), remplaçables.
  - `RoofPalette.AUBE` : sept toits en trois tons, pris dans la palette Aube.
  - `InhabitantKind.of(agent)` : scribe (`claude`), bricoleur (`codex`), cartographe (`opencode`), d'après `Agent.id` annoncé par le pont (modèle du ticket 31, fusionné) ; tout autre agent prend l'habitant par défaut, le scribe. Une maison par session, pas par agent.
  - Port `VillageLayoutStore` (`load`, `save`) et `InMemoryVillageLayoutStore`. La disposition (`VillageLayout`) retient l'ordre des rues, leur nombre de terrains, et pour chaque session son terrain, son modèle et son toit.

Disposition retenue (la maquette n'est qu'un point de départ) :

- La place et sa fontaine au centre, une rivière au nord, une ceinture d'arbres autour.
- Les rues vont par paires sur des rangées : rue 0 vers l'est depuis la place, rue 1 vers l'ouest, rues 2 et 3 une rangée plus bas, desservies par une avenue qui descend de la place. Les terrains ont tous la même taille, fixe (9 tuiles de large, pour une maison d'au plus 7 × 5 tuiles), et s'alignent en colonnes d'une rangée à l'autre : le hameau reste lisible à 1 comme à 30 sessions.
- Chaque maison est au nord de sa route, porte au sud, avec une allée d'une tuile, un jardin clos (fleurs de part et d'autre de l'allée, clôture ailleurs), une lanterne au coin et un buisson contre un pignon.
- Le décor ne se pose que sur l'herbe, hors des terrains et de la place des toits : il ne bloque jamais une route ni une porte. À l'intérieur, il est tiré par tuile, en coordonnées relatives à la place : une rue qui s'allonge n'y déplace aucun arbre. La ceinture d'arbres, plus dense (550 ‰), suit les bords de la carte et se redessine quand la carte grandit.

Décisions :

- **Session disparue** : son terrain s'affiche libre, mais la disposition le lui garde. Si elle revient (par exemple après une coupure du pont, où la vue d'ensemble est vide un moment), elle retrouve sa maison. Une session nouvelle prend d'abord un terrain libre sans réserve, puis un terrain réservé (la réserve tombe), puis la rue s'allonge.
- **Rue** : elle s'allonge d'un terrain dès qu'elle n'a plus de terrain libre ; elle garde toujours un terrain libre (`minFreePlots`) ; elle ne raccourcit pas et ne disparaît pas, même vide. Un nouveau projet ouvre la rue suivante (par ordre alphabétique si plusieurs arrivent ensemble).
- **Tirages** : modèle et toit par hachage stable (FNV-1a puis SplitMix64) de la graine et de l'identifiant de session, en évitant le modèle et le toit des voisines ; ensuite enregistrés. Un modèle retiré du catalogue est retiré au sort, sur le même terrain. Les sessions sont traitées par identifiant : l'ordre de la vue d'ensemble ne change rien.
- **Coordonnées** : la carte est symétrique autour de la place et couvre au moins 30 × 17 tuiles. Dès qu'une rue, à l'est comme à l'ouest, s'allonge au-delà des autres, la carte s'élargit et tout glisse, `origin` compris. Il faut donc appeler `Village.relocate(pos, previous)` à **chaque** régénération. Elle reporte la position par rapport à la place ; si cette tuile est devenue bloquée (une maison bâtie sous le coursier), elle renvoie la tuile praticable et reliée à la place la plus proche (parcours en largeur).
- **Terrains indépendants du catalogue** : la taille des terrains est fixe (`HouseModel.MAX_FOOTPRINT_WIDTH` × `MAX_FOOTPRINT_HEIGHT` = 7 × 5 tuiles, plus 2 tuiles d'écart). Changer d'atlas ne déplace ni rue ni terrain. **Contrainte pour le ticket 42** : aucune empreinte de maison ne dépasse 7 × 5 tuiles, et la porte est sur la dernière rangée de l'empreinte ; `HouseModel` refuse sinon. Si un modèle change d'empreinte, seule sa maison se recentre dans son terrain.
- `chimney` et `windows` sont en pixels natifs depuis le coin haut gauche de l'empreinte ; les valeurs par défaut attendent l'atlas du ticket 42.

Tests (`./gradlew check` vert) : `FindPathTest`, `VillageGeneratorTest` (déterminisme, ordre indifférent, aucune superposition, portes et terrains libres accessibles depuis la place, voisines différentes, 30 sessions sur 6 projets), `VillageGrowthTest` (ajout, retrait, retour, réserve, allongement, rue qui ne raccourcit pas, nouveau projet, `relocate` vers l'ouest, vers l'est et hors d'une maison bâtie, disposition rechargée, changement de projet), `HouseCatalogTest` (dont l'empreinte maximale et le changement de catalogue), `InhabitantKindTest`.

Reste, pour les lots suivants :

- 41 : dessiner `Village.world` ; `objects` est trié de haut en bas. Les clés `maison.*` et `decor.*` sont celles de `HouseCatalog.DEFAULT` et `DecorCatalog.DEFAULT`.
- 42 → 45 : construire `HouseCatalog` et `DecorCatalog` à partir des JSON des atlas, si leurs clés diffèrent. Empreintes de maison de 7 × 5 tuiles au plus.
- 44 : `findPath(world.map, coursier, house.entry)` ; entrer quand le coursier est sur `entry` et regarde vers le haut (`houseAtEntry`). À chaque régénération, reporter sa position avec `relocate`.
- 45 : adapter `SharedPreferences` de `VillageLayoutStore` dans `android/app` ; panneaux aux `labelAnchor` et aux `Street.sign`.
- 48 : « Bâtir ici » sur un terrain précis n'est pas encore possible : une session nouvelle prend le premier terrain libre de sa rue. Il faudra un moyen de réserver un terrain avant de créer la session.
