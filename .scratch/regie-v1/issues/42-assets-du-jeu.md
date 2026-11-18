# 42 — Assets du jeu

Statut : fusionné dans ticket/40-vue-jeu
Possède : `android/game/ui/src/main/assets/game/`, `docs/rebrand/outils/`, `docs/rebrand/assets/`
Dépend de : le contrat des assets

## Objectif

Générer avec Codex, puis ramener sur la grille et la palette Aube, tous les atlas du contrat : personnages animés, au moins six modèles de maison au toit recolorable, décor, icônes, fonds vides et `palette.json`.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- Les atlas et leurs JSON respectent le contrat.
- Planche d'aperçu dans `docs/rebrand/assets/planches/`.
- Sources et licences notées dans `da-jeu.md`.

## Rapport

Commit `c23256a`, fusionné dans `ticket/40-vue-jeu`. Les atlas sont dans `android/game/ui/src/main/assets/game/`. Planche d'aperçu : `docs/rebrand/assets/planches/atlas-apercu.png`.

**Contenu**

| Atlas | Contenu |
| --- | --- |
| `personnages` | 49 images, 27 animations : le coursier en 4 directions plus « parler » ; chaque habitant au repos, au travail, en attente, endormi, plus « parler » et « joie » |
| `maisons` | 6 modèles : chaumière, atelier, cabane, maison à étage, tour, échoppe |
| `decor` | 51 images, 59 objets |
| `icones` | 46 images, 6 animations |
| `fonds` | un atlas de 5 fonds en 480 × 270 |
| `palette.json` | les 32 couleurs et les 16 rôles du sol |

**Dimensions**
- Couleurs clés des toits : `#3C78FF`, `#1E50D2`, `#0F2D96`.
- Personnages : 30 px de haut.
- Empreintes des maisons : de 5 × 4 à 7 × 5 tuiles, porte sur la dernière rangée.

**Écarts au contrat**
- `fonds` est un atlas et non un dossier. La place de l'habitant et l'entrée de chaque fond sont dans une clé `scenes`.
- `chimney` et `windows` sont en pixels, depuis le coin haut gauche de l'image de la maison.
- Les icônes `ui.*` sont prévues pour un affichage à ×2.
- Les intérieurs sont à l'échelle du monde : une pièce de 307 × 204 centrée sur un fond sombre.

**Défauts connus**
- quelques pixels bruités dans les cheveux du bricoleur ;
- les lunettes du scribe sont pâles ;
- la marche ne sautille pas ;
- les icônes à l'échelle du monde sont un peu floues ;
- les ponts ne font pas des tuiles entières ;
- l'écran titre reprend l'ancienne illustration, plus grande.
