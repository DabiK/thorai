# ADR 12 — Relecture des éditions exposées par l’agent

Statut : accepté, 14 novembre 2026 (ticket 10).

## Source et limites

La relecture utilise le journal ACP déjà chargé par `AgentGateway.thread`, sans
nouvel accès au disque ni RPC. Elle montre les éditions terminées, regroupées par
fichier, dans leur ordre d’arrivée. Les éditions successives restent des blocs
séparés : les fusionner sans base commune inventerait un état Git. Les comptes
+/− cumulent les lignes des blocs exposés, ce ne sont pas les statistiques d’un
`git diff` net. Une commande shell ne fournit pas nécessairement un diff ; les
fichiers connus sans contenu affichent « Diff non communiqué » et aucun commentaire
n’est possible. La limite existante de 300 éléments du fil s’applique ; ce n’est pas
une archive exhaustive des changements anciens d’une longue session.

`ThreadBuilder` conserve les diff de chaque appel, même si sa confirmation ne
répète pas le contenu. `DiffDecoder` traduit ACP ; `LineDiff` réutilise Myers via
java-diff-utils 4.18, sans nouvelle dépendance. Le domaine reçoit `DiffFragment`,
`DiffLine` et les coordonnées anciennes/nouvelles, jamais un objet de bibliothèque.
Les suppressions portent seulement leur numéro ancien, les ajouts leur numéro nouveau.

**Un extrait n’est pas un fichier entier.** Claude publie des `structuredPatch`
avec `oldStart` / `newStart` : ils donnent les coordonnées exactes (fixture réelle,
ligne ajoutée 5 dans todo.md). Une création (`oldText=null` ou `_meta.kind=add`)
commence à 1. En revanche, les anciens/nouveaux textes d’une mise à jour Codex
peuvent être des extraits sans position : affichage « de l’extrait », numéros
relatifs, et avertissement inclus dans la demande. Aucun numéro absolu deviné.

## Domaine et confirmation

`ReviewDiff` agrège les éditions terminées, conserve la sélection, borne la
navigation, prépare le commentaire et la demande d’explication. Les éditions
échouées ou en attente ne sont pas présentées comme appliquées. Les dépendances
restent `AgentGateway` et des flux Kotlin ; tests avec `StubGateway` uniquement.

L2 fige session, chemin, côté ancien/nouveau, numéro et texte de la ligne.
`ReviewDictation` et `DeliverDictation` du ticket 08 gardent A/B/Y/X et le traitement
des livraisons incertaines. Le message envoyé contient ce contexte et le commentaire.
Les coordonnées sont celles de l’édition affichée : le texte sélectionné permet de
la reconnaître même si l’agent a déjà modifié le fichier depuis.

Y prépare « explique-moi ce bloc » sur l’édition sélectionnée, bornée à 61 lignes
au voisinage de la sélection. A confirme (principe du PRD : rien sans A), B annule.
La demande précise « sans modifier les fichiers », réponse française écrite.
Elle utilise la session et le tour normal de l’agent, sans synthèse ni second modèle.
Le panneau suit les textes rattachés au message utilisateur correspondant, jamais une
ancienne réponse ou celle d’une demande suivante. Double A protégé ; seul un échec
certain permet un nouvel essai. Un accusé incertain n’autorise pas de renvoi automatique.

## Interface

Export local `project/Diff.dc.html` lu en entier, empreinte dans
`artifacts/screenshots/10/maquette-sha256.txt`. Pas d’outil Artifact Claude disponible ;
la fraîcheur distante de cet export n’a pas été vérifiée.

- Haut : chemin, comptes, ancien/nouveau, ajouts verts et suppressions roses,
  sélection blanche, Geist Mono ; retour à la ligne pour garder le code long lisible.
- Bas : fichiers tactiles, comptes, ligne choisie, micro maintenu, Y et B.
- ↑↓ sélectionne une ligne ; ←→ change de fichier ; stick droit fait défiler.
- L1/R1 et Start gardent le comportement de session, avec priorité aux validations.
- Un écran : fichiers en rangée et actions sous le contenu ; deux écrans : liste en bas.
- Adaptation de la maquette : la dictée relue utilise l’écran commun livré au ticket 08,
  avec chemin et ligne, au lieu de créer une deuxième relecture dans une bulle inline.

