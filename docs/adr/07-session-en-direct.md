# ADR 07 — Session en direct : le fil, l'envoi, l'interruption, la navigation

Statut : accepté, 14 novembre 2026 (ticket 06).

## Contexte

Le ticket 06 livre l'écran 3 de la maquette : suivre une session en direct (le fil, le plan, les fichiers modifiés), lui écrire, l'interrompre, passer à la voisine, revenir à la régie. Le port `AgentGateway` de l'ADR 06 ne publiait que les sessions ; rien pour le fil, l'envoi d'un message ni l'interruption.

## Décisions

### Bibliothèque de diff

| Brique | Version | Pourquoi |
| --- | --- | --- |
| java-diff-utils (`io.github.java-diff-utils:java-diff-utils`) | 4.18 (octobre 2026, dernière publiée sur Maven Central) | algorithme de Myers éprouvé, Java pur (bytecode Java 8), sans dépendance, 80 Ko. `DiffUtils.diff(lignes, lignes)` donne des deltas dont `source.size()` et `target.size()` sont les lignes retirées et ajoutées |

- Pas sur context7 : version et API vérifiées sur Maven Central (POM, `javap` du jar).
- Elle sert ici à compter « +N −M » à partir des `oldText` et `newText` d'un diff d'agent. Le ticket 10 (écran du diff) en aura besoin pour le diff ligne à ligne : un algorithme maison aurait été à refaire et à tester deux fois.
- Elle vit dans l'adapter `:gateway-bridge` (`LineDiff`), pas dans le domaine : le domaine ne dépend que des coroutines et reçoit des concepts (`LineChanges`). `ArchitectureTest` interdit `com.github.difflib` au domaine.
- Un fichier créé (`oldText` absent) compte toutes ses lignes en ajout. Le saut de ligne final ne compte pas comme une ligne.

### Markdown des réponses

| Brique | Version | Pourquoi |
| --- | --- | --- |
| commonmark-java (`org.commonmark:commonmark`) | 0.32.0 (novembre 2026, dernière publiée sur Maven Central) | analyseur CommonMark de référence en Java pur (bytecode Java 11), sans dépendance, 220 Ko. Il ne refuse aucun texte |

- Écarté : multiplatform-markdown-renderer de mikepenz (0.46.0). Son AAR exige compileSdk 37 (`minCompileSdk=37`) et Compose 1.12, alors que la console reste en compileSdk 36 et BOM 2026.06.01 (ADR 05).
- commonmark analyse ; `app/ui/markdown/Markdown.kt` réduit l'arbre à des blocs (paragraphe, titre, élément de liste avec puce et profondeur, citation, bloc de code) et à des morceaux stylés (gras, italique, code). L'écran les dessine : titres en gras, code en Geist Mono sur un fond à peine plus clair, liens et images en texte. Un retour simple reste un retour : les agents écrivent ligne à ligne.
- Texte en flux : un bloc ```` ``` ```` encore ouvert va jusqu'à la fin du texte, comme une fois fermé, donc rien ne clignote. Un `**` pas encore fermé reste littéral jusqu'à sa fermeture. Un test analyse chaque préfixe d'une réponse. Le curseur termine le dernier bloc.
- La bibliothèque est dans `:app`, pas dans le domaine : c'est de la mise en forme.

### Port `AgentGateway`

Trois ajouts :
- `thread(id): Flow<SessionThread>` : le fil d'une session. L'observer charge son journal (`session.events`) s'il ne l'est pas encore, y compris au-delà des 4 cartes affichées. Après un `resync`, le journal des fils observés est rechargé, comme celui des cartes ;
- `sendMessage(id, text): Delivery` : `session.prompt`. `PROMPT`, `STEERING`, `QUEUED` et `CANCELLED` viennent du pont. `NOT_SENT` veut dire : pas connecté, erreur du pont, ou pas de réponse en 2 minutes (le temps de recharger une session de l'historique) ;
- `interrupt(id): Boolean` : `session.cancel`.

Les deux passerelles l'implémentent. La factice ajoute au fil le message envoyé (réponse `PROMPT`) et une ligne « Interrompue » ; l'agent enregistré, lui, n'en sait rien.

### Modèle du fil

- **Le port livre des `ThreadEntry`**, une par élément : message de l'utilisateur, texte de l'agent, action (`ToolCall`, avec son genre, son titre, sa commande, son fichier, ses lignes `+/−`, son statut et son résultat), tour en échec, tour interrompu. Plus le plan (`PlanStep` : fait, en cours, à faire) et le contexte (`ContextUsage`).
- **Le domaine met le fil en lignes** (`LiveSession.of`, `ThreadLine`). Les règles de l'écran sont là, testées sans Android :
  - lectures réussies ou en cours consécutives regroupées (« lu · 3 fichiers · a.ts, b.ts, c.ts ») ;
  - curseur en fin du dernier texte tant que la session travaille ;
  - « Toi, dicté » ;
  - ligne « attend ta validation : <sujet> » en fin de fil ;
  - une lecture en échec ou interrompue reste une action avec son statut, jamais un fichier présenté comme lu ;
  - fichiers modifiés et point écrit (`SpokenSummary`, réutilisable par le ticket 12).
- **Décodage ACP** (`ThreadBuilder`, dans l'adapter) :
  - `agent_message_chunk` : morceaux mis bout à bout, un bloc par suite de morceaux ;
  - `agent_thought_chunk` (réflexion) : non affiché ;
  - `tool_call`, `tool_call_update` : mis à jour sur place. Un appel `_meta.regie.hidden` est retiré. Les chemins sont rendus relatifs au projet ;
  - sortie d'une commande (genre `execute` seulement) : `delta` s'ajoute, `final` ou sans indication remplace. Seuls les 4 derniers Ko sont gardés. Le résultat est la dernière ligne non vide, sans les clôtures ```` ``` ```` de Claude ;
  - `turn_end` `cancelled` → « Interrompue » ; `session_error` → « Le tour a échoué : … » ;
  - à la fin d'un tour, une action encore en attente ou en cours passe à « interrompue » (`ActionStatus.INTERRUPTED`) si le tour a été interrompu, sinon elle perd son statut. Codex peut ne rien envoyer immédiatement pour une commande interrompue : sans cela, `sleep 90` resterait « en cours ». Si un statut arrive ensuite, on conserve ce que l'agent expose. L'essai de reprise a reçu `completed` après l'annulation du tour : voir la limite relevée dans `essai-thor.txt`. La carte de la vue d'ensemble suit la même règle.
