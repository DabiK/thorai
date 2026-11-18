# CLAUDE.md

## Projet

Régie (nom de travail) est une app Android pour l'AYN Thor, une console Android à deux écrans. Elle sert de poste de pilotage pour Claude Code et Codex, qui tournent déjà sur la console (dans une Debian sous Termux). Elle se commande à la voix et à la manette.

L'app ne refait pas le travail des agents. Elle :
- lance des sessions ;
- montre leur avancement sur les deux écrans ;
- relaie les demandes de validation ;
- transmet ce que l'utilisateur dit.

**Ce qu'on valorise : l'expérience de l'app.** Pas d'ingénierie de harness. On réutilise ce qui existe (adaptateurs, SDK, serveurs MCP, modèles de voix), quitte à l'adapter, et on note le choix dans un ADR.

## Sources de vérité

| Quoi | Où |
| --- | --- |
| Produit : promesse, principes, parcours, fonctionnalités | `docs/PRD.md` |
| Maquette des 6 écrans (référence visuelle) | https://example.invalid/regie-maquette, lisible avec l'outil Artifact (action `read`, `path` `project/<Écran>.dc.html` : `Main`, `Dictee`, `Session`, `Validation`, `Diff`, `Lancer`). Antérieure aux tickets 22 à 26 : accueil en liste, réglages et dictée en bas, fil brut (ADR 17) priment |
| Fiche de la console : écrans, manette, Termux, agents installés | `docs/thor.md` |
| Installation sur une Thor neuve | `docs/installation.md` |
| Développement : portes, banc, captures | `docs/developpement.md` |
| Textes de l'interface | `docs/textes.md` |
| Tickets, avec leurs bloquants et leur statut | `.scratch/regie-v1/issues/` |
| Décisions techniques | `docs/adr/` |
| Avancement, questions en attente, comment reprendre | `docs/adr/00-journal-de-reprise.md` |

Lire la section concernée du PRD avant d'écrire du code. Ne pas inventer une règle produit : si le PRD ne tranche pas, poser la question à l'utilisateur.

## Structure

```
android/   app Android (Kotlin, Jetpack Compose) : domaine, passerelle vers le pont, interface
voice/     la voix, dans un build Gradle séparé, inclus par android/ en composite build
bridge/    pont Node/TypeScript qui tourne dans la Debian de la Thor et pilote Claude et Codex
thor/      scripts et réglages poussés sur la console (démarrage, accès des agents à Android)
scripts/   déploiement, installation et captures d'écran depuis le Mac
artifacts/ captures d'écran des écrans livrés, rangées par ticket
```

## Architecture hexagonale

- **Domaine** (`android/domain`), en Kotlin pur sans dépendance Android :
  - le modèle : `Session`, `Agent`, `Approval`, `Overview` ;
  - les ports, par exemple `AgentGateway` ;
  - les cas d'usage.
- **Ports de la voix** : dans `voice/api`, en Kotlin pur : transcription en flux, synthèse, détection de parole, vocabulaire à favoriser.
- **Adapters** :
  - la passerelle vers le pont, et une passerelle factice ;
  - la voix ElevenLabs, la voix locale gratuite, et une voix factice.
  
  Un adapter n'importe pas un autre adapter.
- **Composition** : `android/app` est la seule racine de composition. L'interface ne parle qu'au domaine.
- **Tests** : chaque port a un adapter factice. Les tests unitaires n'appellent jamais un vrai service, ni la console.
- **Changer de fournisseur** (transcription ou synthèse) se fait par un réglage, sans toucher au reste.

## La console

Tout ce qu'il faut est dans `docs/thor.md`. En résumé :
- adb, numéro de série `THOR0000000001` ;
- écran haut : `displayId` 0 ; écran bas : `displayId` 4 sur la Thor, découvert dynamiquement sur le banc ;
- Claude Code et Codex tournent dans une Debian (proot-distro, utilisateur `dev`).

Toutes les sessions partagent **une seule console** :
- n'installer que l'APK de son ticket, ne pas désinstaller ceux des autres ;
- ne pas modifier la configuration des agents en dehors de son ticket ;
- ne pas redémarrer la console sans prévenir l'utilisateur ;
- ne jamais écrire de clé d'API ni de mot de passe dans un champ ou dans le dépôt : c'est l'utilisateur qui les saisit dans l'app.

## Comment traiter un ticket

0. Travailler dans un worktree, sur une branche `ticket/<NN>-<slug>`, jamais directement sur `main`.
1. Lire le ticket, la section du PRD, les ADR cités et, pour l'interface, l'écran de la maquette.
2. Chercher d'abord ce qui existe déjà. Pour une API de bibliothèque, vérifier la documentation à jour (MCP `context7` ou le web) plutôt que de répondre de mémoire. Noter le choix dans un ADR.
3. Implémenter par petites étapes, avec les tests à côté.
4. Passer la porte de qualité de la partie touchée :
   - `android/` ou `voice/` : `./gradlew check`
   - `bridge/` : `npm run check`
5. Relire le diff contre le ticket et les consignes. Par défaut, utiliser une revue indépendante ; si la mission impose le solo, effectuer cette revue soi-même, sans sous-agent.
6. Tester sur la Thor. Joindre les captures des deux écrans dans `artifacts/screenshots/<NN>/`.
7. Faire les commits (Conventional Commits) sur la branche du ticket.
8. Mettre le statut du ticket à `à fusionner`, remplir sa section « Rapport » et mettre à jour le journal de reprise.
9. **Ne pas fusionner, ne pas rebaser sur `main` et ne pas pousser** : c'est l'orchestrateur qui fusionne.

Si une vérification exige l’utilisateur (permission, association, clé, vraie voix, redémarrage), faire tout le reste et écrire son pas à pas dans le rapport. Sans Thor, utiliser le banc de `docs/emulateur.md`. Quand tout ce qui ne dépend pas de lui est vert, passer à `à fusionner`, en laissant les critères non prouvés décochés. Réserver `bloqué` aux décisions produit manquantes et aux obstacles non contournables. Pour les essais réels, utiliser Codex ; ne pas consommer le quota Claude sans instruction explicite.

## Vocabulaire

Documentation en français, code en anglais.

| PRD | Code |
| --- | --- |
| Session | `Session` |
| Vue d'ensemble (la régie) | `Overview` |
| Validation | `Approval` |
| Dictée | `Dictation` |
| Ordre vocal | `VoiceCommand` |
| Point écrit (retiré du produit, ADR 17) | `WrittenSummary` (détection de question), `SessionSummary` (faits de session) |
| Pont | `Bridge` |

## Outils sur le Mac

- **SDK Android** : `/opt/homebrew/share/android-commandlinetools` (plateformes 34 à 36, build-tools 35 et 36, NDK 27 et 28). `sdk.dir` est à mettre dans `local.properties`, qui n'est pas versionné.
- **JDK** :
  - Temurin 25 par défaut ;
  - `openjdk@21` de Homebrew (`/opt/homebrew/opt/openjdk@21`) si la version de Gradle ou d'AGP refuse le 25.
- **Gradle** : installé par Homebrew. Chaque build Android a son wrapper versionné.
- **`gh`** : connecté au compte propriétaire du dépôt GitHub privé.
