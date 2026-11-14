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
- [x] Les portes de qualité sont vertes → Android `./gradlew check` (326 tests + lint), pont `npm run check` (305 tests + typage/lint).

- [x] Start interrompt depuis les options, même pendant un réglage → `SessionSettingsInputTest`, mutation busy détectée ; vrai Codex `turn_end cancelled` seq 1463.

- [x] Une reprise temporairement refusée conserve toutes les sélections jusqu’à la réussite → trois régressions rouges/vertes, `review-resume-*.txt` (mode/modèle/effort, nouvelle tentative après redémarrage).
- [x] Options annonce Start et propose le tactile pendant un tour, même pendant un réglage → `SessionSettingsScreenTest`, captures review-interrupt-30–33, vrai Codex cancelled seq 1911.

- [x] Un modèle sauvegardé retiré du catalogue peut être remplacé explicitement sans perdre les autres choix → régressions domaine/WS, `review-catalog.txt`, captures 42–45, vrai Codex `REPRISE_OK` seq 1980.

- [x] Modèle et effort retirés se remplacent ensemble, sans perdre les autres choix → régressions domaine/WS/Android, `review-joint.txt`, captures 48–51, vrai Codex `REPRISE_CONJOINTE_OK` seq 2069.
- [x] Relâcher le stick dans Options arrête le défilement au retour → `SessionSettingsInputTest`, `review-joint-scroll-red.txt`, porte finale verte.

## Rapport

STATUT: done
RÉSUMÉ: Modèle et effort retirés se remplacent ensemble avant de reprendre la session.
Options conserve les choix préparés entre les lignes ; A confirme l’ensemble.
Le relâchement du stick dans Options arrête le défilement. Ticket à fusionner.
CRITÈRES:
- [x] Remplacements conjoints → régressions domaine/WS/Android et vrai Codex, fin seq 2069.
- [x] Refus temporaire et autres sélections conservés → test domaine puis reprise après redémarrage.
- [x] Choix visibles avant A → SessionSettingsScreenTest, captures review-joint-48/49.
- [x] Stick relâché dans Options → SessionSettingsInputTest rouge puis vert.
- [x] Permissions, modèle/réflexion, plan, interruption → preuves précédentes acceptance.txt et captures 09–15/30–33.
- [x] Protocole documenté et testé → docs/protocole-pont.md, contrat WebSocket.
PORTE DE QUALITÉ: Android ./gradlew check : 326 tests + lint ; pont npm run check : 305 tests + typage/lint.
THOR: absente ; émulateur deux écrans/pont Mac, vrai Codex repris, deux choix retirés simulés
uniquement dans la sauvegarde du banc. Sol/low/Tout autoriser confirmés, prompt puis seconde reprise.
Agent, pont et émulateur arrêtés ; aucun Claude ni sous-agent.
REVIEW: deux constats et option sans sélection corrigés ; diff main...HEAD relu après a744d45, aucun autre blocage trouvé.
Preuve : review-joint-relecture.txt. Travail commité, aucun push ni fusion.
DÉCISIONS PRISES: ADR 02 complété ; table conjointe explicite, A confirme, B abandonne, aucun fallback.
POUR L'UTILISATEUR: 1. Rebrancher Thor et installer pont/app selon essai-emulateur.txt.
2. Options > Reprendre ; si plusieurs choix sont retirés, préparer modèle et effort, puis un seul A.
3. Vérifier mode/autres choix, envoyer une consigne, quitter/rouvrir et reprendre.
4. Défiler au stick, ouvrir Options, relâcher, B : vérifier que le fil reste immobile.
5. Refaire écriture/commande sans validation, plan/retour, Start/tactile, micro et deux écrans physiques.
6. Essayer Claude quand son quota le permet ; détails dans review-joint.txt.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/17/review-joint-48bas.png, review-joint-49bas.png, review-joint-51haut.png.
HORS PÉRIMÈTRE: warnings Codex de configuration rmcp_client ignorée ; réglages personnels inchangés.
Micro/stick physiques non testés ; refus temporaire couvert par tests isolés ; ancien délai ACP à froid inchangé.
