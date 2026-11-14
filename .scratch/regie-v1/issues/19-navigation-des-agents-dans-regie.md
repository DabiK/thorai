# 19 — Les agents lancés par Régie doivent pouvoir piloter Android

Statut : prêt
Bloqué par : 16
Possède : `bridge/`, `thor/`

## Objectif

Retour de l'utilisateur, le 14 novembre :

> « La navigation de l'agent dans la console, j'ai pas l'impression qu'elle fonctionne. »

Dans un terminal, Claude et Codex pilotent bien Android (ticket 14).

## Piste de l'orchestrateur

`bridge/src/adapters/acp/acp-connector.ts` ouvre les sessions avec `mcpServers: []`. Les sessions lancées par Régie n'ont donc probablement ni `thor_android` ni `thor_browser`. Il est possible aussi qu'elles ne chargent pas les consignes globales de l'utilisateur : `CLAUDE.md` pour Claude, `AGENTS.md` pour Codex, ni ses réglages, dont les permissions posées par le ticket 14. C'est le cas si l'adaptateur Claude ne charge pas les sources de réglages de l'utilisateur.

## À livrer

- Vérifier dans la documentation à jour de `claude-agent-acp` et `codex-acp` ce qu'ils chargent : MCP de l'utilisateur, consignes globales, réglages.
- Faire en sorte qu'une session lancée par Régie ait exactement les mêmes outils, consignes et permissions que l'agent lancé dans un terminal. Par exemple :
  - passer à `session/new` et `session/load` les serveurs MCP déclarés dans la configuration de l'agent ;
  - activer le chargement des réglages de l'utilisateur.
  
  Garder une seule source de vérité : la configuration de l'agent, pas une copie dans le pont.
- Test unitaire. Test de contrat sur le Mac avec un serveur MCP factice déclaré dans la configuration de l'agent : la session Régie le voit et l'appelle.
- Écrire dans le rapport l'essai à refaire sur la Thor au retour de l'utilisateur : « ouvre les Paramètres et dis-moi la version d'Android », lancé depuis Régie avec Codex.

## Critères d'acceptation

- Sur le Mac (pont du ticket 16), une session Codex lancée par Régie liste et appelle un outil MCP déclaré dans `~/.codex/config.toml`. Même chose pour Claude, sans dépenser de quota : vérifier seulement la liste des outils au démarrage.
