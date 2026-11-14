# ADR 05 — Socle Android : versions, deux écrans, manette, polices

Statut : accepté, 13 novembre 2026 (ticket 01).

## Contexte

Le ticket 01 pose l'app Android : deux activités sur les deux écrans de la Thor, la manette lue quel que soit l'écran qui a le focus, et le système visuel de la maquette. Le Mac a les plateformes Android 34 à 36 ; la console est sous Android 13 (API 33).

## Décisions

### Versions

| Brique | Version | Pourquoi |
| --- | --- | --- |
| Gradle (wrapper) | 9.9.1 | celle du Mac ; AGP 9.5 demande au moins 9.6 |
| Android Gradle Plugin | 9.5.1 | dernière stable ; Kotlin intégré à AGP 9 (plus de plugin `kotlin-android`) |
| Kotlin (KGP, plugin Compose) | 2.4.21 | dernière stable |
| compileSdk / targetSdk / minSdk | 36 / 36 / 33 | 36 est la plus haute plateforme installée ; 33 est la console |
| Compose BOM | 2026.06.01 (Compose 1.11.4) | les BOM 2026.08 et suivants exigent compileSdk 37 |
| lifecycle | 2.10.0 | la 2.11 exige compileSdk 37 |
| activity-compose | 1.13.0 | |
| kotlinx-coroutines | 1.11.0 | |

Le build tourne sur le JDK par défaut (Temurin 25) et produit du bytecode 17. Le lint `OldTargetApi` est coupé tant que la cible est 36. Passer à 37 : installer la plateforme 37, monter le BOM et lifecycle, réactiver `OldTargetApi`.

### Modules

- `:domain` : Kotlin JVM pur, avec le lint Android (`com.android.lint`) pour que `check` le couvre aussi.
- `:app` : interface et composition. `AppContainer`, créé par `RegieApplication`, est la seule racine de composition.
- `settings.gradle.kts` inclut `../voice` en composite build dès que `voice/settings.gradle.kts` existe (ticket 04).
- Test d'architecture : une lecture des imports des sources (`ArchitectureTest`), sans bibliothèque. Il vérifie que le domaine n'importe ni Android ni l'app, que l'interface n'importe ni adapter, ni port, ni racine de composition, et que seule la racine choisit la passerelle.

### Deux activités, un état

