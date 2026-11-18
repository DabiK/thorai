# ADR 04 — La voix : ports, ElevenLabs, alternative locale

Statut : accepté, ticket 04 fusionné. Choix actuel : transcription locale par défaut, aucune lecture à voix haute dans Régie (ADR 09/10). Les essais humains et cloud non réalisés restent identifiés dans les rapports ; ils ne conditionnent plus le choix du fournisseur. Le détail ci-dessous retrace le laboratoire du 13 novembre, y compris sa synthèse vocale.

## Contexte

- Le PRD (« Voix ») demande trois ports : transcription en flux avec partiels, langue et termes à favoriser ; synthèse en flux ; détection de parole sur l'appareil. ElevenLabs était le choix initial ; il a été remplacé par le local par défaut après l’essai utilisateur du 13 novembre.
- La Thor n'a aucun moteur de reconnaissance ni de synthèse Android (`docs/thor.md`, « Voix ») : tout passe par nos adapters.
- L'ADR 01 place la voix dans un build Gradle séparé, `voice/`, inclus par l'app en composite build.
- La dictée se fait en maintenant L2 : début d'appui = début de dictée, relâcher = fin de parole.

## Décision

### Build

- `voice/` est un build Gradle autonome :
  - wrapper Gradle 9.9.1, car AGP 9.5.1 exige Gradle 9.6 ou plus (la phase 1 était en 9.3.1) ;
  - Kotlin 2.4.21, cible JVM 17, comme `android/` (ticket 01), qui inclura ce build ;
  - avertissements traités comme des erreurs.
- Versions des bibliothèques :
  - kotlinx-coroutines 1.10.2 ;
  - kotlinx-serialization-json 1.9.0, sans le plugin : on lit et on écrit des `JsonObject` ;
  - OkHttp 5.4.0 et mockwebserver3 5.4.0. La phase 1 était en 5.5.1, mais `okhttp-android` 5.5.x exige `compileSdk` 37, alors que le Mac n'a que les plateformes 34 à 36 et qu'`android/` compile en 36. La 5.4.0 demande 36 ;
  - JUnit 5.14.4. Les modules Android ajoutent `kotlin-test-junit5`, qu'ils ne choisissent pas tout seuls ;
  - AGP 9.5.1, `compileSdk` 36, `minSdk` 33 (Android 13 sur la Thor), Compose BOM 2026.06.01, comme `android/` ;
  - sherpa-onnx v2.0.1 et commons-compress 1.28.0 (voir plus bas).
- Les modules ont le groupe `app.regie.voice`. L'app les obtient par substitution : `includeBuild("../voice")` puis `implementation("app.regie.voice:api")`. La substitution a été vérifiée avec un projet consommateur jetable.
- La porte de qualité se lance par `./gradlew check` depuis `voice/`, avec le JDK par défaut du Mac (Temurin 25). Aucun `org.gradle.java.home` n'est versionné. Elle comprend les tests JVM de tous les modules et le lint Android de `:local` et `:demo`. Le SDK est lu dans `voice/local.properties`, qui n'est pas versionné.
- Modules :
  - `:api` : Kotlin pur, seule dépendance kotlinx-coroutines ;
  - `:fake` : JVM ;
  - `:elevenlabs` : JVM, avec OkHttp ;
  - `:local` : bibliothèque Android, avec sherpa-onnx ;
  - `:demo` : application Android `app.regie.voicelab`, racine de composition de l'appli d'essai.
- Un test d'architecture (`api/src/test/.../ArchitectureTest.kt`) lit les sources de tous les modules et vérifie que :
  - `:api` n'importe que Kotlin, les coroutines et le JDK ;
  - un adapter n'importe aucun autre adapter, et son build ne dépend que de `:api`. Cela couvre `:local`.
  
  `demo`, racine de composition de l'appli d'essai, est exempté.

### Les ports (`voice/api`, paquet `app.regie.voice.api`)

