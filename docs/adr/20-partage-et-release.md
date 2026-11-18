# ADR 20 — Installation autonome et APK signé

Statut : accepté, 18 novembre 2026 (ticket 27, demande explicite de partage).

## Décision

- Licence MIT, © 2026 DabiK, auteur des commits du dépôt.
- Un appareil adb autorisé est choisi s'il est unique ; sinon `THOR_SERIAL` est
  obligatoire. Aucun numéro de console n'est une valeur par défaut.
- `thor/install-on-device.sh`, lancé depuis un checkout dans Termux, prépare
  Debian 13 et les versions déjà éprouvées de Node, Claude Code et Codex.
  Il réutilise les installeurs pont/computer-use avec un transport local
  (`REGIE_ON_DEVICE=1`), sans adb ni `run-as` pour la préparation. Les scripts USB
  gardent leur transport existant. L'association sans fil reste guidée et manuelle.
- La connexion du pont sans `run-as app.regie` est définie dans l'ADR 06.
- La signature utilise `~/.config/regie/release.properties` : `storeFile`
  (chemin absolu du keystore externe), `storePassword`, `keyAlias`, `keyPassword`.
  `REGIE_RELEASE_CONFIG` permet un autre emplacement externe, notamment en CI.
  Les fichiers sont privés (0600). Ne jamais les joindre aux artefacts.
- `scripts/release.sh <major.minor.patch>` crée une clé absente hors du dépôt,
  construit, vérifie avec apksigner, calcule SHA-256 et publie par
  `gh release create v<version>`, sur le SHA complet courant. Les notes viennent
  du README. `--build-only` s'arrête avant tout appel GitHub. Le ticket n'exécute
  que ce mode ; la publication reste à l'orchestrateur après fusion.
- Versions : trois composantes entre 0 et 999 ; versionCode =
  `major * 1 000 000 + minor * 1 000 + patch + 1`. Pas de suffixe de préversion.
  Une clé existante mais perdue n'est jamais remplacée automatiquement : la
  restaurer depuis sa sauvegarde pour préserver les mises à jour Android.

## Conséquences

Une signature release différente du debug exige de désinstaller l'ancien APK.
Les préférences/modèles privés de Régie sont alors perdus ; projets et comptes
Termux sont conservés. Sauvegarder la clé de release et sa configuration : ils
seront nécessaires pour toutes les mises à jour distribuées.

La préparation peut se rejouer sans réinstaller Debian ni les versions inchangées
et conserve les configurations des agents hors des réglages nécessaires à Régie.
Une console vierge et les gestes Android restent à vérifier avec l'utilisateur.

## Sources vérifiées

Context7 (`library` puis `docs`) le 18 novembre 2026 :
[signature Android](https://developer.android.com/studio/publish/app-signing),
[PRoot Distro](https://github.com/termux/proot-distro#readme),
[gh release create](https://cli.github.com/manual/gh_release_create).
