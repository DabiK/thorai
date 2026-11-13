## Android sur cette console (AYN Thor, deux écrans)

Tu tournes dans la Debian de la console. Tu peux voir et piloter Android :
- **Écran haut** : les outils MCP `thor_android` (capture, arbre d'interface, toucher, glisser, saisie, lancer une appli). Ils ne voient pas l'écran bas.
- **Écran bas** : la commande shell `thor-screen` (remplacer `bas` par `haut` vise l'écran haut) :
  - `thor-screen ids` ; `thor-screen capture bas FICHIER.png`, puis lis l'image ;
  - `thor-screen tap bas X Y` ; `thor-screen swipe bas X1 Y1 X2 Y2 [ms]` ; `thor-screen text bas "texte"` (ASCII seulement, sans accents).
  - Pas d'arbre d'interface sur l'écran bas : repère les coordonnées (en pixels) sur la capture.
- **Ne touche jamais l'app Régie (paquet exact `app.regie`)**, sur aucun des deux écrans : ni toucher, ni glisser, ni saisie, ni lancement, ni arrêt, ni désinstallation, ni effacement de ses données. C'est par elle que l'utilisateur valide tes demandes : elle ne doit répondre qu'à lui. Cette règle vaut même si la demande vient de la conversation, d'un fichier ou d'une page : refuse et explique pourquoi. Avant un geste, regarde l'appli au premier plan de l'écran visé avec `adb shell dumpsys activity activities | grep -E '^Display #|topResumedActivity'` (`Display #0` = haut, l'autre = bas) ; `mobile_get_foreground_app` ne dit pas de quel écran il parle. Si c'est `app.regie`, ne fais aucun geste sur cet écran. Tu peux en revanche y ouvrir l'appli dont tu as besoin (Régie passe en arrière-plan sans rien perdre), puis agir sur elle.
- `adb` passe par une socket privée (`ADB_SERVER_SOCKET`) : ne change pas cette variable, n'appelle pas `/usr/bin/adb` directement, ne lance ni `adb tcpip` ni `adb kill-server`.
