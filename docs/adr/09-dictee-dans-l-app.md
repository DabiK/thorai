# ADR 09 — Dictée relue dans Régie

Statut : accepté, 14 novembre 2026 (ticket 08).

## Architecture et confirmation

`android/` consomme `voice/api`, `voice/local` et `voice/elevenlabs` par le composite build existant, avec le dépôt JitPack limité à sherpa-onnx. Versions inchangées (ADR 04/05), dépendances dans le catalogue. L'APK conserve arm64 et x86_64 pour respecter le lint ABI sans dérogation.

`ReviewDictation` vit dans le domaine Kotlin pur, derrière `DictationCapture`. Il fige la cible et le vocabulaire à l'appui, garde les partiels, attend le relâchement même si le fournisseur finit tôt, puis attend A. B efface ; Y prépare un complément ; X sélectionne un mot (délimité par les espaces), la croix le déplace et L2 le remplace. Une capture échouée conserve le texte déjà relu ; aucun partiel échoué ne le remplace. L1/R1 ne changent pas la cible pendant la dictée.

`VoiceCapture` traduit l'API voix, mesure le RMS réel du PCM et sa durée, sans règle d'envoi. La racine de composition choisit les fournisseurs au début de chaque capture. Locale par défaut, termes favorisés activés conformément à la recommandation de l'ADR 04 et à la décision utilisateur du journal. Aucun moteur de synthèse n'est préparé ni utilisé.

`DeliverDictation` transmet via `SendMessage(dictated=true)`. Un double A est protégé dès le premier appui. Une livraison incertaine reste terminale, sans renvoi automatique. Seul un refus certain de livraison rend le brouillon réessayable.

Cibles :
- session ouverte (ou carte sélectionnée depuis la régie) ;
- validation : L2 prépare un refus avec consigne. A refuse l'identité figée, attend son accusé positif, puis envoie la consigne. `AnswerApproval` est partagé avec la validation ordinaire. Si seul le message échoue, la nouvelle tentative cible le message et ne répète pas le refus ;
- `DictationTarget.DiffComment(sessionId, path, line)` : port d'entrée prêt pour le ticket 10 via `AppContainer.beginDictation`. L'envoi porte `Sur <chemin>, ligne <N> :` puis le commentaire. L'écran du diff reste au ticket 10.

## Vocabulaire du dépôt

Nouvelle lecture authentifiée `session.vocabulary` du pont : `{sessionId}` → `{terms: string[]}`. Le domaine résout le projet de la session avant de demander la lecture au port `Workspace`. Aucun agent n'est lancé par cette lecture, aucune branche n'est changée.

`GitWorkspace` lit `git ls-files --cached --others --exclude-standard` et les références de branches locales. Pour les sources courantes, il extrait les noms de déclarations `function`, `fun`, `def`, `fn`, `func` et les fonctions fléchées. C'est une extraction indicative, pas un index sémantique exhaustif : méthodes Java, surcharges et syntaxes non reconnues peuvent manquer.

Lecture bornée : 400 chemins, sources de 64 Ko maximum, 30 branches, 300 termes de 80 caractères maximum. Fichiers cachés et liens symboliques (parents compris) exclus. Les contenus ne quittent pas le pont, seuls les identifiants sont retournés. Les fichiers ignorés non suivis ne sont pas lus. Les noms suivis par Git restent visibles même si une règle d'ignore est ajoutée ensuite, selon la sémantique de Git.

L'app précharge les termes en consultant les sessions ; changements de branches ou fichiers suivis déclenchent une nouvelle lecture. L2 ne retarde jamais la capture pour le réseau. Si le pont est ancien ou la lecture échoue, dictée disponible avec le contexte connu de la session. Le vocabulaire est un instantané, pas un observateur du disque. Les limites particulières de chaque fournisseur restent celles de l'ADR 04.

## Android et réglages

Les réglages voix sont accessibles sur l'écran tactile, et sur le panneau du mode un seul écran. Fournisseur persisté, clé saisie uniquement par l'utilisateur dans un champ masqué. AES-GCM et Android Keystore, préférences privées exclues des sauvegardes, aucune clé journalisée. Changer de fournisseur ne relance pas l'app.

