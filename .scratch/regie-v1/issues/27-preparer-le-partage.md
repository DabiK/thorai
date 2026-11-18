# 27 — Préparer le partage : rien de propre à l'utilisateur, APK de release, installation sans Mac

Statut : prêt
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

- `./gradlew check` et `npm run check` sont verts. `./gradlew :app:assembleRelease` produit un APK signé (vérifié avec `apksigner verify`).
- Sur la Thor : l'APK de release s'installe à la place du build debug, après désinstallation si les signatures diffèrent (préviens-le dans le rapport). Le jeton s'établit par RUN_COMMAND, et Régie affiche les sessions.
