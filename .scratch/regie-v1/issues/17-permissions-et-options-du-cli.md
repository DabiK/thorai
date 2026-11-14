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
- [x] Les portes de qualité sont vertes → Android `./gradlew check` (321 tests + lint), pont `npm run check` (303 tests + typage/lint).

- [x] Start interrompt depuis les options, même pendant un réglage → `SessionSettingsInputTest`, mutation busy détectée ; vrai Codex `turn_end cancelled` seq 1463.

- [x] Une reprise temporairement refusée conserve toutes les sélections jusqu’à la réussite → trois régressions rouges/vertes, `review-resume-*.txt` (mode/modèle/effort, nouvelle tentative après redémarrage).
- [x] Options annonce Start et propose le tactile pendant un tour, même pendant un réglage → `SessionSettingsScreenTest`, captures review-interrupt-30–33, vrai Codex cancelled seq 1911.

- [x] Un modèle sauvegardé retiré du catalogue peut être remplacé explicitement sans perdre les autres choix → régressions domaine/WS, `review-catalog.txt`, captures 42–45, vrai Codex `REPRISE_OK` seq 1980.

## Rapport

STATUT: done
RÉSUMÉ: Une session bloquée par un modèle retiré peut reprendre avec un remplacement explicite.
Le catalogue est actualisé et les choix sauvegardés restent intacts en cas de refus temporaire.
Ticket à fusionner, travail commité sur sa branche, sans push ni fusion.
CRITÈRES:
- [x] Modèle disparu → régressions domaine/WS, vrai Codex REPRISE_OK seq 1980, captures 42–45.
- [x] Conservation des choix et reprise après redémarrage → tests et review-catalog-persist-resume.json.
- [x] Choix Android puis A → SessionSettingsTest, essai manette sur émulateur.
- [x] Permissions, modèle/réflexion, plan, interruption → preuves précédentes acceptance.txt, captures 09–15/30–33.
- [x] Protocole et domaine → docs/protocole-pont.md, portes vertes.
PORTE DE QUALITÉ: Android ./gradlew check : 321 tests + lint ; pont npm run check : 303 tests + typage/lint.
THOR: absente ; émulateur deux écrans et pont Mac, vrai Codex, ancien modèle simulé dans
la sauvegarde du banc. Remplacement par Sol/low/Tout autoriser, prompt puis seconde reprise.
Agent, pont et émulateur arrêtés ; aucun Claude ni sous-agent.
REVIEW: catalogue figé après deux erreurs identiques découvert et corrigé, régression WS rouge/verte.
Relecture finale après commit consignée dans review-catalog-relecture.txt.
DÉCISIONS PRISES: ADR 02 complété ; catalogue courant séparé des sélections, remplacement explicite uniquement.
POUR L'UTILISATEUR: 1. Rebrancher Thor, installer pont et app (essai-emulateur.txt).
2. Ouvrir une ancienne session, Options > Reprendre ; si modèle indisponible, choisir un modèle proposé puis A.
3. Vérifier effort/mode, envoyer une consigne, quitter/rouvrir et reprendre.
4. Refaire écriture/commande sans validation, plan puis retour, Start et tactile pendant un tour.
5. Vérifier micro/manette/deux écrans physiques ; Claude quand le quota le permet.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/17/review-catalog-42bas.png, review-catalog-43haut.png, review-catalog-44haut.png.
HORS PÉRIMÈTRE: chargement ACP à froid dépassant l’attente de l’app : fermer/rouvrir Options après résultat incertain.
Micro et console physiques non testés ; refus ACP temporaire couvert uniquement par les tests isolés.
