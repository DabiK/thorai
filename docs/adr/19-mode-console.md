# ADR 19 — Mode console : L2 partout, message volant, session console

Statut : accepté, 18 novembre 2026 (ticket 25). Complète les ADR 05 (routeur d'entrées),
09 (dictée), 11 (ordres vocaux) et 17 (dictée et réglages en bas).

## Contexte

L'utilisateur veut parler à l'agent pendant qu'il pilote d'autres applis, sans revenir dans
Régie : Chrome ou YouTube en haut, Régie en bas, et la voix qui marche quand même. Jusqu'ici,
la manette n'arrivait à Régie que si une de ses deux fenêtres avait le focus : dès que
l'agent ouvrait Chrome sur l'écran du haut, L2 allait à Chrome.

## Décisions

### L2 et R2 depuis toute appli : un service d'accessibilité qui filtre la manette

- `ConsoleService` est un service d'accessibilité avec `canRequestFilterKeyEvents` et
  `flagRequestFilterKeyEvents`. Il ne lit ni le contenu des fenêtres
  (`canRetrieveWindowContent="false"`) ni les événements d'accessibilité. Android lui passe
  chaque touche avant l'appli qui a le focus, quel que soit l'écran.
- Il ne prend que ce dont Régie a besoin (`ConsoleKeys`, testé en JVM) :
  - L2 et R2, toujours ;
  - les autres boutons seulement pendant qu'un panneau de Régie les attend : relecture d'une
    dictée (A, B, X, Y, croix), ordre vocal (A, B, haut, bas), fiche ouverte par un ordre
    « lance … » (A, B, croix), carte volante (B, et A pour une validation qui l'offre). Le reste va
    à l'appli au premier plan.
  - Une carte toute neuve ne prend A et B qu'au bout d'une seconde : un A destiné à Chrome au
    moment où elle apparaît n'autorise rien. La garde du second A après une dictée (ADR 17)
    s'applique aussi à la carte.
  - Le relâché suit toujours son appui : une touche prise à l'appui est prise au relâché, une
    touche laissée passer l'est jusqu'au bout.
- Rien n'est pris quand une activité de Régie a la manette, c'est-à-dire quand elle est
  l'activité au premier plan du système (`onTopResumedActivityChanged`) : sa fenêtre lit déjà
  la manette, axes compris (ADR 05). Rien non plus avant que Régie soit branchée au pont, ni
  pendant « Quitter ».
- Un service d'accessibilité ne reçoit pas les mouvements sous Android 13 (il faudrait
  l'API 34). Les gâchettes de la Thor arrivent aussi en touches (`BTN_TL2`, `BTN_TR2`) :
  le service s'appuie sur elles.
- Le message dicté part vers la session au premier plan de Régie : celle affichée (session ou
  modifications), celle de la validation affichée, sinon celle qui a le focus dans la liste.
  Hors de Régie, L2 dicte toujours un message : on ne regarde ni le diff ni la validation. Un
  panneau passif resté ouvert dans Régie (réglages de la voix ou de la session, connexion des
  agents) se referme d'abord.

### Une seule permission spéciale

- La carte volante, la dictée et la bulle sont posées par le service lui-même, en
  `TYPE_ACCESSIBILITY_OVERLAY`. La permission de superposition (`SYSTEM_ALERT_WINDOW`) n'est
  donc pas demandée : sans le service, ni L2 ni la réponse depuis la carte ne marcheraient,
  la carte n'aurait pas de sens. Android ne compte pas une fenêtre d'accessibilité comme une
  superposition qui masque les touchers des autres applis.
- La fenêtre n'est ni focalisable ni tactile (l'écran du haut n'est pas tactile, la manette
  passe par le service). Elle n'existe que tant qu'il y a quelque chose à montrer.
- L'utilisateur active le service lui-même. Tant qu'il ne l'est pas, le bas de la vue
  d'ensemble montre « Mode console » : « L2 depuis toute appli : active Régie dans
  Accessibilité. » Un toucher ouvre les réglages d'accessibilité sur l'écran du bas, qui est
  tactile. Régie ne l'active jamais, ni par adb.

### Suivre l'agent sans revenir dans Régie

- **Écran du bas** : quand une autre appli occupe le haut, la table des réglages d'une session
  laisse la place au fil de la session en version compacte (messages, actions, validation en
  attente dans les touches). Les réglages restent à un geste : Droite, ou « → Modifier ». La
  dictée s'affiche en bas comme d'habitude (ADR 17).
- **Message volant** (`FloatingMessages`, domaine) : quand la conversation n'est visible nulle
  part, un message terminé de l'agent, sa question ou une validation qu'il demande devient une
  carte en haut à droite de l'écran du haut, par-dessus l'appli en cours. Précision de
  l'utilisateur, 18 novembre : la carte est de trop quand l'écran du bas montre Régie. Elle ne
  s'affiche donc que si aucun écran de Régie n'est visible : écran du bas éteint (mode un seul
  écran) ou occupé par une autre appli, et autre appli en haut.
  - Jamais un fragment en cours de flux : un texte est terminé quand une autre entrée le suit
    dans le fil, ou quand le tour est fini. Les textes intermédiaires (« J'ouvre YouTube. »)
    font donc une carte : c'est ce qui permet de suivre l'agent.
  - Jamais l'historique : ce qui est déjà là au premier chargement du fil, ou arrivé pendant que
    Régie était visible, reste silencieux.
  - Durée : 8 s pour un message, 15 s pour une question ou une validation. B ferme la carte ;
    Régie revenue en haut la ferme ; une validation réglée ailleurs aussi.
  - Une carte plus importante encore affichée n'est pas remplacée (validation, puis question,
    puis message) ; à importance égale, la plus récente gagne. Une carte à laquelle on répond ne
    cède qu'à une validation ; une validation qui n'a pas pu s'afficher passe dès que la carte en
    cours s'en va.
  - Touches : L2 « Répondre » (message de la session de la carte), B « Fermer ». Pour une
    validation : A « Autoriser une fois », B « Fermer », L2 « Refuser en dictant » (même règle
    que l'écran de validation), seulement si l'agent offre ces choix. Pendant la réponse, la
    dictée s'affiche en bas de l'écran du haut avec la phrase à laquelle on répond ; la carte part
    avec la dictée.
  - Un texte long garde sa fin (la conclusion ou la question), coupée entre deux phrases.
  - Les notifications Android restent (ADR 08 et 10).
- **Écran du bas éteint ou occupé** : la dictée ou l'ordre vocal en cours s'affichent en bas
  de l'écran du haut ; sinon la carte ; sinon une bulle discrète en haut à droite donne l'état de la session
  affichée dans Régie et la dernière phrase de l'agent, **tant qu'il travaille**. Une session
  au repos n'a rien à suivre : la carte suffit.
- Priorité de ce qui est posé (`OverlayContent.of`, testé) : rien dès qu'un écran de Régie est
  visible ; sinon la dictée ou l'ordre ; la carte ; la bulle.

### Dictée vide et retour système (bogue du 18 novembre)

- L2 relâché sans parole : rien n'est envoyé. Un message pour la session affichée se referme
  seul, et le bas revient à la table des réglages avec « Rien entendu. Aucun message envoyé. ».
  Ailleurs (dictée depuis l'accueil, refus, commentaire), la relecture reste avec cette ligne
  jusqu'à B. Un texte déjà relu (compléter, corriger) est gardé.
- Le retour système de l'écran du bas (sa barre de navigation, typiquement juste après avoir
  fermé le clavier) fermait `BottomActivity` pour de bon, et Régie ne la relançait qu'à son
  prochain passage au premier plan. Il n'a plus d'effet sur l'écran du bas ; le clavier se ferme
  toujours d'abord. Rouvrir Régie depuis le lanceur relance aussi un écran du bas disparu.

### Session « console »

- Y sur la vue d'ensemble (« Piloter la console ») reprend la session dont le projet est
  `console`, la plus récente, une session chargée d'abord. Rien n'est envoyé.
- S'il n'y en a pas, la fiche de nouvelle session s'ouvre pré-remplie : projet `console`
  (nouveau dossier vide `~/projects/console`, comme tout nouveau projet, ticket 15, s'il
  n'existe pas), agent de la dernière session utilisée (Codex à défaut), mode « Je valide
  chaque action » au ticket 25, remplacé par **« Tout autoriser » au ticket 26** (décision
  de l'utilisateur, 18 novembre). Ce défaut reste modifiable dans la fiche puis les réglages ;
  reprendre une session existante ne change pas son mode.
  A lance, comme toute fiche.
- Les consignes globales des agents (ticket 24) leur apprennent déjà à piloter Android de façon
  visible : la session console n'ajoute pas de consigne.

### Ordre vocal « où en est … » (décision de l'utilisateur, 18 novembre)

- « Où en est … » et « fais le point … » ouvrent la conversation de la session nommée, ou de
  la session active si aucune n'est nommée, comme L1/R1. Plus de point écrit
  (`VoiceCommand.Summary` est retiré ; `WrittenSummary` ne sert plus qu'à la détection de
  question des notifications).
- « Où en sont toutes les sessions ? » ouvre la vue d'ensemble.
- **Ticket 26, décision de l'utilisateur du 18 novembre** : la navigation ouvre directement
  après relâchement de R2 et transcription finale, sans relecture ni A ; le panneau vocal se
  ferme. Cela vaut aussi pour les autres formulations de navigation et la vue d'ensemble :
  aucun ordre n'est envoyé à l'agent. Les cibles restent uniques et figées à R2 ; les ordres
  qui agissent (lancer, approuver, refuser, interrompre) gardent A.
- L'heure du fil reste affichée une fois par minute (ADR 17).

## Vérification

- Tests : `ConsoleKeysTest` (prise et relâché), `FloatingMessagesTest` (fragment, historique,
  question, durée, épinglage, priorité, validation, extrait, choix de la session console),
  `ConsoleModeIntegrationTest` (de la touche lue par le service jusqu'au pont : L2 au-dessus de
  Chrome, A envoie, carte fermée par B, réponse par L2, validation par A, retour de Régie),
  `ConsoleModeTest` (fil compact en bas, carte, avis et Y), `VoiceCommandsTest`.
- Thor : session console créée par Y puis A ; dictée « Ouvre YouTube et cherche lofi » envoyée ;
  Codex ouvre la recherche dans Chrome en haut, le bas suit le fil. Captures
  `artifacts/screenshots/25/`.
- Non vérifié sur la console, faute de permission accordée par l'utilisateur : le service,
  la carte et la bulle. Pas à pas dans le ticket 25.

## Conséquences

- Les tickets suivants qui ajoutent un panneau à la manette doivent dire si le service doit lui
  passer des boutons (`AppContainer.consoleCaptured`).
- Le service garde le processus de Régie lié par Android, même après « Quitter » : la racine de
  composition y est alors vide (pas de pont branché), et le service laisse toute la manette aux
  autres applis.
