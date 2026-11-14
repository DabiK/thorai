# Journal de reprise

## Où on en est

- **13 novembre, ticket 04 fait** : module `voice/` (ports, local sherpa-onnx, ElevenLabs, factice) et appli d'essai `app.regie.voicelab` (ADR 04). La mesure ElevenLabs n'est plus nécessaire : l'utilisateur a retenu la voix locale par défaut après l'avoir essayée. Recommandation du ticket : termes du dépôt favorisés activés par défaut.

**13 novembre 2026, cadrage terminé.**

- Maquette des 6 écrans validée « dans les grandes lignes » par l'utilisateur.
- PRD, ADR 01 et 12 tickets écrits.
- **Première vague lancée en parallèle**, une session par ticket, chacune dans son worktree :
  - 01 : socle Android ;
  - 02 : pont ;
  - 03 : accès des agents à Android ;
  - 04 : voix.
- **13 novembre, ticket 04 (voix) `bloqué`, branche prête à relire.** Le module `voice/` est livré : ElevenLabs, local sherpa-onnx et appli d'essai `app.regie.voicelab`. Le banc local a tourné sur la Thor. Il manque la clé ElevenLabs et la lecture des 20 consignes par l'utilisateur ; la marche à suivre est dans l'ADR 04.

- **13 novembre, ticket 01 fait** : socle Android livré et vérifié sur la Thor, y compris l'écran du bas éteint (ADR 05). R1 et l'écran du bas en arrière-plan tranchés par l'utilisateur.
- **13 novembre, ticket 02 fait** : pont ACP (ADR 02), contrat `docs/protocole-pont.md`, fixtures réelles dans `bridge/fixtures/bridge-ws/`. Reste à vérifier avec l'utilisateur : l'historique après un redémarrage de la console (marche à suivre dans le rapport du ticket).
- **14 novembre, ticket 05 bloqué sur un essai de l'utilisateur** : vue d'ensemble branchée sur le pont (ADR 06, module `android/gateway-bridge`). Reconnexion et rattrapage vérifiés sur la Thor, « Quitter » aussi. Relecture approuvée en 3 tours. Reste à faire par l'utilisateur pour le critère 3 : accorder `WRITE_SECURE_SETTINGS` (`pm grant`) et RUN_COMMAND, redémarrer la console, ouvrir Régie. Marche à suivre dans le rapport du ticket.

- **Ticket 03 bloqué** (13 novembre) : mobile-mcp déclaré aux deux agents sous `thor_android`, testé avec un échafaudage. Attend l'association du débogage sans fil par l'utilisateur (`thor/computer-use/README.md`) et deux réponses (ADR 03, « Questions ouvertes »).
- **Ticket 03, reprise** (13 novembre) : consignes globales des agents posées (`~dev/.claude/CLAUDE.md`, `~/.codex/AGENTS.md`, bloc entre marqueurs : `thor_android`, `thor-screen`, ne jamais toucher `app.regie`) ; serveur adb de la Debian sur une socket Unix privée (`/home/dev/.thor-adb/server`), plus rien sur 5037. Essayé par la socket avec un échafaudage retiré depuis. Reste bloqué sur la seule association du débogage sans fil.
- **Ticket 03 bloqué sur une question produit** (13 novembre, 23 h 40) : un agent peut-il ouvrir une appli par-dessus Régie ? Les consignes livrées l'interdisent en attendant. Pour le reste : association faite avec la clé de la Debian. Sur le chemin livré, sans le Mac, la consigne « À propos » réussit avec Claude puis avec Codex. mobile-mcp passe en mode adb seul par défaut (`MOBILEMCP_LEGACY_ROBOT=1`) : en mode mobilecli, des ports `127.0.0.1` s'ouvraient sur la console et mobilecli injectait son agent dans le build de debug de Régie. `pair.sh` refuse un serveur adb relayé. **Reste à faire avec l'utilisateur** : redémarrer la console une fois pour voir si le débogage sans fil revient seul, et retirer l'association du Mac dans « Appareils associés » si elle y est encore.
- **13 novembre, ticket 03 fait** : fusionné ; les deux questions sont tranchées par l'utilisateur (ticket 14 : liberté totale des agents).
- **13 novembre, ticket 13 fait** : le pont accepte seul les lectures shell en mode « valider » (règle stricte, testée), purge le détail des sessions après 30 jours (`REGIE_BRIDGE_RETENTION_DAYS`) et laisse les branches à l'agent (consigne en tête, plus de `git switch`). Essayé sur la Thor avec Codex, sur un pont isolé (port 47613). Protocole et ADR 02 à jour. Le pont installé sur la console est à réinstaller (`thor/bridge/install.sh`) après la fusion.
- **Ticket 14 à fusionner** (13 novembre, minuit) : les agents pilotent tout Android sans validation, Régie comprise. mobile-mcp passe en mobilecli par défaut ; pour revenir à adb seul : `touch ~/.config/thor-android/adb-seul`. Les permissions de Claude (`settings.json`) et de Codex (`approve` et `rules/thor-android.rules`) sont posées par `install.sh` et retirées par `uninstall.sh`. Essayé sur la Thor sans aucune validation. **L'association du débogage sans fil est à refaire par l'utilisateur** : l'essai de `uninstall.sh` a effacé la clé adb de la Debian.