- **Audio** :
  - `AudioFormat` : PCM 16 bits, mono, little-endian ; seule la fréquence varie. `AudioFormat.SPEECH` vaut 16 kHz.
  - `AudioFrame` : des échantillons `ShortArray` et leur format.
  - `AudioSource` : un flux froid de trames. Collecter démarre la capture, annuler l'arrête. Le micro Android viendra en phase 2 ; `WavAudioSource` rejoue un WAV, au rythme réel ou d'un coup.
  - `AudioSink.play(flow)` : jouer ; annuler l'appelant coupe le son.
- **Transcription** : `SpeechToText.transcribe(audio, DictationRequest(language, vocabulary)) : Flow<DictationState>`. Les adapters respectent tous le même contrat :
  - le flux est froid ;
  - l'audio est lu dès le début, pour ne rien perdre pendant la connexion ;
  - la fin du flux audio signifie la fin de parole : l'adapter émet alors `Finalizing`, puis `Final` ;
  - les erreurs arrivent en état `Failed`, elles ne sont pas levées ;
  - annuler la collecte annule la dictée.
- **États de dictée** : `PreparingModel(progress)` (pour `:local`), `Connecting`, `Listening(partial)`, `Finalizing(partial)`, `Final(text)`, `Failed(error)`, `Cancelled`.
- **`Dictation`** : branche la transcription sur un bouton à maintenir. `startDictation(scope, source, request)` se lance à l'appui ; `finish()` au relâcher arrête la capture et demande le texte final ; `cancel()` abandonne la dictée. `state` est un `StateFlow` pour l'interface.
- **Erreurs** (`VoiceError`) : `Offline`, `KeyMissing`, `KeyRejected`, `QuotaExceeded`, `RateLimited`, `ModelMissing`, `NotConfigured(setting)`, `Provider(code, message)`. Les flux de synthèse les lèvent dans une `VoiceException`.
- **Synthèse** : `TextToSpeech.synthesize(text, SpeechRequest(language)) : Flow<AudioFrame>`. Pour interrompre, on annule la collecte. `voices()` liste les voix proposées ; la voix choisie est un réglage de l'adapter.
- **Détection de parole** : `VoiceActivityDetector.process(frame) : SpeechActivity` (`SPEECH` ou `SILENCE`), avec un état interne et `reset()`.
- **Vocabulaire** : `Vocabulary(terms)`, par ordre de priorité, lisible depuis un fichier (`Vocabulary.parse`). Chaque adapter applique ses propres limites.
- **Choix du fournisseur** : chaque adapter expose un `VoiceProvider` (`id`, `displayName`, `speechToText`, `textToSpeech`, `prepare()`). La racine de composition les range dans un `VoiceProviders` ; l'app y prend celui dont l'`id` est en réglage (`elevenlabs`, `local`, `fake`). Comme ce réglage est lu au début de chaque dictée, on change de fournisseur sans relancer l'app. `prepare()` rend `Ready`, `Downloading(progress)` ou `Unavailable(error)`, ce qui couvre la clé absente et le modèle à télécharger.

### ElevenLabs (`voice/elevenlabs`)

