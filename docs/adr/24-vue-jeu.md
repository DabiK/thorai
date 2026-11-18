# ADR 24 — Vue jeu : rendu pixel sur un Canvas Compose

Statut : accepté, 18 novembre 2026 (ticket 41, épopée 40). Complète `docs/rebrand/plan-dev-jeu.md`
et `docs/rebrand/da-jeu.md`.

## Contexte

L'app devient un jeu d'exploration vu de dessus, en pixel art (direction « Aube ») : un hameau où
chaque maison est une session. Le moteur de l'app ne change pas (pont, agents, cas d'usage) : seule
la vue est refaite, par lots parallèles (41 à 51). Le lot 41 pose le socle commun : modules, rendu,
atlas, kit d'interface et polices.

Le jeu est simple à dessiner : une carte de tuiles de 16 px, quelques dizaines de sprites,
480 × 270 pixels natifs agrandis ×4 sur l'écran du haut, des menus et des listes sur l'écran du bas.
Il doit vivre dans les activités existantes (`TopActivity`, `BottomActivity`, `GamepadActivity`),
garder le routeur d'entrées de la manette (ADR 05) et se tester sans console.

## Décisions

### Un Canvas Compose plutôt qu'un moteur de jeu embarqué

- Le monde est dessiné par un `Canvas` Compose (`PixelWorld`), au milieu des écrans Compose.
- **libGDX** a été écarté : il impose sa propre surface OpenGL et son cycle de vie
  (`AndroidApplication`, `AndroidFragmentApplication`), ses entrées et sa boucle. Il faudrait les
  marier aux deux écrans, au routeur de la manette et aux superpositions Compose (panneaux, bulles,
  dialogues), et on perdrait les tests Robolectric des écrans.
- **KorGE** a été écarté : il amène son propre plugin Gradle multiplateforme et sa vue, plus lourds
  que tout le reste de l'app, pour un besoin qui tient en quelques `drawImage`.
- Le Canvas suffit à 480 × 270 pixels : le sol est une seule image, les objets quelques dizaines
  d'appels. Les textes, menus et cadres restent de vrais composables (accessibilité, tests, polices).

### Modules

- `android/game/engine`, `:game-engine` : Kotlin pur, sans Android. Le modèle du monde
  (`TilePos`, `Ground`, `RoofColors`, `Placed`, `TileMap`, `World`), et plus tard le hameau
  procédural, les chemins et les scènes (lots 43, 44).
- `android/game/ui`, `:game-ui` : bibliothèque Android Compose, paquet `app.regie.game.ui`. Elle
  dépend de `:game-engine` et de `:domain`, jamais des adapters ni de la racine de composition.
  `ArchitectureTest` le vérifie, ainsi que l'absence d'Android dans `:game-engine`.
- `android/app` dépend de `:game-ui` et reste la seule racine de composition. Un bac à sable
  (`GameSandboxActivity`, source `debug` de `:game-ui`) montre la scène de démonstration sur le banc.

### Le sol dessiné par le code

- Herbe, chemin, place, eau et pont sont peints par `GroundPainter` dans une image au pixel natif,
  puis agrandie : pas de jeu de tuiles généré, qui se raccorde mal. Le dessin suit le hameau de
  l'artefact (`docs/rebrand/assets/aube/hameau.png`), comparé au banc en trois tours :
  - l'herbe en trois tons, par plaques douces tramées (bruit de valeur et trame de Bayer), avec des
    touffes, des brins clairs et de petites fleurs ;
  - le chemin crème et caillouteux, bordé d'un liseré, l'herbe débordant de 0 à 2 pixels sur des
    bords aux coins arrondis ;
  - la place en pavés arrondis et irréguliers (cellules de Voronoï), joints gris, éclairés en haut
    à gauche ;
  - l'eau turquoise avec remous et reflets, bordée de moellons gris et d'une ombre ;
  - le pont en planches perpendiculaires au passage, avec ses garde-corps.
