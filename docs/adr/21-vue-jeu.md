# ADR 21 — Vue jeu : rendu pixel sur un Canvas Compose

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
  calculée une fois par carte puis agrandie : pas de jeu de tuiles généré, qui se raccorde mal.
- Chaque matière a trois tons (`palette.json`, rôles `<sol>.light`, `.base`, `.dark`, plus
  `outline`) et une texture déterministe (hachage des coordonnées) : la même carte donne toujours
  le même sol.
- Les bords dépendent des huit voisines (`GroundEdges`) : l'herbe est le fond ; le chemin et la
  place se bordent contre l'herbe, la place contre le chemin ; l'eau a une berge et de l'écume ; le
  pont a des garde-corps au-dessus de l'eau. Coins extérieurs arrondis, coins intérieurs marqués.

### Atlas et toits

- Le contrat de `plan-dev-jeu.md` est appliqué tel quel : `<atlas>.png` et `<atlas>.json`
  (`frames`, `anims`, `objects`), lus par `GameAssetLoader` dans `assets/game/`.
- Un identifiant se résout dans cet ordre : objet, animation (image selon l'horloge), image.
- Un atlas absent ou invalide est signalé dans `GameAssets.problems` et remplacé par des formes
  (bloc de l'empreinte, toit compris) : les lots avancent sans attendre les images.
- Les trois couleurs clés d'un toit sont remplacées par les `RoofColors` de la session ; l'image
  repeinte est gardée en cache par couple (image, couleurs).
- Ajout au contrat : `palette.json` est `{"colors": ["#RRGGBB", …], "roles": {"grass.light": …}}`.
  Un rôle absent garde sa couleur par défaut, prise dans la palette du hameau.
- Pas encore dans le contrat : la cheminée et les fenêtres des maisons. Le lot des assets (42)
  fixe leurs noms de champs ; le lecteur les ajoutera alors.

### Agrandissement ×4 au pixel près

- Un pixel du dessin vaut 4 pixels physiques, sur les deux écrans (`GameScale.WORLD`).
- La Thor a une densité de 2,3 : des dp ronds donneraient des pixels de 4 ou 5 pixels physiques.
  Le kit passe donc par `GameMetrics`, qui convertit les pixels de l'écran des maquettes en dp
  exacts, et le rendu travaille en pixels physiques entiers.
- Images dessinées avec `FilterQuality.None`, positions entières, caméra centrée sur un point et
  bornée au monde (centrée si le monde est plus petit que la vue).
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

## Conséquences

- Les lots suivants dessinent avec `PixelWorld` et le kit, sans nouvelle dépendance.
- Une carte modifiée doit arriver dans un nouveau `World` : le sol est recalculé par carte.
- Les textes du kit sont rendus avec l'anticrénelage des polices ; seules les images sont au plus
  proche voisin.
