# 08 — Dictée dans l'app

Statut : en attente
Bloqué par : 01, 04, 06

## Objectif

Écran 2 de la maquette : dicter avec L2 maintenu, relire, puis envoyer.

## À livrer

- **`voice/` branché en composite build**, avec le fournisseur choisi dans les réglages de l'app. La clé est saisie par l'utilisateur.
- **L2 maintenu** :
  - texte en direct en haut, avec les termes du dépôt mis en avant (vocabulaire tiré des fichiers, fonctions et branches du projet de la session) ;
  - niveau et durée en bas.
- **Au relâchement** :
  - A : envoyer ;
  - B : effacer ;
  - Y : compléter ;
  - X : corriger un mot, à la croix puis en redictant.
- **Cibles de la dictée** : la session affichée, le refus avec consigne (ticket 07), un commentaire de diff (ticket 10).
- **Service au premier plan de type micro** pendant la dictée, si nécessaire.

## Critères d'acceptation

- Sur la Thor, une consigne dictée en français avec des noms de fichiers arrive à l'agent après relecture, avec chacun des deux fournisseurs.