- Chaque matière a trois tons (`palette.json`, rôles `<sol>.light`, `.base`, `.dark` pour `grass`,
  `path`, `plaza`, `water`, `bridge` et `stone`, plus `grass.deep`, `water.mid`, `flower.0` à
  `flower.3` et `outline`). Tout est déterministe (hachage des coordonnées) : la même carte donne
  toujours le même sol.
- Les bords dépendent des huit voisines (`GroundEdges`) : une distance au bord, arrondie aux coins
  extérieurs et mordant aux coins intérieurs, choisit la bande (herbe qui déborde, liseré, berge).
- Le sol est peint **hors du fil principal**, une fois par contenu : `GroundKey` est l'empreinte du
  sol (dimensions et matière de chaque tuile), `GroundCache` garde les derniers sols peints, partagés
  par les deux écrans. Le générateur du hameau crée une `TileMap` à chaque vue d'ensemble : un sol
  identique n'est pas repeint. En attendant le premier dessin, l'écran montre l'herbe unie ; ensuite
  l'ancien sol reste affiché jusqu'au nouveau. La boucle des pixels n'alloue rien.

### Atlas et toits

- Le contrat de `plan-dev-jeu.md` est appliqué tel quel : `<atlas>.png` et `<atlas>.json`
  (`frames`, `anims`, `objects`), lus par `GameAssetLoader` dans `assets/game/`.
- Un identifiant se résout dans cet ordre : objet, animation (image selon l'horloge), image.
- Un atlas absent ou invalide est signalé dans `GameAssets.problems`. Un objet qu'aucun atlas ne
  connaît est dessiné par une **forme de repli** (`PlaceholderPainter`) : l'appelant donne la nature
  et l'empreinte du moteur (`Village.fallbacks()`), et la maison de repli couvre toute son empreinte,
  toit aux couleurs de la session, porte, fenêtres et cheminée visibles ; arbres, buissons, fleurs,
  rochers, clôtures, fontaine, bancs, lanternes et panneaux ont aussi la leur. Le repli ne sert que
  si l'atlas manque.
- Les atlas sont décodés une fois par processus (`SharedGameAssets`) et partagés par les deux écrans.
- Les modèles de maison du générateur viennent de l'atlas (`GameAssets.houseCatalog()` : empreinte
  et porte de chaque objet `maison.*`) : le moteur bloque exactement ce que l'image occupe.
- Les trois couleurs clés d'un toit sont remplacées par les `RoofColors` de la session ; l'image
  repeinte est gardée en cache par couple (image, couleurs).
- Ajout au contrat : `palette.json` est `{"colors": ["#RRGGBB", …], "roles": {"grass.light": …}}`.
  Un rôle absent garde sa couleur par défaut, prise dans la palette du hameau.
