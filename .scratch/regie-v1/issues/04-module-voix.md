# 04 — Module voix : ports, ElevenLabs et alternative gratuite

Statut : prêt
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

- Sur la Thor, la dictée marche avec ElevenLabs **et** avec l'alternative locale, et on passe de l'une à l'autre sans relancer.
- Mesure sur une vingtaine de consignes typiques, en français mêlé de termes de code (préparer le texte et demander à l'utilisateur de les lire) : latence, erreurs grossières, termes du dépôt reconnus. Résultats dans `docs/adr/04-voix.md`, avec la recommandation du fournisseur par défaut.
- Tests unitaires de `:api` et des adapters sur des enregistrements, sans réseau.

## Rapport

(à remplir)
