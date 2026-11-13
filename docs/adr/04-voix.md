# ADR 04 — La voix : ports, ElevenLabs, alternative locale

Statut : en cours, 13 novembre 2026 (ticket 04, phase 1 sur 2). La phase 2 ajoute `:local` (sherpa-onnx) et l'appli d'essai `:demo`, puis les mesures.

## Contexte

- Le PRD (« Voix ») demande trois ports : transcription en flux avec partiels, langue et termes à favoriser ; synthèse en flux ; détection de parole sur l'appareil. ElevenLabs est le fournisseur par défaut, une alternative locale et gratuite se choisit par un réglage.
- La Thor n'a aucun moteur de reconnaissance ni de synthèse Android (`docs/thor.md`, « Voix ») : tout passe par nos adapters.
- L'ADR 01 place la voix dans un build Gradle séparé, `voice/`, inclus par l'app en composite build.
- La dictée se fait en maintenant L2 : début d'appui = début de dictée, relâcher = fin de parole.

## Décision

### Build

- `voice/` est un build Gradle autonome :
  - wrapper Gradle 9.3.1 ;
  - Kotlin 2.2.21, cible JVM 17 ;
  - avertissements traités comme des erreurs.
- Versions des bibliothèques :
  - kotlinx-coroutines 1.10.2 ;
  - kotlinx-serialization-json 1.9.0, sans le plugin : on lit et on écrit des `JsonObject` ;
  - OkHttp 5.5.1 et mockwebserver3 5.5.1 ;
  - JUnit 5.14.4.
- Les modules ont le groupe `app.regie.voice`. L'app les obtient par substitution : `includeBuild("../voice")` puis `implementation("app.regie.voice:api")`. La substitution a été vérifiée avec un projet consommateur jetable.
- La porte de qualité se lance par `./gradlew check` depuis `voice/`, avec le JDK par défaut du Mac (Temurin 25). Aucun `org.gradle.java.home` n'est versionné.
- Modules de la phase 1 :
  - `:api` : Kotlin pur, seule dépendance kotlinx-coroutines ;
  - `:fake` : JVM ;
  - `:elevenlabs` : JVM, avec OkHttp.
- Un test d'architecture (`api/src/test/.../ArchitectureTest.kt`) lit les sources de tous les modules et vérifie que :
  - `:api` n'importe que Kotlin, les coroutines et le JDK ;
  - un adapter n'importe aucun autre adapter, et son build ne dépend que de `:api`.
  
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
- **Prix** : relevés au lancement du ticket. L'agent n'a pas relu la page des prix, il faut les revérifier avant la recommandation finale.
  - Scribe v3 Live et v2 Realtime : 0,22 $ de l'heure, plus 0,08 $ de l'heure avec les keyterms ;
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

## Ce qui reste (phase 2)

- `:local` avec sherpa-onnx, branché sur les mêmes ports, sans les modifier :
  - transcription française : Parakeet TDT 0.6B v3 int8 découpé par la détection de parole, ou un modèle en flux s'il en existe un bon en français ;
  - Silero pour la détection de parole ;
  - une voix Piper française.
  
  Les modèles se téléchargent au premier usage, ce qui donne `PreparingModel` et `ProviderStatus.Downloading`.
- `:demo` (`app.regie.voicelab`) :
  - le micro (`AudioSource`) et la sortie audio (`AudioSink`) Android ;
  - L2 pour dicter ;
  - le choix du fournisseur ;
  - la saisie de la clé, stockée chiffrée ;
  - le choix de la voix ;
  - la mesure de la latence (de la fin de parole au texte final).
- Remplacer les fixtures Scribe par une capture réelle.
- Piste à évaluer avec les mesures : la `keyterm_library` de Scribe v3 Live (jusqu'à 10 000 termes de 50 caractères, envoyée dans le premier morceau d'audio). Elle lèverait la limite de 50 termes et de 20 caractères, par exemple pour `kotlinx-coroutines-test`.

## Mesures

À remplir en phase 2, sur la Thor, avec les 20 consignes du corpus lues par l'utilisateur :

| Fournisseur | Latence fin de parole → texte final (médiane, max) | Erreurs grossières | Termes du dépôt reconnus |
| --- | --- | --- | --- |
| ElevenLabs Scribe v3 Live | | | |
| ElevenLabs Scribe v2 Realtime | | | |
| Local (sherpa-onnx) | | | |

Recommandation du fournisseur par défaut : à écrire après les mesures.

## Conséquences

- L'app (tickets 08, 09, 12) ne connaît que `voice/api` : `VoiceProviders`, `Dictation`, `TextToSpeech`, `AudioSink`. Changer de fournisseur ne demande qu'un réglage.
- Une évolution du protocole Scribe se corrige dans `ScribeProtocol` et dans les fixtures, sans toucher au reste.
- Le contenu dicté part chez ElevenLabs quand ce fournisseur est choisi (PRD, « Risques »).
