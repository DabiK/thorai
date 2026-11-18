# 31 — opencode comme troisième agent

Statut : en attente (après 27)
Possède : `bridge/`, `android/domain` (modèle des agents), le choix de l'agent dans l'app, `thor/` (installation)

## Objectif

Demande de l'utilisateur, 18 novembre : ajouter opencode comme connecteur, à côté de Claude Code et Codex. L'architecture est hexagonale ; ce doit être un adapter de plus, pas un cas particulier partout.

## À livrer

- **Vérifier dans la documentation à jour comment piloter opencode** : support d'ACP (`opencode acp` ou équivalent), sinon son serveur ou son SDK. S'il parle ACP, le brancher comme les deux autres derrière le même connecteur. Noter le choix dans l'ADR 02.
- **Généraliser la notion d'agent** : le pont annonce la liste des agents disponibles (nom, version, état de connexion, options de configuration). L'app construit ses listes (fiche, filtres, réglages, connexion) à partir de cette liste, au lieu d'une énumération figée Claude/Codex. Un quatrième agent ne doit plus demander de changer l'app.
- **Installation d'opencode** dans la Debian de la Thor : par `thor/install-on-device.sh` et par les scripts lancés depuis le Mac, avec la build arm64 officielle.
- **Connexion** : comment opencode s'authentifie (fournisseurs, `opencode auth login`), et ce que l'écran de connexion du ticket 23 peut en montrer. Aucun secret dans l'app ni dans les journaux.
- **Précisions de l'utilisateur (18 novembre)** :
  - **Son usage : « opencode Go ».** Il lui faut en général une simple clé d'API. Vérifier dans la doc à jour ce qu'est exactement cette offre ou ce fournisseur d'opencode, et comment opencode s'y authentifie.
  - Gérer les nuances de configuration d'opencode (fournisseurs, modèles, `opencode.json`) si c'est raisonnable. Sinon, rester concentré sur opencode Go pour l'authentification.
  - **Onboarding** : le parcours d'accueil (ticket 28) aura une étape opencode, facultative si l'utilisateur ne s'en sert pas. Le pont expose ce qu'il faut pour cette étape : état de connexion, saisie de la clé par l'utilisateur dans l'app, envoi au pont, stockage par opencode lui-même dans ses fichiers, jamais dans les journaux. Documente-le dans le protocole pour que l'agent du ticket 28 le branche.
  - **Essais avec de vrais identifiants** : l'utilisateur a des identifiants opencode sur ce Mac. Teste opencode avec le pont lancé sur le Mac (`scripts/bridge-mac.sh`), en t'appuyant sur cette configuration existante. Ne lis, n'affiche, ne copie et ne commite jamais la valeur d'une clé, et ne la transfère pas sur la Thor. Sur la Thor, c'est l'utilisateur qui saisira sa clé dans l'app.
- **Consignes globales** : opencode reçoit les mêmes consignes d'accès à Android que les deux autres, si son format le permet.

## Critères d'acceptation

- Tests du pont et du domaine. `npm run check` et `./gradlew check` sont verts.
- Sur la Thor : une session opencode se lance depuis Régie, répond à une consigne, et ses demandes de validation et ses options de configuration passent par les mêmes écrans.
