# PRD — ThorAI, poste de pilotage vocal de Claude Code et Codex sur l'AYN Thor

ThorAI s'appelait Régie jusqu'au ticket 30 : ce nom de code reste dans les paquets Kotlin, les scripts et le dépôt (ADR 21).

Statut : version installable, consolidée le 18 novembre 2026 après les tickets 22 à 26, complétée par le ticket 31 (opencode), puis par les tickets 28 (parcours d'accueil) et 30 (ThorAI, finitions). Les décisions datées du journal remplacent les intentions antérieures de la maquette. Installation : `docs/installation.md`.

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
- **Rien ne part sans un appui sur A.** Une dictée se relit avant l'envoi. Dire « approuve » demande quand même A. La navigation vocale (« où en est … ») ouvre directement la session visée ou active, sans relecture ni A (ticket 26).
- **L'attention n'est demandée que pour une décision** : une validation, une question de l'agent, une fin de session, un échec. Le reste se consulte.
- **L'app ne refait pas le travail des agents.** Outils, consignes et réglages restent ceux de Claude Code et Codex. On relaie ce qu'ils exposent. Quand un agent n'expose pas une information, l'app le dit au lieu d'inventer.
- **Une app qu'on lance, rien au démarrage de la console.** Rien ne démarre au boot. À son lancement, l'app démarre le pont s'il ne tourne pas. Passer l'app en arrière-plan ou éteindre l'écran ne coupe pas une session en cours. « Quitter » arrête le pont et les sessions, avec une confirmation si une session travaille encore.
- **L'écran du bas reste un tableau de bord.** Quand ThorAI passe en arrière-plan, son écran du bas reste affiché pendant qu'on regarde autre chose en haut. Seul « Quitter » le ferme.
- **Une information par zone.** Pas de jargon : on écrit « attend ta validation », pas « permission_request ».

## Commandes

| Entrée | Action |
| --- | --- |
| L2 maintenu | Dicter un message à la session affichée (ou un commentaire sur la ligne de diff sélectionnée). Avec le mode console, aussi depuis une autre appli (ticket 25) |
| R2 maintenu | Donner un ordre vocal : « lance Claude sur météo », « où en est Codex ? » (ouvre la session), « approuve », « mets tout en pause » |
| A | Valider, envoyer, ouvrir |
| B | Retour, annuler, refuser ; sur la vue d'ensemble, connexion des agents (ticket 23) |
| X, Y | Actions de l'écran, affichées en bas ; sur la vue d'ensemble, X crée une session et Y pilote la console (ticket 25) |
| L1 / R1 | Session précédente / suivante. Sur la vue d'ensemble, R1 ouvre d'abord la validation en attente s'il y en a une |
| Croix | Se déplacer (lignes de la vue d'ensemble, champs, lignes de diff) ; dans une session, Droite passe les réglages en modification |
| Stick droit | Faire défiler |
| Start | Interrompre la session affichée. Sur la vue d'ensemble (le hameau), ouvrir le menu de pause : « Tout mettre en pause » s'y trouve et se valide par A (décision de l'utilisateur, 18 novembre, ticket 49) |

L'écran du bas affiche toujours les boutons utiles à cet instant.

## Parcours (les 6 écrans de la maquette)

1. **Vue d'ensemble.**
   - En haut : une liste dense, une ligne par session (agent, projet, branche, statut, étape x sur y, dernière action, ancienneté), défilable (ticket 22).
   - En bas : la première validation en attente, le micro, et les actions : créer une session, piloter la console, connecter les agents, configurer, Quitter.
2. **Dictée.**
   - En haut : rien ne change, le fil (ou l'écran affiché) reste visible (ticket 22).
   - En bas : le texte transcrit en direct, avec les noms de fichiers et de fonctions du dépôt reconnus, le niveau du micro et la durée.
   - Une fois L2 relâché : A envoie, B efface, Y complète, X corrige un mot. Un message reçu par l'agent ramène seul à l'écran précédent.
3. **Session en direct.**
   - En haut : le fil brut de l'agent (message envoyé, réponses, actions résumées en une ligne, texte en cours), son plan et les fichiers modifiés s'il en expose. Aucun point ni bilan ajouté par ThorAI (ticket 22).
   - En bas : la table des réglages exposés par l'agent (mode, modèle, réflexion, mode rapide…), modifiables sur place ; parler à l'agent sans l'arrêter, voir le diff, interrompre. Quand une autre appli occupe le haut, le bas suit le fil en version compacte (ticket 25).
4. **Validation.**
   - En haut, dans le fil de la session (ticket 18) : la commande ou le fichier, pourquoi, et ce que ça touche.
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
- **Parcours d'accueil** (ticket 28, ADR 23) : au premier lancement, neuf étapes vérifiées sur la console, explication en haut, état et actions en bas : Termux et Debian, pont, voix, notifications, puis mode console, accès à Android et comptes, facultatifs, jusqu'à la première session. « Configurer », sur l'accueil, le reprend à la première étape non faite.
- **Agents disponibles** (ticket 31) : catalogue annoncé par le pont (nom, version, connexion, options), listes construites depuis ce catalogue dans l’app ; opencode est un troisième connecteur ACP, les fournisseurs et modèles restent natifs. Le parcours d'accueil (ticket 28) l'ouvre à son étape des comptes, facultative.
- **Pont sur la Thor** :
  - sessions Claude et Codex exposées à l'app par une connexion locale protégée ;
  - les sessions continuent quand l'app passe en arrière-plan ;
  - historique et reprise ;
  - démarré par l'app à son lancement, jamais au boot.
- **Accès des agents à la console** : Claude et Codex peuvent voir et piloter tout Android, les deux écrans et ThorAI comprise, sans validation des gestes (décision du ticket 14). Quand l'utilisateur parle de l'écran, d'une appli ou du navigateur, ils agissent de façon visible, applis ouvertes en haut (ticket 24).
- **Connexion des agents** (ticket 23, ADR 18) : état, connexion et déconnexion depuis ThorAI. Codex par code d'appareil, avec adresse, code et QR code ; Claude, sans code d'appareil, par `claude auth login` dans Termux, marche indiquée dans l'app. OpenCode Go par clé saisie par l’utilisateur dans un champ masqué, transmise au pont puis stockée nativement par opencode (ticket 31). Aucun compte existant n’est importé.
- **Voix** :
  - **Transcription** : en français en direct, avec le vocabulaire du dépôt.
  - **Pas de lecture à voix haute** (décision de l'utilisateur, 14 novembre) : tout se lit à l'écran.
  - **Fournisseurs** : transcription locale et gratuite par défaut ; ElevenLabs en option. Les ports de synthèse restent dans le module voix et son laboratoire, sans lecture dans ThorAI.
- **Vue d'ensemble, session en direct, validations, diff, nouvelle session** : comme décrits dans les parcours.
- **Ordres vocaux (R2)** : règles fixes pour les ordres courants, plus un petit modèle de langage quand la phrase est ambiguë.
- **Nouveau projet** : création explicite d’un dépôt Git vide sur main, nom relu et collisions refusées ; les changements de branches existantes restent confiés à l’agent (ticket 15).
- **Options natives** : modèle, réflexion et sélecteurs ACP exposés, à la création et en session ; Plan d’abord, Modifs auto, Je valide chaque action et Tout autoriser (ticket 17).
- **Session tout-en-un** : validations de la session dans son fil, options sur le bas, détails d’actions dépliables, dates et suivi du défilement (ticket 18).
- **Configuration native** : MCP, consignes et permissions restent résolus par les agents, sans copie par le pont (ticket 19).
- **Mode console** (tickets 25–26, ADR 19) : L2 et R2 marchent depuis toute appli grâce au service d'accessibilité de ThorAI, activé par l'utilisateur ; l'écran du bas suit la session quand une autre appli occupe le haut ; un message terminé, une question ou une validation de l'agent passe en carte volante par-dessus l'écran du haut (B la ferme, L2 y répond) ; Y ouvre la session console. À sa création, elle propose « Tout autoriser », modifiable dans la fiche et les réglages ; A la crée. Les sessions existantes gardent leur mode.
- **Notifications** : validation demandée, question, fin et échec de session. Plus de point écrit ni de résumé ajouté au fil (ticket 22, ADR 17) : une fin cite la dernière phrase de l'agent. Aucune lecture à voix haute.

## Voix

- **Port de transcription** : un flux de texte partiel puis final, la langue, et une liste de termes à favoriser (fichiers, fonctions et branches du dépôt).
- **Port de synthèse** : du texte en audio, en flux.
- **Détection de parole** : sur l'appareil.
- **Fournisseur par défaut : la voix locale**, gratuite et hors ligne (sherpa-onnx), choisie après l’essai du 13 novembre. ThorAI prépare uniquement la transcription ; la synthèse reste disponible pour le laboratoire voix.
- **ElevenLabs en option** (Scribe pour la transcription, Flash pour la synthèse), si la voix locale déçoit. L'utilisateur saisit lui-même sa clé dans l'app.
- **Pas de reconnaissance vocale système** : la Thor n'a aucun service de reconnaissance ni de synthèse Google. On ne peut donc pas compter sur ceux d'Android.

## Hors périmètre de la v1

- Distribuer l'app sur un store : l'APK signé est distribué par release GitHub, la console se prépare depuis Termux ou par USB (ticket 27). Chacun y connecte ses propres comptes (ticket 23).
- Un mode conversation en temps réel avec un agent vocal.
- Un mot d'éveil.
- Faire les commits et les pushs depuis l'app.
- Afficher l'éditeur web ou le navigateur des agents.

## Risques

- **Claude Code** n'a pas de version Android officielle. Il tourne dans la Debian, et une mise à jour peut le casser. Le pont relève les versions et avertit.
- **Les protocoles des agents bougent.** Il faut figer les versions testées.
- **Android peut tuer les processus de Termux.** Termux est exempté de l'économie de batterie et le tueur de processus fantômes est coupé, mais une mise à jour système peut rétablir ces réglages.
- **Une connexion locale est joignable par toutes les applis de la console** : un jeton est obligatoire.
- **La sandbox de Codex est désactivée** dans la Debian. Les validations ne sont pas une frontière de sécurité : les agents peuvent piloter ThorAI et ses boutons, et le mode Tout autoriser ne demande aucune confirmation.
- **Le contenu dicté part chez le fournisseur de voix** quand on utilise le cloud.