- **14 novembre, ticket 06 à fusionner** : reprise solo Codex du travail non commité, écran de session livré (fil, plan, fichiers, texte de secours, L1/R1, X/Y/Start/B), ADR 07. 174 tests Android + lint verts ; installation et essais Thor, mode un seul écran forcé compris. Claude confirmé par captures précédentes et journal réel, nouveau Codex suivi jusqu'à fin puis interrompu, navigation par sendevent. Correctifs testés d'abord : comptes multi-fichiers, messages annulés avant livraison, lectures échouées. Limite Codex : `session.cancel` annule le tour mais `sleep 90` a continué jusqu'à `completed`. RUN_COMMAND / WRITE_SECURE_SETTINGS du ticket 05 restent à accorder ; pont lancé pour l'essai puis arrêté par Quitter. Preuves et pas à pas utilisateur : `artifacts/screenshots/06/essai-thor.txt`.

- **14 novembre, ticket 06, correction après revue bloquante** : livraison `UNKNOWN` après coupure ou délai sans accusé, sans remise du texte dans le champ ni renvoi automatique ; refus certains toujours réessayables. Quatre régressions rouges puis vertes, second A et origine dictée couverts : 181 tests Android + lint verts. APK réinstallé, envoi réel Codex terminé, navigation L1/R1 vérifiée ; aucun nouveau Claude. ADR 07 et preuves `artifacts/screenshots/06/delivery-*` mis à jour. Statut maintenu à `à fusionner`.

- **14 novembre, ticket 07 en échec d'environnement (`bloqué`)** : reprise solo Codex, branche propre sans travail antérieur. La porte initiale `cd android && ./gradlew check` échoue avant les tests : `SDK location not found`, aucun `android/local.properties` dans le worktree. Arrêt sans nouvel essai conformément à la consigne ; aucun code changé, aucune installation ni session d'agent lancée. Sortie exacte : `artifacts/screenshots/07/android-check.txt`. Configurer le SDK indiqué dans `CLAUDE.md`, puis reprendre le ticket tests d'abord ; détails dans son rapport.

- **14 novembre, ticket 07, deuxième reprise solo en échec (`bloqué`)** : SDK corrigé, préparation partielle du modèle des validations et des ports Android (184 tests + lint verts). Pas d’écran, pas de notification ni de nouveaux modes livrés. Installation npm faite à tort avec `--omit=optional` sur le Mac : Vitest échoue avant les tests (`Cannot find native binding`, `@rolldown/binding-wasm32-wasi` absent). Arrêt sans nouvel essai conformément à la consigne ; aucun changement du pont, aucune installation Thor ni session d’agent. Conserver les changements Android testés ; reprise détaillée dans le ticket, sortie exacte `artifacts/screenshots/07/red-modes.txt`. Claude `allow-with-updates` est durable, donc exclu du choix « cette session ». Ne pas considérer cette branche comme le ticket 07 livré.

