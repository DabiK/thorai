# 04 — Module voix : ports, ElevenLabs et alternative gratuite

Statut : fait (la mesure ElevenLabs n'est plus demandée : voix locale par défaut, décision de l'utilisateur du 13 novembre)
Bloqué par : rien
Possède : `voice/`, `docs/adr/04-*`

## Objectif

Livrer la voix de l'app sous forme de ports et d'adapters interchangeables. ElevenLabs est le fournisseur par défaut, une alternative gratuite et locale se branche par un simple réglage. Le tout est mesuré sur la Thor avec une appli d'essai.

## À livrer

- **Build Gradle séparé `voice/`**, avec wrapper versionné et `./gradlew check`. L'app l'inclura par `includeBuild("../voice")`, donc ne pas toucher à `android/`.
- **`:api`, en Kotlin pur** :
  - transcription en flux : début et fin de dictée, textes partiels puis final, langue, termes à favoriser ;
  - synthèse en flux, interruptible ;
  - détection de parole ;
  - erreurs et états (connexion, quota, hors ligne).
- **`:elevenlabs`** :
  - transcription Scribe en temps réel, avec mots-clés pour les termes du dépôt ;
  - synthèse Flash en streaming.
  
  Vérifier les API à jour : Scribe v2 Realtime, Scribe v3 Live et leur prix. Réutiliser un SDK officiel s'il y en a un pour Android ou Java.
- **`:local`, gratuit et hors ligne, avec sherpa-onnx** :
  - un modèle de transcription qui couvre le français, par exemple Parakeet TDT 0.6B v3 int8 découpé par la détection de parole, ou un modèle en flux s'il en existe un bon en français ;
  - Silero pour la détection de parole ;
  - une voix française pour la synthèse (Piper ou équivalent).
  
  Les modèles sont téléchargés au premier usage depuis leurs sources officielles, jamais versionnés.
- **`:fake`** : rejoue des textes, pour les tests de l'app.
- **Appli d'essai `:demo`**, applicationId `app.regie.voicelab`, sur la Thor :
  - maintenir L2 pour dicter, avec affichage des partiels et du texte final ;
  - choix du fournisseur dans les réglages ;
  - mesure de la latence (fin de parole → texte final) et de la vitesse ;
  - lecture d'un texte à voix haute.
  - **La clé ElevenLabs est saisie par l'utilisateur dans l'appli** et stockée chiffrée. Jamais dans le dépôt, jamais tapée par l'agent.

## Critères d'acceptation

- [ ] Sur la Thor, la dictée marche avec ElevenLabs **et** avec l'alternative locale, et on passe de l'une à l'autre sans relancer.
- [ ] Mesure sur une vingtaine de consignes typiques, en français mêlé de termes de code (préparer le texte et demander à l'utilisateur de les lire) : latence, erreurs grossières, termes du dépôt reconnus. Résultats dans `docs/adr/04-voix.md`, avec la recommandation du fournisseur par défaut.
- [x] Tests unitaires de `:api` et des adapters sur des enregistrements, sans réseau.

## Rapport

**13 novembre 2026. Bloqué : il manque la clé ElevenLabs et la lecture des consignes par l'utilisateur.**

Ce qui est livré, sur la branche `ticket/04-module-voix` :
- **Build `voice/`** (Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21), avec les modules :
  - `:api`, en Kotlin pur ;
  - `:fake` ;
  - `:elevenlabs` : Scribe en temps réel par WebSocket (v3 Live par défaut, v2 Realtime en option, keyterms) et Flash v2.5 en PCM, avec OkHttp, faute de SDK officiel ;
  - `:local` : sherpa-onnx 2.0.1, Parakeet TDT v3 int8 découpé par Silero, synthèse Supertonic 3 (la voix Piper ne marche plus depuis que sherpa-onnx 2.0.1 a retiré espeak-ng) ; modèles téléchargés au premier usage ;
  - `:demo`, l'appli `app.regie.voicelab` : dictée à L2, choix du fournisseur à chaud, clé chiffrée par le Keystore, mesures, lecture à voix haute, banc et lecture guidée pilotables par adb.
- **Porte de qualité** : `./gradlew check` vert, 127 tests sans réseau, sur des enregistrements WAV et un MockWebServer.
- **Thor** : banc local sur les 20 consignes (voix `say -v Thomas`). Latence médiane 223 ms, WER 0,29, 14 termes sur 45 ; avec les termes favorisés : 279 ms, 0,21, 22 sur 45. Détails et recommandation dans `docs/adr/04-voix.md`.
- **Captures** : `artifacts/screenshots/04/`.
- **Relecture** : trois tours, approuvée au troisième.

Ce qui reste, et qui demande l'utilisateur :
- ❓ **Saisir la clé ElevenLabs et lire les 20 consignes au micro.** La marche à suivre, pas à pas, est dans `docs/adr/04-voix.md`, « Marche à suivre pour l'utilisateur ». Une fois les rapports récupérés, il faut remplir le tableau « Mesures » et trancher le fournisseur par défaut. Le local est très rapide mais reconnaît mal les termes de code.
- Remplacer les fixtures Scribe (`voice/elevenlabs/src/test/resources/scribe/`) par une capture réelle.

Hors périmètre déclaré, mais demandé par le ticket : `scripts/voice-corpus.sh` et `artifacts/screenshots/04/`.
