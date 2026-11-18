# 29 — README plus vendeur et première release

Statut : en attente (après 28)
Possède : `README.md`, `artifacts/screenshots/vitrine/`

## Objectif

Demande de l'utilisateur, 18 novembre : un README plus marketing, et un APK disponible en release.

## À livrer

- **Un README qui donne envie** :
  - accroche forte, avec en tête une capture ou un GIF des deux écrans en action ;
  - trois à cinq bénéfices clairs ;
  - une démonstration en étapes illustrées (parler, l'agent agit, on valide) ;
  - un bouton ou un lien « Télécharger l'APK » vers la dernière release ;
  - l'installation en résumé, avec le lien vers `docs/installation.md` ;
  - la FAQ courte, la licence, les crédits.
  
  Reste honnête sur les limites, sans exagération ni emoji.
- **Visuels de vitrine** :
  - des captures soignées et recadrées des deux écrans côte à côte, prises sur la Thor en lecture seule ;
  - un GIF court si possible, à partir d'une suite de captures (outil local, sans service en ligne).
- **Emplacement et propreté des visuels** (décision du 18 novembre, nettoyage de l'historique) : `artifacts/screenshots/` sera purgé de tout l'historique et ne sera plus versionné. Les visuels de vitrine vont donc dans `docs/images/`. Chacun est vérifié à l'œil avant d'être commité : barre d'état recadrée ou masquée (pas de nom de Wi-Fi, d'IP, d'heure ni de notification), aucun nom de projet privé, aucun compte, aucun chemin personnel. Le README ne pointe que vers `docs/images/`.
- **Première release** : l'orchestrateur lance `scripts/release.sh 0.1.0` (ticket 27), qui crée la release avec l'APK.