API Compose vérifiée avec Context7 (`library Jetpack Compose`, puis `docs`) :
[LazyListState et défilement](https://developer.android.com/develop/ui/compose/lists).
Recherche Context7 java-diff-utils sans correspondance ; référence primaire lue :
[dépôt et API du projet](https://github.com/java-diff-utils/java-diff-utils).
Version conservée, signatures vérifiées par compilation et tests des coordonnées.

## Vérification

261 tests Android et lint verts ; rouge puis vert sur les lignes, le décodage réel,
la sélection, l’explication, l’envoi de commentaire, X et le panneau compact.
Thor : Codex réel dans un projet d’essai dédié, WAV Thomas sur le moteur local,
commentaire reçu une seule fois, seuil réellement passé de 70 à 80, explication
retournée, croix/stick/tactile/un-deux écrans. Pas de Claude, permission, reboot,
installation autre que `app.regie`. Preuves et pas à pas :
`artifacts/screenshots/10/essai-thor.txt`. Le microphone humain reste à essayer.

## Relecture solo après commit

Relecture de `git diff main...bec880e` (base `1ec1415`), puis des corrections :

- La croix au bord du fichier doit ramener la sélection hors champ, même si le numéro
  ne change pas. `selectionRevision` signale ce geste au défilement Compose ; deux
  régressions domaine/UI rouges puis vertes. Le stick reste un défilement libre.
- Le relâchement du stick reste traité lorsqu’un panneau voix intercepte les entrées.
  Il ne laisse plus de vitesse périmée au retour dans le diff. Régression de la racine
  de composition sous Robolectric, sans démarrer passerelle, voix ni service.
- La réponse d’explication réutilise le rendu Markdown du fil, y compris son curseur
  pendant la réponse. Régression Compose rouge puis verte sur la réponse terminée.
- Le test de demande d’explication vérifie maintenant le message entier contre un
  attendu littéral (instruction, chemin, ligne et bloc), pas seulement des sous-chaînes.

Porte finale : **265 tests Android, zéro échec ni ignoré, lint vert**. APK réinstallé,
Thor : sélection ramenée en vue (15–16), Codex repris pour une seule nouvelle demande
après deux A (seq 3006), Markdown réel en un/deux écrans (23–24), réglages voix ouverts
pendant un mouvement puis relâchement et retour stables (25–27). Quitter a de nouveau
arrêté l’unique session d’essai et le pont à 04:44:27 locale. Aucun autre agent lancé.

## Correction de revue : identité de la sélection

L’index aplati sert à l’affichage, pas à reconnaître une ligne après une mise à jour.
`ReviewDiff` retrouve le chemin, l’identité du bloc et la position dans ce bloc
avant de recalculer l’index. L’éviction d’une édition antérieure ou sa complétion
ne change donc ni la cible du prochain L2 ni le bloc du prochain Y. Si le bloc
raccourcit, la position est bornée à sa dernière ligne. Si le bloc disparaît,
le repli existant sur l’index borné reste applicable ; une liste vide ne propose
aucun commentaire. Il n’y a aucune recherche par texte ou numéro de ligne,
qui peuvent se répéter entre éditions.

Deux régressions reproduites avant correction puis vertes : éviction du premier
bloc parmi trois et complétion d’une édition antérieure en attente. Elles visent
une ligne interne au bloc, vérifient la cible complète du commentaire, y compris
côté supprimé et coordonnées d’extrait, ainsi que la demande d’explication entière.
Porte Android : 267 tests, aucun échec/erreur/ignoré, lint vert. Essai sur la Thor
avec l’historique réel existant : navigation, Y annulé, L2/WAV local annulé,
un/deux écrans. Les deux mutations de blocs sont prouvées dans les tests de domaine,
pas reproduites sur la console. Aucun agent relancé. Preuves `identity-*`.


## Correction de revue : réponse d’une explication mise en file

La position d’un `user_message` ne marque pas le début de sa réponse : le pont le
publie immédiatement, même avec `delivery=queued`. Le contrat existant distingue
les messages en file et ceux injectés dans le tour courant (`steering`).
`ThreadBuilder` décode cette chronologie : une file FIFO des messages attend la
fin de chaque tour (`turn_end` ou `session_error`), tandis qu’un steering devient
la cible des morceaux suivants du tour courant. Il ne crée pas de tour supplémentaire.
Les morceaux de texte de deux tours ne sont jamais concaténés, même sans message
utilisateur entre eux. Aucune modification du protocole ni du pont.

Le domaine reçoit `responseTo` sur textes, erreurs et interruptions, et les
`completedMessages` du fil. Ces identités sont locales au fil, comme ses identifiants
existants ; les fins sont bornées aux messages encore présents et le rechargement
vide la file. Une origine inconnue reste nulle, sans attribution par proximité.
`ReviewDiff` choisit uniquement les éléments liés à sa demande et sa fin explicite,
pas le statut global de la session ni l’arrivée d’un autre message en file.
Le décodage reste dans la passerelle ; la sélection de la réponse à afficher reste
dans le domaine, sans changement de l’interface.

Régression indépendante reproduite rouge puis verte et étendue jusqu’à la bonne
réponse, sa fin, puis une réponse suivante pendant que la session reste occupée.
Décodage couvert avec deux demandes en file, morceaux contigus, steering, annulation,
erreur, éviction et remise à zéro. 271 tests Android et lint verts.
Thor : même Codex repris, Y/A pendant `sleep 25`, attente puis explication écrite en
un/deux écrans (39–42). Le Codex installé accepte cette demande en `steering` :
le scénario exact `queued` est prouvé par les tests, pas par cet essai matériel.
Quitter arrête le Codex et le pont à 05:03:51 locale. Preuves `queued-*`.


## Correction de revue : resynchronisation de l’explication

Les identifiants locaux `ThreadEntry.id` sont reconstruits au chargement du fil :
ils ne permettent pas de dater une demande par rapport à un instantané antérieur.
Le domaine utilise désormais `UserMessage.sequence` et `SessionThread.lastSequence`,
issus du `seq` persistant du journal existant (contrat « Numéros de séquence et
rattrapage »). Ce numéro ne change pas lors d’une relecture du même journal.
La passerelle ne fait que le décoder ; `ReviewDiff` conserve la position à laquelle
Y a préparé la demande et choisit le message identique de séquence supérieure.
Les liens `responseTo` et `completedMessages` restent locaux au même instantané :
ils sont résolus à nouveau depuis le message retrouvé, sans comparer deux générations
d’identifiants locaux. Les valeurs par défaut du modèle servent aux fils synthétiques
sans reconstruction ; le vrai décodeur fournit toujours les séquences du protocole.
Aucun changement du pont, du protocole, des dépendances ou du rendu.

Régression `ResyncedExplanationTest` rouge puis verte : vrai `BridgeState`, relecture
répétée de `session.events`, `hello` avec `resync=true`, nouvelle réponse et fin
malgré le statut global working ; une nouvelle demande identique ne recycle pas
l’ancienne réponse après plusieurs rechargements. Les fixtures réelles vérifient
également les séquences 551 (Claude) et 560 (Codex). Porte Android : 272 tests et
lint verts. Thor : même Codex repris, Y/deux A, un seul message 3398, réponse terminée
3612, un/deux écrans. La resynchronisation exacte est prouvée par le test, pas par
cet essai console. Quitter a arrêté la session et le pont à 05:16:14 locale.

## Correction de revue : sélection pendant resync et arrêt de l’agent

`loaded=false` décrit un instantané incomplet, pas la disparition des éditions.
Le domaine conserve donc la liste et la sélection précédentes pendant le rechargement,
et suspend la navigation, L2 et la préparation par Y jusqu’au journal chargé.
Les blocs sont désormais identifiés par `ToolCall.persistentId` (le `toolCallId`
ACP décodé par la passerelle), puis l’index du fragment dans cet appel. Cette identité
survit aux reconstructions d’identifiants locaux ; le chemin et la position interne
restent les autres composantes de la sélection. Le repli sur l’index borné ne
s’applique qu’après chargement, si l’édition a réellement disparu. Aucun rapprochement
par texte, qui peut être identique dans plusieurs éditions.

La fin explicite de la réponse reste prioritaire. Exception nécessaire : une session
inactive `FAILED` ou `STOPPED` termine aussi une explication envoyée, même sans
`turn_end` ni `session_error`. On conserve le texte partiel et ajoute l’erreur du
pont, ou une indication d’arrêt. Une coupure réseau seule, une session encore active,
une demande non confirmée ou non livrée ne suffisent pas. Une explication terminée
reste figée ; le crash d’un tour suivant ne lui est pas attribué. Le même calcul est
fait au retour de l’accusé d’envoi, car l’arrêt peut avoir été reçu avant cet accusé.
Ces règles sont dans `ReviewDiff`, derrière le port existant ; aucun nouveau protocole,
service, bibliothèque ni changement de maquette.

`DiffRecoveryTest` utilise le vrai décodeur : instantané vide observé séparément,
rechargements, éviction d’une édition antérieure, texte identique dans deux blocs,
cible L2 et bloc Y exacts ; crash sans fin avec réponse partielle et resync ; arrêt
avec demande queued sans réponse ; demande non confirmée et réponse déjà terminée.
Trois tests rouges avant correction, puis quatre verts. Une mutation remettant
l’identité locale fait échouer la sélection même avec la conservation de la liste.
Porte Android : 276 tests sans échec ni ignoré, lint vert.
Thor : même Codex repris, Y/A ligne 2, SIGTERM ciblé sur son adapter après le premier
morceau ; réponse partielle et erreur affichées, un/deux écrans (56–57), retour sur la
ligne 2 (58). Cet arrêt réel émet `session_error` avant `active=false` : le cas sans
aucune fin explicite est prouvé par test uniquement, comme la resynchronisation exacte.

Relecture après f284d9a : cinquième régression pour l’arrêt reçu avant l’accusé
(ici UNKNOWN). L’erreur et le partiel apparaissent au retour de l’accusé, sans autre
événement ni second envoi. Retirer le recalcul au retour d’envoi fait échouer ce test
(recovery-red-receipt.txt). Code de production inchangé, porte finale : 277 tests + lint.
API du test vérifiée avec Context7, library puis docs :
[CompletableDeferred](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-completable-deferred.html)
et `await`/`complete` ; extraits dans recovery-docs-deferred.txt.
