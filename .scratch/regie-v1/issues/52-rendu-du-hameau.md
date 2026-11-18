# 52 — Rendu du hameau fidèle à l'artefact

Statut : à fusionner
Possède : le dessin du sol et des objets dans `:game-ui`, la densité et le placement du décor dans `:game-engine`
Dépend de : 41, 42, 43

## Objectif

Retour de l'utilisateur, 18 novembre, sur la première démo du socle : le rendu est trop nu. Il doit se rapprocher nettement du hameau de l'artefact (`docs/rebrand/assets/aube/hameau.png`, `hameau-agrandi.png`, et le canvas page 1). Il faut itérer jusqu'à obtenir quelque chose de beaucoup plus beau.

## À livrer

- Un sol riche :
  - herbe en trois tons, avec touffes, petites fleurs et variations ;
  - chemins crème bordés, avec l'herbe qui déborde un peu ;
  - place en pavés arrondis, comme sur l'artefact ;
  - eau avec reflets et berges en pierre ;
  - pont en bois.
- Un décor abondant mais ordonné : arbres ronds, buissons, massifs de fleurs, clôtures, rochers, lanternes, bancs, tonneaux. Il borde les rues et les jardins, sans jamais gêner les routes ni les portes.
- Les vraies maisons de l'atlas du ticket 42, avec les toits recolorés, la fumée des cheminées et les fenêtres éclairées pour une maison au travail.
- Une boucle d'itération visuelle :
  1. une capture du hameau généré (3, 8 et 20 sessions) sur le banc ;
  2. comparaison côte à côte avec l'artefact ;
  3. correction, puis nouvelle capture, au moins trois tours.
- Les captures finales vont dans `docs/images/jeu/52-*.png`.

## Critères d'acceptation

- [ ] Côte à côte avec l'artefact, le hameau rendu a la même richesse et la même ambiance (à juger par l'utilisateur : `docs/images/jeu/52-comparaison.png`).
- [x] Toujours fluide : 60 images par seconde visées, sans allocation à chaque image.
- [x] Les tests du moteur restent verts : décor jamais sur une route, une porte ou un terrain.
- [x] `./gradlew check` vert.

## Rapport

Branche `ticket/52-rendu-du-hameau`, partie de `ticket/40-vue-jeu` et à jour de sa fusion `ebe9510` (main avec ThorAI, coursier du ticket 44).

