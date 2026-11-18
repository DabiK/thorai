# 28 — Parcours d'accueil au premier lancement

Statut : en attente (après 27)
Possède : `android/` (interface)

## Objectif

Demande de l'utilisateur, 18 novembre : un parcours d'onboarding au premier lancement. Une personne qui installe l'APK doit être guidée, sans lire la notice, jusqu'à sa première session.

## À livrer

- **Un parcours en quelques écrans**, sur les deux écrans de la Thor : explication en haut, actions et état en bas, à la manette (A continuer, B revenir) et au toucher.
- **Des étapes qui vérifient l'état réel et passent toutes seules au vert** :
  1. bienvenue : ce qu'est Régie, en deux phrases ;
  2. Termux et la Debian : détecter l'installation, sinon donner la commande d'installation sur la console (`thor/install-on-device.sh`, ticket 27) ;
  3. autorisation RUN_COMMAND, puis jeton du pont (ticket 27) et pont joignable ;
  4. micro, puis téléchargement du modèle de voix locale, avec sa taille et sa progression ;
  5. notifications ;
  6. mode console : le service d'accessibilité, facultatif, avec ce qu'il apporte ;
  7. accès des agents à Android : association du débogage sans fil, facultatif, avec la marche à suivre ;
  8. connexion des agents (écran du ticket 23) ;
  9. fini : lancer une première session.
- **Chaque étape facultative** peut être passée. Le parcours se rouvre depuis l'accueil (« Configurer ») et reprend à la première étape non faite.
- **Les textes** suivent `docs/textes.md`. Le système visuel est celui de Régie.

## Critères d'acceptation

- Sur la Thor, après effacement des données de l'app, le parcours démarre, détecte ce qui est déjà en place, et mène à la première session.
- Captures de chaque étape dans `artifacts/screenshots/28/`.
- `./gradlew check` est vert.