- `TopActivity` : lanceur, `singleTask`, display 0.
- `BottomActivity` : `singleInstance`, sa propre `taskAffinity`, hors des récents. `TopActivity` la lance par `ActivityOptions.setLaunchDisplayId` et `FLAG_ACTIVITY_NEW_TASK`, à chaque retour au premier plan et à chaque passage en mode deux écrans, si elle n'est pas déjà visible.
- `TopActivity` ouverte sur un autre display que le 0 (lanceur de l'écran du bas, `am start --display 4`) se ferme et se relance sur le display 0 (`DisplayPolicy.topScreenTarget`).
- L'écran du bas se ferme seul en mode un seul écran, quand l'écran du haut est détruit, ou si Android l'a déplacé sur un autre écran.
- L'état partagé vit dans `AppContainer` : la vue d'ensemble (domaine), `OverviewController` (focus et gâchettes maintenues), `InputRouter` et `DisplayMonitor`. Les deux activités l'observent.

### Écran du bas absent ou éteint

`DisplayMonitor` écoute `DisplayManager` ; `DisplayPolicy`, en Kotlin pur et testée, choisit le mode :
- le display 4 s'il est allumé et n'est pas privé ; seuls `STATE_ON` et `STATE_VR` comptent comme allumés, `OFF`, `DOZE`, `DOZE_SUSPEND` et `ON_SUSPEND` donnent le mode un seul écran ;
- sinon le premier écran de présentation allumé et public ;
- sinon le mode un seul écran, où la zone du bas devient un panneau en bas de l'écran haut.

En debug, `am start -n app.regie/.TopActivity --ez force_single_screen true|false` force le mode, pour le tester sans éteindre l'écran du bas.

### Routeur d'entrées

- Chaque activité enveloppe son `Window.Callback`. La fenêtre lui passe touches et mouvements avant la hiérarchie de vues, donc avant le focus de Compose. Surcharger `dispatchKeyEvent` de `ComponentActivity` déclenche le lint `RestrictedApi`.
- Les touches de la manette sont consommées. Les raccourcis système (A → DPAD_CENTER, B → BACK, X → DEL, Y → SPACE) ne s'appliquent donc pas. DPAD_CENTER et Entrée sont aussi avalés : A ne peut pas provoquer de clic parasite.
- L2 et R2 sont maintenues tant que la touche **ou** l'axe l'est : un seul début, une seule fin.
  - L2 : maximum de `AXIS_LTRIGGER` et `AXIS_BRAKE` ; R2 : maximum de `AXIS_RTRIGGER` et `AXIS_GAS`.
  - Seuils avec hystérésis : appui à 0,5, relâché sous 0,25.
- Le stick droit (`AXIS_RZ`, zone morte de 0,2) publie une vitesse de défilement. Les mouvements ne sont pas consommés, pour qu'Android convertisse encore une croix en axe (HAT) en touches.
- Quand une de nos activités passe en arrière-plan, le routeur termine les appuis en cours : le relâché d'une gâchette irait à une autre appli.
- La vue Compose racine prend le focus dès sa création. Sans vue focalisée, Android avale la première touche de navigation pour quitter le mode tactile, avant même le `Window.Callback`. C'était visible sur la Thor : le premier appui sur la croix après le lancement était perdu.

### Commandes sur la vue d'ensemble

Elles suivent le tableau « Commandes » du PRD, et appellent les cas d'usage vides de `domain/usecase/Commands.kt` :
- croix : focus dans la grille 2×2, arrêté aux bords et limité aux 4 cartes affichées ;
- A : ouvrir la session qui a le focus ;
- X : nouvelle session ;
- Y : point écrit depuis le ticket 12 (ADR 10) ;
- Start : tout mettre en pause ;
- L2 et R2 maintenues : dictée et ordre vocal ;
- R1 : ouvrir la première validation en attente (maquette, ticket 05), ou session suivante s'il n'y en a pas ;
- L1 : session précédente ;
- B : rien, la vue d'ensemble est le premier écran.

Toucher le bandeau, les boutons du bas ou le micro (maintenu) fait la même chose.

### Polices

- Geist et Geist Mono, polices variables (axe `wght`), prises dans le dépôt google/fonts (`ofl/geist`, `ofl/geistmono`), qui reprend vercel/geist-font.
- Elles sont rangées dans `app/src/main/assets/fonts/`, à côté de leur licence `OFL.txt`, qui part ainsi dans l'APK. `res/font` refuse un fichier texte.
- Elles sont chargées par `Font(path, assetManager, weight)` de Compose, une instance par graisse.

### Essais sur la console

Pour simuler la manette, on écrit dans le nœud evdev « Odin Controller » (`/dev/input/event9` le 13 novembre, accessible au shell) avec `sendevent` :
- touches : `BTN_TL2` = 312, `BTN_DPAD_RIGHT` = 547, etc. ;
- axes : `ABS_BRAKE` = 10, `ABS_GAS` = 9.

Les événements suivent ainsi le même chemin que la vraie manette, gâchettes comprises (touche et axe). `input keyevent --longpress` ne permet pas de maintenir une touche.

## Conséquences

- Les tickets suivants ajoutent leurs écrans derrière `OverviewController`, ou à côté, et branchent leurs cas d'usage à la place des cas vides.
- Le mode un seul écran n'a été essayé que forcé. Relevé le 13 novembre, en lecture seule : le display 4 (« Screen-2 ») est `state ON, committedState ON`. Aucune clé de `settings` système, sécurisée ou globale ne l'éteint (`dual_screen_display_mode` n'y figure pas). L'extinction passe par l'assistant d'Odin, `com.odin.dualscreen.assistant` 1.3 (`ScreenUtil.closeSecondaryScreen`, mode « Only bottom screen »). Son code ne dit pas si l'écran passe à `OFF`, est retiré ou garde `ON` avec une luminosité nulle. Ce dernier cas échapperait à `DisplayPolicy`.
- Test réel, à faire par l'utilisateur :
  1. Régie ouverte, éteindre l'écran du bas par l'assistant d'écran double d'Odin.
  2. Relever `adb -s THOR0000000001 shell dumpsys display | grep -E 'Screen-2.*state'` et `adb -s THOR0000000001 logcat -d -s Regie`, qui doit montrer `mode d'écran : Single`.
  3. Vérifier que le panneau apparaît en bas de l'écran haut.
  4. Rallumer l'écran du bas et vérifier que l'écran du bas de Régie revient.


## Ticket 15 — Marges système sur les deux écrans

Choix : respecter les insets, plutôt que masquer les barres au gain du focus.
Le conteneur commun `GamepadActivity.setScreen` utilise `safeDrawingPadding` sur
un fond sombre : statut, navigation, découpes et clavier restent hors du contenu,
pour chaque écran et chaque panneau, y compris en mode un seul écran. Les icônes
de statut sont claires pour rester lisibles. Aucun réglage système n’est modifié.

API vérifiée avec Context7, documentation officielle Android `insets-ui` et
`setup-e2e`, extraits `artifacts/screenshots/15/docs-insets.txt` et
`docs-system-bar-style.txt`. Test Robolectric sur le vrai conteneur commun, sans
le démarrer : injecter 24 px en haut et 20 px en bas déplace les limites du contenu
d’autant. Retirer le padding fait échouer le test. Captures Thor des deux écrans.