- **Mémoire** : 300 éléments au plus par session, les plus anciens oubliés. Un texte s'accumule dans un `StringBuilder` et n'est recopié qu'à la publication. Chaque élément garde sa copie immuable tant qu'il ne change pas. Seuls les fils observés en ce moment sont republiés à chaque événement.
- **« dicté »** : le pont ne dit pas d'où vient un message. `DictatedMessages` (domaine) retient, pour le processus, les textes envoyés avec `dictated = true` (ticket 08). Le texte est retenu avant l'envoi et oublié s'il n'est pas parti. Un message du fil est dit dicté si son texte est égal à l'un d'eux. Les réponses `NOT_SENT` et `CANCELLED` oublient ce marquage ; le contrôleur remet le texte non livré dans le champ s'il est encore vide.
- **Plan** : entrées ACP `plan`. Un plan vide, ou pas de plan : « Plan non communiqué par l'agent ». Les enregistrements n'en contiennent aucun ; le décodage est testé sur la forme ACP.
- **Fichiers modifiés** : d'abord `modifiedFiles` de la session (pont), puis les éditions terminées vues dans le fil. Les `+/−` sont cumulés par fichier depuis les diffs du fil, sans chiffres sinon. Un appel portant plusieurs fichiers les conserve tous : le fil cite leurs chemins et le total, le panneau garde le compte propre à chaque fichier (test de régression multi-fichier). Les enregistrements datent d'avant `modifiedFiles` : sans le fil, la liste y serait vide.
- Le pont ne compte pas les fichiers écrits par une commande shell. Liste vide : « Aucune édition suivie », puis, plus discret, « Les fichiers écrits par une commande n'y figurent pas. ». Même chose dans le point écrit. Le bouton X n'affiche pas de compteur à zéro.
- **Contexte** : « contexte N % » (arrondi) seulement si `usage_update` donne `used` et `size`.

### Cas d'usage (domaine)

