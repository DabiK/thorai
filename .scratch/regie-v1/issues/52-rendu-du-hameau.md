# 52 — Rendu du hameau fidèle à l'artefact

Statut : bloqué par 41, 42 et 43
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

- Côte à côte avec l'artefact, le hameau rendu a la même richesse et la même ambiance.
- Toujours fluide : 60 images par seconde visées, sans allocation à chaque image.
- Les tests du moteur restent verts : décor jamais sur une route, une porte ou un terrain.
- `./gradlew check` vert.

## Rapport

À remplir à la fin du lot.