- Lu tel que le lot 42 l'a livré : l'ancre d'un objet est le milieu de la dernière rangée de son
  empreinte ; les toits ont pour clés `#3C78FF`, `#1E50D2`, `#0F2D96`. `chimney` et `windows`
  (en pixels de l'image) sont lus depuis le ticket 52 (`AtlasObject`), et passés au repère de
  l'empreinte dans `houseCatalog()` ; `scenes` (atlas `fonds`) ne l'est pas encore.

### Agrandissement ×4 au pixel près

- Un pixel du dessin vaut 4 pixels physiques, sur les deux écrans (`GameScale.WORLD`).
- Les tailles de texte du kit sont aussi en pixels physiques : `GameMetrics.sp` divise par la
  densité et par l'échelle de police du système. Le réglage « taille du texte » d'Android est donc
  **ignoré** par la vue jeu : un texte pixel doit tomber sur la grille ×4 et tenir dans ses cadres.
  La lisibilité se règle dans le jeu, sur la Thor.
- La Thor a une densité de 2,3 : des dp ronds donneraient des pixels de 4 ou 5 pixels physiques.
  Le kit passe donc par `GameMetrics`, qui convertit les pixels de l'écran des maquettes en dp
  exacts, et le rendu travaille en pixels physiques entiers.
- Images dessinées avec `FilterQuality.None`, positions entières, caméra centrée sur un point et
  bornée au monde (centrée si le monde est plus petit que la vue). Le point suivi est une fonction
  (`focus: () -> ArtPoint`) lue au dessin et au placement : la caméra suit le coursier sans
  recomposer les panneaux accrochés au monde (`anchorBottomCenter`).
- Une horloge (`rememberGameClock`, `withInfiniteAnimationFrameMillis`) anime les sprites et le
  kit. Elle est lue au dessin, sans recomposer ; les tests Compose la mettent en pause ou la figent.
  Les clignotements suivent les pas francs des maquettes (`Blink`).

### Kit pixel et thème

- `GameTheme` porte les jetons Aube (`thorai-pixel.css`), les polices, les mesures et l'horloge.
- Composants génériques, sans copie d'écran : `PixelFrame` (fin, épais), `KeyChip`, `KeyHint`,
  `KeyHintBar`, `StateTag`, `SignPanel`, `Bubble`, `DialogBox`, `ChoiceMenu`, `Toast` et
  `ToastHost`, `PixelDiffLine`, `MicMeter`, `PixelArrow`.
- Écarts assumés aux maquettes, pour la cohérence :
  - une touche posée sur le papier est vert profond sur papier, sinon elle disparaît (la maquette
    met le papier sur le papier) ; la barre des touches garde les touches couleur papier ;
  - les touches d'une barre sont triées dans l'ordre de `docs/textes.md` (A, B, X, Y, L1, R1, L2,
    R2, Start), la croix et les sticks en tête ;
  - les couleurs d'agent ne supposent pas de liste figée (ticket 31) : Claude, Codex et opencode
    ont la leur, un agent inconnu une couleur neutre (`GameColors.agent`) ;
  - une session arrêtée s'affiche « au repos ».

### Polices

- Jersey 10 (titres), Pixelify Sans (texte, police variable de 400 à 700) et VT323 (code), de
  Google Fonts, sous SIL OFL 1.1. Les TTF sont dans `res/font`, leurs licences dans
  `assets/licences/`. Sources dans `docs/rebrand/da-jeu.md`.

### Le coursier (ticket 44)

- La marche est dans le moteur (`CourierController`, Kotlin pur) : un pas d'une tuile en 160 ms,
  vitesse constante, jamais d'arrêt entre deux tuiles ; la dernière direction enfoncée l'emporte, un
  appui pendant un pas est joué après lui. L'app traduit la manette en `CourierCommand` ; le
  contrôleur répond par des `CourierEvent`. Il est piloté par le temps (`advance`), donc testé sans
  horloge ni Compose.
- La scène (`VillageScene`, `VillageSceneState`) ne fait tourner une horloge d'images que pendant
  un pas ; à l'arrêt, rien n'est redessiné pour le coursier.
- Le coursier est dessiné dans une couche au-dessus de `PixelWorld`, pas dans sa liste de sprites :
  cette liste est triée à la composition, et la changer à chaque image recomposerait et allouerait.
  Pour garder la profondeur, la couche redessine, découpés au seul rectangle du coursier, les objets
  qui sont devant lui dans l'ordre de `PixelWorld` (un test compare au pixel près avec un sprite
  fixe de `PixelWorld`). Si `PixelWorld` accepte un jour des sprites lus au dessin, la couche
  pourra s'y fondre.
- L'entrée dans une maison passe par un iris en vrais pixels (rangées de 4 pixels physiques),
  fermé sur la porte, puis par le rappel `onEnterHouse`.

### Le hameau vivant et ce qui se lit dessus (ticket 52)

