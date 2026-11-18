# 23 — Se connecter à Claude et à Codex depuis la console

Statut : à fusionner
Possède : `bridge/`, `android/` (écran de connexion)

## Retour de l'utilisateur, 18 novembre

« Le mécanisme d'authentification de Claude ou de Codex, c'est toi qui l'as configuré. Si l'application est livrée seule, une personne doit pouvoir se connecter directement depuis la console, je pense par device code. »

## À livrer

- **Le pont expose, pour chaque agent** : état de connexion (connecté ou non, compte si l'agent le donne sans secret), lancement d'une connexion, déconnexion.
- **Connexion par code d'appareil** si l'agent le propose :
  - Codex : `codex login --device-auth`.
  - Claude Code : vérifier dans sa documentation à jour s'il existe un flux par code ou par URL à ouvrir sur un autre appareil, en mode sans interface. Sinon, trouver l'équivalent le plus simple et l'écrire dans l'ADR.
- **L'app** montre l'URL et le code en grand, avec un QR code à scanner au téléphone, puis le résultat. La connexion se fait depuis l'accueil ou les réglages, et elle est proposée au premier lancement si un agent n'est pas connecté.
- **Aucun secret** ne transite par l'app ni par les journaux : les jetons restent dans les fichiers des agents.

## Critères d'acceptation

- [ ] Sur la Thor, une déconnexion de Codex depuis Régie, puis une reconnexion par code d'appareil, se font sans terminal ni Mac.
- [x] Même chose pour Claude, si son CLI le permet. Sinon, la limite est écrite et la marche manuelle est indiquée dans l'app.


## Rapport

STATUT: done
RÉSUMÉ: État, connexion Codex par code/QR, annulation et déconnexion accessibles depuis l’accueil.
Claude expose son état et sa déconnexion ; la limite du CLI et le parcours Termux sont indiqués dans l’app (ADR 17).
CRITÈRES:
- [x] Méthodes du pont et isolation des secrets → 321 tests pont, contrat WS et sélection explicite des champs publics.
- [x] Premier lancement, manette/tactile, QR et résultat → 371 tests Android et captures 01, 09–15 sur émulateur.
- [x] Limite Claude et marche manuelle → aide 2.1.322 sur Thor, documentation actuelle, capture 14.
- [ ] Cycle réel du compte habituel depuis l’app Thor → différé avec l’utilisateur ; aucun APK installé ni compte courant déconnecté.
PORTE DE QUALITÉ: android/ ./gradlew check vert (371 tests + lint) ; bridge/ npm run check vert (321 tests + typage/lint).
THOR: série THOR0000000001 ; vrais état/challenge/annulation/logout du seul profil Codex temporaire ; comptes habituels toujours connectés.
REVIEW: courses callback/lecture périmée, annulation après accusé perdu, récupération réseau, arrêt/expiration et champs inattendus corrigés par tests rouges/verts.
DÉCISIONS PRISES: ADR 17 ; QR local ZXing, états transitoires hors journal, changements de compte avant ACP, B depuis l’accueil.
POUR L’UTILISATEUR: après intégration, Quitter/rouvrir, B → Codex, A puis B garde le compte, A puis A déconnecte ; A lance le code, scanner/saisir au téléphone, attendre Connecté ; créer une session Codex puis Quitter. Pour Claude, suivre Termux puis Y. Détail : artifacts/screenshots/23/essai.md, six étapes.
QUESTIONS: aucune.
CAPTURES: artifacts/screenshots/23/ ; les captures « simulation » n’attestent pas une connexion fournisseur réelle.
HORS PÉRIMÈTRE: le port Mac 47600 est relayé vers la Thor du ticket 22 ; pont/état temporaires isolés sur 47623, redirection existante préservée.
