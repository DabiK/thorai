# 50 — Cohabitation sur la Thor

Statut : fusionné dans ticket/40-vue-jeu
Possède : `android/app/build.gradle.kts` (identifiant), la gestion du jeton dans `bridge/` si nécessaire
Dépend de : —

## Objectif

L'utilisateur garde l'app classique sur la Thor. La version jeu s'installe à côté, sous son propre applicationId (suffixe `.jeu`). Depuis le ticket 27, l'app génère le jeton du pont et le lui envoie par RUN_COMMAND. Il faut vérifier si le pont ne garde qu'un seul jeton. Dans ce cas, deux apps se déconnecteraient l'une l'autre, et le pont doit accepter plusieurs jetons.

Référence : `docs/rebrand/plan-dev-jeu.md` (architecture, contrat des assets, règles de lot), `docs/rebrand/da-jeu.md`, et les écrans du canvas https://claude.ai/artifact/M6dsMX8171h3WhpyDvq6Zh (page 3).

## Critères d'acceptation

- [ ] Les deux apps sont installées et connectées au même pont, sans se gêner → à vérifier sur la Thor par l'orchestrateur (pas d'installation dans ce lot) ; prouvé hors console par les tests ci-dessous.
- [x] Tests du pont → `token-files.test.ts`, `ws-server.test.ts`, `tests/main.test.ts`, `configure-token-test.sh`, `configure-lock-test.py`.
- [x] `npm run check` et `./gradlew check` verts.

## Rapport

STATUT : à fusionner. Rien d'installé sur la Thor, pont ni redémarré ni déployé.
`ticket/40-vue-jeu` (58bf8a1, avec le ticket 31) est fusionnée dans la branche ;
seul conflit, l'en-tête de `scripts/thor-install.sh` (`--release` gardé).

### Constat

Le pont ne gardait **qu'un seul jeton** (`~/.config/regie/bridge-token`, lu au
démarrage). `regie-bridge-configure` le remplaçait et redémarrait le pont dès
qu'il différait. Deux apps se seraient donc remplacé leur jeton l'une l'autre,
avec un redémarrage du pont à chaque fois (sessions interrompues).

### Ce qui est fait (ADR 22)

