# ADR 08 — Validations, niveaux de lancement et notifications

Statut : accepté, 14 novembre 2026 (ticket 07).

## Décisions

- `Approval` conserve commande exacte, justification, dossier, cibles, plan, contenu avant/après et options ACP. A choisit `allow_once`, B `reject_once`. X n'est disponible que pour l'identifiant `allow_for_session` de type `allow_always`. Les options Claude `allow-with-updates` et Codex `accept_execpolicy_amendment` peuvent modifier une permission durable : jamais assimilées à « cette session ».
- `AnswerApproval` réserve l'identité avant l'envoi. Une réponse acceptée, incertaine ou indisponible ne se renvoie pas. Seul `NOT_SENT` permet de réessayer. Les erreurs `unknown_approval` / `unknown_option` du pont deviennent `UNAVAILABLE`. Une coupure ou un délai après mise en file reste `UNKNOWN`.
- `ReviewApprovals`, dans le domaine, garde la demande sélectionnée après sa résolution. Les boutons deviennent inactifs ; R1 choisit explicitement la suivante. Un double A/B ne peut donc pas décider pour une autre demande. Les décisions restent mémorisées en revenant à l'écran pendant le même processus.
- Le refus accompagné d'une consigne est prêt pour le ticket 08 : `ReviewApprovals.answer(REJECT, instruction)` appelle `AnswerApproval`. La consigne part uniquement après l'accusé positif du refus ; sa livraison est rendue séparément. La transcription et sa confirmation restent au ticket 08.
- R1 ouvre une validation en attente depuis la régie **et depuis le fil d'une session**, conformément à la consigne de reprise du ticket 07. Sans validation, la navigation entre sessions reste inchangée. Cela remplace le comportement R1 du ticket 06 décrit dans l'ADR 07. L1 quitte l'écran des validations ; B refuse, il ne sert pas au retour.

## Niveaux au lancement

| Niveau produit | Contrat du pont | Claude | Codex |
| --- | --- | --- | --- |
| Plan d'abord | `plan` | mode `plan` | mode `workspace-write`, puis configuration `collaboration_mode=plan` |
| Modifications automatiques | `auto-edit` | mode `acceptEdits` | mode `workspace-write`, demandes structurées `kind: edit` acceptées une fois par le domaine du pont |
| Tout valider | `ask` | mode `default` | mode `workspace-write` avec la consigne d'escalade de l'ADR 02 |

Les lectures shell strictement reconnues par le ticket 13 restent automatiques pour ces trois niveaux. En `auto-edit`, une écriture par commande shell reste soumise à validation : on ne prétend pas interpréter ses effets comme une édition structurée. Aucun choix de permission durable n'est sélectionné automatiquement.

Le mode Codex `agent` reste écarté : son examen automatique peut aussi autoriser des commandes, au-delà des seules éditions. La compensation des éditions reste dans le domaine, les adapters ne décident pas. Les modes sont appliqués après création et reprise. Un refus de configuration échoue au lieu de lancer silencieusement dans un autre mode.

`ask` et `allow-all` des sessions enregistrées restent compatibles, sans migration ni changement silencieux. La fiche de lancement et son sélecteur visuel sont explicitement le périmètre du ticket 11 ; son champ `approvalMode` devra proposer `plan`, `auto-edit`, `ask`. Aucun écran de lancement concurrent n'est ajouté ici.

## Notifications

- Port `ApprovalNotifications` ; `NotifyApprovals` décide dans le domaine, `AndroidApprovalNotifications` ne fait que parler à Android.
- Le premier plan se mesure sur l'écran du **haut**, puisque le bas doit rester visible en arrière-plan. Une demande encore en attente devient notifiable quand le haut est masqué ; une notification déjà émise ne sonne pas à chaque mise à jour. Elle est retirée quand la demande disparaît.
- Canal Android `approvals`, importance haute, contenu privé sur l'écran verrouillé, aucune action d'autorisation directe dans la notification. Le `PendingIntent` immuable ouvre `TopActivity` avec l'identité exacte de la demande, y compris après relancement. Une demande disparue affiche son indisponibilité.
- `POST_NOTIFICATIONS` est déclarée et demandée après RUN_COMMAND pour ne pas superposer deux demandes Android. Une permission refusée ou des notifications désactivées ne marquent pas l'alerte comme livrée : elle peut être retentée après un retour de premier plan.
- L'adapter relit également l'importance du canal à chaque tentative : un canal absent ou à `IMPORTANCE_NONE` renvoie `false`, sans appeler `notify`. Réactiver le canal permet ainsi au domaine de retenter la demande au prochain passage en arrière-plan.