- **Pas de SDK officiel Java ni Kotlin** pour la transcription et la synthèse : les SDK officiels sont en Python et en JS, et `io.elevenlabs:elevenlabs-android` ne couvre que les agents conversationnels. On a donc écrit l'adapter avec OkHttp (WebSocket et HTTP).
- **Transcription : Scribe en temps réel.**
  - Connexion WebSocket à `/v1/speech-to-text/realtime`, clé dans l'en-tête `xi-api-key`.
  - Paramètres : `model_id`, `audio_format=pcm_16000`, `language_code`, `commit_strategy=manual`, `keyterms`.
  - **Modèle** : `scribe_v3_live` par défaut (sorti le 10 novembre 2026), `scribe_v2_realtime` en option (`ScribeModel`).
  - **Fin de dictée** : on envoie un morceau vide avec `commit: true`, comme le `commit()` du SDK JS. Le serveur répond par `committed_transcript`, qui donne le texte final.
  - **Délai** : sans texte final au bout de 10 s (réglable), la dictée échoue au lieu de rester bloquée.
  - **Keyterms** : le paramètre est répété dans l'URL (`keyterms=A&keyterms=B`). C'est vérifié dans le code du SDK JS officiel (`elevenlabs-js`, `src/wrapper/realtime/scribe.ts` : `params.append("keyterms", term)`) et dans le guide « Keyterm prompting », qui donne l'exemple `?model_id=scribe_v3_live&keyterms=ElevenLabs&keyterms=AnotherTerm`.
  - **Limites en temps réel** : 50 termes au plus, de 20 caractères au plus. L'adapter :
    - garde les 50 premiers termes utilisables ;
    - écarte un terme trop long au lieu de le couper, car un terme coupé favoriserait un mot qui n'existe pas ;
    - retire les doublons, sans tenir compte de la casse.
  - **Erreurs** (`message_type`) : `auth_error` → `KeyRejected` ; `quota_exceeded` → `QuotaExceeded` ; `rate_limited` et `commit_throttled` → `RateLimited` ; les autres → `Provider(type, error)`. Les autres types sont `error`, `transcriber_error`, `unaccepted_terms`, `input_error`, `invalid_request`, `queue_overflow`, `resource_exhausted`, `session_time_limit_exceeded`, `chunk_size_exceeded` et `insufficient_audio_activity`. Le type s'écrit bien `unaccepted_terms`, pas `unaccepted_terms_error` : seul le nom d'événement du SDK JS porte le suffixe.
  - **Échec de la connexion initiale** : une réponse HTTP 401 ou 403 donne `KeyRejected`, 429 donne `RateLimited`, et `detail.status = quota_exceeded` donne `QuotaExceeded`. Un échec réseau ou une coupure en cours de dictée donne `Offline`.
- **Synthèse : Flash v2.5.**
  - Requête `POST /v1/text-to-speech/{voice_id}/stream?output_format=pcm_22050`, avec le corps `{text, model_id: "eleven_flash_v2_5", language_code}`.
  - La réponse, du PCM 16 bits mono, est lue au fil de l'eau : chaque lecture donne une trame d'au plus 100 ms.
  - Interrompre annule la requête HTTP.
- **Voix de synthèse** : il n'y a pas de `voice_id` par défaut, car les voix par défaut d'ElevenLabs expirent le 31 décembre 2026. C'est un réglage ; `voices()` (`GET /v1/voices`) les liste pour qu'on en choisisse une.
- **La clé** : elle est lue à chaque usage par une fonction fournie par l'app (`ElevenLabsConfig.apiKey`). C'est l'utilisateur qui la saisit dans l'app ; elle n'est jamais dans le dépôt.
- **Prix** : vérifiés le 13 novembre 2026 sur https://elevenlabs.io/pricing/api.
  - Scribe v3 Live et v2 Realtime : 0,22 $ de l'heure, plus 0,08 $ de l'heure avec les keyterms (ou la bibliothèque de termes de v3 Live) ;
  - Flash : 0,04 $ pour 1 000 caractères.

### Factice (`voice/fake`)

- `FakeSpeechToText` rejoue des textes scriptés, une dictée par script, en boucle :
  - il émet un partiel par trame reçue, puis le texte final à la fin de l'audio ;
  - il peut simuler une erreur, à la connexion ou après n trames.
- `FakeTextToSpeech` produit du silence, d'une durée proportionnelle au texte, et garde les textes demandés.
- `FakeVoiceActivityDetector` décide sur l'énergie du signal, avec un maintien après la parole.
- `RecordingAudioSink` garde les trames au lieu de les jouer.
- `FakeVoiceProvider` réunit ces faux.

### Corpus de mesure

- `voice/corpus/consignes.txt` : 20 consignes typiques, en français mêlé de termes de code.
- `voice/corpus/termes.txt` : les termes du dépôt à favoriser.
- `scripts/voice-corpus.sh` génère un WAV par consigne (16 kHz, mono, 16 bits), lu par la voix « Thomas » de macOS (`say` puis `afconvert`).
- Trois de ces WAV sont versionnés dans `voice/testdata/wav/` pour les tests ; ils font moins de 135 Ko chacun.

