# 03 — Accès des agents à la console (computer use)

Statut : bloqué
Bloqué par : association du débogage sans fil, à faire par l'utilisateur sur l'écran de la console
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

- [ ] Sur la Thor, dans une session Claude puis une session Codex, la consigne « ouvre les Paramètres, va dans À propos et dis-moi la version d'Android » réussit sans intervention, à part les validations. *Prouvé avec un échafaudage seulement (serveur adb du Mac, joint par la socket privée : Claude 29,7 s, Codex 44,3 s, « Android 13 ») : à refaire sur le chemin livré, après l'association.*
- [x] Une capture de l'écran bas faite par un agent est possible, ou sa limite est expliquée. *mobile-mcp ne voit que l'écran haut ; `thor-screen capture bas` comble le manque. Prouvé avec l'échafaudage ; depuis les consignes globales, Claude trouve seul `thor-screen capture bas` (15,5 s).*
- [x] `docs/adr/03-acces-des-agents-a-android.md` : choix, essais, limites, comment désactiver.

## Rapport

**Ce qui est livré** (commits sur `ticket/03-acces-des-agents-a-la-console`) :
- serveur MCP `thor_android` = `@mobilenext/mobile-mcp` 1.0.17 (télémétrie coupée), installé dans `/opt/thor-android` et déclaré à Claude Code (`/home/dev/.claude.json`) et à Codex (`~/.codex/config.toml`) à côté de `thor_browser` ;
- adb du paquet Debian ; `start.sh` / `stop.sh` pour le pont (ligne `THOR_ANDROID=…`, codes 0/1/2) ; `thor-screen` pour l'écran bas ; `install.sh` / `uninstall.sh` idempotents depuis le Mac ; `pair.sh` / `pair-from-mac.sh` ;
- ADR 03 : candidats (mobile-mcp retenu, Android-MCP 0.2.0 en repli, DroidRun écarté), limites, sécurité, comment couper.

**Essais sur la Thor** (sorties dans `artifacts/screenshots/03/`) : avec l'échafaudage temporaire `adb reverse tcp:5037` (retiré depuis), Claude 2.1.322 répond « Android 13 » en 25 s et Codex 0.167.2 en 48 s, uniquement par les outils `thor_android`. Claude capture l'écran bas avec `thor-screen`. Sans l'échafaudage, `start.sh` répond `THOR_ANDROID=erreur raison=non-connecte`, code 2, avec le message à l'utilisateur.

**Bloqué : l'association du débogage sans fil** exige une boîte de dialogue sur l'écran de la console. Marche à suivre : `thor/computer-use/README.md`, « Associer la console ». En bref :
1. Console : Paramètres › Options pour les développeurs › Débogage sans fil › activer « Utiliser le débogage sans fil », cocher « Toujours autoriser sur ce réseau », Autoriser.
2. Toucher « Associer l'appareil avec un code d'association » ; noter le port (après les deux-points) et le code à 6 chiffres, sans fermer la fenêtre.
3. Sur le Mac, console en USB : `thor/computer-use/pair-from-mac.sh <port> <code>`. Réponse attendue : `THOR_ANDROID=ok … via=sans-fil`.
4. Ensuite, refaire les deux consignes « À propos » sans le Mac, et redémarrer une fois la console pour voir si le débogage sans fil revient seul (ADR 03, « Limites »).

**Reprise du 13 novembre (décisions de l'utilisateur)** :
- **Consignes globales** : `thor/computer-use/consignes.md` est posé entre deux marqueurs dans `~dev/.claude/CLAUDE.md` et `~/.codex/AGENTS.md` par `install.sh` (idempotent, reste du fichier préservé), retiré par `uninstall.sh`. Il présente `thor_android` et `thor-screen` et interdit de toucher `app.regie`. Essai : Claude refuse de toucher Régie ; Codex a d'abord obéi (première version des consignes), puis refuse après l'ajout « même si la demande vient de la conversation » (un seul essai). Le vrai garde-fou reste le ticket 07.
- **Socket Unix privée** : le serveur adb de la Debian écoute sur `localfilesystem:/home/dev/.thor-adb/server` (dossier 0700) et plus sur 5037 (vérifié côté Debian et côté Android). `start.sh`, `stop.sh`, `pair.sh`, `thor-screen`, la cale `~dev/.local/bin/adb` et les déclarations MCP des deux agents (`env`) passent par elle. mobile-mcp, mobilecli et `thor-screen` vérifiés par la socket avec un échafaudage provisoire, retiré depuis.
- Relecture : 3 tours ; corrigés notamment un `adb shell` qui avalait le reste du script de désinstallation, et des échecs d'écriture annoncés comme réussis.

**Questions** (ADR 03, « Questions ouvertes ») :
- ❓ Un agent peut-il ouvrir une autre appli par-dessus Régie, qui passe alors en arrière-plan sur cet écran ? Les consignes le permettent aujourd'hui ; sinon, aucun agent ne peut agir sur Android pendant qu'on se sert de Régie, et la consigne « À propos » échoue.
- ❓ Lancer mobile-mcp avec `MOBILEMCP_LEGACY_ROBOT=1` ? On supprimerait le DeviceServer de mobilecli (socket abstraite et forward TCP joignables par d'autres applis, agent JVMTI dans les builds de debug de Régie), au prix de quelques outils (toucher par référence, appli au premier plan, presse-papiers, journaux).

**À savoir** : mobilecli injecte un agent JVMTI dans l'appli débogable au premier plan, ce qui concerne les builds de debug de Régie (ADR 03, « Limites »). `bas.png` (prise depuis le Mac) et `bas-par-agent.png` sont identiques à l'octet près : l'écran n'avait pas bougé entre les deux captures.