- **14 novembre, ticket 07 à fusionner (reprise solo)** : écran des validations, file, A/X/B, R1 et un/deux écrans livrés ; refus avec consigne prêt pour 08 ; niveaux `plan`/`auto-edit`/`ask` au contrat du pont (fiche au 11), notifications via port (ADR 08). 196 tests Android + lint, 266 tests pont + typage/lint verts. Thor : commandes et éditions Codex acceptées/refusées, file et double B, modes, bas en arrière-plan et intent ciblé ; aucun Claude lancé (consigne quota). Permission notifications et essai Claude à faire avec l'utilisateur. Limite réelle : Codex redemande après `allow_for_session` sur le même fichier ; pas de contournement. Quatre sessions d'essai arrêtées par Quitter, aucune fusion/push. Preuves et pas à pas : `artifacts/screenshots/07/essai-thor.txt`.

- **14 novembre, ticket 07, correction de revue du canal** : `show()` renvoie faux si le canal est absent ou désactivé, pour permettre la nouvelle tentative. Régression du vrai adapter sous Robolectric 4.18 rouge puis verte, 197 tests Android + lint et 266 tests pont + typage/lint verts. APK réinstallé sur la Thor, permission laissée à l’utilisateur, demande résolue ouverte par intent sur les deux écrans puis retour régie et Quitter ; aucun agent lancé. ADR 08 et pas à pas canal bloqué/réactivé complétés. Captures 20–22 et preuves `review-channel-*`. Statut maintenu à `à fusionner`.

- **14 novembre, ticket 07, correction de revue R1** : retour à la première validation après la dernière, dans le domaine. Régression `[a, b]` ouverte sur `b` rouge puis verte ; file vide et réponse déjà donnée couvertes. 199 tests Android + lint, 266 tests pont + typage/lint verts. Thor : deux nouvelles demandes Codex, intent sur la dernière puis boucle R1 par `sendevent`, un/deux écrans, double refus protégé, commandes refusées et fichiers absents. Sessions d'essai et pont arrêtés par Quitter ; aucun Claude ni permission accordée. ADR 08 précisé, captures 23–28 et preuves `review-r1-*`. Ticket maintenu à `à fusionner`.

- **14 novembre, ticket 08 à fusionner (solo Codex)** : composite voice, local par défaut, ElevenLabs réglable avec clé chiffrée, dictée L2/relecture A/B/Y/X, vocabulaire du dépôt via session.vocabulary, refus avec consigne, cible du futur diff, service micro (ADR 09). Android 210 tests + lint, voix 127 + lint, pont 268 + typage/lint verts. Thor : WAV français sur moteur local réel, manette et tactile, correction/complément, un/deux écrans, message et refus suivis de réponse Codex. Qualité ASR imparfaite sur noms techniques/phrase longue ; micro réel et ElevenLabs avec clé restent à l'utilisateur. Aucun Claude ni permission accordée ; session d'essai et pont arrêtés par Quitter. Preuves et pas à pas : artifacts/screenshots/08/essai-thor.txt.

- **14 novembre, ticket 08, correction après relecture solo** : course entre résultat final/erreur et relâchement L2 corrigée dans le domaine ; deux régressions rouges puis vertes et tentative de changement de cible couverte. Android 212 tests + lint, voix 127 + lint, pont 268 + typage/lint verts. APK réinstallé, relecture WAV locale vérifiée (30–32), brouillon effacé sans envoi ; aucun nouvel agent, 30 sessions arrêtées, pont quitté à 02:03:33Z. Micro réel et ElevenLabs restent au pas à pas utilisateur ; statut à fusionner. Affichage « compriscompris » dans le fil relevé hors périmètre (30-haut).

- **14 novembre, ticket 08, correction de revue du service micro (solo Codex)** : mutex commun aux instances de ForegroundMicrophone, de l'ouverture à la fermeture complète de l'audio puis stopService ; B/L2 immédiat ne laisse plus l'ancienne capture arrêter la nouvelle. Test déterministe rouge puis vert, annulation en attente et erreur/reprise couvertes. Android 215 tests + lint, voix 127 + lint, pont 268 + typage/lint verts. APK réinstallé ; reprise WAV locale et permission absente vérifiées sur Thor (33–37), aucun agent/message lancé, pont arrêté par Quitter à 02:12:30Z. Critère des deux fournisseurs toujours décoché : permission micro, clé ElevenLabs, vraie voix et preuve texte relu/reçu à faire avec l'utilisateur (essai-thor.txt, étapes 1–8). Statut à fusionner selon la consigne nocturne ; ADR 09 complété.

