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
- [x] Les portes de qualité sont vertes → Android `./gradlew check` (316 tests + lint), pont `npm run check` (297 tests + typage/lint).


## Rapport

STATUT: done
RÉSUMÉ: Tout autoriser à la création et en session ; modèle/réflexion issus d’ACP,
modifiables à la manette ou au tactile. Mode visible, plan, reprise et interruption.
CRITÈRES:
- [x] Permissions explicites → mapping des deux agents testé ; Codex réel sans validation.
- [x] Options dynamiques → domaine/passerelle/protocole, captures 04/10, vrais turn_context.
- [x] Plan / reprendre / interrompre → captures 12/15/20, événements du pont.
- [x] Contrat documenté et portes vertes → docs/protocole-pont.md, android-check.txt, bridge-check.txt.
PORTE DE QUALITÉ: Android 316 tests et lint ; pont 297 tests, typage et lint, verts.
THOR: absente ; émulateur Regie_Thor à deux écrans avec pont Mac et vrai Codex.
REVIEW: première passe : options non confirmées, mode spontané, retour arrière du mode,
consigne masquée par l’aide corrigés. Relecture main...HEAD après 949e61f : double
échec changement/retour suspend désormais la session, deux tests rouges puis verts.
DÉCISIONS PRISES: ADR 02 complété ; découverte ACP sans prompt avant A, sélecteurs reçus
entiers, changement confirmé par A, pas de renvoi après résultat incertain.
POUR L’UTILISATEUR: suivre les étapes 1–8 de artifacts/screenshots/17/essai-emulateur.txt :
installer sur Thor, édition + commande en Tout autoriser, changer modèle/effort, plan,
Start, reprise, manette/micro/écrans physiques ; Claude quand le quota le permet.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/17/ (04, 10, 12, 14–17, 20–25).
HORS PÉRIMÈTRE: saisie longue adb input text tronquée, déjà relevée au ticket 11 ;
la consigne exacte de vérification a été envoyée par WebSocket. Pas de voix réelle testée.
