# PRD — Régie, poste de pilotage vocal de Claude Code et Codex sur l'AYN Thor

Statut : version installable, consolidée le 14 novembre 2026 après les tickets 15 à 19. Les décisions datées du journal remplacent les intentions antérieures de la maquette.

## Problème

Claude Code et Codex tournent sur la Thor, dans un terminal Termux. Sur une console, le terminal est pénible :
- il faut taper au clavier ;
- on ne voit qu'une session à la fois ;
- rien ne prévient quand un agent attend une validation ;
- un diff se lit mal.

## Promesse

On prend la console et on parle. On voit d'un coup d'œil où en sont toutes ses sessions. On lance une session à la voix, on lui parle en cours de route, on valide ses actions à la manette et on relit ses changements, sans jamais taper.

## Principes d'expérience

- **La voix d'abord, la manette ensuite, le tactile en secours.** Tout se fait sans clavier.
- **Écran du haut = le contenu**, ce qui se passe dans les sessions. **Écran du bas = la voix et les actions**, tactiles aussi.
- **Rien ne part sans un appui sur A.** Une dictée se relit avant l'envoi. Dire « approuve » demande quand même A.
- **L'attention n'est demandée que pour une décision** : une validation, une question de l'agent, une fin de session, un échec. Le reste se consulte.
- **L'app ne refait pas le travail des agents.** Outils, consignes et réglages restent ceux de Claude Code et Codex. On relaie ce qu'ils exposent. Quand un agent n'expose pas une information, l'app le dit au lieu d'inventer.
- **Une app qu'on lance, rien au démarrage de la console.** Rien ne démarre au boot. À son lancement, l'app démarre le pont s'il ne tourne pas. Passer l'app en arrière-plan ou éteindre l'écran ne coupe pas une session en cours. « Quitter » arrête le pont et les sessions, avec une confirmation si une session travaille encore.
- **L'écran du bas reste un tableau de bord.** Quand Régie passe en arrière-plan, son écran du bas reste affiché pendant qu'on regarde autre chose en haut. Seul « Quitter » le ferme.
- **Une information par zone.** Pas de jargon : on écrit « attend ta validation », pas « permission_request ».

## Commandes

| Entrée | Action |
| --- | --- |
| L2 maintenu | Dicter un message à la session affichée (ou un commentaire sur la ligne de diff sélectionnée) |
| R2 maintenu | Donner un ordre vocal : « lance Claude sur météo », « où en est Codex ? », « approuve », « mets tout en pause » |
| A | Valider, envoyer, ouvrir |
| B | Retour, annuler, refuser |
| X, Y | Actions de l'écran, affichées en bas |
| L1 / R1 | Session précédente / suivante. Sur la vue d'ensemble, R1 ouvre d'abord la validation en attente s'il y en a une |
| Croix | Se déplacer (cartes, champs, lignes de diff) |
| Stick droit | Faire défiler |
| Start | Interrompre la session affichée, ou tout mettre en pause depuis la vue d'ensemble |

L'écran du bas affiche toujours les boutons utiles à cet instant.

## Parcours (les 6 écrans de la maquette)

1. **Vue d'ensemble.**
   - En haut : une carte par session (agent, projet, branche, statut, étape x sur y, dernière action, ancienneté).
   - En bas : la première validation en attente et le micro.
2. **Dictée.**
   - En haut : le texte transcrit en direct, avec les noms de fichiers et de fonctions du dépôt reconnus.
   - En bas : le niveau du micro et la durée.
   - Une fois L2 relâché : A envoie, B efface, Y complète, X corrige un mot.
3. **Session en direct.**
   - En haut : le fil (message dicté, réponses, actions résumées en une ligne, texte en cours), le plan de l'agent et les fichiers modifiés.
   - En bas : parler à l'agent sans l'arrêter, voir le diff, faire le point (résumé écrit), interrompre.
4. **Validation.**
   - En haut : la commande ou le fichier, pourquoi, et ce que ça touche.
   - En bas : autoriser une fois, toujours dans cette session, refuser ; ou refuser en dictant une consigne.
5. **Diff.**
   - En haut : le diff du fichier, ligne par ligne à la croix.
   - En bas : la liste des fichiers, commenter la ligne à la voix, demander une explication.
