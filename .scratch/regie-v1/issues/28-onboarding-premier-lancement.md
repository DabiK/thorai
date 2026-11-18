# 28 — Parcours d'accueil au premier lancement

Statut : à fusionner
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
  8. connexion des agents (écran du ticket 23) : Claude, Codex et opencode (ticket 31 ; pour opencode Go, saisie de la clé d'API par l'utilisateur), chacun facultatif ;
  9. fini : lancer une première session.
- **Chaque étape facultative** peut être passée. Le parcours se rouvre depuis l'accueil (« Configurer ») et reprend à la première étape non faite.
- **Les textes** suivent `docs/textes.md`. Le système visuel est celui de Régie.

## Critères d'acceptation

- Sur la Thor, après effacement des données de l'app, le parcours démarre, détecte ce qui est déjà en place, et mène à la première session.
- Captures de chaque étape dans `artifacts/screenshots/28/`.
- `./gradlew check` est vert.

## Rapport

STATUT: à fusionner
RÉSUMÉ: parcours d'accueil en neuf étapes (ADR 23), vérifiées sur la console chaque seconde : bienvenue, Termux et Debian, pont, voix (micro puis modèle de transcription, 488 Mo, progression), notifications, puis mode console, accès à Android et comptes des agents, facultatifs, jusqu'à la première session. Explication en haut, état et actions en bas, A/B/X/Y et toucher. « Configurer » sur l'accueil le rouvre à la première étape non faite.
CRITÈRES:
- [ ] Sur la Thor, après effacement des données, le parcours démarre, détecte ce qui est en place et mène à la première session → prouvé avec un build debug installé à neuf (jeton du pont copié par `token-app`, aucune permission accordée) : Termux, pont et comptes détectés au vert, fin du parcours, fiche, session Codex créée (`10` à `12`). Avec la release, le parcours démarre et détecte Termux (`13` à `15`) ; la suite attend le geste RUN_COMMAND de l'utilisateur.
- [x] Captures de chaque étape → `artifacts/screenshots/28/01` à `09` (non versionnées).
- [x] `./gradlew check` vert.
PORTE DE QUALITÉ: android `./gradlew check` vert (475 tests, lint) ; voix `./gradlew check` vert ; pont `npm run check` vert (351 tests) ; `device-test`, `release-test`, `repo-audit` verts.
THOR: release signée `app.thorai` installée à côté de `app.regie`, ouverte sur la bienvenue. Pont redémarré une fois (`run-command.sh stop/start`) : il tournait depuis avant le dépôt du code d'ADR 22 et n'aurait pas lu le jeton de ThorAI. Une session Codex d’essai créée sur un projet existant, sans consigne, au repos. Aucune permission accordée, aucun WAV ni extra de debug laissé actif, console non redémarrée.
REVIEW: revue indépendante (sous-agent) : 1 bloquant (« Plus tard » muet, parcours sans sortie), 1 important (carte volante qui détournait A/B), 5 mineurs. Tous corrigés avec tests, sauf le chemin d'association affiché (données d'Android, gardé).
DÉCISIONS PRISES: ADR 23. Debian prouvée par le pont ; association prouvée par WRITE_SECURE_SETTINGS ; seules les étapes facultatives se passent, plus le micro ou les notifications refusés pour de bon et un téléchargement en échec, jamais le pont. Options développeur ouvertes en haut, Termux et Accessibilité en bas. Commandes d'association qui demandent port et code. Extra debug `setup_step` pour les captures.
POUR L'UTILISATEUR: dans ThorAI ouverte, A sur chaque étape : autoriser les commandes Termux (le pont passe au vert), le micro, télécharger le modèle, les notifications ; activer « ThorAI, mode console » dans Accessibilité ; refaire l'accès à Android (Y copie les commandes, coller dans Termux, saisir port et code) ; puis A « Créer une session ».
QUESTIONS: aucune.
CAPTURES: `artifacts/screenshots/28/` : `01`–`09` étapes, `10`–`12` fiche et première session Codex, `13`–`15` release.
HORS PÉRIMÈTRE: `am start -p` du ticket 50 ne résout pas un lanceur (CATEGORY_DEFAULT) ; `thor-install.sh` et `emu-install.sh` résolvent l'activité d'abord.
