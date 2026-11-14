# ADR 13 — Fiche de nouvelle session

Statut : accepté, 14 novembre 2026 (ticket 11).

## Parcours et domaine

`ReviewVoiceCommand` transmet un lancement reconnu à `ReviewNewSession` après le
relâchement de R2. Ce passage ouvre la fiche, sans créer de session et sans demander
un A intermédiaire. X ouvre exactement la même fiche avec agent, projet et consigne
vides ; le niveau initial « Plan d’abord » reste visible, comme au ticket 09.

`ReviewNewSession` porte la sélection, les modifications, la capture L2 et le lancement,
derrière `AgentGateway` et `DictationCapture`. La croix choisit un champ puis sa valeur.
La base apparaît sous la branche quand « Nouvelle » est choisie. Les choix de branche
sont « Courante », les branches locales existantes et « Nouvelle ». Changer de projet
efface branche et base. L2 remplace uniquement la consigne, après relâchement et résultat
final. B pendant la dictée revient à la fiche sans changer son ancienne consigne ;
B sur la fiche l’annule. Aucun texte partiel ne rend A disponible.

Un nom de nouvelle branche absent reste absent : l’écran dit « nom choisi par l’agent ».
R2 permet de dicter un nouvel ordre complet, nom et base compris ; le tactile offre
également un champ de nom. Le champ consigne a un secours tactile sur l’écran du bas.
Ces choix réversibles évitent d’inventer un nom à partir d’une consigne ambiguë.

Avant A, aucune création. A fige la fiche et recharge le catalogue ; projet, branche
existante et base doivent encore être disponibles. Une erreur certaine autorise une
nouvelle tentative ; une création incertaine interdit tout nouvel envoi depuis cette
fiche. B permet alors de revenir vérifier la régie. L’identité reçue ouvre directement
la session en direct. Les doubles appuis pendant la requête sont ignorés.

## Branches : consigne à l’agent, jamais mutation Git par Régie

`projects.list` ajoute le champ facultatif `branches`, liste des branches locales.
`GitWorkspace` utilise seulement `for-each-ref` ; les tests vérifient que HEAD et les
modifications non commitées restent inchangés. Le contrat est compatible avec un pont
ancien : sans ce champ, seule la branche courante connue est proposée.

La fiche envoie toute l’intention de branche dans `session.create.prompt`, sans
renseigner `branch`. Le préfixe historique du pont (`branch`) demanderait de créer
depuis `main` et contredirait une base différente ou une branche strictement existante.
Ce contrat ancien est conservé pour ses autres clients. La fiche envoie une seule
consigne précise : « branche existante » ou « crée une nouvelle branche … depuis … ».
`requestedBranch` reste donc nul pour ces créations ; `Session.branch` rapporte la
branche réellement observée. C’est exclusivement l’agent qui réalise les opérations Git.

Une base est obligatoire pour une nouvelle branche ; un nom déjà présent est refusé
par la fiche. Le catalogue reste un instantané, l’agent doit donc vérifier les conflits.
Un test vérifie le prompt exact et l’absence de `branch` pour une base `develop`,
pour `main` et pour une branche existante ; le préfixe hérité ne peut pas s’y ajouter.

## Interprétation et interface

Les règles françaises reconnaissent les trois formes suivantes sans clé ni modèle :
- « Lance Codex sur test pour lire la branche courante » ;
- « Lance Codex sur test, branche lecture, tout valider, pour afficher la branche courante » ;
- « Lance Codex sur test, nouvelle branche essai depuis main, modifs auto, pour afficher la branche courante ».

« Nouvelle branche depuis main » sans nom est également admis. Le texte après « pour »
ou « et demande-lui de » reste la consigne littérale, y compris une négation ou une
condition qui s’adresse à l’agent. Les ordres de contrôle conservent leurs exclusions.