## Interface et références

La consultation distante du canvas a échoué. Export local retrouvé et lu en entier :
`/tmp/regie-reference{Validation,Lancer}.dc.html`.
Empreintes conservées dans `artifacts/screenshots/07/maquette-sha256.txt` ; fraîcheur distante non vérifiée.

Structure reprise : en-tête agent/projet, commande ou fichier en pleine largeur avec contour ambre, justification, cibles, autres demandes en bas ; grand A clair, X conditionnel et B sur l'écran inférieur. Avant/après et plan sont défilables par croix/stick. Mode un écran : actions compactes sous le contenu. Les effets et justifications absents sont annoncés, jamais inférés d'une commande. La mention « lu à voix haute » est omise conformément à la décision du 14 novembre.

Pas de nouvelle bibliothèque de production. Documentation actuelle consultée via Context7 :
- [Notifications Android](https://developer.android.com/develop/ui/compose/notifications/create-notification), [permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) ;
- [Configuration ACP](https://agentclientprotocol.com/protocol/session-config-options) ; syntaxe confrontée aux types du SDK 2.6.0 et au code installé de `codex-acp` 2.6.0 (`CollaborationModeConfig`, `fileChangeDecisionOptions`).
Les extraits consultés sont dans `docs-*-current.txt` du dossier de preuves.

## Vérifications et limites

- 196 tests Android et lint verts ; 266 tests du pont, typage et lint verts. Tests de domaine, passerelle réelle avec faux pont, passerelle factice, contrôles A/X/B/R1, notifications, choix et reprise des modes. Aucun test unitaire ne contacte la console ou un agent.
- Thor : nouveau Codex seulement, A pour commande, B pour commande et édition, X pour édition, reprise du fil et effets sur disque ; file de deux demandes et double B ; deux écrans, mode un écran forcé, défilement, bas maintenu en arrière-plan, ouverture ciblée par l'intent de notification. Plan accepté avec `collaboration_mode=plan`, édition automatique exécutée sans demande UI. Détails et numéros de séquence : `artifacts/screenshots/07/essai-thor.txt`.
- **Limite Codex observée** : `allow_for_session` est bien transmis et accepté (événement 1829), l'édition s'effectue. Une édition ultérieure du même fichier redemande pourtant une validation. Régie relaie la demande ; aucune autorisation locale n'est inventée pour masquer cette limite de l'agent/adaptateur.
- Pas de nouveau Claude, conformément à la consigne d'économie de quota : contrat couvert par les faux et enregistrements existants, parcours réel restant à faire avec l'utilisateur.
- Notification système non affichée : permission laissée à l'utilisateur. La boîte Android, le domaine et le chemin d'ouverture sont vérifiés, pas le clic sur une notification réellement publiée.

### Relecture de la sélection après commit

L'identité ouverte avant la synchronisation est conservée dans `ReviewApprovals`, même quand la demande n'est pas encore connue. La composition ne garde plus une deuxième identité susceptible de réouvrir une ancienne demande après R1. Test de régression avec arrivée tardive, changement de sélection puis mise à jour de file ; ouverture ciblée à froid vérifiée sur la Thor après réinstallation (capture 19).

### Relecture du canal désactivé

Robolectric 4.18 est ajouté uniquement aux tests de l'app pour exercer le vrai adapter face à Android simulé (API 33 comme la Thor). Une `Application` neutre remplace la composition : aucun service, agent ou accès console. Le test accorde la permission, active les notifications globales, bloque le canal, vérifie `show() == false` et l'absence de publication, puis réactive le canal en importance basse et vérifie la publication de la même demande. Un nouveau blocage est également relu. Le test était rouge avant la correction ; les tests existants de `NotifyApprovals` couvrent la nouvelle tentative après un échec du port.

Références consultées par Context7 : [importance du canal](https://developer.android.com/reference/android/app/NotificationChannel#getImportance()), [configuration Robolectric](https://robolectric.org/configuring). Version stable vérifiée dans les métadonnées Maven Central. Preuves : `review-channel-*-docs.txt`, `review-channel-red.txt` et `review-channel-android-check.txt` dans `artifacts/screenshots/07/`.
