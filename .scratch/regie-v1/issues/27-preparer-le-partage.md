# 27 — Préparer le partage : rien de propre à l'utilisateur, APK de release, installation sans Mac

Statut : à fusionner
Possède : `scripts/`, `thor/`, `bridge/`, `android/` (build, jeton), `LICENSE`

## Contexte

L'utilisateur veut publier Régie : un APK disponible en release GitHub, un projet qu'un inconnu peut installer. Constats de la revue de doc du 18 novembre, plus ce qu'implique une release.

## À livrer

1. **Plus de console par défaut.** Les scripts n'ont plus `THOR0000000001` comme valeur par défaut de `THOR_SERIAL`. Ils prennent l'unique appareil adb branché, ou demandent `THOR_SERIAL` s'il y en a plusieurs. La notice et la doc suivent.
2. **Le message de connexion de Claude** dans l'app fonctionne sur une Thor neuve. Il ne suppose plus de lanceur `claude` dans Termux : il donne les vraies étapes (entrer dans la Debian, puis lancer la connexion).
3. **Noms neutres.** Remplacer les noms des projets privés de l'utilisateur (`atelier`, `atelier-meubles`, et tout autre nom personnel) dans le code, les tests, les données de démo et la doc, par des noms neutres (`atelier`, `meteo`, `notes`…). Ne pas toucher aux captures déjà versionnées.
4. **Licence MIT** : `LICENSE` à la racine, au nom de l'auteur des commits du dépôt, année 2026, et une mention dans le README.
5. **Jeton du pont sans `run-as`.** Une app de release n'est pas débuggable, et `install.sh token-app` ne marche plus.
   - L'app génère un jeton aléatoire et le transmet au pont par RUN_COMMAND, à une commande de configuration côté Termux. Celle-ci l'enregistre dans `~/.config/regie/bridge-token` (droits 0600) et redémarre le pont.
   - Aucun jeton dans les journaux.
   - Le chemin debug actuel continue de marcher.
   - Mettre à jour le protocole et l'ADR concerné.
6. **Installation sans Mac.** Un installeur à lancer dans Termux sur la console (`thor/install-on-device.sh`) prépare la Debian, installe Claude Code, Codex, le pont et l'accès à Android (`thor/computer-use`), comme le font aujourd'hui les scripts lancés depuis le Mac. L'association du débogage sans fil reste un geste de l'utilisateur, guidé. Il est idempotent. Les scripts lancés depuis le Mac restent valables.
7. **Build de release signé.**
   - `android/app` produit un APK de release signé, avec la clé lue hors du dépôt (`~/.config/regie/release.properties` et le keystore indiqué dedans).
   - Si la clé n'existe pas, `scripts/release.sh` la crée hors du dépôt, en le disant.
   - `scripts/release.sh <version>` construit l'APK, calcule son SHA-256, et crée la release GitHub avec `gh release create` : tag `v<version>`, APK et somme en pièces jointes, notes tirées du README. Ne lance pas la publication toi-même : l'orchestrateur le fera.
   - Aucune clé, aucun mot de passe dans le dépôt : vérifier avec `rg` et `git status`.

## Critères d'acceptation

- [x] `./gradlew check` et `npm run check` sont verts. `./gradlew :app:assembleRelease` produit un APK signé (vérifié avec `apksigner verify`).
- [x] Sur la Thor (release éprouvée avant disparition USB, captures 04) : l'APK de release s'installe à la place du build debug, après désinstallation si les signatures diffèrent (préviens-le dans le rapport). Le jeton s'établit par RUN_COMMAND, et Régie affiche les sessions.


## Rapport

STATUT: done
RÉSUMÉ: Distribution MIT, installeur local Termux et APK signé préparés ; aucune publication exécutée.
La release éprouvée sur Thor établit son jeton par RUN_COMMAND et affiche les sessions réelles.
CRITÈRES:
- [x] Appareil adb unique ou THOR_SERIAL → scripts/tests/device-test.sh et installation debug Thor.
- [x] Connexion Claude avec entrée Debian et shell de connexion → test authentication, commandes complètes dans l'app.
- [x] Projets neutres, MIT au nom de l'auteur des commits → diff, LICENSE, audit rg sans secret.
- [x] Jeton release sans run-as, 0600, sans journalisation ; debug conservé → tests de domaine/adapters/scripts, preuve Thor et capture 04.
- [x] Installeur local idempotent → tests hors console et trois exécutions Termux sur la console préparée.
- [x] APK signé, SHA-256 et publication préparée → release.sh --build-only, apksigner verify ; GitHub remplacé par un faux dans les tests.
- [ ] Dernier APK physiquement réinstallé après extraction du contrôle d'UID → Thor disparue d'adb ; la release précédente éprouvée reste installée.
- [ ] Parcours complet sur Thor vierge → aucun effacement de Termux ; association et comptes à éprouver avec l'utilisateur.
PORTE DE QUALITÉ: Android 441 tests + lint, voix 127 + lint, pont 320 + typage/lint ; check-sharing et 13 tests scripts émulateur verts.
THOR: debug puis release signée ; désinstallation app.regie nécessaire et effectuée, préférences/modèles réinitialisés, permissions antérieures restituées ; jeton changé, 0600, 49 sessions retrouvées, aucun agent lancé. Après disparition USB, dernier code debug installé sur émulateur deux écrans avec pont Mac, connecté sans démo.
REVIEW: lecture du ticket et des frontières ; transfert par provider retiré après refus Android, contrôle UID testé par mutation ; chemin Claude rendu indépendant du PATH préexistant.
DÉCISIONS PRISES: socket Unix éphémère et contrôle UID pour éviter les logs de Termux ; scripts partagés entre Mac et local ; versionCode déterministe, clé externe sauvegardable ; ADR 06/18/20.
POUR L'UTILISATEUR: 1. Réactiver Mode console dans Accessibilité. 2. Reconfigurer la voix et télécharger le modèle local. 3. Réinstaller le dernier APK à reconnexion USB. 4. Suivre le pas à pas Thor vierge dans artifacts/screenshots/27/essai-thor.md. 5. Sauvegarder la clé externe avant publication.
QUESTIONS: aucune décision produit en attente.
CAPTURES: artifacts/screenshots/27/04-release-visible-{haut,bas}.png (Thor), 07-emulator-ready-{haut,bas}.png ; preuves et vérifications dans essai-thor.md.
HORS PÉRIMÈTRE: deux avertissements Biome préexistants dans bridge.test.ts ; aucun problème fonctionnel hors périmètre traité.
