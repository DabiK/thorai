# ADR 22 — Plusieurs apps sur le même pont

Statut : accepté, 18 novembre 2026 (ticket 50, branche du jeu). Complète
l'ADR 06 (connexion) et l'ADR 20 (jeton sans `run-as`).

Mise à jour du 18 novembre au soir : la branche du jeu est fusionnée dans main,
et le jeu devient ThorAI (décision de l'utilisateur). Le suffixe `.jeu` et les
libellés « ThorAI jeu » sont retirés : l'app est de nouveau `app.thorai`, qui
s'installe par-dessus l'app classique. Le pont multi-jetons reste : il ne coûte
rien et sert à toute autre build installée à côté.

## Contexte

L'utilisateur garde l'app classique installée et s'en sert chaque jour. La
version jeu (`ticket/40-vue-jeu`) doit s'installer à côté, sur la même Thor,
et parler au même pont : mêmes sessions, mêmes agents.

Depuis le ticket 27, chaque app crée son jeton et le transmet par RUN_COMMAND à
`regie-bridge-configure`. Le pont ne gardait qu'un fichier,
`~/.config/regie/bridge-token`, lu à son démarrage. Avec deux apps, chacune
remplaçait le jeton de l'autre et redémarrait le pont : l'autre app était
déconnectée et les sessions en cours étaient interrompues.

## Décision

- **Identité Android.** Tous les types de build ajoutent
  `applicationIdSuffix = ".jeu"` : `app.regie.jeu` aujourd'hui,
  `app.thorai.jeu` après le renommage du ticket 30, sans autre changement. Le
  `namespace` (classes, `R`) ne bouge pas. Tout ce qu'Android partage entre
  applis suit l'identifiant : autorités des fournisseurs, permission
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, et l'affinité de tâche de l'écran
  du bas (`${applicationId}.bottom`, jusque-là écrite en dur). Le nom affiché,
  « ThorAI jeu », et celui du mode console, « ThorAI jeu, mode console »,
  viennent de `android/app/src/jeu/res`, ajouté aux sources de debug et de
  release : `src/main/res` reste celui de l'app classique.
- **Un jeton par app.** L'app passe son nom de paquet en second argument de
  `regie-bridge-configure`. La commande écrit alors
  `~/.config/regie/bridge-tokens/<paquet>` (0600, dossier 0700), sans arrêter
  le pont. Sans second argument, elle garde exactement le comportement du
  ticket 27 sur `bridge-token`, arrêt et redémarrage compris : l'app classique
  installée, inchangée, continue de marcher, avec ce pont comme avec un pont
  plus ancien.
- **Le pont relit les jetons à chaque connexion** : le fichier historique et
  chaque fichier de `bridge-tokens/` nommé comme un paquet Android. Un fichier
  lisible par d'autres, trop court ou qui n'est pas un fichier ordinaire est
  refusé ; le journal nomme le fichier, jamais le jeton. Le jeton reçu est
  comparé à tous les jetons acceptés, à temps constant. Le pont refuse de
  démarrer sans aucun jeton valable.
- **Les scripts** lisent le paquet dans `android/app/build.gradle.kts`
  (`regie_app_id`, surchargé par `REGIE_APP_ID`) et lancent l'app par son
  lanceur (`am start -p`), le nom de classe ne suivant pas le suffixe.

## Conséquences

- Les deux apps sont connectées en même temps. L'une peut changer son jeton
  (réinstallation, données effacées) sans toucher à l'autre ni aux sessions.
- La version jeu exige le `regie-bridge-configure` et le pont de ce ticket :
  l'ancienne commande refuse un second argument, et la version jeu ne se
  connecte pas (401), sans rien changer chez l'app classique. Installer le
  pont avec `thor/bridge/install.sh` avant d'ouvrir la version jeu.
- La branche du jeu n'est jamais fusionnée dans main, mais le pont est commun :
  le pont déployé sur la console doit contenir ce changement. Le reporter sur
  main, ou déployer le pont de main seulement après l'avoir reporté.
- Le jeton d'une app désinstallée reste accepté jusqu'à ce qu'on supprime son
  fichier ; sans le stockage privé de l'app, personne ne le connaît.
- « Quitter » arrête toujours le pont et toutes les sessions, quelle que soit
  l'app : elles partagent le même pont. L'autre app le redémarre à sa
  prochaine ouverture.
- Les deux services « mode console » lisent la même manette : n'en activer
  qu'un à la fois dans Accessibilité.
- Les consignes et règles des agents (`thor/computer-use`) nomment encore
  `app.regie` ; elles ne sont pas changées ici (configuration des agents).

## Sources vérifiées

Context7, documentation Android Gradle Plugin, le 18 novembre 2026 :
[`applicationIdSuffix` et types de build](https://developer.android.com/build/build-variants),
[`${applicationId}` dans le manifeste](https://developer.android.com/build/manage-manifests),
[ensembles de sources](https://developer.android.com/build/build-variants#sourcesets).