6. **Nouvelle session à la voix.**
   - Un ordre R2 devient une fiche pré-remplie : agent, projet, branche, niveau de validation, consigne.
   - On corrige à la croix, puis A lance.

## Fonctionnalités de la v1

Chaque lot correspond à un ou plusieurs tickets.

- **Socle Android** :
  - deux écrans ;
  - mode un seul écran si celui du bas est éteint ;
  - manette lue quel que soit l'écran qui a le focus ;
  - système visuel de la maquette.
- **Pont sur la Thor** :
  - sessions Claude et Codex exposées à l'app par une connexion locale protégée ;
  - les sessions continuent quand l'app passe en arrière-plan ;
  - historique et reprise ;
  - démarré par l'app à son lancement, jamais au boot.
- **Accès des agents à la console** : Claude et Codex peuvent voir et piloter tout Android, les deux écrans et Régie comprise, sans validation des gestes (décision du ticket 14).
- **Voix** :
  - **Transcription** : en français en direct, avec le vocabulaire du dépôt.
  - **Pas de lecture à voix haute** (décision de l'utilisateur, 14 novembre) : les résumés sont écrits à l'écran.
  - **Fournisseurs** : transcription locale et gratuite par défaut ; ElevenLabs en option. Les ports de synthèse restent dans le module voix et son laboratoire, sans lecture dans Régie.
- **Vue d'ensemble, session en direct, validations, diff, nouvelle session** : comme décrits dans les parcours.
- **Ordres vocaux (R2)** : règles fixes pour les ordres courants, plus un petit modèle de langage quand la phrase est ambiguë.
- **Nouveau projet** : création explicite d’un dépôt Git vide sur main, nom relu et collisions refusées ; les changements de branches existantes restent confiés à l’agent (ticket 15).
- **Options natives** : modèle, réflexion et sélecteurs ACP exposés, à la création et en session ; Plan d’abord, Modifs auto, Je valide chaque action et Tout autoriser (ticket 17).
- **Session tout-en-un** : validations de la session dans son fil, options sur le bas, détails d’actions dépliables, dates et suivi du défilement (ticket 18).
- **Configuration native** : MCP, consignes et permissions restent résolus par les agents, sans copie par le pont (ticket 19).
- **Points écrits et notifications** :
  - fin de session, validation demandée, ou point d'avancement à la demande (Y), sous forme de résumé écrit ;
  - aucune lecture à voix haute.

## Voix

- **Port de transcription** : un flux de texte partiel puis final, la langue, et une liste de termes à favoriser (fichiers, fonctions et branches du dépôt).
- **Port de synthèse** : du texte en audio, en flux.
- **Détection de parole** : sur l'appareil.
- **Fournisseur par défaut : la voix locale**, gratuite et hors ligne (sherpa-onnx), choisie après l’essai du 13 novembre. Régie prépare uniquement la transcription ; la synthèse reste disponible pour le laboratoire voix.
- **ElevenLabs en option** (Scribe pour la transcription, Flash pour la synthèse), si la voix locale déçoit. L'utilisateur saisit lui-même sa clé dans l'app.
- **Pas de reconnaissance vocale système** : la Thor n'a aucun service de reconnaissance ni de synthèse Google. On ne peut donc pas compter sur ceux d'Android.

## Hors périmètre de la v1

- Distribuer l'app à d'autres personnes : il faudrait des clés d'API à la place de l'abonnement claude.ai.
- Un mode conversation en temps réel avec un agent vocal.
- Un mot d'éveil.
- Faire les commits et les pushs depuis l'app.
- Afficher l'éditeur web ou le navigateur des agents.

## Risques

- **Claude Code** n'a pas de version Android officielle. Il tourne dans la Debian, et une mise à jour peut le casser. Le pont relève les versions et avertit.
- **Les protocoles des agents bougent.** Il faut figer les versions testées.
- **Android peut tuer les processus de Termux.** Termux est exempté de l'économie de batterie et le tueur de processus fantômes est coupé, mais une mise à jour système peut rétablir ces réglages.
- **Une connexion locale est joignable par toutes les applis de la console** : un jeton est obligatoire.
- **La sandbox de Codex est désactivée** dans la Debian. Les validations ne sont pas une frontière de sécurité : les agents peuvent piloter Régie et ses boutons, et le mode Tout autoriser ne demande aucune confirmation.
- **Le contenu dicté part chez le fournisseur de voix** quand on utilise le cloud.
