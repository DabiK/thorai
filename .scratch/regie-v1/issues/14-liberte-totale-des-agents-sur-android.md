# 14 — Liberté totale des agents sur Android

Statut : prêt
Bloqué par : rien (le ticket 03 est fusionné)
Possède : `thor/computer-use/`, `docs/adr/03-acces-des-agents-a-android.md`, la configuration MCP et les permissions des deux agents sur la console

## Objectif

Appliquer la décision de l'utilisateur du 13 novembre, en réponse aux questions du ticket 03 :

> « L'agent peut faire tout ce qu'il veut, écran du bas, écran du haut, il peut tout piloter. La méthode où il a le plus de liberté. S'il y a des restrictions à mettre, je les mettrai plus tard. Ne te mets pas de barrières. »

Régie comprise : l'utilisateur veut pouvoir « devenir méta » et faire piloter Régie par un agent.

## À livrer

1. **mobile-mcp en mode mobilecli par défaut** (`MOBILEMCP_LEGACY_ROBOT=0`), pour un arbre d'interface fiable et tous les outils. Les ports locaux et l'injection dans les builds de debug sont acceptés par l'utilisateur. Le mode adb seul reste disponible en réglage.
2. **Consignes globales des agents** : retirer l'interdiction de toucher Régie et la variante prudente. Les agents peuvent agir sur les deux écrans et sur toutes les applis, Régie comprise. Garder les informations utiles : quel outil pour quel écran, et comment savoir quelle appli est au premier plan de chaque écran.
3. **`thor-screen`** : retirer le refus de `launch … app.regie` et toute autre restriction propre à Régie.
4. **Pas de validation pour piloter Android.**
   - Claude : autoriser sans demande les outils `thor_android` et les commandes `thor-screen` et `adb` dans ses permissions sur la console.
   - Codex : l'équivalent, s'il existe, à vérifier dans sa documentation à jour.
   - Le tout est scripté dans `install.sh` et retiré par `uninstall.sh`.
5. **ADR 03 et README** : la décision, ce qu'elle ouvre, et comment remettre des restrictions plus tard (le retour arrière doit tenir en une ou deux lignes).

## Critères d'acceptation

- Sur la Thor, sans le Mac :
  - Claude ouvre Régie sur l'écran du bas et y touche « Nouvelle session » ;
  - Codex ouvre les Paramètres sur l'écran du haut et lit la version d'Android.
  
  Dans une vraie session, aucune validation n'est demandée pour ces gestes.
- `install.sh` reste idempotent ; `uninstall.sh` revient à l'état d'avant.

## Rapport

(à remplir)
