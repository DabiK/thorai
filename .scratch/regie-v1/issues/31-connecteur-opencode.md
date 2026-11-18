# 31 — opencode comme troisième agent

Statut : à fusionner
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

- [x] Tests du pont et du domaine. `npm run check` et `./gradlew check` sont verts : 336 tests pont, 450 Android, typage/lint ; traces dans `artifacts/screenshots/31/`.
- [ ] Sur la Thor autonome (agent dans la Debian) : attend la clé saisie par l’utilisateur. Le même parcours est prouvé sur la Thor avec le pont/agent sur le Mac et la connexion Go existante : création depuis Régie, réponse, A/B, modèle/réflexion et reprise. Pas à pas dans `artifacts/screenshots/31/essai-thor.md`.


## Rapport

- Livré : connecteur ACP natif opencode 2.1.2 ; catalogue ouvert (y compris voix et connexion), modes/configuration communs ; auth Go éphémère, stockage par l'API native et déconnexion ciblée ; installation officielle arm64, consignes/MCP Android ; contrat d'onboarding facultatif du ticket 28.
- Preuves : `artifacts/screenshots/31/essai-thor.md`, portes vertes, cycle de clé factice dans un profil Mac isolé, capt. 09 (réponse), 11 (validation), 15 (réglages), 20 (refus), 22 (connexion native vide). Aucun compte usuel déconnecté, aucune clé transférée.
- Relecture solo : sélecteur natif build/plan séparé des permissions, disponibilité initiale des fournisseurs et enveloppe API corrigées ; rejet d'agent inconnu avant création de projet ; quatrième onglet entièrement visible. Deux axes de revue conservés dans `relecture.md`.
- État : APK final release signé installé sans désinstallation ; pont Debian rétabli et 49 sessions conservées. Ancien Codex idle reprenable. Banc Mac et ses adapters arrêtés, reverse retiré. Aucun push/fusion/sous-agent.
- Décisions : ADR 02/18 complétés. Go seul dans le parcours de clé, autres fournisseurs et modèles natifs ; pas d'éditeur JSON dans l'app. Étape 28 documentée, non implémentée ici.
- Utilisateur : suivre les sept étapes d'`essai-thor.md` (saisir la clé Go dans l'app, puis essai autonome Debian, validation/refus, réglages, accès Android et reprise). Aucun blocage produit.