- **Identité** : `applicationIdSuffix = ".jeu"` sur tous les types de build
  (`app.regie.jeu`, `app.thorai.jeu` après le ticket 30). Nom « ThorAI jeu » et
  « ThorAI jeu, mode console » par `android/app/src/jeu/res`, ajouté aux sources
  debug et release. L'affinité de tâche de l'écran du bas, écrite en dur
  (`app.regie.bottom`), suit maintenant l'identifiant : sinon l'écran du bas
  d'une app pouvait rejoindre la tâche de l'autre. Le manifeste fusionné
  (debug et release) ne contient plus que des noms en `app.regie.jeu`
  (autorité androidx-startup, permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` :
  pas de conflit `INSTALL_FAILED_DUPLICATE_PERMISSION`). Canaux de
  notification, préférences, fichiers, socket `regie-pair-<UUID>` : propres à
  chaque app, rien à changer.
- **Pont multi-jetons** : l'app envoie son paquet en second argument de
  `regie-bridge-configure`, qui écrit `bridge-tokens/<paquet>` (0600, dossier
  0700) **sans arrêter le pont**. Le pont relit le fichier historique et
  `bridge-tokens/` à chaque connexion, compare à temps constant contre chacun,
  refuse les fichiers lisibles par d'autres, trop courts ou liens, et ne
  journalise que des noms de fichiers.
- **Compatibilité** : l'app classique installée (un seul argument) garde
  exactement le comportement du ticket 27 sur `bridge-token`. Le fichier
  historique reste accepté.
- **Scripts** : `regie_app_id` (dans `scripts/device.sh`) lit l'identifiant et
  le suffixe dans `build.gradle.kts` (`REGIE_APP_ID` pour viser l'app
  classique). `thor-install.sh`, `emu-install.sh` et `thor/bridge/lib.sh`
  l'utilisent et lancent l'app par son lanceur (`am start -p`).
- **Tests de scripts** : sous le bash 3.2 de macOS, un `[[ ]]` faux n'arrête
  pas `set -e`, et `! grep` jamais. Les assertions de
  `configure-token-test.sh` et `device-test.sh` finissent maintenant par
  `|| exit 1`. Une mutation de `configure.sh` (arrêt du pont pour un jeton
  d'app) fait bien échouer le test.

### Pas à pas sur la Thor (orchestrateur, puis utilisateur)

1. Déployer le pont de cette branche : `thor/bridge/install.sh`. Il installe le
   nouveau `regie-bridge-configure`, sans redémarrer le pont.
2. Redémarrer le pont une fois, à un moment sans session en cours, pour charger
   le code multi-jetons : « Quitter » dans l'app classique, puis la rouvrir.
   L'app classique se reconnecte avec son jeton historique.
3. Installer la version jeu : `scripts/thor-install.sh` (APK debug) ou
   `scripts/thor-install.sh --release` (signature release existante). Les deux
   donnent `app.regie.jeu`. L'app classique, une release `app.regie`, n'est ni
   remplacée ni désinstallée.
4. Ouvrir « ThorAI jeu » et accepter :
   - « Exécuter des commandes dans l'environnement Termux » (RUN_COMMAND).
     `allow-external-apps` est déjà actif dans Termux ;
   - les notifications ;
   - le micro, à la première dictée.
   L'app crée son jeton et l'envoie au pont : elle doit afficher les mêmes
   sessions que l'app classique, qui reste connectée.
5. Facultatif, pour rallumer le débogage sans fil :
   `adb shell pm grant app.regie.jeu android.permission.WRITE_SECURE_SETTINGS`.
6. Mode console : dans Accessibilité, « ThorAI jeu, mode console ». N'activer
   qu'**un** des deux modes console à la fois, sinon les deux lisent L2 et R2.
   Pas de permission de superposition à donner : la carte volante est posée
   par le service d'accessibilité.
7. Batterie : rien de plus. Termux est déjà hors optimisation.
8. Voix : à régler de nouveau dans la version jeu (clé saisie par
   l'utilisateur, modèle local à retélécharger). Les préférences ne sont pas
   partagées entre les apps.

Vérification attendue : les deux apps ouvertes l'une après l'autre restent
connectées. `ls -l ~/.config/regie/bridge-tokens/` dans la Debian montre
`app.regie.jeu` en `-rw-------`, et `bridge-token` est inchangé.

### Risques et écarts

- **Le pont est commun, la branche du jeu n'est jamais fusionnée dans main.**
  Si le pont de main (un seul jeton) est redéployé sans ce changement, la
  version jeu ne se connecte plus (401), sans gêner l'app classique. Reporter
  le commit du pont sur main (`bridge/`, `thor/bridge/configure.sh`).
- Premier appairage de la version jeu : l'app peut se connecter avant que
  `regie-bridge-configure` ait écrit son jeton, et recevoir un bref `401`. Le
  réessai de l'app (configuration redemandée au plus toutes les 30 s, puis
  reconnexion) l'absorbe ; aucune action de l'utilisateur (relecture, point 3).
- « Quitter » dans l'une des apps arrête le pont et toutes les sessions pour
  les deux : c'est le comportement produit actuel, non changé ici.
- La règle des agents `thor-screen launch bas app.regie` et les consignes de
  `thor/computer-use` nomment encore l'app classique. Ce sont des réglages des
  agents, non touchés : lancer la version jeu sur l'écran du bas demandera une
  validation.
- `thor/install-on-device.sh` et `docs/installation.md` décrivent la release
  de main (`app.regie`) : non changés.
- Le nom `app_name` sert aussi de titre à l'accueil classique : il y affiche
  « ThorAI jeu », ce qui aide à distinguer les deux apps.
- Journal de reprise non mis à jour : le plan des lots demande le rapport dans
  le ticket et évite les conflits avec main.
- `scripts/repo-audit.py` signale `docs/rebrand/plan-dev-jeu.md:121`, un
  signalement antérieur à ce lot : la règle elle-même cite `/home/…`.

### Suites de la relecture (e30b893)

- Un fichier de jeton refusé n'est plus signalé à chaque connexion : une fois
  par fichier et par problème, de nouveau si le problème change ou revient
  après réparation (test `reports a refused file once per state`).
- `scripts/bridge-mac.sh` donne au pont du banc son propre dossier,
  `$REGIE_BENCH/bridge-tokens` : il n'accepte plus les jetons du Mac.
- d9197a2 puis e30b893, rejoués sur main (`git merge-tree`, sans conflit) :
  `npm run check` (351 tests), `configure-token-test.sh` et
  `configure-lock-test.py` verts, sans aucun fichier du jeu.

### Porte de qualité

- `bridge/` : `npm run check` vert (Biome : 2 avertissements antérieurs dans
  `bridge.test.ts` ; tsc ; 351 tests vitest, après fusion de
  `ticket/40-vue-jeu` qui apporte le ticket 31).
- `android/` : `./gradlew check` vert, avec `:app:assembleDebug` et le manifeste
  release contrôlés (`app.regie.jeu`).
- `scripts/check-sharing.sh` vert : tests de scripts, `configure-lock-test.py`
  avec de vrais verrous noyau.
