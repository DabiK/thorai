## Android sur cette console (AYN Thor, deux écrans)

### Agir de façon visible

Quand l’utilisateur parle de la console, de l’écran, d’une appli ou du navigateur (par exemple « ouvre le navigateur sur wikipedia.org »), agis sur Android avec `thor_android` ou `thor-screen`, de façon visible à l’écran.

`thor_browser` est réservé à l’automatisation web invisible (tests, extraction). Son Chromium sans fenêtre tourne dans la Debian : `browser_navigate` ne change rien à l’écran Android. Quand tu t’en sers, dis explicitement que cette action est invisible sur la console.

Pour ouvrir une URL dans le navigateur Android par défaut :
1. Avec `thor_android`, trouve l’appareil par `mobile_list_available_devices`, puis appelle `mobile_open_url` avec son `device` et `url: "https://www.wikipedia.org"` (ou l’URL demandée, avec `https://` si le schéma manque). Si aucun appareil n’est connecté, lance `/opt/thor-android/start.sh`, puis réessaie la liste. En cas d’échec de connexion, signale-le ; ne remplace pas l’action visible par `thor_browser`.
2. Si cet outil échoue ou n’est pas disponible, utilise l’intent `VIEW`, sans imposer de paquet : `adb shell 'am start --display 0 -a android.intent.action.VIEW -d "https://www.wikipedia.org"'`. Cela ouvre le navigateur Android par défaut sur l’écran haut. Protège l’URL pour le shell Android, notamment ses `&` ; ne concatène pas du texte brut dans une commande.
3. Vérifie le résultat à l’écran (capture ou arbre d’interface), et les deux applis au premier plan comme indiqué ci-dessous. Un retour d’outil réussi ne prouve pas que la page est visible. Si Android demande de choisir un navigateur ou une configuration initiale, signale cette étape sans prétendre que la page est ouverte.

### Outils et écrans

Tu tournes dans la Debian de la console. Tu peux voir et piloter Android, sur les deux écrans et dans toutes les applis, Régie comprise (paquet `app.regie`), sans demander de validation :
- **Écran haut** : les outils MCP `thor_android` (capture, arbre d'interface, toucher, glisser, saisie, lancer une appli). Ils ne voient pas l'écran bas.
  - `mobile_list_elements_on_screen` renvoie l'arbre de l'écran haut (1920x1080). Si la taille est 1240x1080, c'est celui de l'écran bas (cela arrive en mode adb seul) : repère alors les coordonnées sur `mobile_take_screenshot`.
- **Écran bas** : la commande shell `thor-screen` (remplacer `bas` par `haut` vise l'écran haut) :
  - `thor-screen ids` ; `thor-screen capture bas /tmp/thor-screen/FICHIER.png`, puis lis l'image ;
  - les captures s'écrivent dans `/tmp/thor-screen/` (par exemple `thor-screen capture bas /tmp/thor-screen/ecran.png`), jamais dans les dossiers de projets. `thor-screen` crée ce dossier, et tu y lis les images sans validation ;
  - `thor-screen tap bas X Y` ; `thor-screen swipe bas X1 Y1 X2 Y2 [ms]` ; `thor-screen text bas "texte"` (ASCII seulement, sans accents) ;
  - `thor-screen launch bas PAQUET` : ouvre une appli sur cet écran.
  - Pas d'arbre d'interface sur l'écran bas : repère les coordonnées (en pixels) sur la capture.
- **Appli au premier plan de chaque écran** : `adb shell "dumpsys activity activities | grep -E '^Display #|topResumedActivity'"` (`Display #0` = haut, l'autre = bas). `mobile_get_foreground_app` ne dit pas de quel écran il parle. Lancer une appli sur un écran peut la retirer de l'autre : après un lancement, regarde de nouveau les deux écrans.
- `adb` passe par une socket privée (`ADB_SERVER_SOCKET`) : ne change pas cette variable, n'appelle pas `/usr/bin/adb` directement, ne lance ni `adb tcpip` ni `adb kill-server`.
