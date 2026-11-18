# 30 — L'app s'appelle ThorAI, et une passe de finition de l'interface

Statut : en attente (après 31 ; à faire avec le 28 par le même agent)
Possède : `android/` (interface, nom, applicationId), docs qui citent le nom

## Décisions de l'utilisateur, 18 novembre

- **Nouveau nom : ThorAI.** L'app n'est pas destinée à être vendue. « Régie » devient le nom de code historique.
- **Soigner l'interface** : une passe de finition.
- **Retirer le texte « maintiens R2… »** de l'accueil.

## À livrer

- **Renommage visible** :
  - nom de l'app, titres, notifications, libellé du service d'accessibilité (« ThorAI, mode console »), textes, README et docs ;
  - **applicationId** `app.thorai`. La release (ticket 27) demande déjà une réinstallation : on n'en fait qu'une.
  
  Adapter les scripts et la doc qui citent `app.regie`. Les noms techniques internes (paquets Kotlin, scripts `regie-bridge-*`, dépôt) peuvent rester « regie » : le noter dans un ADR.
- **Accueil** : retirer le texte d'aide sur R2 et toute aide permanente devenue inutile.
- **Passe de finition sur tous les écrans** : accueil, session (haut et bas), fiche, diff, connexion, parcours d'accueil (ticket 28), mode console. La skill `impeccable` peut servir (« polish »).
  - Hiérarchie visuelle, espacements, alignements, densité, contrastes.
  - États vides, de chargement et d'erreur.
  - Petites transitions sobres, icônes cohérentes.
  - Lisibilité à bout de bras sur l'écran de 6 pouces et sur celui de 3,9 pouces.
  
  Pas de refonte de parcours : on garde les décisions des tickets 22 à 26.
- **Captures avant et après** de chaque écran sur la Thor, pas versionnées : `artifacts/screenshots/` sera purgé de l'historique. Seuls les visuels de vitrine vérifiés vont dans `docs/images/` (ticket 29).

## Critères d'acceptation

- `./gradlew check` est vert.
- Sur la Thor, l'app s'appelle ThorAI partout, et l'accueil n'a plus de texte d'aide sur R2.
