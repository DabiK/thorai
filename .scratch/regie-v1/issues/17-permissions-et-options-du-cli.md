# 17 — Tout autoriser, modèle, niveau de réflexion : les options principales du CLI dans Régie

Statut : fait
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
- [x] Les portes de qualité sont vertes → Android `./gradlew check` (329 tests + lint), pont `npm run check` (306 tests + typage/lint).

- [x] Start interrompt depuis les options, même pendant un réglage → `SessionSettingsInputTest`, mutation busy détectée ; vrai Codex `turn_end cancelled` seq 1463.

- [x] Une reprise temporairement refusée conserve toutes les sélections jusqu’à la réussite → trois régressions rouges/vertes, `review-resume-*.txt` (mode/modèle/effort, nouvelle tentative après redémarrage).
- [x] Options annonce Start et propose le tactile pendant un tour, même pendant un réglage → `SessionSettingsScreenTest`, captures review-interrupt-30–33, vrai Codex cancelled seq 1911.

- [x] Un modèle sauvegardé retiré du catalogue peut être remplacé explicitement sans perdre les autres choix → régressions domaine/WS, `review-catalog.txt`, captures 42–45, vrai Codex `REPRISE_OK` seq 1980.

- [x] Modèle et effort retirés se remplacent ensemble, sans perdre les autres choix → régressions domaine/WS/Android, `review-joint.txt`, captures 48–51, vrai Codex `REPRISE_CONJOINTE_OK` seq 2069.
- [x] Relâcher le stick dans Options arrête le défilement au retour → `SessionSettingsInputTest`, `review-joint-scroll-red.txt`, porte finale verte.

- [x] Un effort dépendant du modèle ne bloque plus A ni Reprendre avant validation par le pont → SessionSettingsTest rouge/vert, FakeAgentConnector, SessionSettingsScreenTest ; banc avec résumé conditionnel simulé puis vrai Codex high, captures 52–56 et review-dependent.txt.

## Rapport

STATUT: done
RÉSUMÉ: Un effort absent du catalogue provisoire ne bloque plus le remplacement du modèle.
A transmet les choix au pont, qui restaure l’effort après application du modèle.
Le ticket reste à fusionner ; aucun push ni fusion.
CRITÈRES:
- [x] Catalogue conditionnel → SessionSettingsTest rouge/vert et FakeAgentConnector.
- [x] A depuis Reprendre, effort conservé → test Compose, captures 53–55, vrai Codex high.
- [x] Refus, doubles A, choix devenus indisponibles → régressions domaine Android.
- [x] Écriture et commande sans validation → review-dependent-events.json, fin seq 2230.
- [x] Reprise après redémarrage → review-dependent-reloaded.json.
- [x] Autres critères du ticket → preuves précédentes conservées, suites complètes vertes.
PORTE DE QUALITÉ: Android ./gradlew check : 329 tests + lint ; pont npm run check : 306 tests + typage/lint.
THOR: absente ; émulateur deux écrans/pont Mac, vrai Codex Sol/high/never/danger-full-access.
Catalogue vide et modèle retiré simulés dans la seule sauvegarde du banc, confirmation par A.
Agent, pont et émulateur arrêtés ; aucun Claude ni sous-agent.
REVIEW: validation prématurée corrigée ; attente correspondante de l’ancien test corrigée,
autres assertions conservées. Diff main...HEAD relu après 5a41ed5, aucun autre blocage trouvé.
Deux fins de journaux nettoyées ; preuve : review-dependent-relecture.txt.
DÉCISIONS PRISES: ADR 02 et protocole précisés : le pont valide les sélections dépendantes.
POUR L'UTILISATEUR: 1. Rebrancher Thor, installer pont/app selon essai-emulateur.txt.
2. Dans une reprise bloquée, choisir un modèle disponible en conservant l’effort, puis Reprendre/A.
3. Vérifier modèle/effort/mode, envoyer une consigne, quitter/rouvrir et reprendre.
4. Refaire écriture/commande, plan/retour, Start/tactile, relâchement du stick et vrai micro.
5. Essayer Claude quand son quota le permet ; détails dans review-dependent.txt.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/17/review-dependent-54bas.png, review-dependent-55bas.png, review-dependent-56haut.png.
HORS PÉRIMÈTRE: warnings Codex rmcp_client préexistants ; réglages personnels inchangés.
Catalogue ACP conditionnel et refus temporaires testés avec doubles ; Thor/Claude restent à vérifier.
