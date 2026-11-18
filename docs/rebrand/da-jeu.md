# Direction artistique du rebrand en jeu — « Aube »

Statut : exploration en maquettes, sans code de l'app. Le moteur ne change pas : le pont, les agents et la logique de l'app restent tels quels. Seule la surcouche sera refaite, plus tard.

Maquettes : canvas Design https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (privé, à partager depuis son menu).

Le canvas a trois pages :
- page 1, les trois directions explorées ;
- page 2, une variante écartée ;
- **page 3, les treize écrans de l'app dans la direction retenue.**

## L'idée

ThorAI devient un jeu d'exploration en 2D, vu de dessus, en pixel art. On incarne un coursier qui se promène dans un hameau.

Le hameau suit trois règles :
- **chaque maison est une session ;**
- son habitant est l'agent qui y travaille ;
- on entre dans une maison pour lire le fil, parler à l'agent, valider ses actions et relire ses modifications.

L'univers, les personnages, les tuiles et l'interface sont originaux. On reprend un genre, celui des RPG d'exploration vus de dessus, mais aucun élément d'un jeu existant.

## Principes

1. **Le monde en haut, le carnet en bas.** L'écran du haut montre la scène : le hameau, l'intérieur d'une maison, la tour de la console. Il porte aussi les répliques courtes, en bulles ou en boîtes de dialogue. L'écran du bas reste le tableau de bord : listes, fil complet, réglages, touches.
2. **L'état se lit de loin.** Sur chaque maison, une étiquette indique l'état de la session : au travail (points animés), au repos, ou attend ta validation (fond ambre, qui sautille).
3. **Un geste de jeu pour chaque fonction**, sans cacher l'information. Un parchemin tendu reste une validation, avec sa commande en clair. Les données des agents ne sont jamais réécrites. Les textes suivent `docs/textes.md`.
4. **De vrais pixels.** Les pixels sont carrés et agrandis à un facteur entier, sans anticrénelage. Toutes les images partagent une seule palette.
5. **Clair et accueillant.** On écrit en sombre sur du papier clair, avec des contours vert profond et jamais de noir pur.

## Grille et rendu

| | Valeur |
| --- | --- |
| Tuile | 16 × 16 px |
| Agrandissement | × 4 sur les deux écrans, au plus proche voisin |
| Écran du haut | 480 × 270 px natifs, soit 1920 × 1080 |
| Écran du bas | 310 × 270 px natifs, soit 1240 × 1080 |
| Personnages | 30 px de haut, partout : dans le hameau, les intérieurs et la tour (atlas `personnages`). Les grands portraits de `assets/aube/` (environ 48 × 77 px) ne servent qu'aux maquettes |
| Icônes d'interface | × 2, la seule exception à l'échelle × 4 |
| Contour | vert profond `#1F583A` ou `#222F36`, jamais noir |
| Lumière | soleil en haut à gauche, trois tons par matière |

Avec une échelle de × 4, les deux écrans ont exactement la même taille de pixel. Leur densité est en effet la même : 832 × 468 dp en haut et 538 × 468 dp en bas, à 2,3 px par dp.

## Palette

Les 32 couleurs ci-dessous sont relevées sur le hameau puis figées. Toute nouvelle image est ramenée à cette palette.

```
#222F36 #203648 #503835 #1F583A #1B5F53 #615039 #605751 #9E5A37
#5A718C #A16948 #22944C #DB6633 #DF724A #05ACAB #718EA0 #63A53A
#58A949 #A19E5C #A0A0A0 #8FB237 #71C645 #73C839 #27D1CB #E5A669
#D8AFA1 #A0D944 #A2DA3B #53E6DE #DDDC2C #DCDA5F #F1D8A6 #F0DDD1
```

Couleurs de l'interface (jetons de `outils/thorai-pixel.css`, thème `.t-aube`) :