Export local `Lancer.dc.html` lu intégralement, mêmes couleurs et polices, champs à
44 dp, contour de sélection, consigne et ordre entendu. Aucun outil Artifact connecté :
fraîcheur distante non vérifiée. En un écran, liste défilable et actions épinglées ; en
deux écrans, contenu en haut et commandes/correction tactile en bas. La zone d’édition
peut défiler pour conserver les actions visibles, notamment avec le clavier.

API Compose vérifiée par Context7 (`library`, puis deux `docs`) :
[LazyColumn et défilement](https://developer.android.com/develop/ui/compose/lists),
[BasicTextField](https://developer.android.com/develop/ui/compose/text/user-input).
Extraits conservés dans `artifacts/screenshots/11/docs-compose-*.txt`.
Aucune nouvelle dépendance ; versions du socle conservées.

## Constat de resynchronisation hérité

La branche du ticket 11 repartait du socle fusionné, sans commit propre ni modification
non commitée. Le constat `ReviewDiff.afterEntry` était déjà corrigé par le ticket 10 :
`afterSequence` utilise la séquence persistante, puis retrouve les liens locaux au sein
du nouvel instantané (ADR 12). `ResyncedExplanationTest` vérifie la réponse, sa fin et
l’exclusion d’une ancienne demande identique. Réexécuté vert ; mutation remettant la
comparaison d’identifiants locaux : échec. Code corrigé restauré avant la porte complète.
Preuves `resync-check.txt`, `resync-mutation.txt`.

## Corrections issues des essais Thor

Le moteur local transcrit « tout validé » et ajoute des majuscules aux références.
Cette variante phonétique du niveau et la ponctuation finale sont reconnues ; une
référence existante est retrouvée sans tenir compte de la casse uniquement si elle
est unique (correspondance exacte prioritaire). Les noms approchants ne sont jamais
substitués : l’essai « essai / modifs auto » transcrit « EC / modif photo » est refusé.

Un premier R2 peut précéder la fin du chargement des projets au démarrage. La capture
commence immédiatement ; pour un en-tête de lancement reconnu, le catalogue est relu
au moment de l’interprétation, avant la fiche. Les cibles de contrôle (session,
validation) restent figées à l’appui. La fiche puis A revérifient leur propre catalogue.
Régression avec catalogue suspendu : aucun effet ni A possible avant son arrivée.

Lors du premier essai d’une branche existante, « travaille sur … » a été interprété
par Codex comme une règle pour de futures modifications : il a seulement lu `main`.
La consigne exige maintenant explicitement de se placer sur la branche **avant**
d’exécuter la demande, y compris une simple lecture. Pour une branche nouvelle,
elle exige création depuis la base puis placement dessus. Les prompts exacts sont
testés ; les résultats matériels sont détaillés dans `essai-thor.txt`.

Tests de concurrence vérifiés par Context7 sur kotlinx.coroutines 1.11.0 :
[runTest / runCurrent](https://github.com/Kotlin/kotlinx.coroutines/blob/1.11.0/kotlinx-coroutines-test/README.md),
avec `CompletableDeferred` pour garder une création en attente. Ni B, ni X, ni une
modification du brouillon ne peut provoquer un deuxième lancement pendant cette attente.


## Reprise finale et preuves

Le travail non commité des passages précédents a été conservé. Le 14 novembre,
`ResyncedExplanationTest` passe à nouveau ; la mutation temporaire qui remet
`id > afterEntry` échoue, puis le code du socle est restauré. Les traces préfixées
`reprise-` distinguent ces vérifications des essais antérieurs.

Android : 294 tests et lint verts ; pont : 271 tests, typage et lint verts.
Trois parcours de lancement réel sont prouvés par les journaux Codex : courante
main, existante lecture, nouvelle travail depuis lecture. Les trois fiches ont été
rejouées avec WAV Thomas sur l'APK réinstallé ; travail existant désormais, le
nouveau lancement est correctement bloqué. Correction croix, L2, B et mode un écran
revérifiés sans création. Quitter a arrêté les quatre sessions Codex d'essai et le
pont à 09:04:35 Paris. Méthode, limites ASR et pas à pas humain dans
`artifacts/screenshots/11/essai-thor.txt` ; aucune preuve de voix humaine revendiquée.


## Correction de revue : consigne longue au stick

Le stick droit pilote maintenant la liste du haut, quelle que soit l'activité qui
reçoit la manette, également en mode un écran. Les croix gardent leur rôle de sélection
et de correction ; A/B restent épinglés. L'interface annonce le défilement au stick.
Vitesse maximale de 500 dp/s, proportionnelle à l'axe et au temps entre images, comme
le diff. C'est un choix d'interface réversible ; aucune règle de lancement ne change.
Le relâchement est pris en compte même derrière les réglages ou l'ordre vocal.
Ouverture, A, B et passage à R2 remettent également la vitesse à zéro.

`LongInstructionReviewTest` exerce le vrai routeur et la racine de composition sans
les démarrer, puis le rendu Compose : consigne valide de 1 950 caractères,
dernière ligne initialement coupée, entièrement visible après le stick, marche
arrière et relâchement derrière les réglages. Deux tests rouges puis verts, un/deux
écrans. La mesure porte sur les coordonnées de la dernière ligne, pas sur la simple
présence du texte dans l'arbre d'accessibilité. Aucun service ni agent dans ces tests.
API vérifiée via Context7 : `ScrollableState.scrollBy`, `withFrameNanos`,
`MainTestClock` et `getUnclippedBoundsInRoot` ; sources dans `scroll-docs-*.txt`.

Android : 296 tests et lint verts ; pont : 271 tests, typage et lint verts.
`ResyncedExplanationTest` du socle réexécuté sans cache : vert. Thor : WAV Thomas
01 relu par le moteur local, consigne longue saisie au secours tactile, stick par
sendevent, fin visible en un/deux écrans, arrêt stable et B. La saisie adb rapide
a perdu des caractères : 1 670 réellement affichés et relevés par uiautomator,
contre 1 950 exactement dans le test JVM. Aucune preuve de dictée longue humaine.
Captures `scroll-51` à `scroll-54`, protocole dans `essai-thor.txt`. Aucun agent lancé ;
Quitter a arrêté l'app et le pont à 09:20:58 Paris. Micro réel et extinction physique
restent au pas à pas utilisateur. Le débogage sans fil des agents reste hors périmètre.


## Ticket 15 — Catalogue vivant et nouveau projet

La fiche observe la connexion et recharge `projects.list` à l’ouverture et à chaque
reconnexion. Une requête remplacée est annulée ; elle ne remplace pas les choix ou
la consigne déjà saisis. La ligne Projet distingue chargement et pont indisponible.
Le catalogue est encore vérifié avant A ; un échec bloque toute création.

Dernier choix à gauche/droite : « Nouveau projet… ». L2 sur cette ligne remplace
le nom uniquement ; sur les autres lignes, il conserve son rôle de dictée de la
consigne. Le nom normalisé et son chemin sont relus avant A. Un champ tactile de
secours est disponible en bas. La règle R2 reconnaît « lance Codex sur un nouveau
projet qui s’appelle météo », avec consigne facultative après « pour ».
Les champs de branche sont masqués pour ce dépôt vide, initialisé sur main.
Un nom invalide ou déjà listé bloque A ; le pont refuse aussi les collisions
survenues entre la lecture du catalogue et la création. Les erreurs de projet
certaines sont affichées, distinctes d’un accusé perdu (aucun renvoi).

La maquette locale Lancer.dc.html a été relue intégralement, même export que le
ticket 11 ; l’outil Artifact distant reste indisponible. Couleurs, polices, lignes
et actions sont conservées. Aucun fournisseur vocal ajouté : moteur local existant.