Le micro utilise `AudioRecord` (16 kHz mono). `ForegroundMicrophone` démarre un service `microphone` seulement pour la capture réelle, après vérification de `RECORD_AUDIO`, puis l'arrête au relâchement, annulation ou erreur. Permissions de premier plan déclarées ; le service n'est pas exporté et ne redémarre pas seul. La permission micro se demande explicitement depuis les réglages voix. L'absence de permission est expliquée dans le parcours, sans envoi.

Revue du 14 novembre : l'ouverture du service, la collecte audio et leur fermeture sont protégées par un `Mutex` commun aux instances de `ForegroundMicrophone`. B annule sans bloquer l'interface, mais la capture suivante attend que l'ancien flux IO ait fini de fermer `AudioRecord` et d'appeler `stopService`. L'ancienne capture ne peut donc plus arrêter le service de la suivante. Annuler une capture qui attend le verrou ne démarre ni n'arrête de service. C'est une gestion de ressource Android dans l'adapter, sans règle métier supplémentaire. Le port `AudioSource` permet de simuler une fermeture retardée dans les tests de `VoiceCapture` et du véritable `ForegroundMicrophone`, sans micro ni service réel.

Sémantique vérifiée avec Context7 pour kotlinx.coroutines 1.11.0 : [`Mutex.withLock`](https://github.com/Kotlin/kotlinx.coroutines/blob/1.11.0/docs/topics/shared-mutable-state-and-concurrency.md) libère le verrou dans `finally` ; la concurrence structurée attend la fermeture des enfants, dont le flux IO de `flowOn`. Régression rouge puis verte, annulation pendant l'attente et récupération après erreur : `ForegroundMicrophoneTest`.

Documentation actuelle vérifiée par Context7 (`ctx7 library Android`, puis `docs`) : [types de service au premier plan](https://developer.android.com/develop/background-work/services/fgs/service-types), [ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE](https://developer.android.com/reference/android/content/pm/ServiceInfo). Le service doit être créé quand l'app est visible, avec la permission micro déjà accordée. Le routeur termine les gâchettes lorsqu'une activité passe en arrière-plan (ADR 05).

## Référence visuelle et essais

Canvas distant inaccessible ; export local `project/Dictee.dc.html` lu en entier dans le cache du canvas, empreinte dans `artifacts/screenshots/08/maquette-sha256.txt`. Structure : texte 25 sp en haut, vocabulaire bleuté, niveau réel et durée en bas, A/B/Y/X. Un petit micro tactile permet aussi de maintenir la capture. Le mode un écran réunit contenu et actions.

En debug uniquement, l'intent `--es voice_wav <nom>.wav` sélectionne un WAV du stockage privé de Régie. Il traverse le véritable fournisseur, la relecture et la livraison ; aucun faux texte ni fausse session n'est injecté. Une valeur qui ne désigne pas un WAV, par exemple `microphone`, rétablit le microphone. Ce réglage n'est pas persisté et n'existe pas en release. Il permet l'essai nocturne avec `say -v Thomas` sans permission micro ni intervention humaine.

Preuves, limites de reconnaissance et marche à suivre avec l'utilisateur : `artifacts/screenshots/08/essai-thor.txt` et rapport du ticket.

Le maintien tactile est suivi par la fenêtre jusqu'au véritable `UP`/`CANCEL` : le remplacement du micro de session par l'écran de dictée ne termine pas le geste. Une fin ou erreur du fournisseur annule aussi sa collecte, même s'il n'a pas encore fermé son flux : le micro ne reste pas détenu après un état terminal. Le texte long se consulte à la croix haut/bas ou au stick droit ; en correction, le mot choisi défile automatiquement en vue.

Relecture après commit : le relâchement applique le dernier état de capture avant de quitter la phase d’enregistrement. Ainsi, un résultat final ou une erreur publié avant la reprise du collecteur ne laisse pas un partiel obsolète dans la relecture. Deux tests d’ordonnancement reproduisent le défaut avant correction.
