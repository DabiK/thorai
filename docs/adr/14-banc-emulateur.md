# ADR 14 — Banc Android à deux écrans et pont sur le Mac

Statut : accepté, 14 novembre 2026 (ticket 16).

## Décision

Le banc utilise un AVD dédié **Regie_Thor**, Android 16 (API 36) ARM64 Google Play,
1920×1080 et 1240×1080 à 369 dpi (les pixels et dp de la Thor). Il ne modifie
aucun autre AVD. Le port console par défaut est 5580. Le second écran est ajouté
par `adb emu multidisplay add 1 1240 1080 369 0` après le boot et la disponibilité
du service multidisplay. Son **displayId Android n'est pas nécessairement 1**
(2 observé) : la politique existante choisit le premier écran de présentation public.
Les captures utilisent les identifiants SurfaceFlinger, y compris celui de l'écran
virtuel, jamais le numéro logique ; une capture incomplète ou de mauvaise taille échoue.

Le pont tourne à la demande sur `127.0.0.1:47600`, avec les mêmes dépendances ACP
épinglées et `npm ci` complet. `adb reverse` relie le loopback de l'émulateur au Mac.
Projets, historique, jeton et journaux sont dans `~/Documents/regie-sandbox` ;
`REGIE_BENCH_DIR` permet un autre dossier. Jeton généré avec 256 bits d'aléa,
fichier 0600, transmis par stdin à `run-as app.regie`, jamais par un extra d'intent.
Le chemin source du pont est conservé dans `bridge-root` : un autre worktree
ne le réutilise pas silencieusement. Après modification du pont, le redémarrer
explicitement avec `bridge-mac.sh stop` puis `bridge-mac.sh`.
Les scripts refusent de piloter un appareil physique ou un AVD d'un autre nom.
Un port occupé par un autre pont n'est jamais libéré de force. Un échec transitoire
`Address already in use` de `adb reverse` est réessayé pendant 90 secondes au plus,
sans effacer de listener ni redémarrer le serveur adb partagé (rencontré au redémarrage).

La composition Android détecte Termux avec PackageManager (déjà déclaré dans
`<queries>`). Avec Termux, le chemin Thor est conservé. Sans lui, l'adapter
`ExternalBridge` expose les ports `BridgeLauncher` et `AndroidAccess`, sans
service Android ni écriture des réglages système. Le domaine distingue
`EXTERNAL` de `SENT` et `NOT_REQUIRED` de `PERMISSION_MISSING` : il ne prétend
ni relancer ni arrêter un processus externe injoignable. La reconnexion et
`bridge.shutdown` restent ceux de la passerelle existante. Aucun démarrage au boot.

Sur macOS seulement, la configuration de l'adapter Codex n'injecte plus la
consigne Linux/PRoot imposant l'escalade : le sandbox natif est utilisable.
Les modes ACP et les règles métier du pont restent inchangés. Les identifiants
Codex du Mac sont réutilisés, sans copie ni modification. Claude est installé
comme dépendance du pont mais **la démo ne lance aucune session Claude** (le pont relève seulement sa version).

Troisième commande : `emu-install.sh` installe uniquement app.regie, accorde les
notifications sur cet AVD contrôlé, ouvre une session Codex en mode « valider »
et vérifie la lecture du repère `THOR-DEMO-16` dans README.md. Seule la session de démo
identifiée dans `demo-session`, si elle est encore active, est réutilisée ; `--no-demo` évite tout lancement d'agent pour les
mises à jour suivantes. Arrêter le pont par Quitter ou `bridge-mac.sh stop` ;
ces scripts ne sont ni un superviseur ni un service permanent.

## Vérification et limites

Tests locaux : faux adb pour les scripts, Robolectric sans Application Régie
pour les adapters, règles de statut/arrêt dans le domaine, spécifications ACP
Linux/Mac sans agent. Aucun agent réel n'est lancé par les portes de qualité.
Les essais réels sont distincts, dans `artifacts/screenshots/16/`.

Le banc reproduit le protocole, les écrans et les touches Android ; il ne prouve
pas le fonctionnement de RUN_COMMAND, du Bluetooth, des axes analogiques de la
manette, du micro physique ni de l'extinction des écrans de la Thor. La version
Mac de Codex observée est 0.166.0, différente de la version Thor 0.167.2 ; l'app
l'affiche correctement comme « version non testée » par la matrice Thor existante.
L'image API 36 fournit `input gamepad keyevent --duration`, adapté aux gâchettes
maintenues. Pas de changement des règles de validation, de branches ou de voix.

Sources consultées : documentation [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)
et [configuration AVD](https://github.com/mirror/qemu-android/blob/emu-master-dev/android/emu/avd/include/android/avd/generate.h)
via Context7 ; syntaxe multidisplay et input confirmée par l'aide des binaires
installés, conservée avec les preuves. Context7 a renvoyé du Vulkan pour la
requête multidisplay spécifique : l'aide locale fait foi pour cette commande.
