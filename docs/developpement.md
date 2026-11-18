# Développer Régie

Cette page s’adresse à qui veut modifier Régie. Pour l’installer, voir la [notice d’installation](installation.md).

## Organisation du dépôt

```
android/   l’app Android (Kotlin, Jetpack Compose) : domaine, passerelle vers le pont, interface
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

`thor-install.sh` construit et installe `app.regie` seulement, puis l’ouvre. `thor-shot.sh` capture les deux écrans. Après un changement du pont : `thor/bridge/install.sh`. La [fiche de la console](thor.md) donne les écrans, la manette et l’environnement Termux.

## Façon de travailler

- Le travail est découpé en [tickets](../.scratch/regie-v1/issues), un par branche et par worktree, tests d’abord. [`CLAUDE.md`](../CLAUDE.md) donne les consignes, valables pour tous les agents.
- Le [journal de reprise](adr/00-journal-de-reprise.md) dit où en est le projet et ce qui reste à vérifier avec la console.
- Les commits suivent Conventional Commits. Les preuves d’essai vont dans [`artifacts/screenshots/<ticket>/`](../artifacts/README.md).
- Les textes de l’interface suivent les [règles de texte](textes.md) : tutoiement, phrases courtes, pas de jargon.

## Avant de partager

`python3 scripts/repo-audit.py` cherche avec `rg`, dans le texte versionné et les nouveaux fichiers, les clés usuelles, jetons littéraux, IP privées, adresses e-mail et chemins de comptes. Ses exceptions sont explicites et testées. Il ne parcourt ni l’historique Git ni les images : les captures publiées sont relues à l’œil.
