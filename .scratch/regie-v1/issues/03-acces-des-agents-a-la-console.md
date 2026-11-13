# 03 — Accès des agents à la console (computer use)

Statut : à fusionner
Bloqué par : rien
Possède : `thor/computer-use/`, `docs/adr/03-*`, la déclaration MCP correspondante dans la configuration des deux agents

## Objectif

Permettre à Claude Code et à Codex, qui tournent sur la Thor, de voir l'écran d'Android et d'agir dessus : capture, lecture de l'arbre d'interface, toucher, glisser, saisir du texte, ouvrir une appli. Les deux écrans sont concernés si c'est possible. On s'appuie sur des outils existants : pas de serveur maison si un existant fait l'affaire.

## À faire

1. **Repérer les candidats et en essayer deux au plus.** Pistes :
   - serveurs MCP de pilotage d'Android : `mobile-mcp` de mobile-next, Android-MCP, DroidRun ;
   - adb en local sur la console : `android-tools` de Termux, débogage sans fil, `adb pair` puis `adb connect` sur localhost.
   
   Critères : marche dans la Debian (arm64, proot), gère deux écrans, n'impose pas d'abonnement, maintenu.
2. **Déclarer le serveur retenu aux deux agents**, comme `thor_browser` l'est déjà. Script dans `thor/computer-use/install.sh`.
3. **Persistance, sans rien au boot.** L'utilisateur ne veut rien qui démarre au boot. Prévoir `thor/computer-use/start.sh`, que le pont appellera à son démarrage, c'est-à-dire au lancement de l'app. Le débogage sans fil se coupe au redémarrage : si le rétablir exige une action sur l'écran, l'écrire noir sur blanc. Si une étape exige l'appairage sur l'écran de la console, demander à l'utilisateur de la faire et attendre.
4. **Sécurité.** Écrire dans l'ADR ce que cet accès permet, qui y a accès, et comment le couper.

## Critères d'acceptation

- [x] Sur la Thor, dans une session Claude puis une session Codex, la consigne « ouvre les Paramètres, va dans À propos et dis-moi la version d'Android » réussit sans intervention, à part les validations. *Chemin livré, après l'association, sans échafaudage, mode adb seul livré : Claude ouvre lui-même les Paramètres sur l'écran haut et répond « Android 13 » en 31 s, Codex en 61 s environ, tous deux par les outils `thor_android` (`artifacts/screenshots/03/adb-seul-essais.txt`). Aussi réussi en mode mobilecli (`sans-fil-essais.txt`).*
- [x] Une capture de l'écran bas faite par un agent est possible, ou sa limite est expliquée. *mobile-mcp ne voit que l'écran haut ; `thor-screen capture bas` comble le manque. Prouvé sans échafaudage : pendant la consigne « À propos », Claude a capturé et piloté l'écran bas par `thor-screen` (`sans-fil-claude-bas.png`).*
- [x] `docs/adr/03-acces-des-agents-a-android.md` : choix, essais, limites, comment désactiver.

## Rapport

**Statut : à fusionner** (13 novembre, 23 h 15). L'association du débogage sans fil est faite avec la clé de la Debian. Tout passe sur le chemin livré, sans le Mac.

**Livré** : `thor_android` = mobile-mcp 1.0.17, déclaré à Claude Code et à Codex ; adb de la Debian sur une socket Unix privée ; `start.sh` / `stop.sh` pour le pont ; `thor-screen` pour l'écran bas (`ids`, `capture`, `tap`, `swipe`, `text`, `launch`) ; consignes globales des deux agents ; `install.sh` / `uninstall.sh` idempotents ; `pair.sh`. ADR 03 : choix, essais, limites, sécurité, comment couper.

**Essais sur la Thor, sans échafaudage** (aucun `reverse` côté Mac ; le Mac ne fait que lancer le script dans la Debian) :
- mode mobilecli : Claude « Android 13 » en 45,5 s (par `thor-screen`, sur l'écran bas : l'écran haut portait `app.regie.voicelab`, qu'il n'a pas voulu toucher), Codex « Android 13 » par `thor_android` (`sans-fil-essais.txt`) ;
- mode adb seul (livré) : Claude en 31 s, Codex en 61 s environ, tous deux par `thor_android` sur l'écran haut. Ni forward, ni DeviceServer, ni dossier `mobilecli` dans Régie (`adb-seul-essais.txt`) ;
- `thor-screen launch bas com.android.settings` marche ; `launch bas app.regie` est refusé (`launch-bas.png`) ;
- `stop.sh` ferme tout : serveur adb, forwards, DeviceServer.

**Changements de cette reprise** :
- **mobile-mcp en mode adb seul par défaut** (`MOBILEMCP_LEGACY_ROBOT=1`), tranché à la relecture. En mode mobilecli, on a observé deux choses : des ports `127.0.0.1:N` ouverts sur la console, joignables par toute appli, et l'agent JVMTI de mobilecli injecté dans le build de debug de Régie (`/data/data/app.regie/mobilecli`, en 0777), sans geste de l'agent sur Régie. Le dossier a été retiré ; `uninstall.sh` le retire aussi. Coût du mode adb seul : l'arbre d'interface renvoyé est celui de l'écran bas, et quelques outils disparaissent. Les agents travaillent sur la capture ; les consignes le leur disent. Retour arrière : `MOBILEMCP_LEGACY_ROBOT=0` dans l'`env` des deux déclarations MCP.
- `pair.sh` refuse d'associer si le serveur qui répond sur la socket n'est pas celui de la Debian (code 3). C'est ce qui avait fait associer la clé du Mac la première fois.
- `thor-screen launch`.

**À faire avec l'utilisateur** :
1. **Redémarrage** (pas fait : les tickets 02 et 04 utilisent la console). Après un redémarrage, lancer `/opt/thor-android/start.sh` dans la Debian (`dev-linux`). Si la réponse est `raison=non-connecte`, rallumer « Utiliser le débogage sans fil » (Paramètres › Options pour les développeurs › Débogage sans fil), puis relancer. Noter dans l'ADR 03 si l'action manuelle a été nécessaire.
2. **Clé du Mac** : dans Débogage sans fil › Appareils associés, retirer l'appareil associé lors de la première tentative (la clé du Mac), s'il y figure encore. Garder celui de la Debian.

**Questions** (ADR 03, « Questions ouvertes ») :
- ❓ Un agent peut-il ouvrir une autre appli par-dessus Régie, qui passe alors en arrière-plan sur cet écran ? Les consignes le permettent.
- ❓ Garder le mode adb seul (sûr, mais sans arbre d'interface fiable en haut), ou revenir au mode mobilecli (arbre fiable, mais ports ouverts et injection dans les builds de debug de Régie) ?

