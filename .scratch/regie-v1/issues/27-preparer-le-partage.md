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
- [x] Sur la Thor (captures 04 puis 09 après correction) : l'APK de release s'installe à la place du build debug, après désinstallation si les signatures diffèrent (préviens-le dans le rapport). Le jeton s'établit par RUN_COMMAND, et Régie affiche les sessions.
- [x] Deux configurations successives, jeton différent ou identique, ne restent pas bloquées par les processus détachés → test avec verrou noyau sur Mac et Debian, mutations des deux lancements détectées, essai release Thor.


## Rapport

STATUT: done
RÉSUMÉ: Distribution MIT, installeur Termux et APK signé préparés ; aucune publication exécutée.
Le verrou hérité est corrigé : la release Thor reconfigure son jeton et affiche les 49 sessions après plusieurs ouvertures.
CRITÈRES:
- [x] Appareil adb unique ou THOR_SERIAL → scripts/tests/device-test.sh et installation debug Thor.
- [x] Connexion Claude avec entrée Debian et shell de connexion → test authentication, commandes complètes dans l'app.
- [x] Projets neutres, MIT au nom de l'auteur des commits → diff, LICENSE, audit rg sans secret.
- [x] Jeton release sans run-as, 0600, sans journalisation ; debug conservé → tests et essai RUN_COMMAND Thor, captures 04/09.
- [x] Configuration répétable avec pont actif → configure-lock-test.py rouge puis vert, vrais verrous Mac/Debian ; deux mutations détectées.
- [x] Installeur local idempotent → tests hors console et trois exécutions Termux sur la console préparée.
- [x] APK signé, SHA-256 et publication préparée → release.sh --build-only, apksigner verify ; GitHub remplacé par un faux dans les tests.
- [x] Dernier APK signé réinstallé physiquement → adb install -r réussi, jeton différent réparé par l'app, réouvertures successives, capture 09.
- [ ] Parcours complet sur Thor vierge → aucun effacement de Termux ; association et comptes à éprouver avec l'utilisateur.
PORTE DE QUALITÉ: Android ./gradlew check + assembleRelease, voix ./gradlew check, pont npm run check (320 tests) verts ; signature v2 vérifiée ; check-sharing et 13 tests émulateur verts ; audit rg 0 signalement.
THOR: dernier APK release installé sans désinstallation, données conservées. Le debug est refusé (INSTALL_FAILED_UPDATE_INCOMPATIBLE) ; aucun contournement destructif. Ancien superviseur au repos arrêté pour libérer son verrou ; jeton différent réparé par RUN_COMMAND, réouvertures sans blocage, 0600, 49 sessions conservées, pont joignable. Aucun agent lancé ni redémarrage console. La désinstallation debug→release du passage précédent avait réinitialisé préférences/modèles.
REVIEW: blocage reproduit ; fd 8 fermé pour le superviseur et l'accès Android. Régression avec verrou noyau, dont exec à jeton identique ; nettoyage du banc synchronisé après essai Debian. Après d99fdee : espaces des traces nettoyés et preuve release distinguée du test à jeton identique ; aucun autre blocage. Détail dans revision-lock.md.
DÉCISIONS PRISES: aucun choix produit nouveau ; ADR 06 et protocole précisent la durée du verrou et sa fermeture dans les processus détachés ; ADR 18/20 conservés.
POUR L'UTILISATEUR: 1. Réactiver Mode console dans Accessibilité. 2. Reconfigurer la voix et télécharger son modèle. 3. Suivre les étapes 3 à 6 de artifacts/screenshots/27/essai-thor.md sur une Thor vierge. 4. Sauvegarder la clé externe avant publication.
QUESTIONS: aucune décision produit en attente.
CAPTURES: artifacts/screenshots/27/09-lock-repaired-{haut,bas}.png ; preuves et relecture dans revision-lock.md ; campagnes précédentes dans essai-thor.md et relecture.md.
HORS PÉRIMÈTRE: deux avertissements Biome préexistants dans bridge.test.ts ; aucun problème fonctionnel hors périmètre traité.
