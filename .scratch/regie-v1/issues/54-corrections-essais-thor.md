# 54 — Corrections des premiers essais sur la Thor

Statut : en cours
Possède : la navigation et le retour (touche B) de la version jeu
Dépend de : 41 à 53

## Objectif

Retour de l'utilisateur, 18 novembre, après ses premiers essais de ThorAI jeu sur la Thor :

1. Après avoir quitté l'app puis y être revenu, le menu ne permet pas de retourner au hameau, et B ne fait pas « retour ».
2. Dans une maison, après l'envoi d'un message (« le chat »), un bug a empêché de revenir au fil, et B ne marchait pas.

Il faut reproduire les deux cas sur la Thor, trouver la cause, corriger avec des tests, puis vérifier sur la console. Il faut aussi passer en revue les autres chemins de retour (B) du jeu : hameau, maison, menu de pause, courrier, agents, réglages, validation, diff, chantier, mode console.

## Critères d'acceptation

- Les deux cas sont reproduits avant la correction et ne se reproduisent plus après, sur la Thor.
- B ramène toujours à l'écran précédent, de façon cohérente, partout dans le jeu.
- Des tests couvrent la reprise de l'app (arrière-plan puis retour) et le retour après un envoi.
- `./gradlew check` vert.

## Rapport

À remplir à la fin du lot.
