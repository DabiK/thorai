# 20 — Revue d'ensemble, homogénéisation et vitrine du projet

Statut : prêt
Bloqué par : 17, 18, 19
Possède : tout le dépôt (passe de nettoyage), `README.md`

## Objectif

L'utilisateur veut pouvoir poster et partager le projet. Le dépôt doit être propre et homogène, avec un README qui sert de vitrine.

## À livrer

- **Revue de tout le dépôt** (`android/`, `voice/`, `bridge/`, `thor/`, `scripts/`, `docs/`) :
  - code mort, doublons, noms incohérents, docs périmées ou contradictoires ;
  - ADR à renuméroter, tickets terminés, fichiers d'essai inutiles dans `artifacts/`.
  
  Corriger sans changer le comportement. Les portes de qualité restent vertes.
- **`README.md`, la vitrine**, en français :
  - ce qu'est Régie, en une phrase, avec une ou deux captures des deux écrans tirées d'`artifacts/screenshots/` ;
  - ce qu'on fait avec : sessions, voix, validations, diff, nouveau projet, agents qui pilotent Android ;
  - le matériel visé (AYN Thor) ;
  - **l'installation pas à pas** : Termux, Debian, Claude Code et Codex, pont, app, association sans fil, permissions ;
  - les commandes de la manette ;
  - l'architecture en un schéma ;
  - les limites connues ;
  - le développement : portes de qualité, émulateur, flow des tickets.
  
  Sans secret, sans donnée personnelle (clés, IP, e-mails, noms de projets privés).
- **Pousser `main`** sur le dépôt GitHub privé `DabiK/regie` : c'est l'orchestrateur qui le fait à la fusion.

## Critères d'acceptation

- `./gradlew check` et `npm run check` sont verts.
- Le README se lit seul. Une recherche de secrets (`rg` sur jetons, clés, IP privées, e-mails) ne trouve rien dans le dépôt.