### Tests sans réseau

- Les tests d'ElevenLabs passent par un `MockWebServer` local. Ce faux serveur Scribe rejoue des messages en JSON (`elevenlabs/src/test/resources/scribe/`) et reçoit les trames d'un enregistrement WAV.
- Ils vérifient :
  - l'URL et ses keyterms filtrés ;
  - l'en-tête de clé ;
  - l'audio envoyé, octet pour octet, puis le commit ;
  - les partiels et le texte final ;
  - chaque type d'erreur ;
  - les refus à la connexion ;
  - la coupure réseau ;
  - le délai sans texte final ;
  - la fermeture de la socket quand on annule.
- Pour la synthèse, ils vérifient la requête, le PCM reconstitué malgré des morceaux de taille impaire, l'annulation de la requête HTTP quand on interrompt, les erreurs HTTP et la liste des voix.
- **Les messages Scribe des fixtures suivent la documentation, ils n'ont pas été capturés sur une vraie session** : l'agent n'a pas de clé. Il faudra les remplacer par une capture réelle en phase 2.

### Locale (`voice/local`)

- **sherpa-onnx v2.0.1 (26 octobre 2026), par JitPack** : `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v2.0.1`.
  - Il n'est pas sur Maven Central. Le dépôt JitPack est déclaré dans `voice/settings.gradle.kts` et limité à ce groupe.
  - L'AAR fait 49 Mo, pour 4 ABI, et contient les classes Kotlin `com.k2fsa.sherpa.onnx` : il n'y a rien à copier. L'appli ne garde que `arm64-v8a` (`abiFilters`).
  - Les signatures ont été vérifiées par `javap` sur l'AAR.
  - Repli possible : l'AAR des releases GitHub (`sherpa-onnx-2.0.1.aar`), mais une dépendance fichier ne passe pas d'un module à l'autre.
- **Isolement** : seul `SherpaOnnxEngines.kt` importe `com.k2fsa`. Le reste de l'adapter passe par trois petites interfaces internes :
  - `SegmentDecoder` (Parakeet) ;
  - `SpeechSegmenter` (Silero) ;
  - `SpeechSynthesizer` (Supertonic).
  
  Les tests JVM remplacent ces interfaces par des faux, sur les WAV de `voice/testdata/wav/`, sans code natif ni modèle.