- **Décor** : le moteur pose un décor abondant mais ordonné (`VillageDecorator`) : jardins par
  maison (lanterne, clôture aux bouts raccordés, haie ou fleurs, accessoires contre les pignons tirés
  par session), bords des routes et de l'avenue en rythme avec les terrains, place arrondie, puits
  et bosquet au pont, berges, ceinture d'arbres en quinconce, bosquets dans les prés. Jamais sur un
  passage, une porte, un terrain libre ni la place d'un toit ; les empreintes multi-tuiles (puits,
  jardinière, banc de côté…) viennent de `DecorCatalog.sizes`, alignées sur l'atlas.
- **Eau** : un sentier monte de la place et franchit la rivière (trois rangées) sur un pont en
  planches peint par le code, avec garde-corps, poteaux et ombre sur l'eau ; nénuphars.
- **Variété** : une maison évite le modèle et le toit de ses voisines de rue, puis de ses voisines
  franches (en face sur la place, au-dessus, au-dessous), puis du reste de ce qui se voit. Huit toits,
  tous dans la palette Aube (pas de prune : aucune couleur de la palette ne la donne).
- **Vie** : `PixelWorld(states = village.houseStates())`. Au travail : fenêtres allumées qui
  vacillent, fumée animée depuis `chimney`. En attente : fenêtres allumées, lumière ambre qui sort
  de la porte. Au repos, arrêtée ou en échec : vitres éteintes (bleu ardoise). Les images de maison
  (toit et vitres) sont recolorées une fois par (image, toit, lumière) et mises en cache.