- **14 novembre, ticket 08, correction de revue du panneau compact (solo Codex)** : micro tactile et niveau PCM rétablis en mode un écran, côte à côte ; deux régressions Compose/Robolectric rouges puis vertes après Compléter/Corriger. Android 217 tests + lint, voix 127 + lint, pont 268 + typage/lint verts. Thor : maintien tactile injecté sur display 0, WAV Thomas, complément puis remplacement du mot choisi à la croix, retour en deux écrans (38–48). Brouillon effacé sans envoi, aucun agent lancé ; pont quitté à 03:24:21 locale. Critère des deux fournisseurs toujours décoché : vraie voix, permission micro, clé et preuves relu/reçu restent à l'utilisateur. Statut à fusionner selon la consigne nocturne ; ADR 09 et pas à pas complétés.

- **14 novembre, ticket 12 à fusionner (solo Codex)** : points écrits en fin de tour et par Y, bilans demandés dans le tour normal de l’agent sans appel supplémentaire, notifications questions/fins/échecs (ADR 10). Android 226 tests + lint, pont 270 tests + typage/lint verts. Thor : Codex réel, un/deux écrans, Y, fin automatique, validation en arrière-plan et intents ciblés. Publication/clic notification restent à l’utilisateur : POST_NOTIFICATIONS refusée avec USER_FIXED, aucun droit accordé. Agent d’essai arrêté par bridge.shutdown, pas de Claude. Preuves et pas à pas : artifacts/screenshots/12/essai-thor.txt.

- **14 novembre, ticket 12, relecture solo après commit** : titre tardif sans réouverture du point fermé, question visible dans le panneau (9 lignes maximum), bilan incomplet correctement rejeté, notification d’échec indépendante du chargement du fil. Quatre régressions rouges puis vertes et test de hauteur Compose : Android 230 tests + lint, pont 270 tests + typage/lint verts. APK réinstallé ; même Codex repris pour un quatrième tour sans outil, ouverture à froid, Y et question en un/deux écrans vérifiés (captures 16–18). Quitter arrête la session et le pont. Aucun Claude ni permission accordée ; publication/clic Android toujours à vérifier par l’utilisateur. Statut à fusionner maintenu.

- **14 novembre, ticket 09 à fusionner (solo Codex)** : R2 capture un ordre distinct de L2, règles fixes puis Ministral 3B par appel de fonction (clé facultative, ADR 11), confirmation A et cible figée. Lancement minimal en plan, pause, validation, navigation et points écrits dans le domaine. Android check vert ; Thor : premier corpus local Thomas 14/20, aucun effet sans A, refus réel Codex confirmé. Aucun Claude ; vraie voix et clé Mistral restent à l’utilisateur. Éditeur de fiche au 11. Rapport et preuves : artifacts/screenshots/09/essai-thor.txt.

- **14 novembre, ticket 09, relecture solo terminée** : 250 tests Android + lint verts. Quatre régressions rouges puis vertes (maintien tactile Redire, variantes ASR, condition, fonction LLM tronquée). Thor : corpus final 15/20 (quatre noms erronés, un texte vide à froid repris séparément avec succès), autorisation/refus/pause/points/navigation, création Codex en plan et double A. Les deux Codex d'essai et le pont arrêtés par Quitter ; aucun Claude, clé ou permission. Critère des vingt lectures humaines et essai Mistral réel toujours décochés ; statut à fusionner selon la consigne nocturne, ADR 11 et pas à pas complétés. Preuves : artifacts/screenshots/09/essai-thor.txt.

- **14 novembre, ticket 10 à fusionner (solo Codex)** : diff par fichier/édition, croix/stick, commentaire L2 contextualisé, Y puis A pour explication écrite (ADR 12). 261 tests Android + lint verts. Thor : vrai Codex, WAV Thomas/local, seuil.ts ligne 1 passé de 70 à 80, double A protégé, réponse d’explication, tactile/un-deux écrans. Coordonnées d’extrait explicitement distinguées ; aucune reconstruction Git. Un seul Codex lancé puis arrêté par Quitter, aucun Claude ni permission. Vraie voix et extinction physique restent au pas à pas `artifacts/screenshots/10/essai-thor.txt`.

