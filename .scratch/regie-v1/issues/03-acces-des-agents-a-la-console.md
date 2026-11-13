# 03 — Accès des agents à la console (computer use)

Statut : prêt
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

- Sur la Thor, dans une session Claude puis une session Codex, la consigne « ouvre les Paramètres, va dans À propos et dis-moi la version d'Android » réussit sans intervention, à part les validations.
- Une capture de l'écran bas faite par un agent est possible, ou sa limite est expliquée.
- `docs/adr/03-acces-des-agents-a-android.md` : choix, essais, limites, comment désactiver.

## Rapport

(à remplir)
