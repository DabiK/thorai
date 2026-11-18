# 25 — Mode console : parler à l'agent depuis n'importe quelle appli et le suivre

Statut : fait (L2 depuis une autre appli à vérifier avec l'utilisateur, après activation du service)
Possède : `android/`

## Retour de l'utilisateur, 18 novembre

> « J'aimerais que l'application puisse passer en arrière-plan et que je puisse quand même parler à l'agent : lui demander d'effectuer des actions sur la console sans perdre le contexte, sans revenir à l'appli pour chatter. Qu'il contrôle vraiment la console, qu'on puisse le suivre et le guider au fur et à mesure, un peu comme s'il incarnait la console elle-même. »

## À livrer

- **L2 maintenu fonctionne partout**, même quand une autre appli a le focus sur l'un ou l'autre écran.
  - Piste : service d'accessibilité de Régie qui filtre les touches de la manette (`canRequestFilterKeyEvents`). Il ne consomme que L2/R2, laisse passer tout le reste, et s'active une fois par l'utilisateur.
  - Le message dicté part vers la session « au premier plan » de Régie.
- **Suivre l'agent sans revenir dans l'appli.**
  - L'écran du bas garde Régie : la session en cours en version compacte (derniers messages, actions, validation en attente), la dictée, les réglages. L'écran du haut, lui, montre ce que l'agent pilote.
  - Si l'écran du bas est éteint ou occupé : une bulle flottante discrète sur l'écran du haut (`SYSTEM_ALERT_WINDOW`), avec l'état de l'agent et sa dernière phrase.
- **Message volant** (retour du 18 novembre) : quand l'agent envoie un message et que Régie n'est pas visible à l'endroit où l'utilisateur regarde, le message s'affiche dans une carte flottante sur l'écran du haut, par-dessus l'appli en cours. On voit l'agent, sa phrase, et éventuellement sa question. La carte s'efface seule après quelques secondes, ou avec B. Avec L2, on répond directement depuis la carte, sans revenir dans Régie. Pas de carte pour les fragments en cours de flux : seulement pour un message terminé, une question ou une validation demandée.
- **Une session « console »** : un raccourci pour lancer ou reprendre une session dont le dossier de travail est neutre, prévue pour piloter la console plutôt qu'un projet.
- **Les permissions spéciales** (accessibilité, superposition) sont demandées avec une explication courte. Ce sont des gestes de l'utilisateur.

## Ajouts en cours de ticket (18 novembre)

- **Ordre vocal « où en est … »** (décision de l'utilisateur) : il ouvre la conversation de la session visée, ou de la session active, comme L1/R1 ; plus de point écrit. L'heure du fil reste, une fois par minute.
- **Carte volante** (précision de l'utilisateur) : seulement si la conversation n'est visible nulle part. Écran du bas qui montre Régie = pas de carte.
- **Bogue** « quand je tape le texte dans la vue chat et que je parle pas, l'écran du bas crash » : à reproduire, corriger, avec un test.

## Critères d'acceptation

- [ ] Sur la Thor, Chrome ouvert en haut : L2 maintenu, « ouvre YouTube et cherche lofi », relâché, A → l'agent le fait sous les yeux de l'utilisateur, qui suit le fil sur l'écran du bas. → Prouvé de la touche au pont par `ConsoleModeIntegrationTest` ; sur la Thor, le même parcours depuis Régie (dictée dans Régie, Codex ouvre la recherche dans Chrome, le bas suit le fil). La partie « L2 au-dessus de Chrome » attend l'activation du service par l'utilisateur (pas à pas ci-dessous).

## Rapport

STATUT: à fusionner
RÉSUMÉ: mode console livré (ADR 19). Un service d'accessibilité de Régie prend L2 et R2 à toute appli, et les boutons qu'un panneau de Régie attend. Le bas suit la session en fil compact quand une autre appli occupe le haut. Une carte volante (message terminé, question, validation) passe par-dessus l'écran du haut quand aucun écran de Régie n'est visible ; L2 y répond, B la ferme. Bulle et dictée posées en haut si le bas est éteint ou occupé. Y ouvre ou prépare la session console. Une seule permission spéciale : la superposition n'est pas demandée, le service pose ses propres fenêtres.
CRITÈRES:
- [ ] L2 au-dessus de Chrome, A, l'agent agit, fil suivi en bas → tests de bout en bout verts ; parcours réel prouvé depuis Régie (`04` à `07`) ; geste « L2 dans Chrome » à refaire après activation du service.
- [x] Écran du bas : fil compact, dictée, réglages à un geste → `06-agent-pilote-bas`, `07-fin-tour-bas`, `ConsoleModeTest`.
- [x] Message volant : jamais un fragment ni l'historique, durée, B, L2, validation → `FloatingMessagesTest`, `ConsoleModeIntegrationTest` (pas vu sur la console, service inactif).
- [x] Carte seulement si la conversation n'est visible nulle part → `OverlayContent.of`, test d'intégration.
- [x] Session console → Y, fiche pré-remplie puis A (`02`, `03`), reprise au second Y.
- [x] Permission expliquée et demandée à l'utilisateur, jamais accordée par l'agent → avis « Mode console » (`01-accueil-bas`), toucher = réglages d'accessibilité.
- [x] « Où en est … » ouvre la session → `VoiceCommandsTest`.
- [x] Bogue de l'écran du bas → reproduit et corrigé (voir ci-dessous), `BottomBackTest`, `DictationReviewTest`.
PORTE DE QUALITÉ: `cd android && ./gradlew check` vert après fusion de `main` (tests JVM, Robolectric, lint).
THOR: APK installé deux fois par `scripts/thor-install.sh`. Session console créée, dictée WAV à usage unique envoyée, Codex ouvre YouTube « Lofi » dans Chrome en haut, le bas suit le fil. Bogue reproduit puis corrigé sur la console. WAV supprimé, Régie relancée sans extra. Détail : `artifacts/screenshots/25/essai-thor.md`.
REVIEW: relecture indépendante (sous-agent) : 1 bloquant, 3 importants, 8 mineurs. Corrigés : A d'une carte toute neuve ou juste après une dictée, validation masquée par une carte épinglée, fiche ouverte par R2 hors de Régie, manette suivie par instance, catalogue de la session console, touches selon les choix de l'agent, titre du fil compact, renvois d'ADR. La carte gênée par l'écran du bas a disparu avec la nouvelle règle. Reste non vérifiable sans le service : le micro démarré quand aucun écran de Régie n'est visible.
DÉCISIONS PRISES: une seule permission (accessibilité), fenêtres en `TYPE_ACCESSIBILITY_OVERLAY` ; touches de la carte actives après 1 s ; bulle seulement pendant que l'agent travaille ; session console dans `~/projects/console`, mode proposé « Je valide chaque action ». Le retour système de l'écran du bas n'a plus d'effet. Détail : ADR 19.
BOGUE SIGNALÉ: le scénario qui plantait est la lecture (b). Après la saisie au clavier dans le champ du bas, le retour système de la barre de navigation du bas fermait `BottomActivity` (`wm_finish_activity … app-request`). Le lanceur prenait le bas, et rouvrir Régie ne le ramenait pas. Le journal de l'utilisateur (16:32) montre la même suite, par un toucher de la barre de navigation du bas. Corrigé : le retour système ne ferme plus le bas, et rouvrir Régie relance un bas disparu. Lecture (a) : sur la console, le silence donne une transcription du bruit (« Yeah. »), rien ne part sans A ; une transcription vraiment vide restait en relecture vide. Elle se referme désormais seule avec « Rien entendu. Aucun message envoyé. ».
POUR L'UTILISATEUR:
1. En bas de la régie, toucher l'avis « Mode console » : Accessibilité s'ouvre sur l'écran du bas.
2. Dans « Applications téléchargées » (ou « Services installés »), choisir **Régie, mode console**, l'activer et accepter. Si Android parle de paramètre restreint : Infos de l'appli Régie → ⋮ → « Autoriser les paramètres restreints », puis recommencer.
3. Revenir dans Régie : l'avis disparaît. Y → session console.
4. Critère : ouvrir Chrome en haut (ou laisser l'agent l'ouvrir), maintenir L2, dire « ouvre YouTube et cherche lofi », relâcher, A. Le bas montre la transcription puis le fil.
5. Carte : éteindre l'écran du bas (ou y ouvrir une autre appli), demander une action ; une carte arrive en haut à droite. B la ferme ; L2 y répond. Si la dictée reste muette dans ce cas, relever `adb -s THOR0000000001 logcat -d -s Regie` (micro démarré sans écran de Régie visible).
QUESTIONS:
- Mode de la session console : « Je valide chaque action » demande une validation pour chaque commande (ex. `/opt/thor-android/start.sh`). Préfères-tu « Tout autoriser » pour cette session ?
- Ordre vocal « où en est … » : il garde la relecture avant A (« Ouvrir : … »), comme « passe à … ». Faut-il l'ouvrir directement, sans A ?
CAPTURES: `artifacts/screenshots/25/` : `00` à `07` (accueil, fiche et session console, dictée, agent qui pilote, fil compact), `10` à `13` (bogue reproduit), `15` à `17` (après correction, dictée de bruit effacée), `essai-thor.md`.
HORS PÉRIMÈTRE: avec le clavier ouvert, le bas ne montre que le champ (comportement du ticket 22, inchangé). La voix locale transcrit le bruit ambiant en mots.
