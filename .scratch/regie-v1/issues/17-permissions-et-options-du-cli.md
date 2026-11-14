# 17 — Tout autoriser, modèle, niveau de réflexion : les options principales du CLI dans Régie

Statut : à fusionner
Bloqué par : 16
Possède : `bridge/`, `android/`, `docs/protocole-pont.md`

## Objectif

Retours de l'utilisateur, le 14 novembre :

> « J'avais choisi une permission qui disait que tout était autorisé, mais les permissions m'étaient toujours demandées. Rajoute un truc explicite qui bypasse toutes les permissions à la création du projet. »

> « Je veux une interface qui soit limite un proxy de Claude Code et Codex, au moins les trucs principaux : changer le modèle, changer le niveau de réflexion… »

## À livrer

1. **Mode « Tout autoriser »**, explicite. Aucune demande de validation de toute la session.
   - Claude : `bypassPermissions`.
   - Codex : aucune approbation et accès complet, ce qui est déjà le cas sous proot.
   - Il se choisit à la création et peut se changer en cours de session.
2. **Libellés sans ambiguïté.**
   - « Tout valider » a été compris à l'envers.
   - Proposer par exemple : « Plan d'abord » · « Je valide chaque action » · « Modifs auto » · « Tout autoriser ».
   - Le mode en cours est toujours visible dans la session.
3. **Modèle et niveau de réflexion**, par agent.
   - Les listes viennent de ce que l'agent expose par ACP (options de configuration de session, modèles), jamais d'une liste figée dans l'app.
   - Ils se choisissent à la création et se changent en cours de session.
   - Vérifier ce que `claude-agent-acp` et `codex-acp` permettent dans leur documentation à jour, et le noter dans l'ADR 02.
4. **Autres options principales du CLI**, si l'agent les expose : passer en mode plan, interrompre, reprendre une ancienne session. Ne pas réinventer ce que l'agent ne propose pas.
5. **Protocole et tests.** Le protocole du pont est mis à jour et testé ; l'app a ses tests de domaine.

## Critères d'acceptation

- Sur l'émulateur (ticket 16) avec une vraie session Codex :
  - [x] en « Tout autoriser », une écriture et une commande passent sans aucune demande → `artifacts/screenshots/17/acceptance.txt`, `events-final.json` (apply_patch et printf avec redirection, zéro approval_request) ;
  - [x] le changement de modèle et de niveau de réflexion en cours de session est pris en compte → captures 09–11 et `codex-turn-contexts.json` (Sol/low → Terra/high, never/danger-full-access).
- [x] Les portes de qualité sont vertes → Android `./gradlew check` (319 tests + lint), pont `npm run check` (297 tests + typage/lint).

- [x] Start interrompt depuis les options, même pendant un réglage → `SessionSettingsInputTest`, mutation busy détectée ; vrai Codex `turn_end cancelled` seq 1463.

## Rapport

STATUT: done
RÉSUMÉ: Tout autoriser, modèles et réflexion ACP disponibles à la création et en session.
Start interrompt aussi depuis les options, même pendant l’application d’un réglage.
CRITÈRES:
- [x] Permissions explicites → mapping testé ; acceptance.txt et events-final.json.
- [x] Modèle/réflexion effectifs → captures 09–11, codex-turn-contexts.json.
- [x] Plan/reprise → captures 12/15 ; Start dans les options → captures review-start-26–28, seq 1463 cancelled.
- [x] Start pendant busy → SessionSettingsInputTest, mutation détectée ; session au repos protégée.
- [x] Protocole/domaine et portes vertes → docs/protocole-pont.md, review-start-*-check.txt.
PORTE DE QUALITÉ: Android 319 tests + lint ; pont 297 tests + typage/lint, verts.
THOR: absente ; émulateur deux écrans, pont Mac, vrai Codex interrompu depuis Options.
Agent d’essai, pont et émulateur arrêtés ; aucun Claude lancé.
REVIEW: constat bloquant Start corrigé ; test rouge puis vert, mutation busy détectée.
Relecture complète main...HEAD consignée dans review-start-relecture.txt.
DÉCISIONS PRISES: ADR 02 précisé ; routage vers le contrôleur existant, sans nouvelle règle produit.
POUR L’UTILISATEUR: suivre essai-emulateur.txt, étapes 1–8 : installer sur Thor,
créer en Tout autoriser, dicter édition/commande, changer modèle/effort, plan/reprise ;
pendant un tour : Droite puis Start, B pour voir Interrompue ; refaire après A sur un réglage.
Contrôler manette/micro/écrans physiques ; Claude quand le quota le permet.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/17/ ; nouvelles review-start-26haut/bas.png à 28haut/bas.png.
HORS PÉRIMÈTRE: saisie longue adb tronquée déjà relevée au ticket 11 ; vraie voix non testée.
Le cas busy est testé avec un accusé suspendu, pas forcé sur le vrai agent.