- `OpenSession(id): Flow<LiveSession?>` : la session et son fil, `null` si elle n'est plus connue.
- `SendMessage(id, text, dictated)` : texte rogné, rien pour un texte vide.
- `InterruptSession(live)` : seulement si la session travaille ou attend une validation (`null` sinon, rien n'est demandé au pont).
- `Overview.neighboursOf(id)` : précédente, suivante, « i / n », dans l'ordre de la vue d'ensemble, toutes les sessions comprises.

### Navigation et commandes

- `AppContainer` tient l'écran affiché (`Screen.Overview` ou `Screen.Session(id)`), le même sur les deux écrans. La manette va au contrôleur de l'écran affiché (`OverviewController` ou `SessionController`).
- En changeant d'écran, les gâchettes tenues sont relâchées une fois. Le texte en cours, l'avis et le point écrit sont oubliés : rien ne part vers la mauvaise session.
- Sur la session :
  - A envoie le texte saisi ; rien ne part sans A ou « Envoyer ». La touche du clavier ferme le clavier sans envoyer ;
  - B revient à la régie, ou ferme le point écrit ;
  - X : avis sobre « Le diff ne se consulte pas encore dans Régie. » ;
  - Y ouvre le point écrit (Y ou B le ferme) et appelle `SpeakSummary`, vide jusqu'au ticket 12 ;
  - Start interrompt sans confirmation, seulement si la session travaille ou attend une validation ; sinon le bouton est éteint ;
  - L1 / R1 : précédente / suivante, **sans boucler**, comme le focus de la vue d'ensemble qui s'arrête aux bords. R1 ne fait que « suivante » ;
  - croix haut / bas : un pas de 120 dp ; stick droit : défilement continu ;
  - L2 / R2 maintenues : cas d'usage de dictée et d'ordre vocal, vides jusqu'aux tickets 08 et 09.
- Le fil est une liste inversée : en bas, un texte qui s'allonge et une ligne qui arrive restent en vue. Remonté, le fil ne bouge plus jusqu'au retour en bas.

### Écran

- Haut :
  - en-tête « Régie › », agent, projet, branche, statut et durée depuis le dernier changement (`updatedAt`), contexte ;
  - le fil ;
  - à droite (236 dp), le plan et les fichiers modifiés (nom du fichier, `+N` vert, `−M` rose de la maquette).
- Bas :
  - « L1 Agent · projet   i / n   Agent · projet R1 » (un côté vide aux bouts) ;
  - le micro et « Maintiens L2 pour lui parler » ;
  - le champ « Écrire un message » ;
  - les 4 boutons de la maquette, tactiles aussi.
- **Le champ de secours est une ligne au-dessus des 4 boutons**, toujours visible, avec « A Envoyer » quand il n'est pas vide. Les avis (envoi, façon dont le message est parti, interruption, diff) prennent 6 s la place de la phrase sous le micro.
- **Clavier** :
  - les activités passent en `adjustResize` ;
  - le champ demande `IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`, ajoutés à l'`EditorInfo` par `InterceptPlatformTextInput`. Sans ces drapeaux, Gboard passait en plein écran sur l'écran du bas, en paysage. Cette API est encore expérimentale dans Compose 1.11 (`@OptIn(ExperimentalComposeUiApi::class)`) : à revoir en montant le BOM, sinon `EditText` dans `AndroidView` avec les mêmes drapeaux ;
  - clavier ouvert, l'écran du bas garde l'en-tête des voisines, l'avis juste au-dessus du champ, et le champ. Le micro et les 4 boutons s'effacent le temps de la saisie, la manette les garde.
- Les actions qui se suivent forment un bloc serré, comme la maquette : 2 dp entre deux lignes, 5 dp de marge verticale, Geist Mono 12 sp sur 16 sp. 11 dp autour des messages et des textes.
- Verbes du fil, selon que l'action est en cours ou finie : lit / lu, modifie / modifié, lance / lancé, puis « action » pour les autres. Une commande dit « en cours », « réussi », « échoué » ou « en attente », après son résultat. La commande en cours a le fond bleu nuit de la maquette. La barre de progression « 42 / 68 » de la maquette n'est pas reprise : aucun agent ne l'expose.
- Mode un seul écran : le panneau du bas de l'écran du haut porte le micro, le champ, X, Y, Start (si utile) et B ; les avis s'affichent juste au-dessus. Les voisines n'y sont pas nommées, L1 / R1 marchent.

## Conséquences

- Le ticket 07 (validation) remplacera la ligne « attend ta validation » par l'ouverture de l'écran de validation. Le ticket 08 appellera `SendMessage(…, dictated = true)`. Le ticket 10 branchera X et réutilisera java-diff-utils. Le ticket 12 lira `SpokenSummary`.

## Vérification de reprise (Codex, 14 novembre 2026)

- API Compose vérifiée avec Context7 : `InterceptPlatformTextInput`, enveloppe de `PlatformTextInputMethodRequest.createInputConnection` puis modification de `EditorInfo.imeOptions`. Source : https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/InterceptPlatformTextInput.composable
- 174 tests et lint passent avec `cd android && ./gradlew check` ; aucun test ne lance un agent ni ne contacte la Thor.
- Reprise sans sous-agent : revue du code et tests de régression écrits avant les corrections (diff multi-fichier, message annulé avant livraison, lecture échouée/interrompue).
- Preuves et limites des essais : `artifacts/screenshots/06/essai-thor.txt`. Claude est confirmé par les captures du passage précédent et son journal réel ; seule une nouvelle session Codex a été lancée pendant cette reprise.
