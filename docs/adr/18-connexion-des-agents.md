# ADR 18 — Connexion des agents depuis Régie

Statut : accepté, 18 novembre 2026 (ticket 23).

## Constat

Les sessions ACP réutilisent la connexion des CLI. Le pont doit aussi pouvoir lire
cet état avant de lancer un agent et permettre de changer de compte depuis l’app.
Les identifiants restent gérés par chaque CLI, avec les mêmes binaires, environnement
et répertoires que les adapters ACP. Régie ne lit jamais leurs fichiers de jetons.

Documentation vérifiée le 18 novembre par Context7 (`library`, puis `docs`), pages
officielles et aide des binaires **Codex 0.167.2 / Claude Code 2.1.322** sur la Thor :

- [Codex, authentification](https://developers.openai.com/codex/auth/) :
  `codex login --device-auth` fournit une URL et un code à saisir sur le téléphone.
  L’option peut nécessiter une activation dans les réglages de sécurité ChatGPT
  ou par l’administrateur du compte. `codex login status` et `codex logout` donnent
  l’état et retirent la connexion locale. Aucun compte n’est inventé si le CLI ne
  le donne pas.
- [Claude, commandes](https://code.claude.com/docs/en/cli-reference) et
  [connexion distante](https://code.claude.com/docs/en/troubleshoot-install#oauth-login-fails-in-wsl2-ssh-or-containers) :
  `claude auth status --json`, `claude auth login`, `claude auth logout`.
  Ni l’aide de la version installée ni ces pages n’exposent de flux device-code.
  Le repli distant affiche une URL puis attend que l’utilisateur recopie le code
  OAuth du navigateur dans le terminal. Ce code autorise un échange de jetons :
  il ne passe donc pas par Régie. Le CLI MCP `--no-browser` concerne un autre
  service et ne connecte pas le compte Claude.

## Décisions

- Port `AuthenticationDriver`, cas d’usage `Authentication` dans le domaine du
  pont ; adapter CLI séparé, sans règle de parcours. `auth.status`, `auth.start`,
  `auth.cancel`, `auth.logout` réutilisent le WebSocket protégé existant.
- Codex : sous-processus `login --device-auth`, une tentative à la fois.
  Seules l’URL exacte `https://auth.openai.com/codex/device` et une ligne de code
  au format observé sont admises. Les fragments et couleurs ANSI sont décodés.
  Les sorties brutes, erreurs et clés affichées par `login status` ne sortent
  jamais de l’adapter. Une sortie non comprise donne un état inconnu.
- Le processus s’arrête au bout de quinze minutes au plus, ou sur annulation /
  arrêt du pont : SIGTERM puis SIGKILL après une seconde si nécessaire. Le code
  disparaît à la fin. Une réussite est suivie de la lecture de l’état réel.
  L’annulation arrête l’attente locale ; elle ne prétend pas révoquer le code
  déjà délivré par le fournisseur.
- Claude : état et déconnexion exposés ; démarrer la connexion retourne le
  parcours manuel, sans lancer de processus interactif abandonné. L’app indique :
  ouvrir Termux, entrer dans Debian avec `proot-distro login debian --user dev`
  et les montages projects/.codex du pont, puis lancer `claude auth login`,
  ouvrir son URL sur le téléphone, recopier le code dans Termux, revenir puis
  Actualiser. La commande complète est donnée dans l'app (ticket 27) : aucun
  lanceur `claude` dans Termux n'est supposé.
- Métadonnée de compte facultative : seul l’e-mail public de `auth status` de
  Claude est retenu, s’il est fourni. Exception limitée à `auth.*.account` à la
  règle historique de l’ADR 02 ; les mises à jour ACP restent filtrées. Aucun
  e-mail n’est journalisé par ce parcours.
- Ni challenge ni résultat d’authentification dans le journal des sessions.
  Aucun jeton, clé ou code OAuth de retour dans l’app ou dans les logs du pont.
  Le QR est calculé localement avec **ZXing core 3.5.4**, épinglé au catalogue
  Gradle : [API QRCodeWriter](https://zxing.github.io/zxing/apidocs/com/google/zxing/qrcode/QRCodeWriter.html),
  vérifiée par Context7. Il contient uniquement l’URL publique, jamais le code.
- Un changement de compte est refusé si le processus ACP de cet agent tourne,
  même au repos. L’app explique de Quitter puis rouvrir Régie avant de le faire.
  Cela protège les sessions et évite de garder en mémoire l’ancien compte.
  Une connexion en cours empêche aussi de démarrer ce processus ACP.

## Interface et limites de preuve

Écran dédié ; aucune modification des écrans Session/Accueil. Une seule ligne
`B · Connexion des agents` est ajoutée à l’accueil dans la composition commune.
Gauche/droite choisit l’agent, A connecte ou demande la confirmation de déconnexion,
B ferme ou annule, Y actualise. Les actions sont tactiles aussi. Au premier
contact du processus avec le pont, un agent connu comme non connecté est proposé
une fois ; aucune connexion ne démarre seule. Revenir à l’accueil reste possible.
Le domaine Android garde ces règles derrière `AgentAuthenticationGateway`.

La source distante du canvas n’était pas accessible et aucun outil Artifact
n’était disponible. L’export local `Session.dc.html`, correspondant à l’empreinte
du ticket 21, a été lu ; polices, palette et séparation contenu/actions repris.
Il n’existe pas de maquette Connexion dans les six écrans de référence.

Tests hors services : domaine du pont, processus Node synthétique pour l’expiration,
WebSocket, domaine Android, passerelle, composition/manette et rendu Compose natif.
Le QR est décodé indépendamment pour contrôler son contenu.
Sur Thor, seuls les CLI et ces nouveaux ports/cas d’usage ont été exécutés :
challenge réel Codex, annulation, déconnexion d’un **profil temporaire vide**,
états habituels connectés inchangés avant/après. Aucun APK installé sur Thor.
L’app a été essayée sur l’émulateur à deux écrans avec un pont Mac isolé 47623
(47600 est déjà redirigé vers la Thor pour un autre ticket).
Les captures de résultat/déconnexion sont des simulations explicitement nommées.
La reconnexion du compte habituel sur la vraie console attend l’utilisateur.
Preuves et pas à pas : `artifacts/screenshots/23/essai.md`.

## Ticket 31 — clé Go saisie depuis l'app

L'interdiction historique de faire transiter un secret dans l'app connaît une
exception explicite demandée par l'utilisateur : une clé **saisie par lui** pour
OpenCode Go. Champ masqué, état Compose éphémère non sauvegardé, effacement dès
l'envoi et à la fermeture/changement d'agent ; le domaine ne conserve pas la clé.
`auth.key` ne renvoie que l'état public. Les réponses et erreurs ne citent jamais
les paramètres reçus. Les comptes déjà présents sur le Mac restent sur le Mac.
L'ADR 02 décrit le serveur natif privé utilisé pour laisser opencode enregistrer
la clé ; le catalogue déclare `login.method`, `label`, `help` et `provider` afin que
l'écran de connexion et le futur onboarding n'aient pas de branche opencode.
