# La voix de Régie

Build Gradle séparé, inclus par `android/` en composite build (ADR 01, ADR 04).

| Module | Rôle |
| --- | --- |
| `api` | Les ports, en Kotlin pur |
| `fake` | La voix factice, pour les tests de l'app |
| `elevenlabs` | Scribe en temps réel et Flash |
| `local` | sherpa-onnx, gratuit et hors ligne : Parakeet + Silero, Supertonic |
| `demo` | L'appli d'essai `app.regie.voicelab` |

Porte de qualité : `./gradlew check` (tests, lint Android). Le SDK Android est lu dans `local.properties` (`sdk.dir=/opt/homebrew/share/android-commandlinetools`).

## Appli d'essai sur la Thor

```bash
cd voice && ./gradlew :demo:assembleDebug
adb -s THOR0000000001 install -r demo/build/outputs/apk/debug/demo-debug.apk
adb -s THOR0000000001 shell pm grant app.regie.voicelab android.permission.RECORD_AUDIO
adb -s THOR0000000001 shell am start --display 0 -n app.regie.voicelab/.MainActivity
```

- Maintenir **L2** pour dicter ; **L2** ou **R2** coupe la voix ; en lecture guidée, **B** fait relire la phrase.
- L'écran du haut n'est pas tactile. Pour saisir la clé ElevenLabs, lancer l'appli sur l'écran du bas : `am force-stop app.regie.voicelab`, puis `am start --display 4 …`. La clé est chiffrée par le Keystore d'Android et n'est jamais écrite ailleurs.
- Si la console dort, `adb shell input keyevent KEYCODE_WAKEUP` la réveille.

## Commandes adb, sans toucher l'écran

Toutes passent par `adb -s THOR0000000001 shell am start --display 0 -n app.regie.voicelab/.Adb …`. On peut en combiner plusieurs : les réglages s'appliquent avant les actions.

`.Adb` est un alias de l'activité réservé aux appelants qui détiennent `android.permission.DUMP`, c'est-à-dire le shell adb : aucune autre appli de la console ne peut lancer un banc ElevenLabs (des crédits) ni un téléchargement de 616 Mo. Les extras envoyés à `.MainActivity` sont ignorés.

| Extras | Effet |
| --- | --- |
| `--es download local` | Télécharge et charge les modèles locaux (environ 616 Mo à télécharger, 820 Mo installés) |
| `--es bench local` ou `--es bench elevenlabs` | Rejoue le corpus au rythme réel dans ce fournisseur |
| `--es only 02,13` | Avec `bench` : seulement ces consignes |
| `--es corpus user` | Avec `bench` : rejoue `files/corpus/user/`, les enregistrements de la lecture guidée |
| `--es guided local` ou `--es guided elevenlabs` | Lance la lecture guidée du corpus avec ce fournisseur (voir plus bas) |
| `--es guided stop` | Quitte la lecture guidée |
| `--es speak "'Le texte à lire.'"` | Lit le texte avec le fournisseur choisi (les apostrophes protègent les espaces dans le shell de la console) |
| `--es provider local` ou `--es provider elevenlabs` | Choisit le fournisseur, gardé dans les réglages |
| `--es scribe scribe_v3_live` ou `--es scribe scribe_v2_realtime` | Choisit le modèle Scribe |
| `--ez hotwords true` ou `--ez hotwords false` | Termes favorisés pour le local (recherche en faisceau, plus lente) |

Le suivi s'affiche à l'écran et dans `adb logcat -s VoiceLab`.

Pendant un banc ou une lecture guidée, toute nouvelle commande est refusée, réglages compris, avec un message rouge à l'écran (et dans logcat) : attendre « fini : bench-… », ou quitter la lecture avec `--es guided stop`, envoyé seul. Les réglages notés dans un rapport sont ceux du début du banc ou de la lecture.

## Banc d'essai

1. Pousser les enregistrements et les consignes :

   ```bash
   scripts/voice-corpus.sh                    # ou les WAV lus par l'utilisateur
   adb -s THOR0000000001 push voice/build/corpus/*.wav voice/corpus/consignes.txt \
     /sdcard/Android/data/app.regie.voicelab/files/corpus/
   ```

   Les WAV sont en 16 kHz, mono, 16 bits. Chaque WAV va avec la consigne dont le numéro termine son nom : `07.wav` et `consigne-07.wav` vont tous les deux avec `07|…`. Sans `consignes.txt` ni `termes.txt` dans ce dossier, l'appli prend ceux de `voice/corpus/`, embarqués dans l'APK.

2. Lancer : `… --es bench local`, attendre « fini : bench-… » à l'écran, puis `… --es bench elevenlabs`.

3. Récupérer les rapports :

   ```bash
   adb -s THOR0000000001 pull /sdcard/Android/data/app.regie.voicelab/files/ bench/
   ```

   Chaque banc écrit `bench-<fournisseur>-<horodatage>.json` et `.csv`. On y trouve, par consigne :
   - l'attendu et l'obtenu ;
   - la latence, de la fin du WAV au texte final ;
   - la durée de l'audio ;
   - le taux d'erreur sur les mots (WER), calculé en minuscules et sans ponctuation ;
   - pour le local, le facteur temps réel ;
   - les termes de `termes.txt` présents dans l'attendu, retrouvés ou manqués.
   
   Suivent les moyennes et les médianes.

## Lecture guidée : mesurer sur sa propre voix

L'appli affiche les consignes de `consignes.txt` une par une ; on maintient L2 et on lit. Chaque dictée est enregistrée telle que le micro l'a captée, puis notée comme dans le banc.

1. Lancer, l'appli sur l'écran du haut. Si elle est ouverte sur l'écran du bas (saisie de la clé), la fermer d'abord :

   ```bash
   adb -s THOR0000000001 shell am force-stop app.regie.voicelab
   adb -s THOR0000000001 shell am start --display 0 -n app.regie.voicelab/.Adb --es guided elevenlabs
   ```

   Ou `--es guided local`, ou les boutons « Lecture guidée du corpus » de l'écran.

2. Pour chaque consigne affichée en grand : maintenir **L2**, lire la phrase, relâcher. L'appli :
   - enregistre l'audio en WAV 16 kHz mono dans `files/corpus/user/NN.wav` (une lecture précédente du même numéro est remplacée) ;
   - garde l'attendu, l'obtenu, la latence (relâchement de L2 → texte final), le WER et les termes ;
   - passe à la consigne suivante.

   **B** pendant une dictée l'annule ; après, il revient à la consigne qu'on vient de lire et efface son résultat. On la relit avec L2.

3. Après la dernière consigne, l'appli écrit `guided-<fournisseur>-<horodatage>.json` et `.csv` dans `files/`, au format du banc (le réglage `source` vaut `microphone`).

4. Rejouer exactement les mêmes enregistrements dans l'autre fournisseur, puis comparer les deux rapports :

   ```bash
   adb -s THOR0000000001 shell am start --display 0 -n app.regie.voicelab/.Adb --es guided stop
   adb -s THOR0000000001 shell am start --display 0 -n app.regie.voicelab/.Adb --ez hotwords true --es bench local --es corpus user
   # attendre « fini : bench-… » à l'écran
   adb -s THOR0000000001 pull /sdcard/Android/data/app.regie.voicelab/files/ bench/
   ```

   Le banc écrit alors `bench-<fournisseur>-user-<horodatage>.json` et `.csv`. Il prend `consignes.txt` et `termes.txt` dans `files/corpus/user/`, sinon dans `files/corpus/`, sinon dans l'APK.