- **Ce qui se lit** (`VillageOverlay`, à mettre dans l'`overlay` de `PixelWorld`) : une petite bulle
  d'état au-dessus de **chaque** maison, dessinée au pixel avec l'atlas `icones` (points au travail,
  « ! » ambre qui sautille en attente, « z » au repos, « ! » rouge sur gris en échec), du côté opposé
  à la cheminée, posée sur la ligne du toit ; le **panneau complet d'une seule maison**, `selected`
  (devant laquelle est le coursier, `Village.nearestHouse`, ou désignée par un raccourci), qui pend
  sous la porte, pointe vers le seuil, passe au-dessus du toit faute de place, et reste toujours
  entier dans l'écran (`SignPlacement`). Plus de panneau sur les toits ni coupé par le bord.
- **Performance** : `PixelWorld` prépare une fois par monde les objets, leurs boîtes, leurs images et
  leurs animations (`GameAssets.animation`), ne dessine que ce qui est dans la vue et n'alloue rien
  par objet et par image.

### La vue d'ensemble en hameau (ticket 45)

- **Branchement** : `VillageController` (`android/app`, paquet `overview`) régénère le hameau à
  chaque vue d'ensemble qui change une maison (session, projet, agent, titre, état, validation ;
  l'ordre n'y compte pas), sur un fil à part (un seul à la fois), et porte l'unique `VillageSceneState` du processus : le coursier garde sa place d'un écran à l'autre.
  La disposition passe par `PreferencesVillageLayoutStore` (paquet `storage`, un adapter : l'interface
  ne l'importe pas), en JSON versionné ; une valeur illisible donne un hameau neuf. Les modèles de
  maison sont lus dans `maisons.json` seul, sans décoder d'image, pour générer dès la première vue
  d'ensemble.
- **Manette** : `InputRouter` publie en plus, à part, les directions tenues (croix de l'appui au
  relâché, sans répétitions ; stick gauche, direction dominante avec hystérésis). Sur le hameau elles
  font marcher le coursier ; ailleurs rien ne change. Les touches de croix qu'Android tire d'un
  mouvement non consommé (`FLAG_FALLBACK`) sont avalées quand elles viennent du stick gauche, gardées
  quand elles viennent d'une croix en axe (HAT). A entre devant une porte, sinon marche jusqu'à la
  maison choisie et y entre ; L1/R1 changent de maison ; avec une validation en attente, R1 va à celle
  de `firstPendingApproval` (devant sa porte, il y rentre) ; une ligne
  touchée du carnet et « R1 Voir » de l'oiseau messager y envoient le coursier. Le titre, le parcours
  d'accueil, le menu de pause et les panneaux ouverts prennent la manette avant lui ; B (agents) et
  Start (menu de pause, ticket 49) sont pris par `AppContainer`. Le reste (X, Y, L2, R2, stick droit,
  Quitter) suit `OverviewController`, dont le focus suit la maison choisie (L2 et R2 la visent).
- **Repli** : sans hameau, ou quand l'écran du haut n'est pas visible (mode console), la vue
  d'ensemble classique répond : un coursier qu'on ne voit pas ne marche pas.
- **Maison choisie** : celle où va le coursier, sinon celle dont il regarde la porte, sinon la plus
  proche (`Village.nearestHouse`, sans limite). C'est la ligne choisie du carnet et le `selected` de
  `VillageOverlay`. Retour sur la vue d'ensemble : `leaveHouse`, le coursier ressort devant la porte.

### Le parcours d'accueil (ticket 53)

- **Même parcours, autre décor.** Les étapes, les vérifications, les actions de A, B, X et Y et leur
  routage restent ceux de l'ADR 23 (`SetupReview.keys`, `ReviewSetup.press`, `AppContainer`). Seul le
  point de branchement de `GamepadActivity` change : `GameSetupScreen` (`:game-ui`, `setup/`) à la
  place de `SetupScreen`, gardé pour le ticket 51. Une permission n'est jamais accordée par l'écran :
  il ne fait que transmettre l'action, et c'est Android qui demande.
- **La route du hameau** (`ArrivalRoad`, moteur, Kotlin pur) : une route qui part du bord gauche,
  un panneau par étape, puis la place et deux maisons. Le coursier entre par la gauche, marche au pas
  du hameau (`CourierController.STEP_MILLIS` par tuile) jusqu'au panneau de l'étape et le lit ; sa
  position est lue au dessin, avec `CourierLook` et `drawCourier` du ticket 44. Une case au-dessus
  de chaque autre panneau dit où on en est, comme le journal de quête : verte, prête ; ambre qui
  clignote, l'étape affichée ; vide, à faire. Le panneau de l'étape porte son nom, « Étape n sur 9 »
  et son état (« ! À faire » en ambre qui sautille, ou « Prête »). Les habitants des agents annoncés
  par le pont attendent près du panneau des comptes, contents s'ils sont connectés.
- **En haut**, l'explication tient dans un grand cadre posé sur la scène (commandes à droite quand
  l'étape en a) ; la caméra cadre le panneau et le coursier dans ce que le cadre laisse voir
  (`clearPx`), et la carte est assez haute pour que la caméra ne bute pas sur son bord. Le stick droit
  fait défiler l'explication, comme avant. **En bas**, le carnet : avancement (compteur et une case
  par étape), état vérifié, l'action de A dans un grand cadre, puis B, X et Y dans la barre des
  touches ; « Reprendre plus tard » se touche dans l'en-tête. **Un seul écran** : la route en bandeau,
  l'explication à gauche, l'état et A à droite, la barre en bas.
- **« Préparer la console »** devient une entrée du menu de pause (`MenuEntry.SETUP`) : il rouvre le
  parcours à la première étape non faite et ferme le menu. Son détail signale une étape facultative
  restante, comme le point ambre de « Configurer » sur l'accueil classique.
- **Textes** : ceux du parcours classique (`strings_setup.xml`). Deux renvois changent : Y sur la fin
  dit « Terminer » (on arrive au titre ou au hameau, plus à l'accueil), et la note de fin renvoie à
  « Start › Préparer la console ».


## Conséquences

- Les lots suivants dessinent avec `PixelWorld` et le kit, sans nouvelle dépendance.
- Une carte modifiée doit arriver dans un nouveau `World` (ou une nouvelle `TileMap`) : le sol est
  repeint si son contenu a changé, jamais sur le fil principal.
- Les textes du kit sont rendus avec l'anticrénelage des polices ; seules les images sont au plus
  proche voisin.