- **Modèles**, tous tirés des releases officielles de k2-fsa/sherpa-onnx (`asr-models` et `tts-models`). Ils sont téléchargés au premier usage dans `filesDir/voice-models`, jamais dans le dépôt ni dans l'APK.

  | Rôle | Modèle | Téléchargé | Installé |
  | --- | --- | --- | --- |
  | Transcription | `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (25 langues européennes, dont le français) | 487 Mo | 672 Mo |
  | Détection de parole | `silero_vad.onnx` | 0,6 Mo | 0,6 Mo |
  | Synthèse | `sherpa-onnx-supertonic-3-tts-int8-2026-05-11` (31 langues, dont le français, 10 voix) | 129 Mo | 145 Mo |
  | Total | | 617 Mo | 818 Mo |

- **Téléchargement** (`ModelStore`, OkHttp) :
  - le fichier arrive dans `<fichier>.part` et reprend par `Range` après une coupure. Si le serveur ignore le `Range`, on repart de zéro ; s'il répond 416, la partie est périmée et on la jette. Le fichier n'est renommé qu'une fois complet ;
  - la place libre est vérifiée avant de télécharger : ce qui reste à télécharger, plus la taille installée, plus 50 Mo ;
  - l'archive `.tar.bz2` est extraite avec commons-compress dans un dossier `.extracting`. Une entrée qui sortirait du dossier est refusée. Le dossier est ensuite renommé, le marqueur `.installed` est écrit en dernier et l'archive est supprimée. Une archive corrompue est supprimée, pour être téléchargée de nouveau ;
  - la progression compte le téléchargement et l'extraction. Elle s'expose par `ProviderStatus.Downloading` et `DictationState.PreparingModel` ;
  - deux appels simultanés ne téléchargent qu'une fois ;
  - **non vérifié** : aucune somme de contrôle, car les releases n'en publient pas à côté des fichiers.
- **Transcription en « flux simulé »** (`SegmentedTranscriber`). Parakeet n'est pas un modèle en flux :
  - Silero ne fait que **placer les coupures**, sur une fin d'énoncé : 0,3 s de silence, ou 12 s de parole au plus ;
  - Parakeet décode **tout l'audio entre deux coupures**, et pas seulement le segment que Silero a gardé. Sur la Thor, décoder les seuls segments de Silero perdait le premier mot de chaque consigne (« Lance », « Mets »), dit avant que Silero se déclenche ;
  - pendant la parole, l'énoncé en cours est redécodé toutes les 700 ms d'audio, ce qui donne le partiel ;
  - au relâcher, l'énoncé en cours est fermé et décodé. La queue est décodée si de la parole y a été entendue, ou si rien n'a été reconnu (un mot trop court pour Silero) ;
  - l'audio est mis en file dès le début, y compris pendant le chargement ou le téléchargement du modèle : rien ne se perd ;
  - les modèles sont chargés une fois, sur un fil réservé (`limitedParallelism(1)`), jamais sur le fil principal. `prepare()` les précharge, `release()` les libère. Le chargement de Parakeet prend environ 2,3 s sur la Thor.
- **Piste non retenue** : `sherpa-onnx-streaming-zipformer-fr-kroko-2025-08-06`, un vrai modèle en flux pour le français (57 Mo). Aucune mesure n'est publiée ; à essayer si la latence de Parakeet déçoit.
- **Termes à favoriser (hotwords)** : sherpa-onnx v2.0.1 les accepte pour Parakeet TDT, à condition de passer en `modified_beam_search`. On reprend l'exemple officiel `test_asr_non_streaming_nemo_parakeet_tdt_v2_hotwords.js` :
  - un `bpe.vocab` est dérivé de `tokens.txt` ;
  - `modelingUnit = "bpe"` et `hotwordsScore = 2.0` ;
  - le vocabulaire de la dictée est passé à chaque flux (`createStream(hotwords)`), les termes séparés par `/`.
  
  C'est un réglage (`LocalConfig.hotwords`), **désactivé par défaut** : la recherche en faisceau est plus lente. Changer ce réglage recharge le décodeur. Essayé sur la Thor : pas de plantage, le texte change ; l'effet est à mesurer.
- **Synthèse : Supertonic 3, et non Piper.**
  - sherpa-onnx 2.x a retiré espeak-ng : `data_dir` est ignoré, et une voix Piper demande un lexique ou des phonèmes calculés par l'app (`phoneme_codepoints`). Sur la Thor, `vits-piper-fr_FR-siwis-medium` ne produisait aucun son (« no lexicon, and no frontend available »).
  - Supertonic 3 a son propre frontal texte. La langue de `SpeechRequest` lui est passée (`extra = {lang: "fr"}`), avec 8 pas de calcul.
  - Ses 10 voix sont listées par `voices()` ; la voix est un réglage (`LocalConfig.speaker`).
  - Licences : le code est sous MIT, le modèle sous OpenRAIL-M (restrictions d'usage, compatibles avec un usage personnel).
  - Le texte est découpé en phrases dans l'adapter (après `.`, `!`, `?`, `…`). Sans ce découpage, Supertonic génère tout le texte avant le premier son : 3,3 s pour trois phrases, contre 0,8 s une fois découpé.
  - La génération tourne à part de la collecte, parce qu'un appel natif ne s'interrompt pas. Annuler coupe donc la voix tout de suite ; le moteur s'arrête à son prochain rappel. Sur la Thor, la coupure est passée de 480 ms à 53 ms après R2.
  - Le rappel natif doit être une classe et non un lambda. Le JNI cherche `invoke([F)Ljava/lang/Integer;`, qu'un lambda Kotlin 2 (invokedynamic désucré par D8) n'a pas : le processus s'arrêtait.
- **Détection de parole** : `LocalVoiceActivityDetector` (Silero) implémente le port. On l'obtient par `LocalVoiceProvider.voiceActivityDetector()` et on le ferme après usage.
- **Mesure de vitesse** : `LocalSpeechToText.lastStats` donne la durée de l'audio et le temps de décodage, partiels compris. On en tire le facteur temps réel.

### Appli d'essai (`voice/demo`, `app.regie.voicelab`)

- **Racine de composition** : elle construit ElevenLabs et le local.
  - Le fournisseur, le modèle Scribe, la voix et les hotwords sont des réglages (`SharedPreferences`), lus à chaque usage. On change donc de fournisseur sans relancer.
  - Le fournisseur ElevenLabs est reconstruit quand le modèle Scribe change.
  - Quand le local est choisi et installé, ses modèles sont préchargés.
- **Affichage** : écran du haut, fond sombre, gros caractères, textes en français. L'écran du haut n'étant pas tactile, la clé se saisit en lançant l'appli sur l'écran du bas (`voice/README.md`) ; la mise en page s'adapte aux deux largeurs.
- **Clé ElevenLabs** :
  - champ masqué, saisi par l'utilisateur ;
  - chiffrée en AES-256-GCM par une clé de l'Android Keystore, qui n'en sort pas. Seuls l'IV et le chiffré sont gardés, dans des préférences privées ;
  - sauvegardes exclues : `allowBackup="false"`, plus des règles d'extraction qui excluent tout ;
  - jamais journalisée ;
  - pas d'`EncryptedSharedPreferences`, qui est déprécié.
- **L2 et R2** : on enveloppe le `Window.Callback`, comme le ticket 01, car `dispatchKeyEvent` est restreint sur `ComponentActivity`. Une gâchette est appuyée si la touche **ou** l'axe (`AXIS_LTRIGGER`/`AXIS_BRAKE`) le dit, avec une hystérésis de 0,5 à l'appui et 0,2 au relâcher. Un appui donne un seul événement, quel que soit l'ordre des deux signaux.
  - L2 maintenu : dicter.
  - Un appui sur L2 coupe aussi la voix avant de dicter ; R2 la coupe seulement.
- **Micro** : `AudioRecord`, source `VOICE_RECOGNITION`, 16 kHz mono, par trames de 100 ms.
- **Sortie** : `AudioTrack` en flux, écrit par tranches de 50 ms pour pouvoir couper vite.
- **Mesures** affichées après chaque dictée : la latence du relâcher de L2 au texte final, la durée de l'audio et, pour le local, le facteur temps réel.
- **Vocabulaire** : le dossier `voice/corpus/` est une source d'assets de l'APK. `termes.txt` et `consignes.txt` y sont donc sans copie à la main.
- **Banc d'essai pilotable par adb** : les commandes sont dans `voice/README.md`.
  - Les WAV et `consignes.txt` se poussent dans `/sdcard/Android/data/app.regie.voicelab/files/corpus/`, ce qui marche sur la Thor en Android 13.
  - Chaque WAV est rejoué au rythme réel, comme une dictée. La fin de parole est la fin du WAV, une trame après la dernière émise, comme pour un micro.
  - Pendant un banc ou une lecture guidée, une commande adb est refusée en entier, réglages compris, avec un message à l'écran ; seul `--es guided stop` passe. Un réglage changé en route mélangerait les mesures (le décodeur local serait même rechargé). Les réglages notés dans le rapport sont lus au début.
  - Le banc écrit `bench-<fournisseur>-<horodatage>.json` et `.csv` dans `files/`. On y trouve par consigne l'attendu, l'obtenu, la latence, la durée, le WER (minuscules, ponctuation retirée) et les termes de `termes.txt` retrouvés ou manqués ; puis les moyennes et les médianes.

## Ce qui reste

- Les mesures, sur la Thor, avec les 20 consignes lues par l'utilisateur, pour mesurer la qualité sur une voix humaine ; le fournisseur local est déjà décidé.
- Remplacer les fixtures Scribe par une capture réelle : cela demande la clé de l'utilisateur.
- Piste à évaluer avec les mesures : la `keyterm_library` de Scribe v3 Live (jusqu'à 10 000 termes de 50 caractères, envoyée dans le premier morceau d'audio). Elle lèverait la limite de 50 termes et de 20 caractères, par exemple pour `kotlinx-coroutines-test`.

## Mesures

### Banc du 13 novembre 2026, voix de synthèse

Mesuré sur la Thor avec le banc de l'appli d'essai (`--es bench local`), sur les 20 consignes de `voice/corpus/`. Les enregistrements viennent de `scripts/voice-corpus.sh` (`say -v Thomas`, 16 kHz). Ils durent 3,8 s en moyenne et sont rejoués au rythme réel. Rapports bruts : `bench-local-20261113-222537` (sans termes favorisés) et `bench-local-20261113-222730` (avec), dans le dossier `files/` de l'appli.

| Fournisseur | Latence fin de parole → texte final (médiane, max) | WER moyen | Termes du dépôt reconnus | Facteur temps réel |
| --- | --- | --- | --- | --- |
| Local, Parakeet TDT v3 int8, sans termes favorisés | 223 ms, 367 ms | 0,29 | 14 / 45 | 0,22 |
| Local, avec termes favorisés (`modified_beam_search` + hotwords) | 279 ms, 582 ms | 0,21 | 22 / 45 | 0,27 |
| ElevenLabs Scribe v3 Live | à mesurer par l'utilisateur (pas de clé) | | | |
| ElevenLabs Scribe v2 Realtime | à mesurer par l'utilisateur (pas de clé) | | | |

- **Erreurs grossières**, en local : le français courant passe bien, mais les identifiants se déforment. Par exemple : « Session et vent, renomme le chant Imestan » pour `SessionEvent`/`timestamp`, « Sherpa Hunts », « trait Slay » pour `Thread.sleep`, « Termu », « ProDistro », « la scie » pour la CI. Avec les termes favorisés, on retrouve `AgentGateway`, `MainActivity`, `StateFlow`, `kotlinx-coroutines`, `on-request` et `regie`.
- **Le banc est pessimiste pour les termes** : la voix « Thomas » de macOS lit elle-même les mots anglais à la française (`timestamp`, `tool_call`, `gradlew`). Il faut refaire la mesure avec la voix de l'utilisateur.
- **La latence locale est très bonne** : la transcription du dernier segment tient en un quart de seconde, sur l'appareil, sans réseau.
- Scribe n'a pas pu être mesuré : la clé reste à saisir par l'utilisateur. Le connecteur ElevenLabs de la session ne propose que `eleven_scribe_v1` en différé, qui ne représente pas v3 Live ; on ne l'a pas utilisé.

### Recommandation initiale, remplacée après l’essai utilisateur

Le local est désormais le défaut retenu, avec termes favorisés dans Régie (ADR 09).
La comparaison cloud ci-dessous est facultative et historique.

- **Fournisseur par défaut : ElevenLabs, Scribe v3 Live** (choix du PRD), **sous réserve** de la mesure de l'utilisateur. Le local reconnaît mal les termes de code, alors que c'est le cœur des consignes ; Scribe v3 Live accepte 50 keyterms, ou une bibliothèque de termes. Si Scribe ne fait pas nettement mieux que 22 / 45 sur les termes avec la vraie voix, il faut revoir ce choix.
- **Repli local : activer les termes favorisés par défaut.** Ils font gagner 8 points de WER et 8 termes, pour environ 55 ms de latence en plus.

### Banc comparatif facultatif du laboratoire (hors installation de Régie)

1. **Saisir la clé**, sur l'écran du bas, qui est tactile : `adb -s $THOR_SERIAL shell am force-stop app.regie.voicelab`, puis `adb -s $THOR_SERIAL shell am start --display 4 -n app.regie.voicelab/.MainActivity`. Toucher « ElevenLabs » dans « Fournisseur » (pas celui de « Lecture guidée du corpus », plus bas), coller la clé dans « Clé ElevenLabs », toucher « Enregistrer la clé » : « Clé enregistrée (chiffrée) » s'affiche. Toucher « Charger les voix » et en choisir une, française de préférence.
2. **Vérifier la synthèse** : toucher « Lire » ; « Couper (L2/R2) » ou L2 coupe la voix.
3. **Lire les 20 consignes avec ElevenLabs**, sur l'écran du haut. L'appli est restée ouverte sur l'écran du bas à l'étape 1 : la fermer d'abord, puis la relancer en haut avec la lecture guidée.

   ```bash
   adb -s $THOR_SERIAL shell am force-stop app.regie.voicelab
   adb -s $THOR_SERIAL shell am start --display 0 -n app.regie.voicelab/.Adb --es scribe scribe_v3_live --es guided elevenlabs
   ```

   Pour chaque consigne affichée en haut : maintenir L2, la lire normalement, relâcher. B fait recommencer la consigne. Le micro est enregistré dans `files/corpus/user/`. Après la dernière, « Fini : guided-elevenlabs-….json » s'affiche.
4. **Rejouer la même voix dans les autres fournisseurs**, sans relire. Chaque commande commence par `adb -s $THOR_SERIAL shell am start --display 0 -n app.regie.voicelab/.Adb` et porte ses réglages. Avant la suivante, attendre « fini : bench-… » à l'écran (ou dans `adb logcat -s VoiceLab`) : une commande envoyée pendant un banc est refusée en entier, avec un message rouge à l'écran.
   - `… .Adb --es guided stop` : quitte la lecture guidée, sinon les commandes suivantes sont refusées ;
   - `… .Adb --ez hotwords false --es bench local --es corpus user` ;
   - `… .Adb --ez hotwords true --es bench local --es corpus user` ;
   - `… .Adb --es scribe scribe_v3_live --es bench elevenlabs --es corpus user` : v3 Live rejoué dans les mêmes conditions que les autres (la lecture guidée compte la latence depuis le relâcher de L2, le banc depuis la fin du WAV) ;
   - `… .Adb --es scribe scribe_v2_realtime --es bench elevenlabs --es corpus user`.

   Une fois le dernier banc fini, remettre les réglages par défaut (Scribe v3 Live, ElevenLabs, termes favorisés pour le repli local) : `… .Adb --es scribe scribe_v3_live --es provider elevenlabs --ez hotwords true`.
5. **Vérifier le passage à chaud** : dicter une phrase avec L2, passer au local avec `… .Adb --es provider local` (l'écran du haut n'est pas tactile ; la commande arrive à l'appli ouverte, sans la relancer), dicter une autre phrase. Revenir ensuite avec `… .Adb --es provider elevenlabs`.
6. **Récupérer les rapports** : `adb -s $THOR_SERIAL pull /sdcard/Android/data/app.regie.voicelab/files/ bench/` (fichiers `guided-*` et `bench-*-user-*`), puis remplir le tableau ci-dessus et trancher la recommandation.

## Conséquences

- L'app (tickets 08, 09, 12) ne connaît que `voice/api` : `VoiceProviders`, `Dictation`, `TextToSpeech`, `AudioSink`. Changer de fournisseur ne demande qu'un réglage.
- Pour dépendre de `:local`, `android/` devra déclarer le dépôt JitPack, restreint à `com.github.k2-fsa.sherpa-onnx`. En composite build, les dépendances externes d'un module inclus se résolvent avec les dépôts du build qui l'inclut.
- `android/` doit rester en `compileSdk` 36 ou plus, et ne pas tirer OkHttp 5.5.x tant qu'il n'est pas en 37.
- Le micro et la sortie audio Android (`MicrophoneSource`, `SpeakerSink`) vivent dans `:demo`. L'app les reprendra ou les déplacera dans un module partagé, au ticket qui branche la voix.
- L'appli locale occupe 818 Mo une fois les modèles installés.
- Une évolution du protocole Scribe se corrige dans `ScribeProtocol` et dans les fixtures, sans toucher au reste.
- Le contenu dicté part chez ElevenLabs quand ce fournisseur est choisi (PRD, « Risques »).
