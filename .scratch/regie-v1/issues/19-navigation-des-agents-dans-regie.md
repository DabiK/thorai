# 19 — Les agents lancés par Régie doivent pouvoir piloter Android

Statut : fait (essai final sur la Thor au retour de la console)
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

- [x] Sur le Mac (pont du ticket 16), une session Codex lancée par Régie liste et appelle un outil MCP déclaré dans `~/.codex/config.toml`. Même chose pour Claude, sans dépenser de quota : vérifier seulement la liste des outils au démarrage.

## Livraison et preuves

- [x] Documentation actuelle et code épinglé des deux ACP vérifiés : ADR 19.
- [x] Configuration native conservée, aucune copie de MCP dans le pont ; chargement
  explicite des sources Claude à la création et à la reprise.
- [x] Tests unitaires sans agent : paramètres ACP `new` et `load` des deux agents.
- [x] Mac : Codex découvre et appelle le MCP déclaré dans le `config.toml` de son
  profil utilisateur temporaire, avec consigne globale et permission native ;
  même preuve après arrêt/reprise du pont. Claude découvre l'outil au démarrage,
  sans prompt ni appel au modèle. Contrat dédié `npm run test:contract:mcp`.
- [ ] Navigation Android réelle depuis Régie : console absente ; essai différé
  selon la consigne de l'utilisateur. Émulateur/47600 réservés au ticket 17.

## Rapport

STATUT: done
RÉSUMÉ: Configuration native des agents vérifiée sur Mac ; sources Claude explicites à la création/reprise.
Codex appelle le MCP utilisateur avec sa consigne globale et sans validation, y compris après reprise.
Ticket à fusionner ; la panne de navigation Thor n'est pas reproduite et reste à vérifier sur place.
CRITÈRES:
- [x] Sources/documents ACP → docs/adr/19-configuration-native-des-agents.md.
- [x] Test unitaire new/load → user-configuration.test.ts, rouge puis vert.
- [x] Codex liste et appelle son MCP → contract.json, profils utilisateur isolés, port 47619.
- [x] Claude liste au démarrage, sans quota modèle → tools/list enregistré, aucun prompt envoyé.
- [x] Une source de vérité → configurations natives ; aucune recopie des MCP dans le pont.
- [ ] Geste Android → Thor absente ; essai physique ci-dessous.
PORTE DE QUALITÉ: npm run check vert : 288 tests, typage et lint ; contrat Mac dédié vert.
THOR: adb devices vide. Aucun essai émulateur, conformément à la réservation du ticket 17.
REVIEW: diff main...HEAD relu en solo ; appel MCP/réponse/reprise vérifiés. Mutation approve→prompt détectée ; nettoyage du banc corrigé si la préparation échoue.
DÉCISIONS PRISES: ADR 19 ; garder le chargement natif, profils temporaires pour ne pas perturber le ticket 17.
POUR L'UTILISATEUR:
1. Rebrancher la Thor ; vérifier adb devices, série THOR0000000001.
2. Après fusion, Quitter Régie ; thor/bridge/install.sh puis scripts/thor-install.sh ; rouvrir Régie.
3. Si accès Android indisponible, activer le débogage sans fil et refaire l'association Debian (thor/computer-use/pair-from-mac.sh).
4. Dans Régie, lancer Codex en « valider » : « ouvre les Paramètres et dis-moi la version d'Android ».
5. Vérifier les gestes réels, la bonne version et aucune validation Android ; scripts/thor-shot.sh artifacts/screenshots/19.
6. Quitter, rouvrir, reprendre la session, refaire l'essai puis Quitter pour tout arrêter.
QUESTIONS: aucune décision produit en attente.
CAPTURES: aucune image ; preuves Mac dans artifacts/screenshots/19/ (contrat, portes, mutation, relecture, arrêt).
HORS PÉRIMÈTRE: association adb perdue déjà signalée aux tickets 14/11 ; cause actuelle non vérifiable sans Thor.
