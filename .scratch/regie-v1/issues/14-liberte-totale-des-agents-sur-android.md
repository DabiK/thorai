# 14 — Liberté totale des agents sur Android

Statut : fait
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

- [x] Sur la Thor, sans le Mac :
  - [x] Claude ouvre Régie sur l'écran du bas et y touche « Nouvelle session » (le geste est fait, mais aucune session n'est créée : voir le rapport) ;
  - [x] Codex ouvre les Paramètres sur l'écran du haut et lit la version d'Android.
  
  [x] Dans une vraie session, aucune validation n'est demandée pour ces gestes.
- [x] `install.sh` reste idempotent ; `uninstall.sh` revient à l'état d'avant.

## Rapport

13 novembre 2026. Branche `ticket/14-liberte-totale-des-agents-sur-android`. Essais détaillés : `artifacts/screenshots/14/essais.txt`.

**Ce qui marche**
- mobile-mcp est en mode mobilecli par défaut. Pour repasser en adb seul, une ligne suffit : `mkdir -p ~/.config/thor-android && touch ~/.config/thor-android/adb-seul`. `install.sh` ne touche pas ce fichier, donc le réglage survit à une réinstallation.
- Les consignes et `thor-screen` n'interdisent plus Régie. Les consignes gardent quel outil sert à quel écran et comment voir l'appli au premier plan de chaque écran.
- Sans validation :
  - Claude : quatre entrées dans `~/.claude/settings.json`, `mcp__thor_android__*`, `Bash(thor-screen *)`, `Bash(adb *)` et `Read(//tmp/thor-screen/**)`. Cette dernière est nécessaire pour lire les captures.
  - Codex : `default_tools_approval_mode = "approve"` dans la déclaration `thor_android`, plus les règles `~/.codex/rules/thor-android.rules` (`adb` et `thor-screen` en `allow`).
  - `uninstall.sh` retire tout cela.
- L'ADR 03 et le README disent ce que la décision ouvre et comment remettre des restrictions.

**Essais sur la Thor**
- `claude -p`, sans `--allowedTools` : `permission_denials: []`. Claude ouvre Régie, puis fait `thor-screen tap bas 390 1023` sur « Nouvelle session ».
  - Aucune session n'est créée. « X Nouvelle session » n'est que le rappel d'une touche de la manette. Claude a donc envoyé la touche X.
  - Une demande de permission Termux est apparue à ce moment-là. Elle venait d'un autre ticket, qui réinstallait Régie pendant l'essai.
- Codex interactif dans tmux, sans option d'approbation : il lit « Android 13 », sans qu'aucune validation ne s'affiche.
  - Contre-essai : avec `auto` au lieu de `approve`, Codex demande une validation.
- `install.sh` lancé deux fois : les sommes SHA-256 sont identiques.
- `uninstall.sh` : `settings.json` revient à l'octet près, `config.toml` et les `mcpServers` sont ceux d'avant. `install.sh` a ensuite été relancé.

**Relecture** : un tour, APPROUVÉ. Deux remarques de documentation ont été corrigées.

**Pour l'utilisateur**
- Il faut refaire l'association du débogage sans fil (README, « Associer la console »). L'essai de `uninstall.sh` a effacé la clé adb de la Debian, comme `uninstall.sh` le fait depuis le ticket 03. Depuis, `start.sh` répond `non-connecte`, et aucun agent ne joint Android tant que l'association n'est pas refaite.

**Questions**
- ❓ `uninstall.sh` doit-il continuer à effacer la clé adb de la Debian ? Cela coupe l'accès proprement, mais oblige à refaire l'association à la main après toute désinstallation.

**Hors périmètre**
- Régie montre des sessions factices sur la console, alors que le journal dit qu'elle ne doit jamais en montrer.
- Toucher « Nouvelle session » à l'écran ne fait rien : ce n'est qu'un rappel de touche.
- L'activité du bas de Régie n'est pas exportée : `thor-screen launch bas app.regie` ouvre l'activité du haut sur l'écran du bas.