## Ordre des vagues

| Vague | Tickets | Commence quand |
| --- | --- | --- |
| 1 | 01, 02, 03, 04 | tout de suite, en parallèle |
| 2 | 05, 12 | 01, 02 (et 04 pour le 12) fusionnés |
| 3 | 06, puis 07 et 08 | 05 fusionné |
| 4 | 09, 10, puis 11 | 08 fusionné |

## Ordre des tickets d'interface (révisé le 14 novembre)

Les tickets qui touchent `android/` passent **un par un**. Ils installent tous le même APK (`app.regie`) sur la seule console, et se marcheraient dessus. L'ordre suit le chemin critique :
06 → 07 → 08 → 12 → 09 → 10 → 11. Le 08 passe avant le 12, parce qu'il branche la voix dans l'app.

## Règles d'orchestration

- Une session fraîche par ticket, dans son worktree. Elle teste sur la Thor et ne fusionne pas.
- L'orchestrateur relit, fusionne dans `main`, pousse sur le dépôt GitHub privé et lance la vague suivante. L'utilisateur l'a autorisé le 13 novembre.
- Une seule console pour tout le monde : voir `CLAUDE.md`, « La console ».

## Décisions de l'utilisateur

- 13 novembre : **rien ne démarre au boot**. Régie est une app qu'on lance ; c'est elle qui démarre le pont (RUN_COMMAND) et l'arrête quand on quitte.

- 13 novembre, sur les questions du ticket 03 :
  - Régie n'accepte une validation que d'un vrai bouton ou d'un vrai toucher ; les appuis injectés sont ignorés (ticket 07).
  - Les agents apprennent l'existence de `thor_android` et de `thor-screen` par leurs consignes globales sur la console : `CLAUDE.md` de Claude, `AGENTS.md` de Codex.
  - Le serveur adb de la Debian passe sur une socket Unix privée (`ADB_SERVER_SOCKET`) au lieu de `127.0.0.1:5037`.

- 13 novembre, sur les questions du ticket 01 :
  - R1 ouvre la validation en attente s'il y en a une, sinon passe à la session suivante.
  - L'écran du bas de Régie reste affiché quand l'app passe en arrière-plan ; « Quitter » le ferme.

- 13 novembre, après essai de l'appli voicelab : **la voix locale devient le fournisseur par défaut**, pour la transcription comme pour la synthèse. ElevenLabs reste un adapter optionnel. Ticket 12 : la lecture à voix haute utilise la synthèse locale, sauf réglage contraire.

- 13 novembre, sur les questions du ticket 02 (ticket 13) :
  - le pont accepte seul les commandes en lecture seule ;
  - le détail des sessions est gardé 30 jours, puis seulement leur résumé ;
  - Régie ne gère pas les branches : la branche demandée part dans la consigne, et l'agent s'en occupe ;
  - les enregistrements de test du pont restent tels quels, dépôt privé. L'app installée sur la Thor ne montre jamais de session factice : la passerelle factice ne sert qu'aux tests.

- 13 novembre, sur les questions du ticket 03, **et cela remplace les décisions de sécurité prises plus tôt** : les agents peuvent tout piloter sur Android, les deux écrans, Régie comprise, avec la méthode la plus libre (mobilecli). Pas de validation pour ces gestes, pas de règle « vraie main » dans Régie. Les restrictions viendront plus tard, si l'utilisateur en veut (ticket 14).

- 14 novembre : **pas de lecture à voix haute**. Les points et les résumés sont écrits. Le ticket 12 devient « Points écrits et notifications ».

## Questions en attente

- Nom définitif de l'app (« Régie » est un nom de travail).
- Lecture à voix haute : question close par la décision du 14 novembre, points écrits uniquement.
- Ticket 14 : `uninstall.sh` doit-il continuer à effacer la clé adb de la Debian ? Cela oblige à refaire l'association à la main après toute désinstallation.
