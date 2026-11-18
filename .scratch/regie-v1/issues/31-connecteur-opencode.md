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
- **Consignes globales** : opencode reçoit les mêmes consignes d'accès à Android que les deux autres, si son format le permet.

## Critères d'acceptation

- Tests du pont et du domaine. `npm run check` et `./gradlew check` sont verts.
- Sur la Thor : une session opencode se lance depuis Régie, répond à une consigne, et ses demandes de validation et ses options de configuration passent par les mêmes écrans.
