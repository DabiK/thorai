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
- [x] Écran, file, R1 et deux modes d'affichage → captures 09 à 17 et 23 à 27, tests `ReviewApprovalsTest` / contrôleurs ; R1 boucle depuis la dernière demande (régression rouge puis verte).
- [x] Refus avec consigne préparé pour 08 → `AnswerApproval` et tests d'accusé, livraison et concurrence.
- [x] Trois niveaux au contrat de lancement, anciens modes conservés → tests du pont et essais Codex `plan` / `auto-edit` ; fiche visuelle au ticket 11.
- [x] Notification via port et ouverture ciblée → tests domaine et adapter Android (canal désactivé puis réactivé), permission Android présentée, intent ciblé vérifié ; publication réelle après permission à vérifier avec l'utilisateur.

## Rapport

STATUT: done

Reprise solo Codex du 14 novembre 2026. Ticket **à fusionner** selon la règle de reprise nocturne : tout le travail autonome est vert, les gestes de permission et le nouvel essai Claude restent explicitement à faire. Aucun sous-agent, push ou fusion.

- Écran 4 livré : commande/fichier exact, justification et cibles si exposées, avant/après, plan, file complète ; A une fois, X seulement si limité à la session, B refus. Double appui sans décision sur la demande suivante, R1 explicite. Deux écrans et panneau compact vérifiés.
- R1 ouvre aussi la validation depuis le fil, selon la consigne de reprise ; test du comportement précédent révisé pour cette décision, navigation sans validation toujours couverte.
- Refus dicté prêt derrière `ReviewApprovals.answer(REJECT, instruction)` pour le 08, sans envoyer la consigne après un refus incertain.
- Modes `plan`, `auto-edit`, `ask` disponibles via `session.create`, traduits pour les deux agents ; `allow-all` historique conservé. Sélecteur visuel dans la fiche du ticket 11.
- Notifications Android via un port : demande de permission, canal, retrait à la résolution, ouverture ciblée après relancement. Un refus de permission ne perd plus définitivement l'alerte (régression rouge puis verte).
- Porte Android : **196 tests + lint verts**. Pont : **266 tests + lint + typage verts**. Aucun test ne contacte un agent ni la Thor.
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

### Relecture après commit

`git diff main...HEAD` relu en solo : domaine, ports, adapters, écrans, tests, contrat et preuves. Correction d'une attente d'ouverture conservée dans la composition, qui risquait de reprendre la sélection après R1 lors d'une mise à jour de la file. L'identité attendue est désormais détenue uniquement par `ReviewApprovals`. Régression rouge puis verte : ouverture avant la synchronisation, arrivée de la demande, sélection de la suivante, mise à jour tardive et réponse adressée seulement à la suivante. Ancien cas d'usage vide `OpenApproval` retiré, imports et commentaire nettoyés.

Porte après correction : **196 tests Android + lint verts**, `review-android-check.txt`. Pont inchangé depuis sa dernière porte verte (266 tests). APK réinstallé (`review-install.txt`), ouverture ciblée au démarrage à froid testée avec une demande Codex réelle (capture 19), refus B, fichier absent et nouvelle fermeture par Quitter. Pas de nouveau Claude ni de permission accordée. Statut maintenu à `à fusionner`.

### Correction de la revue bloquante — canal désactivé

STATUT: done. Ticket maintenu à **à fusionner**.

- `AndroidApprovalNotifications.show()` relit le canal à chaque appel et renvoie `false` s'il est absent ou à `IMPORTANCE_NONE`. Le domaine peut ainsi retenter après réactivation ; aucune règle métier déplacée dans l'adapter.
- Test du vrai adapter sous Robolectric 4.18 (Android 33 simulé, Application neutre) : permission accordée et notifications globales actives, canal bloqué → échec sans publication ; réactivation en importance basse → même demande publiée ; nouveau blocage → échec. Rouge avant correction (`review-channel-red.txt`), vert ensuite. Le test de domaine existant couvre la nouvelle tentative après retour du premier plan.
- Portes : **197 tests Android + lint verts**, **266 tests pont + typage/lint verts** (`review-channel-android-check.txt`, `review-channel-bridge-check.txt`). Aucun service, agent ou console dans les tests.
- Thor `THOR0000000001` : `scripts/thor-install.sh` réussi ; permission notifications présentée et fermée par Retour sans l'accorder (capture 20). Pont démarré par le script existant, connexion et historique retrouvés. Intent d'une demande précédemment résolue → indisponible et actions désactivées sur les deux écrans (capture 21), retour à la régie (capture 22), puis Quitter. Aucun agent lancé, aucun redémarrage ni autre APK installé.
- ADR 08 complété pour le canal et l'outil de test. Documentation Android et Robolectric consultée via Context7. L'essai réel canal bloqué/réactivé reste à l'utilisateur, car `POST_NOTIFICATIONS` est toujours refusée ; aucun succès de publication réelle revendiqué.

