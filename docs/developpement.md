# Développer ThorAI

Cette page s’adresse à qui veut modifier ThorAI. Pour l’installer, voir la [notice d’installation](installation.md).

## Organisation du dépôt

```
android/   l’app Android (Kotlin, Jetpack Compose) : domaine, passerelle vers le pont, interface
android/game/ la vue jeu (ADR 24) : moteur du hameau en Kotlin pur, écrans pixel en Compose
voice/     la voix, dans un build Gradle séparé, inclus par android/ en composite build
bridge/    le pont Node/TypeScript qui tourne dans la Debian et pilote Claude Code et Codex
thor/      les scripts poussés sur la console : pont, accès des agents à Android
scripts/   déploiement, banc d’essai, captures et contrôles, lancés depuis le Mac
docs/      PRD, ADR, protocole du pont, fiche de la console, règles de texte
artifacts/ captures et preuves d’essai, rangées par ticket
```

## Architecture

L’app suit une architecture hexagonale.

- `android/domain`, en Kotlin pur, porte les décisions de l’app : le modèle (`Session`, `Agent`, `Approval`, `Overview`), les ports et les cas d’usage.
- `voice/api` définit les ports de la voix : transcription en flux, synthèse, détection de parole, termes à favoriser. Les fournisseurs (local, ElevenLabs, factice) vivent dans `voice/`.
- Les adapters traduisent le réseau et les accès Android ; un adapter n’en importe pas un autre. `android/app` est la seule racine de composition.
- L’interface est celle du jeu (`android/game/ui`, ADR 24) : `android/app` ne fait que brancher l’état et les rappels existants sur ses écrans (`ui/game/`). Il n’y a plus d’écran classique depuis le ticket 51.
- `bridge/src/domain` décide du cycle des sessions et des validations, derrière des ports ACP, fichiers, horloge et shell.

Le [protocole du pont](protocole-pont.md) décrit le contrat WebSocket entre l’app et le pont. Les [ADR](adr/README.md) expliquent chaque choix, et le [PRD](PRD.md) fixe le produit.

## Portes de qualité

Prérequis sur le Mac : JDK 21 ou 25, SDK Android (plateforme 36), Node 24 ou plus, Python 3, `rg`. `sdk.dir` va dans `android/local.properties` et `voice/local.properties`, non versionnés.

```sh
(cd android && ./gradlew check)
(cd voice && ./gradlew check)
(cd bridge && npm ci && npm run check)
python3 scripts/emu-test.py
python3 scripts/repo-audit-test.py
python3 scripts/repo-audit.py
```

Les tests utilisent des faux, des fichiers temporaires et des serveurs locaux. Ils ne lancent aucun vrai agent et ne contactent pas la console. Les contrats réels du pont (`npm run test:contract`) sont séparés : ils consomment les comptes des agents.

Les scripts de `thor/computer-use` ont leurs propres tests, sans console :

```sh
bash thor/computer-use/test-consignes.sh
bash thor/computer-use/test-block.sh
bash thor/computer-use/test-permissions.sh
bash thor/computer-use/test-pair.sh
```

## Essayer sans console, sur le banc

Sur un Mac Apple Silicon, un émulateur Android 16 à deux écrans, aux dimensions de la Thor, fait tourner l’app avec un pont local et un vrai Codex. Il faut l’image `system-images;android-36;google_apis_playstore;arm64-v8a`, Python avec Pillow et Codex connecté sur le Mac.

```sh
scripts/emu-start.sh
scripts/bridge-mac.sh
scripts/emu-install.sh
scripts/emu-key.sh A
scripts/emu-shot.sh artifacts/screenshots/<ticket>
scripts/bridge-mac.sh stop
adb -s emulator-5580 emu kill
```

`emu-install.sh` lance une vraie démo **Codex**, jamais Claude ; `--no-demo` ne fait qu’actualiser l’app. Le [guide du banc](emulateur.md) détaille les variables, la voix simulée et les limites. Le banc ne prouve ni `RUN_COMMAND`, ni les axes physiques de la manette, ni l’association sans fil ([ADR 14](adr/14-banc-emulateur.md)).

## Essayer sur la Thor

```sh
export THOR_SERIAL=XXXXXXXX  # série adb de la console
scripts/thor-install.sh
scripts/thor-shot.sh artifacts/screenshots/<ticket>
```

