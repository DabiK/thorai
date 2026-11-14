# 07 — Validations

Statut : à fusionner
Bloqué par : 06

## Objectif

Écran 4 de la maquette : répondre aux demandes de validation des agents.

## À livrer

- **En haut** : ce que l'agent veut faire (commande ou fichier), pourquoi s'il le dit, ce que ça touche, et la file des autres demandes.
- **En bas** :
  - A : autoriser une fois ;
  - X : toujours dans cette session, si l'agent le permet ;
  - B : refuser.
- **Refus avec consigne dictée** : branché au ticket 08. Ici, préparer le cas d'usage.
- **Niveau de validation au lancement d'une session** (plan d'abord, modifications automatiques, tout valider), traduit vers les modes de chaque agent.
- **Notification Android** quand une validation arrive alors que l'app n'est pas au premier plan.

## Critères d'acceptation

- [ ] Sur la Thor, une commande et une modification de fichier sont validées ou refusées pour chacun des deux agents, et l'agent reprend en conséquence. **Codex vérifié** (A, X et B, journal et effets sur disque) ; essai Claude laissé à l'utilisateur conformément à la consigne de quota.
- [x] Écran, file, R1 et deux modes d'affichage → captures 09 à 17, tests `ReviewApprovalsTest` / contrôleurs.
- [x] Refus avec consigne préparé pour 08 → `AnswerApproval` et tests d'accusé, livraison et concurrence.
- [x] Trois niveaux au contrat de lancement, anciens modes conservés → tests du pont et essais Codex `plan` / `auto-edit` ; fiche visuelle au ticket 11.
- [x] Notification via port et ouverture ciblée → tests domaine, permission Android présentée, intent ciblé vérifié ; publication réelle après permission à vérifier avec l'utilisateur.

## Rapport

STATUT: done

Reprise solo Codex du 14 novembre 2026. Ticket **à fusionner** selon la règle de reprise nocturne : tout le travail autonome est vert, les gestes de permission et le nouvel essai Claude restent explicitement à faire. Aucun sous-agent, push ou fusion.

- Écran 4 livré : commande/fichier exact, justification et cibles si exposées, avant/après, plan, file complète ; A une fois, X seulement si limité à la session, B refus. Double appui sans décision sur la demande suivante, R1 explicite. Deux écrans et panneau compact vérifiés.
- R1 ouvre aussi la validation depuis le fil, selon la consigne de reprise ; test du comportement précédent révisé pour cette décision, navigation sans validation toujours couverte.
- Refus dicté prêt derrière `ReviewApprovals.answer(REJECT, instruction)` pour le 08, sans envoyer la consigne après un refus incertain.
- Modes `plan`, `auto-edit`, `ask` disponibles via `session.create`, traduits pour les deux agents ; `allow-all` historique conservé. Sélecteur visuel dans la fiche du ticket 11.
- Notifications Android via un port : demande de permission, canal, retrait à la résolution, ouverture ciblée après relancement. Un refus de permission ne perd plus définitivement l'alerte (régression rouge puis verte).
- Porte Android : **195 tests + lint verts**. Pont : **266 tests + lint + typage verts**. Aucun test ne contacte un agent ni la Thor.
- Installation `scripts/thor-install.sh` réussie, pont actualisé. Codex : commande autorisée puis refusée, édition autorisée par X puis autre édition refusée ; reprise/annulation confirmée par événements et fichiers. File de deux demandes, double B, défilement, mode un écran, bas en arrière-plan et intent ciblé testés.
- Limite observée : Codex accepte `allow_for_session`, exécute l'édition, mais redemande une validation pour une édition ultérieure du même fichier. Régie relaie ce qu'il expose, sans contourner sa décision. Détails ADR 08.
- Claude réel et publication d'une notification après permission : **non revendiqués**. Le quota Claude est préservé ; aucune permission n'a été accordée à la place de l'utilisateur.
- Maquettes Validation et Lancer retrouvées dans un export local, lues ; structure reprise, fraîcheur distante non vérifiée. Aucune mention de lecture à voix haute dans cet écran.
- Quatre sessions Codex lancées, toutes arrêtées avec le pont via Quitter. Aucun redémarrage ni autre APK installé.
- Relecture solo : voir le complément de revue ci-dessous. ADR créé : `docs/adr/08-validations.md`. Contrat `docs/protocole-pont.md` actualisé.

Preuves : `artifacts/screenshots/07/essai-thor.txt`, `*-check-current.txt`, `codex*-events.json`, `thor-stop.txt` et captures **09, 12, 14, 15, 16, 17** (haut et bas).

### Vérifications avec l'utilisateur

1. Autoriser les notifications de Régie dans Android ; RUN_COMMAND et l'accès Android restent à traiter selon les tickets 05/14.
2. Faire demander à Claude une commande puis une édition sur un fichier d'essai. R1 puis A ; vérifier l'effet et la reprise du fil. Répéter avec B et vérifier l'absence de l'effet.
3. Sur Codex, vérifier la portée réelle de X si proposé, puis refaire une demande dans une nouvelle session.
4. Mettre le haut en arrière-plan, provoquer une validation, vérifier la notification et la toucher : bonne demande ouverte, bas toujours visible. Aucun agent ne décide via la notification seule.
5. Voix du refus : après ticket 08. Fiche et sélecteur du niveau de lancement : après ticket 11.

Hors périmètre : absence RUN_COMMAND / WRITE_SECURE_SETTINGS et association sans fil ; bas derrière Cocoon au premier réveil, récupéré par bascule debug un/deux écrans ; portée effective du choix Codex `acceptForSession`. Le pas à pas et les limites exactes sont dans `essai-thor.txt`.