Vérification supplémentaire avec l'utilisateur :
1. Accorder les notifications à Régie dans Android et conserver l'activation globale.
2. Désactiver uniquement la catégorie « Validations » ; faire demander une validation par Codex, puis mettre le haut en arrière-plan : aucune alerte.
3. Réactiver « Validations », revenir dans Régie puis remettre le haut en arrière-plan sans répondre à la demande : une notification doit apparaître pour cette même demande.
4. Toucher cette notification, vérifier la bonne demande, répondre puis vérifier son retrait. Refaire un passage en arrière-plan : aucune alerte pour la demande résolue.
5. Effectuer ensuite l'essai Claude commande/édition A/B décrit plus haut, quand le quota le permet.

Relecture solo après `4b42673` : diff contre `main` examiné (domaine, ports, adapters, UI, tests, contrat et preuves). Le test échoue bien si le contrôle du canal disparaît ; aucune règle métier ajoutée hors du domaine ni changement visuel. Correction documentaire de l'ancien « Deux niveaux » dans le protocole, désormais trois plus le mode historique ; retrait d'une ligne vide finale de la trace npm. Portes toujours vertes, APK installé identique au code final. `git diff --check main...HEAD` propre après correction.

### Correction de la revue bloquante — parcours R1 de toute la file

STATUT: done. Ticket maintenu à **à fusionner**.

- `ReviewApprovals.next()` revient à la première demande après la dernière. La correction reste dans le domaine ; aucun adapter ni écran modifié. ADR 08 précisé.
- Régression exacte : file `[a, b]`, ouverture de `b`, R1 → `a`, R1 → `b`, aucune réponse envoyée. Rouge avant correction (`review-r1-red.txt`), vert après (`review-r1-green.txt`). File vide, demande unique déjà répondue et protection contre une seconde décision également couvertes.
- Portes : **199 tests Android + lint verts**, **266 tests pont + typage/lint verts** (`review-r1-*-check.txt`, `review-r1-test-counts.txt`). Aucun test n'appelle un agent, un service réel ou la console.
- Thor `THOR0000000001` : APK installé par `scripts/thor-install.sh`. Deux sessions Codex réelles, commandes `touch regie07-r1-first.txt` et `touch regie07-r1-last.txt`. Ouverture ciblée de la dernière par intent, puis appuis R1 par `sendevent` : dernière → première → dernière, en deux écrans puis boucle en mode un écran forcé. Captures 23–27 ; file identique avant/après navigation.
- Refus B de la première, second B sans effet sur la suivante, R1 puis B pour la dernière. Journal : refus 2125/2133, tours annulés 2131/2139 ; fichiers absents. Retour à la régie puis Quitter : les deux sessions d'essai, le pont et son superviseur arrêtés. Le Codex interactif préexistant hors du pont est conservé.
- Aucun Claude lancé, aucune permission accordée, aucun redémarrage ni autre APK installé. Publication réelle d'une notification et essai Claude restent aux étapes utilisateur déjà décrites. Preuve détaillée : `artifacts/screenshots/07/review-r1-thor.txt`.
- Hors périmètre confirmé : accès Android des agents indisponible (débogage sans fil/association), permission `WRITE_SECURE_SETTINGS` absente ; ancien libellé « Point à voix haute » sur la régie à reprendre au ticket 12.

Relecture solo après `e5b70a6` : `git diff main...HEAD` relu (domaine, composition, adapters, UI, tests et contrat), avec les captures et événements de l'essai. Le nouveau test échoue sur l'ancien comportement ; R1 ne décide jamais, la réponse mémorisée reste protégée même sur une file unique et vide. Aucun autre défaut bloquant établi. Correction documentaire : le paragraphe des lectures disait encore que les éditions « remontent toujours », ce qui contredisait `auto-edit` ; il distingue maintenant les deux règles. Aucun code changé après les portes et l'installation Thor ; pas de nouvelle campagne nécessaire pour cette seule précision. `git diff --check main...HEAD` propre, statut maintenu à `à fusionner`.
