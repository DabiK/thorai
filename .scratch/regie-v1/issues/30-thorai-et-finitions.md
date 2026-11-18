# 30 — L'app s'appelle ThorAI, et une passe de finition de l'interface

Statut : à fusionner
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

## Rapport

STATUT: à fusionner
RÉSUMÉ: l'app s'appelle ThorAI, paquet `app.thorai` (ADR 21), noms techniques gardés. Texte d'aide R2 retiré de l'accueil, avis permanents (mode console, accès Android) remplacés par « Configurer » et son point ambre. Passe de finition : tailles de lecture sur l'écran du bas, action principale commune, états vides, fiche, connexion, voix, diff.
CRITÈRES:
- [x] `./gradlew check` vert → 475 tests et lint.
- [x] Sur la Thor, l'app s'appelle ThorAI partout et l'accueil n'a plus d'aide R2 → lanceur, titres, fil d'Ariane, service « ThorAI, mode console », messages ; `30/apres/01`.
PORTE DE QUALITÉ: android, voix et pont verts (voir ticket 28) ; consignes et permissions des agents (`test-consignes`, `test-permissions`) verts.
THOR: avant (`app.regie`) puis après (`app.thorai`, build debug relié au pont par son jeton historique) sur les deux écrans. Release `app.thorai` laissée installée. `app.regie` **non désinstallée** : ThorAI release n'est pas encore reliée au pont, faute du geste RUN_COMMAND de l'utilisateur ; la désinstaller ensuite (`adb uninstall app.regie`).
REVIEW: voir ticket 28 (revue indépendante commune).
DÉCISIONS PRISES: ADR 21. APK de release `thorai-<version>.apk`. Consignes des agents nommant ThorAI, déployées au prochain `thor/computer-use/install.sh`. Messages du pont nommant ThorAI, déployés au prochain `thor/bridge/install.sh`. Les deux apps peuvent rester reliées pendant la transition (ADR 22).
POUR L'UTILISATEUR: suivre le parcours de ThorAI (ticket 28), vérifier les sessions, puis désinstaller Régie ; ne garder qu'un mode console actif.
QUESTIONS: aucune.
CAPTURES: `artifacts/screenshots/30/avant/` et `/apres/` : accueil, session, modifications, fiche, connexion, voix ; `apres/07` montre l'offre de connexion réapparue après un redémarrage du pont, corrigée depuis.
HORS PÉRIMÈTRE: mode console (carte volante, fil compact) non recapturé : le service n'est pas activé sur ThorAI.
