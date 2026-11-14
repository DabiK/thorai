# 20 — Revue d'ensemble, homogénéisation et vitrine du projet

Statut : à fusionner
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

- [x] `./gradlew check` et `npm run check` sont verts : Android 351 tests, voix 127, pont 308 ; sorties dans `artifacts/screenshots/20/*-check.txt`.
- [x] Le README se lit seul : usages, installation complète, commandes, schéma, limites et développement ; deux captures réelles du build final inspectées.
- [x] Recherche `rg` sur clés, jetons, IP privées, e-mails et chemins personnels : zéro signalement réel dans le texte du worktree, avec exceptions fictives explicites et testées (`scripts/repo-audit.py`). Historique Git et pixels des anciennes captures hors de cette preuve.
- [x] Revue des six périmètres, numérotation ADR, statuts et nettoyage : `artifacts/screenshots/20/revue.md`.
- [x] Émulateur deux écrans avec pont Mac et vrai Codex ; arrêt vérifié.
- [ ] Vérification physique Thor et installation vierge : console absente ; pas à pas dans `artifacts/screenshots/20/essai-emulateur.txt`.


## Rapport

STATUT: done
RÉSUMÉ: README français autonome et illustré ; documentation consolidée, ADR renumérotés et preuves textuelles anonymisées.
Nettoyage limité sans changement de comportement ; audit rg reproductible et testé.
CRITÈRES:
- [x] Portes vertes → Android 351, voix 127, pont 308 tests ; sorties du ticket 20.
- [x] Vitrine complète → README et final-session-haut/bas.png inspectés.
- [x] Recherche de secrets → repo-audit.py, zéro signalement réel ; exceptions fictives documentées.
- [x] Revue du dépôt → revue.md, cas vide supprimé, nom vocal corrigé, ADR 18/19 devenus 15/16.
- [ ] Thor physique → absente ; essais différés explicitement sans bloquer la fusion.
PORTE DE QUALITÉ: ./gradlew check Android/voix et npm run check verts ; 13 tests émulateur, 5 tests audit et tests shell verts.
THOR: absente d’ADB. Banc deux écrans : vraie démo Codex, fil, options, un/deux écrans, Quitter ; agent/pont/app/AVD arrêtés.
REVIEW: contrôles avant commit effectués ; relecture du diff commité consignée dans la suite du rapport.
DÉCISIONS PRISES: numérotation continue des ADR, conservation des preuves utiles, anonymisation des chemins ; aucune décision produit ni nouvel ADR.
POUR L’UTILISATEUR: suivre les étapes 1–9 de artifacts/screenshots/20/essai-emulateur.txt (permissions, association, vrai micro, manette, notifications, extinction, redémarrage autorisé).
QUESTIONS: aucune question bloquante.
CAPTURES: artifacts/screenshots/20/final-session-{haut,bas}.png ; options-{haut,bas}.png ; single-{haut,bas}.png.
HORS PÉRIMÈTRE: installation vierge non rejouée, historique Git non purgé, anciennes captures non certifiées pour publication ; limites ACP/voix existantes documentées. Push de main réservé à l’orchestrateur.
