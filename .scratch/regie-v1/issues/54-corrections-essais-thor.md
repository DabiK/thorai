# 54 — Corrections des premiers essais sur la Thor

Statut : en cours
Possède : la navigation et le retour (touche B) de la version jeu
Dépend de : 41 à 53

## Objectif

Retour de l'utilisateur, 18 novembre, après ses premiers essais de ThorAI jeu sur la Thor :

1. Après avoir quitté l'app puis y être revenu, le menu ne permet pas de retourner au hameau, et B ne fait pas « retour ».
2. Dans une maison, après l'envoi d'un message (« le chat »), un bug a empêché de revenir au fil, et B ne marchait pas.

Règles précisées par l'utilisateur le même jour :
- « Continuer », dans le menu Start, ouvre toujours le hameau, d'où qu'on vienne (reprise de l'app, maison, fiche, console). On y retrouve le coursier, prêt à marcher. Jusqu'ici, « Continuer » refermait seulement le menu.
- « Nouvelle session » fait apparaître une session, donc une maison. On revient au hameau, la fiche s'ouvre, A lance le chantier sur un terrain, puis la maison est bâtie (ticket 48). B sur la fiche ramène au hameau.
- Le menu et tous les parcours dans une maison doivent marcher avec B : fil, réglages, dictée, envoi puis retour au fil, validation, diff, sortie vers le hameau.

Il faut reproduire les deux cas sur la Thor, trouver la cause, corriger avec des tests, puis vérifier sur la console. Il faut aussi passer en revue les autres chemins de retour (B) du jeu : hameau, maison, menu de pause, courrier, agents, réglages, validation, diff, chantier, mode console.

## Critères d'acceptation

- Les deux cas sont reproduits avant la correction et ne se reproduisent plus après, sur la Thor.
- B ramène toujours à l'écran précédent, de façon cohérente, partout dans le jeu.
- « Continuer » ramène au hameau, et « Nouvelle session » mène à la fiche puis à une maison, y compris après une reprise de l'app ou depuis une maison.
- Des tests couvrent la reprise de l'app (arrière-plan puis retour) et le retour après un envoi.
- `./gradlew check` vert.

## Rapport

À remplir à la fin du lot.