`thor-install.sh` construit et installe `app.thorai` seulement, puis l’ouvre. En debug, `--es setup_step VOICE` (ou une autre étape de `SetupStep`) ouvre le parcours d’accueil sur cette étape, pour la capturer sans accorder de permission à la place de l’utilisateur. `thor-shot.sh` capture les deux écrans. Après un changement du pont : `thor/bridge/install.sh`. La [fiche de la console](thor.md) donne les écrans, la manette et l’environnement Termux.

## Captures des écrans du jeu

Les écrans du jeu se capturent sans console, par Robolectric (graphismes natifs, vrais atlas, tailles des deux écrans de la Thor). Sans propriété, les tests n’écrivent rien ; avec `-PgameCaptures`, ils rangent leurs images dans le dossier donné :

```sh
cd android
./gradlew :game-ui:testDebugUnitTest --tests 'app.regie.game.ui.FinalScreensTest' --rerun -PgameCaptures=$PWD/../docs/images/jeu
```

`FinalScreensTest` donne les écrans finals (`docs/images/jeu/51-*.png` : titre, hameau, maison, validation, modifications, bâtir, menu de pause, courrier, console) ; les autres captures `4N-*.png` viennent des tests de chaque lot. Les deux activités sont en plein écran : comme sur la Thor, les captures ne montrent aucune barre système.

## Façon de travailler

- Le travail est découpé en [tickets](../.scratch/regie-v1/issues), un par branche et par worktree, tests d’abord. [`CLAUDE.md`](../CLAUDE.md) donne les consignes, valables pour tous les agents.
- Le [journal de reprise](adr/00-journal-de-reprise.md) dit où en est le projet et ce qui reste à vérifier avec la console.
- Les commits suivent Conventional Commits. Les preuves d’essai vont dans [`artifacts/screenshots/<ticket>/`](../artifacts/README.md).
- Les textes de l’interface suivent les [règles de texte](textes.md) : tutoiement, phrases courtes, pas de jargon.

## Avant de partager

`python3 scripts/repo-audit.py` cherche avec `rg`, dans le texte versionné et les nouveaux fichiers, les clés usuelles, jetons littéraux, IP privées, adresses e-mail et chemins de comptes. Ses exceptions sont explicites et testées. Il ne parcourt ni l’historique Git ni les images : les captures publiées sont relues à l’œil.

## Préparer une release

Après les portes de qualité et la relecture :

```sh
scripts/check-sharing.sh
scripts/release.sh 0.1.0 --build-only
```

Le premier build crée si nécessaire la clé dans `~/.config/regie/`, avec
`release.properties` et un keystore en 0600. Sauvegarder les deux hors du dépôt.
L'APK signé et `regie-<version>.apk.sha256` sont dans
`android/app/build/outputs/apk/release/` (ignoré par Git).
`./gradlew :app:assembleRelease` utilise ensuite cette même configuration.
Un checkout sans configuration conserve ses tests/debug utilisables ; le build
release exige la clé et ne produit pas une fausse release signée en debug.

Après fusion et autorisation de publication, l'orchestrateur lance
`scripts/release.sh <version>` : tag `v<version>` sur le commit courant, APK et
somme joints à GitHub, notes prises dans le README. Ce mode publie réellement.
Les versions sont `major.minor.patch`, composantes 0 à 999 (ADR 20).
Une installation debug avec une autre signature doit être désinstallée avant
la release : préférences et modèles de l'app perdus, Termux conservé.

### Essai opencode avec les identifiants du Mac

`scripts/bridge-mac.sh --device` utilise l'appareil adb unique (ou `THOR_SERIAL`),
au lieu d'exiger l'émulateur. Choisir un `REGIE_BENCH_DIR` isolé ; le pont garde ses
projets et son journal sur le Mac. Un `adb reverse tcp:47600 tcp:47600` relie l'app
physique au pont Mac. Le pont Debian occupant déjà ce port doit être arrêté avant
l'essai, seulement après vérification qu'aucune session ne travaille. Conserver
le jeton de l'app sans l'afficher, puis retirer le reverse et remettre le pont
Debian à la fin. Ne jamais transférer de credentials opencode. La configuration
native du Mac est utilisée directement par son exécutable `opencode acp`.
