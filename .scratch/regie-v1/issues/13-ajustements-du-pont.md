# 13 — Ajustements du pont : lectures sans validation, conservation, branches laissées à l'agent

Statut : prêt
Bloqué par : rien (le pont du ticket 02 est fusionné)
Possède : `bridge/`, `docs/protocole-pont.md`, `docs/adr/02-pilotage-des-agents.md`

## Objectif

Appliquer trois décisions de l'utilisateur, prises le 13 novembre sur les questions du ticket 02.

## À livrer

1. **Les lectures passent sans validation.**
   - En mode « valider », le pont accepte seul les commandes en lecture seule et ne les remonte pas à l'app : `cat`, `ls`, `rg`, `grep`, `head`, `tail`, `wc`, `find` sans `-exec` ni `-delete`, `git status`, `git log`, `git diff`, `git show`…
   - La règle est stricte : une redirection, un tube vers une commande qui écrit, une substitution ou un enchaînement avec une commande qui écrit font tout remonter.
   - Liste et règle sont testées une à une, y compris les cas qui doivent remonter.
   - Une lecture acceptée seule reste visible dans le fil, comme une action ordinaire.
2. **Conservation de l'historique.**
   - Détail complet des sessions pendant 30 jours.
   - Au-delà : la session reste listée avec son résumé (titre, agent, projet, statut, dates, fichiers modifiés), et ses événements sont purgés du disque comme de la mémoire.
   - La durée est réglable. Le nettoyage tourne au démarrage du pont, puis une fois par jour.
3. **Le pont ne touche plus aux branches.** C'est l'agent qui les gère.
   - `session.create` ne fait plus de `git switch`. Si `branch` est fourni, il est ajouté en tête de la consigne à l'agent, par exemple « Travaille sur la branche X, crée-la depuis main si elle n'existe pas » ; l'agent choisit comment faire (branche, worktree…).
   - L'erreur `branch_failed` disparaît. La branche affichée d'une session est relue dans son dossier de travail.
   - Mettre à jour `docs/protocole-pont.md` et l'ADR 02.

## Critères d'acceptation

- Tests unitaires des trois points. `npm run check` est vert.
- Sur la Thor, une session Codex en mode « valider » lit des fichiers sans demande de validation, et demande bien une validation pour une écriture.
- Une session créée avec une branche tourne sans `git switch` du pont, et l'agent s'est placé sur la branche demandée.

## Rapport

(à remplir)