**Moteur (`:game-engine`, paquet `village`)**
- `VillageDecorator` : décor abondant mais ordonné. Jardin de chaque maison bâtie (lanterne au coin, clôture aux bouts raccordés, ou haie, ou massif ; fleurs de part et d'autre de l'allée ; contre les pignons, un des quatre ensembles tirés par session : tonneau et caisse, jardinière ou fleurs, banc de côté et petit arbre, buisson fleuri et petit arbre). Bords des routes en rythme avec les terrains (buisson, fleurs, herbes, un arbre une limite sur deux), bords de l'avenue (lanterne basse, fleurs, buissons, arbres), place arrondie avec jardinières, bancs et poteau indicateur, puits et bosquet au pont, roseaux et galets sur les berges, ceinture d'arbres en quinconce, bosquets et petites choses dans les prés.
- Sentier du nord et pont (`Ground.BRIDGE`) sur la rivière, élargie à trois rangées.
- `DecorCatalog` : nouvelles listes et `sizes` (empreintes multi-tuiles alignées sur l'atlas), `all`, `sizeOf` ; les champs existants ne changent pas.
- Variété : `PlotCell` (rangée et colonne visibles). Une maison évite le modèle et le toit de ses voisines de rue (poids 1000), de ses voisines franches, en face sur la place, au-dessus, au-dessous (20), puis du reste de ce qui se voit à deux colonnes (1). `RoofPalette.AUBE` : huit toits (terre cuite, ardoise bleue, mousse, sarcelle, tournesol, corail rosé, chaume, bardeau brun).
- `Village.nearestHouse(tile, maxDistance)` : la maison devant laquelle se tient le coursier.
- Tests : `VillageDecorTest` (pont et sentier praticables jusqu'au bord nord, rien sur passage, porte ou terrain libre et son toit, jardins dans leur terrain avec lanterne et fleurs, empreintes du catalogue, au moins 20 sortes de décor, ceinture dense, voisines franches différentes sur 20 et 30 sessions, maisons de part et d'autre de la place, `nearestHouse`). Les tests du ticket 43 restent verts.

**Rendu (`:game-ui`)**
- `AtlasObject.chimney` et `windows` lus ; `houseCatalog()` les passe au repère de l'empreinte.
- `PixelWorld(states = village.houseStates())` : fenêtres allumées et vacillantes plus fumée animée (`fumee`) au travail ; lumière ambre qui sort de la porte en attente ; vitres éteintes au repos, arrêtée ou en échec (`HouseEffects`, `Recolor.windows`, `Atlas.house`, cache par image, toit et lumière).
- Performance : objets, boîtes, images et animations préparés une fois par monde (`GameAssets.animation`) ; seuls les objets dans la vue sont dessinés ; rien n'est alloué par objet et par image (l'overlay lit l'origine de la caméra une fois par image).
- Sol : pont en planches avec garde-corps, poteaux et ombre sur l'eau ; nénuphars.
- `VillageOverlay` (paquet `village`), la règle des panneaux, pour le ticket 45 :
  - une bulle d'état au pixel au-dessus de chaque maison (points, « ! » ambre qui sautille, « z », « ! » rouge sur gris), du côté opposé à la cheminée, posée sur la ligne du toit ;
  - le panneau complet de la seule maison `selected`, sous sa porte, pointe vers le seuil, au-dessus du toit faute de place, toujours entier dans l'écran (`SignPlacement`). Usage : `PixelWorld(…, states = states) { projection -> VillageOverlay(village, assets, projection, selected = village.nearestHouse(courierTile, 1)?.session?.id) }`.
- Kit : `SignPanel(pointer = false)` et `ArrowDirection.UP`, ajouts compatibles.
- Démo : jusqu'à 20 sessions, overlay et états ; l'écran du haut montre la rivière et le pont.
- Tests : `VillageOverlayTest` (placement du panneau aux bords et sans place, marques des maisons, effets par état, vitres, champs de l'atlas, décor tout entier dans l'atlas avec les mêmes empreintes) et cartes de 8 et 20 sessions rendues.

**Touché hors de mon lot, après la fusion du 44 (à signaler)** : `VillageScene` passe `states` à `PixelWorld`, et `CourierLayer.occludersOf(…, states)` redessine une maison devant le coursier avec ses vitres (sinon une maison éteinte se rallumait sous lui). `VillageSceneTest` compare donc avec un `PixelWorld` qui reçoit `states`. Aucune signature cassée.

**Boucle visuelle sur le banc** (`app.thorai.jeu`, `GameSandboxActivity`, 3, 8 et 20 sessions, comparées à `hameau.png`) :
1. décor, pont et toits variés : bulle de maison flottant au-dessus du toit, panneau sautillant qui couvrait sa pointe, lumière de porte invisible sur le chemin crème, pont qui ressemblait à un escalier ;
2. bulle posée sur la ligne du toit, panneau immobile (la bulle sautille), lumière ambre sur les marches, pont en planches et garde-corps, nénuphars, bulles hors écran retirées ;
3. bords de l'avenue, place arrondie et bancs déplacés, rythme des lanternes desserré, voisinage élargi à deux colonnes (deux échoppes au toit brun se faisaient face de part et d'autre de la place).

Captures finales : `docs/images/jeu/52-{3,8,20}-{haut,bas}.png`, cartes entières `52-carte-8.png` et `52-carte-20.png`, `52-comparaison.png` (artefact à gauche), et l'état de départ `52-avant-haut.png`.

**Écarts et limites**
- Pas de toit prune : aucune des 32 couleurs de la palette Aube ne le permet ; le corail rosé reste proche du chaume de loin.
- Les maisons de l'atlas sont à l'échelle du jeu (personnages de 30 px), plus petites que celles de l'artefact ; les rues restent droites et la place est arrondie en escalier.
- Les terrains libres restent de l'herbe nue (règle « rien sur un terrain libre ») ; un terrain clos de piquets, comme sur l'artefact, revient au ticket 48.
- Deux maisons de même modèle peuvent encore se voir à plus de deux colonnes d'écart.
- Le jugement final « même richesse et même ambiance » revient à l'utilisateur : critère laissé décoché.
- Aucun essai sur la Thor (consigne du lot).