| Rôle | Couleur |
| --- | --- |
| Papier (fond du bas) | `#FBE6B2` |
| Panneau | `#FFF4D6` |
| Encre | `#22343A` |
| Encre secondaire | `#5E6B5F` |
| Cadre | `#2D5354` |
| Sélection, au travail | `#1A8657` |
| Attend ta validation | `#F5B53D` sur texte `#3A2A10` |
| Au repos | `#C9C2A8` |
| Échec | `#C2412D` |
| Claude, Codex, opencode (tenue de l'habitant) | `#C1663F`, `#56708A`, `#4F8A3A` |

## Typographie

Les trois polices viennent de Google Fonts, sous licence SIL OFL 1.1.

| Police | Usage | Tailles (px sur l'écran) |
| --- | --- | --- |
| Jersey 10 | titres, noms, lieux, logo | 44 à 64 ; logo 300 |
| Pixelify Sans | dialogues, fil, réglages, étiquettes | 24 à 46 ; dictée 57 (25 sp) |
| VT323 | code, commandes, chemins, diff | 30 à 46 |

## Composants

Ils sont tous définis dans `outils/thorai-pixel.css`.

- **Cadre pixel** : bord d'un pixel de 4 px, coins crantés, lumière en haut et ombre en bas. Il sert aux panneaux, aux lignes et aux champs.
- **Cadre épais** : bord de deux pixels, coins en escalier. Il sert aux boîtes de dialogue, aux menus, aux fiches et aux cartes volantes.
- **Touche** : 48 px de haut. A, B, X et Y sont octogonales, les autres touches rectangulaires. Dans chaque groupe, l'ordre reste celui de `docs/textes.md`.
- **Étiquette d'état** : au travail (points animés), validation (« ! »), au repos.
- **Panneau de maison** : le projet, la session et l'état, avec une pointe vers la maison.
- **Boîte de dialogue** : un cartouche porte le nom de l'agent, et une flèche clignote pour passer à la suite. On peut ajouter un portrait de l'agent.
- **Menu de choix** : un curseur-flèche clignote. Chaque option porte sa touche.
- **Bulle** : pour l'activité de l'agent (« Modification · Overview.kt ») ou la parole du coursier.
- **Bandeau de notification** : il descend du haut de l'écran, avec une icône.
- **Lignes de diff** : numéro, signe, ligne ajoutée en vert pâle, ligne retirée en rose. La ligne choisie est encadrée.
- **Niveau du micro** : des barres en escalier.

## Correspondances entre l'app et le jeu

| Fonction de l'app | Forme dans le jeu | Écran |
| --- | --- | --- |
| Lancement, menu principal | écran titre : le hameau au lever du soleil et le logo | 01 |
| Connexion des agents, réglages | menu de pause (Start) sur le hameau assombri | 02 |
| Vue d'ensemble | le hameau en haut ; en bas, le carnet avec la première validation et les sessions | 03 |
| Ouvrir une session | marcher jusqu'à la porte ; transition en iris ; fiche de la session en bas | 04 |
| Session en direct | l'habitant à son bureau, avec une bulle d'activité. Son plan devient un journal de quête | 05 |
| Fil | la dernière réplique en boîte de dialogue ; le fil complet, dépliable, en bas | 06 |
| Dictée (L2) | le coursier parle (bulle du micro) ; en bas, la transcription en direct et les termes du dépôt | 07 |
| Validation | l'habitant tend un parchemin ; en bas, le menu de choix | 08 |
| Modifications (diff) | le tableau de liège, une feuille épinglée par fichier | 09 |
| Nouvelle session (X ou R2) | bâtir une maison sur un terrain libre, à partir d'une fiche pré-remplie | 10 |
| Nouveau projet | une nouvelle rue | 10, 11 |
| Mode console | la tour et son miroir. L'app pilotée occupe l'écran du haut, avec une carte volante | 12 |
| Notifications | l'oiseau messager et le courrier | 13 |
| Quitter | menu principal ou menu de pause, avec une confirmation si une session travaille | 01, 02 |

Les quatre étapes du chantier suivent la création d'une session :
1. fiche remplie : le terrain ;
2. session créée : les fondations ;
3. agent lancé : la charpente ;
4. première réponse : la maison finie.

## Le hameau s'agrandit

Le hameau suit la règle **un projet = une rue, une session = une maison sur la rue de son projet** :
- une nouvelle session d'un projet existant prend un terrain libre de sa rue ;
- quand la rue est pleine, elle s'allonge ;
- un nouveau projet ouvre une nouvelle rue, que le bandeau annonce ;
- la carte du hameau, sur l'écran du bas, liste les rues et leurs maisons, chacune avec son état.

L'habitant d'une maison est l'agent de la session : le scribe pour Claude, le bricoleur pour Codex, la cartographe pour opencode. Sa tenue rappelle l'outil, mais c'est la session qui fait la maison. Deux sessions Claude donnent donc deux maisons, chacune avec son scribe.

## Manette

| Entrée | Dans le jeu |
| --- | --- |
| Croix, stick gauche | marcher ; dans les menus, choisir |
| A | parler, entrer, valider |
| B | sortir, revenir, refuser ; sur le hameau, ouvrir les agents |
| X | sur le hameau, bâtir (nouvelle session) ; dans une maison, voir les modifications |
| Y | sur le hameau, aller à la tour (console) ; dans une maison, sans effet (ADR 17) : c’est Droite qui ouvre les réglages, comme dans le PRD |
| L1, R1 | maison précédente ou suivante. R1 ouvre d'abord la validation en attente |
| L2 maintenu | le coursier parle à l'habitant (dictée) |
| R2 maintenu | ordre vocal, valable partout |
| Start | menu de pause ; dans une maison, interrompre l'agent |
| Stick droit | faire défiler le fil |

## Fabrication des images

1. Codex génère chaque image avec son outil intégré `image_gen`. Le script `outils/gen.sh` assemble la consigne commune (`outils/consignes/header.txt`), le style et la scène, puis appelle `codex exec`. Les scènes suivantes reçoivent le hameau en référence, pour garder le même style.
2. `outils/pixelize.py` ramène chaque image sur la vraie grille :
   - réduction par blocs de 3 × 3 ;
   - recadrage à 480 × 270 ;
   - quantification sans tramage sur la palette du hameau ;
   - pour les planches de sprites, détourage du fond magenta (`#FF00FF`) puis découpe d'un fichier par sprite.
3. L'affichage agrandit toujours d'un facteur entier, avec `image-rendering: pixelated`.

Les images retenues sont dans `assets/aube/`.

## Atlas du jeu (ticket 42)

Les atlas de la vue jeu sont dans `android/game/ui/src/main/assets/game/`. `GameAssetLoader` les lit (ADR 24). Planche d'aperçu : `assets/planches/atlas-apercu.png`.

| Fichier | Contenu |
| --- | --- |
| `personnages` | Coursier : 4 directions × 3 images de marche, plus parler. Scribe, bricoleur, cartographe : repos (2), travail (4), attente avec le parchemin (2), sommeil (2), parler (1), joie (1). 49 images, 27 animations |
| `maisons` | 6 modèles d'une même famille : `maison.chaumiere`, `.atelier`, `.cabane`, `.etage`, `.tour`, `.boutique` |
| `decor` | 4 arbres, buissons, herbe haute, souche, rondin, 3 massifs de fleurs, 3 rochers, fontaine, puits, 2 ponts, 2 bancs, 2 lanternes, 2 panneaux, tonneau, caisse, jardinière, bois, brouette, planches, clôture en 16 morceaux, chantier en 4 étapes |
| `icones` | Icônes d'interface `ui.*` (à afficher × 2) : lettre, parchemin, rouleau, cloche, oiseau (2), fumée (3), étincelle (2), cœur, micro, plume, engrenage, sablier, clé, carte. En petit, à l'échelle du monde : lettre, parchemin, rouleau, cloche, oiseau, fumée, étincelle, cœur. Glyphes : bulles d'attente et de travail, sommeil, curseur, flèche, touches A, B, X, Y, L1, R1, L2, R2 |
| `fonds` | `fond.titre`, `fond.interieur.scribe`, `.bricoleur`, `.cartographe`, `fond.tour` : 480 × 270, ancrés en haut à gauche |
| `palette.json` | les 32 couleurs, les 16 rôles du sol (`grass`, `path`, `plaza`, `water`, `bridge` × `light`, `base`, `dark`, plus `outline`) et les couleurs clés des toits |

Conventions, en pixels natifs (le rendu agrandit × 4) :
- **Ancrage** (`ax`, `ay`) : les pieds d'un personnage. Pour un objet, le milieu de la rangée du bas de son empreinte, là où `PixelWorld` le pose.
- **Échelle** : tous les personnages font 30 px de haut, une porte de maison environ 28 px. Les intérieurs sont réduits à la même échelle : la pièce (307 × 204) est centrée sur un fond sombre `#203648`. La tour garde son fond sarcelle `#1B5F53`.
- **Marche** : `.0` pas A, `.1` debout, `.2` pas B ; l'animation enchaîne 0, 1, 2, 1. `coursier.debout.<direction>` montre l'image debout.
- **Maisons** : empreinte de 7 × 5 tuiles au plus, porte sur la dernière rangée (règle du moteur). Les toits sont peints avec trois couleurs clés, du clair au sombre : `#3C78FF`, `#1E50D2`, `#0F2D96`. Elles sont hors de la palette et loin du magenta. `chimney` (sortie de la fumée) et `windows` (rectangles à éclairer) sont comptés depuis le coin haut gauche de l'image de la maison. Le chargeur les lit (ticket 52) : fumée et fenêtres éclairées.
- **Décor** : l'empreinte est la partie qui bloque. Les fleurs et l'herbe ne sont pas des objets : on les traverse. Les ponts se traversent aussi. Une clôture est nommée par ses voisines (`n`, `e`, `s`, `o`) : `cloture.eo` est un morceau droit horizontal, `cloture.ns` vertical, `cloture.es` un coin. Sa tuile est le bas de l'image de 16 × 24. Les clés de `DecorCatalog.DEFAULT` (`decor.arbre.0`, `decor.fontaine` en 3 × 3…) sont des alias vers ces images.
- **Fonds** : `fonds.json` ajoute `scenes`, que le chargeur ignore : `piece` (la pièce), `sol` (le plancher praticable, approché), `habitant` (les pieds de l'agent, sur son tapis) et `entree` (les pieds du coursier à la porte).

Écart assumé aux maquettes 04 à 08 : l'intérieur y est vu de plus près, avec des personnages de 60 px. Les atlas gardent une seule taille de pixel et de personnage, du hameau à la tour.

Fabrication :
1. `PX=<taille d'un bloc> outils/gen.sh`, avec le style `consignes/aube-sprites.txt` et les scènes `consignes/jeu-*.txt`. Le hameau est l'image de référence. Les personnages sont demandés sur une grille de 4 × 4 : le générateur les dessine alors à environ 30 px natifs, et la réduction ne brouille pas les visages.
2. `outils/dessins_code.py <brut>` dessine au pixel près ce qui doit se raccorder : clôtures, bulles, sommeil, curseur, touches.
3. `outils/fonds.py` réduit les intérieurs (facteur 5) et compose les fonds.
4. `outils/pack_atlas.py build outils/atlas-recette.json <brut> android/game/ui/src/main/assets/game` découpe les planches sur fond magenta. Chaque pixel prend la couleur de la palette la plus fréquente sous lui, le centre comptant davantage. Les images d'une animation sont alignées sur les pieds, les toits passent en couleurs clés, puis les atlas sont empaquetés.
5. `outils/verifier_assets.py` contrôle le contrat lu par `GameAssetLoader` et les règles du moteur. `outils/apercu_atlas.py` refait la planche.

Les images brutes ne sont pas versionnées. La recette (`outils/atlas-recette.json`) donne, pour chaque planche, le facteur, l'ordre des sprites et les objets.

Défauts connus :
- La réduction laisse un peu de bruit dans les cheveux blonds du bricoleur. Les lunettes du scribe sont à peine lisibles à 30 px.
- La marche du coursier n'a pas de rebond. Les profils gauche et droit sont dessinés séparément, pas en miroir, d'où de légères différences.
- Les petites icônes à l'échelle du monde (oiseau, rouleau) sont lisibles mais un peu molles.
- La cabane de la cartographe a un plancher qui va jusqu'au bord de l'image, sans mur extérieur.
- L'écran titre reprend `assets/aube/titre.png`. C'est une illustration, à une échelle plus grande que le jeu.
- Les ponts ne font pas un nombre entier de tuiles (55 × 34 et 28 × 54).

## Sources et licences

| Élément | Source | Licence |
| --- | --- | --- |
| Décors, personnages, accessoires (`assets/aube/`) | générés pour ce projet avec Codex (`image_gen`), puis retouchés par `pixelize.py` | produits par l'utilisateur ; sous réserve des conditions d'utilisation d'OpenAI |
| Atlas et fonds du jeu (`android/game/ui/src/main/assets/game/`) | générés pour ce projet avec Codex (`image_gen`, consignes `outils/consignes/jeu-*.txt`), puis réduits sur la palette par `pack_atlas.py` et `fonds.py`. L'écran titre est `assets/aube/titre.png` | produits par l'utilisateur ; sous réserve des conditions d'utilisation d'OpenAI |
| Clôtures, bulles, sommeil, curseur, touches | dessinés par le code pour ce projet (`outils/dessins_code.py`) | produits par l'utilisateur |
| Jersey 10, Pixelify Sans, VT323 | Google Fonts, dépôt `google/fonts` : `ofl/jersey10/Jersey10-Regular.ttf`, `ofl/pixelifysans/PixelifySans[wght].ttf` (variable, 400 à 700), `ofl/vt323/VT323-Regular.ttf`, relevés le 18 novembre 2026. Dans l'app : `android/game/ui/src/main/res/font/` (`jersey10.ttf`, `pixelify_sans.ttf`, `vt323.ttf`) | SIL Open Font License 1.1 ; le `OFL.txt` de chaque police est dans `android/game/ui/src/main/assets/licences/` |
| Écran Android générique de la maquette 12 | dessiné en HTML pour la maquette | — |

Aucun asset tiers n'est utilisé, ni Kenney, ni OpenGameArt, ni aucune ressource CC0. Les consignes de génération interdisent de reprendre des personnages, des tuiles ou une interface de jeux existants.

## Explorations écartées

Elles restent sur le canvas.

- **Braise** : la clairière au crépuscule. Ambiance forte, mais fond sombre.
- **Néon** : un îlot technologique sous la pluie. Image chargée.
- **Aube, version jeu** : vue plus serrée, personnages chibi, une maison par session. L'utilisateur a préféré revenir à la première Aube. On garde de cette variante l'idée d'une maison par session et l'agrandissement du hameau.

## À faire avant l'intégration

- Le mode un seul écran, quand celui du bas est éteint.
- Un essai de lisibilité sur la Thor : taille du texte du fil, contraste des étiquettes.
