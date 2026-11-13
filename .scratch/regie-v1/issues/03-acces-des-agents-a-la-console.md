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

- [ ] Sur la Thor, dans une session Claude puis une session Codex, la consigne « ouvre les Paramètres, va dans À propos et dis-moi la version d'Android » réussit sans intervention, à part les validations. *Prouvé avec l'échafaudage `adb reverse tcp:5037` (serveur adb du Mac) seulement : à refaire sur le chemin livré, après l'association.*
- [x] Une capture de l'écran bas faite par un agent est possible, ou sa limite est expliquée. *mobile-mcp ne voit que l'écran haut ; `thor-screen capture bas` comble le manque. Prouvé avec l'échafaudage, consigne qui donnait la commande.*
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

**Questions** (ADR 03, « Questions ouvertes ») :
- ❓ Un agent peut valider ses propres demandes en touchant la bannière de Régie (écran haut par mobile-mcp, écran bas par `thor-screen`), ce qui contourne « rien ne part sans un appui sur A ». Faut-il interdire aux agents de toucher Régie, ou soumettre les outils `thor_android` à validation ?
- ❓ Comment les agents apprennent-ils que `thor-screen` existe : consignes globales des agents, ou le pont à chaque session ?

**Hors périmètre** : mobilecli injecte un agent JVMTI dans l'appli débogable au premier plan (builds de debug de Régie) ; le serveur adb écoute sur `127.0.0.1:5037` sans authentification tant qu'il tourne (piste `ADB_SERVER_SOCKET` notée dans l'ADR).
